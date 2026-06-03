#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";

const TABLES = [
  {
    name: "language_pairs",
    columns: [
      "id",
      "source_language",
      "target_language",
      "source_locale",
      "target_locale",
      "ui_locale",
      "source_voice",
      "target_voice",
      "target_slow_voices_json",
      "description",
      "active",
      "created_at",
      "updated_at",
    ],
  },
  {
    name: "app_instances",
    columns: [
      "id",
      "display_name",
      "language_pair_id",
      "ui_locale",
      "content_version_id",
      "settings_json",
      "active",
      "created_at",
      "updated_at",
    ],
  },
  {
    name: "ui_labels",
    columns: ["locale", "label_key", "label_value", "updated_at"],
  },
  {
    name: "content_versions",
    columns: ["id", "language_pair_id", "version", "title", "summary", "active", "created_at"],
  },
  {
    name: "content_lessons",
    columns: [
      "content_version_id",
      "lesson_id",
      "lesson_type",
      "sort_order",
      "title",
      "summary",
      "payload_json",
      "updated_at",
    ],
  },
  {
    name: "audio_assets",
    columns: [
      "sha1",
      "text",
      "locale",
      "voice",
      "r2_key",
      "public_url",
      "bytes",
      "uploaded",
      "last_error",
      "created_at",
      "updated_at",
    ],
  },
  {
    name: "content_audio_requirements",
    columns: ["content_version_id", "sha1", "role"],
  },
  {
    name: "sync_events",
    columns: ["id", "instance_id", "event_type", "payload_json", "created_at"],
  },
];

const env = process.env;
const requiredEnv = [
  "OLD_CF_ACCOUNT_ID",
  "OLD_D1_DATABASE_ID",
  "OLD_CF_API_TOKEN",
  "NEW_CF_ACCOUNT_ID",
  "NEW_D1_DATABASE_ID",
  "NEW_CF_API_TOKEN",
];

for (const key of requiredEnv) {
  if (!env[key]) {
    throw new Error(`${key} is required`);
  }
}

const scriptDir = path.dirname(new URL(import.meta.url).pathname);
const defaultSchemaPath = path.resolve(scriptDir, "../migrations/001_schema.sql");
const schemaPath = env.D1_SCHEMA_PATH || defaultSchemaPath;
const chunkSize = Number(env.D1_COPY_CHUNK_SIZE || 100);
const insertRowsPerBatch = Number(env.D1_INSERT_ROWS_PER_BATCH || 200);

async function main() {
  await applySchema();
  await clearDestination();

  const summary = {};
  for (const table of TABLES) {
    summary[table.name] = await copyTable(table);
    console.log(
      `${table.name}: old=${summary[table.name].oldCount} copied=${summary[table.name].copied} new=${summary[table.name].newCount}`,
    );
  }

  if (env.D1_COPY_SUMMARY_PATH) {
    await fs.writeFile(env.D1_COPY_SUMMARY_PATH, `${JSON.stringify(summary, null, 2)}\n`);
  }
}

async function applySchema() {
  const schema = await fs.readFile(schemaPath, "utf8");
  for (const statement of splitSql(schema)) {
    await queryNew(statement);
  }
}

async function clearDestination() {
  for (const table of [...TABLES].reverse()) {
    await queryNew(`DELETE FROM ${table.name}`);
  }
}

async function copyTable(table) {
  const oldCount = await countRows("old", table.name);
  let copied = 0;
  for (let offset = 0; offset < oldCount; offset += chunkSize) {
    const rows = await selectRows(table.name, chunkSize, offset);
    copied += await insertRows(table, rows);
  }
  const newCount = await countRows("new", table.name);
  return { oldCount, copied, newCount };
}

async function countRows(side, tableName) {
  const response = side === "old"
    ? await queryOld(`SELECT COUNT(*) AS count FROM ${tableName}`)
    : await queryNew(`SELECT COUNT(*) AS count FROM ${tableName}`);
  return Number(response.results[0]?.count || 0);
}

async function selectRows(tableName, limit, offset) {
  const response = await queryOld(`SELECT * FROM ${tableName} LIMIT ? OFFSET ?`, [limit, offset]);
  return response.results || [];
}

async function insertRows(table, rows) {
  if (rows.length === 0) return 0;

  let inserted = 0;
  for (const batch of chunks(rows, insertRowsPerBatch)) {
    const valuesSql = batch
      .map((row) => `(${table.columns.map((column) => sqlLiteral(row[column])).join(", ")})`)
      .join(", ");
    const sql = `INSERT OR REPLACE INTO ${table.name} (${table.columns.join(", ")}) VALUES ${valuesSql}`;
    await queryNew(sql);
    inserted += batch.length;
  }
  return inserted;
}

function sqlLiteral(value) {
  if (value === null || value === undefined) return "NULL";
  if (typeof value === "number" && Number.isFinite(value)) return String(value);
  if (typeof value === "boolean") return value ? "1" : "0";
  return `'${String(value).replace(/'/g, "''")}'`;
}

function chunks(items, size) {
  const out = [];
  for (let i = 0; i < items.length; i += size) {
    out.push(items.slice(i, i + size));
  }
  return out;
}

function splitSql(sql) {
  return sql
    .replace(/--.*$/gm, "")
    .split(";")
    .map((statement) => statement.trim())
    .filter(Boolean);
}

async function queryOld(sql, params = []) {
  return queryCloudflare(env.OLD_CF_ACCOUNT_ID, env.OLD_D1_DATABASE_ID, env.OLD_CF_API_TOKEN, sql, params);
}

async function queryNew(sql, params = []) {
  return queryCloudflare(env.NEW_CF_ACCOUNT_ID, env.NEW_D1_DATABASE_ID, env.NEW_CF_API_TOKEN, sql, params);
}

async function queryCloudflare(accountId, databaseId, token, sql, params = []) {
  const response = await fetch(
    `https://api.cloudflare.com/client/v4/accounts/${accountId}/d1/database/${databaseId}/query`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ sql, params }),
    },
  );
  const body = await response.json();
  if (!response.ok || !body.success) {
    const errors = body.errors?.map((error) => error.message).join("; ") || response.statusText;
    throw new Error(`D1 query failed for ${databaseId}: ${errors}\nSQL: ${sql.slice(0, 300)}`);
  }
  return body.result[0] || { results: [] };
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
