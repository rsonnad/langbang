#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const DEFAULT_API_BASE = "https://langbangml-api.langbangml.workers.dev";
const DEFAULT_R2_BASE = "https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev";

const args = parseArgs(process.argv.slice(2));
const apiBase = args.apiBase || process.env.LANGBANGML_API_BASE || DEFAULT_API_BASE;
const r2Base = args.r2Base || process.env.LANGBANGML_R2_BASE || DEFAULT_R2_BASE;
const r2Version = Number(args.r2Version || 4);
const batchSize = Number(args.batchSize || 28);
const mode = args.mode || "both";
const geminiScope = args.geminiScope || "all";
const limit = args.limit ? Number(args.limit) : null;
const outDir = path.resolve(args.out || `/private/tmp/langbangml-common-phrase-audit-v${r2Version}`);

if (!["deterministic", "gemini", "both"].includes(mode)) {
  fail(`Unsupported --mode=${mode}`);
}
if (!["all", "deterministic"].includes(geminiScope)) {
  fail(`Unsupported --gemini-scope=${geminiScope}`);
}

main().catch((error) => {
  console.error(error?.stack || error);
  process.exit(1);
});

async function main() {
  fs.mkdirSync(outDir, { recursive: true });
  const records = await collectRecords();
  const unique = dedupeRecords(records).slice(0, limit || undefined);
  const deterministic = unique
    .map((record) => ({ ...record, issues: deterministicIssues(record) }))
    .filter((record) => record.issues.length > 0);

  writeJson("records.json", unique);
  writeJson("deterministic-flags.json", deterministic);

  let geminiResults = [];
  if (mode === "gemini" || mode === "both") {
    const toAudit = geminiScope === "deterministic"
      ? deterministic
      : unique;
    geminiResults = await runGeminiAudit(toAudit);
    writeJson("gemini-results.json", geminiResults);
  }

  const summary = summarize(unique, deterministic, geminiResults);
  writeJson("summary.json", summary);
  writeMarkdown(summary, deterministic, geminiResults);
  console.log(JSON.stringify(summary, null, 2));
  console.error(`Wrote audit files to ${outDir}`);
}

async function collectRecords() {
  const out = [];
  out.push(...collectLocalAssets());
  out.push(...await collectLiveBootstrap("langbangml-en-pl"));
  out.push(...await collectLiveBootstrap("langbangml-pl-en"));
  out.push(...await collectR2Sentences());
  return out.filter((record) => record.sourceText || record.targetText);
}

function collectLocalAssets() {
  const assets = path.join(ROOT, "app/src/main/assets");
  const out = [];
  for (const file of ["lesson-02.json", "lesson-05.json"]) {
    const payload = JSON.parse(fs.readFileSync(path.join(assets, file), "utf8"));
    extractPayloadRecords(out, {
      origin: `local:${file}`,
      contentVersionId: "local",
      lessonId: payload.id || file.replace(/\.json$/, ""),
      payload,
    });
  }
  return out;
}

async function collectLiveBootstrap(instanceId) {
  const url = `${apiBase.replace(/\/+$/, "")}/v1/instances/${encodeURIComponent(instanceId)}/bootstrap`;
  const bootstrap = await fetchJson(url);
  const out = [];
  for (const lesson of bootstrap?.content?.lessons || []) {
    extractPayloadRecords(out, {
      origin: `live:${instanceId}`,
      contentVersionId: bootstrap?.content?.versionId || "",
      lessonId: lesson.id,
      payload: lesson.payload || {},
    });
  }
  return out;
}

async function collectR2Sentences() {
  const manifestUrl = `${r2Base.replace(/\/+$/, "")}/langbang/sentences/v${r2Version}/manifest.json`;
  const manifest = await fetchJson(manifestUrl);
  const entries = Object.entries(manifest.entries || {});
  const out = [];
  let done = 0;
  for (const [key, entry] of entries) {
    const url = args.trustManifestUrls === "true" && entry.url
      ? entry.url
      : `${r2Base.replace(/\/+$/, "")}/langbang/sentences/v${r2Version}/${key}`;
    const sentences = await fetchJson(url);
    if (Array.isArray(sentences)) {
      sentences.forEach((sentence, index) => {
        pushRecord(out, {
          origin: `r2:v${r2Version}`,
          contentVersionId: `r2-sentences-v${r2Version}`,
          lessonId: key.split("/")[0],
          groupId: key,
          index,
          sourceText: sentence.en || sentence.source || "",
          targetText: sentence.pl || sentence.target || "",
          literal: sentence.literal || "",
        });
      });
    }
    done += 1;
    if (done % 25 === 0) console.error(`Fetched ${done}/${entries.length} R2 bundles`);
  }
  return out;
}

function extractPayloadRecords(out, context) {
  const payload = context.payload || {};
  if (Array.isArray(payload.phrases)) {
    payload.phrases.forEach((phrase, index) => {
      pushRecord(out, {
        ...context,
        groupId: "phrases",
        index,
        sourceText: phrase.en || phrase.source || "",
        targetText: phrase.pl || phrase.target || "",
        literal: phrase.literal || "",
      });
    });
  }
  if (Array.isArray(payload.groups)) {
    for (const group of payload.groups) {
      (group.sentences || []).forEach((sentence, index) => {
        pushRecord(out, {
          ...context,
          groupId: group.id || "group",
          index,
          sourceText: sentence.en || sentence.source || "",
          targetText: sentence.pl || sentence.target || "",
          literal: sentence.literal || "",
        });
      });
    }
  }
}

function pushRecord(out, value) {
  const sourceText = clean(value.sourceText);
  const targetText = clean(value.targetText);
  if (!sourceText && !targetText) return;
  out.push({
    id: "",
    origin: value.origin,
    contentVersionId: value.contentVersionId,
    lessonId: value.lessonId,
    groupId: value.groupId || "",
    index: Number(value.index || 0),
    sourceText,
    targetText,
    literal: clean(value.literal),
  });
}

function dedupeRecords(records) {
  const byKey = new Map();
  for (const record of records) {
    const key = `${normalize(record.sourceText)}\n${normalize(record.targetText)}`;
    const existing = byKey.get(key);
    if (existing) {
      existing.occurrences.push(locationOf(record));
      continue;
    }
    const id = `p${String(byKey.size + 1).padStart(5, "0")}`;
    byKey.set(key, { ...record, id, occurrences: [locationOf(record)] });
  }
  return [...byKey.values()];
}

function deterministicIssues(record) {
  const en = record.sourceText.toLowerCase();
  const pl = record.targetText.toLowerCase();
  const issues = [];
  const checks = [
    ["listen_about", /\blisten(?:ing|s)? about\b/, "English collocation should be 'listen to' or 'hear/talk/read about'."],
    ["sick_love", /\blove (?:a |the )?sick (?:dog|cat|child|neighbor|doctor|friend)\b/, "This is grammatical but an unusually specific/awkward beginner phrase."],
    ["health_doctor", /\bhealth doctor\b/, "Use 'doctor'; 'health doctor' is not common English."],
    ["difficult_weather", /\bdifficult weather\b/, "English normally says 'bad weather' or 'rough weather'."],
    ["important_key", /\bimportant key\b/, "Keys are usually 'my key', 'the key', or 'spare key' in common speech."],
    ["easy_gift", /\beasy gift\b/, "English normally says 'nice gift', 'small gift', or 'good gift'."],
    ["quick_book", /\bquick book\b/, "English normally says 'short book'."],
    ["long_coffee", /\blong coffee\b/, "Not a common beginner phrase in English."],
    ["tall_apple", /\btall apple\b/, "The adjective does not naturally describe the noun."],
    ["small_bread", /\bsmall bread\b/, "Bread is normally mass/portion phrasing in English."],
    ["bad_mouse", /\bbad mouse\b/, "This is likely a contrived animal/adjective phrase."],
    ["expensive_shop", /\bexpensive shop\b/, "Items/prices are expensive; a shop is usually 'small', 'nice', 'nearby', etc."],
    ["low_building", /\blow building\b/, "English normally says 'short building' or avoids this phrase."],
    ["watch_painting", /\bwatch(?:ing)? (?:a |the )?(?:big |old |small )?painting\b/, "You look at a painting; you do not usually watch it."],
  ];
  for (const [code, regex, reason] of checks) {
    if (regex.test(en)) issues.push({ code, reason });
  }
  if (/\bsłucham o\b|\bsłuchasz o\b|\bsłucha o\b|\bsłuchamy o\b|\bsłuchacie o\b|\bsłuchają o\b/.test(pl)) {
    issues.push({ code: "sluchac_o", reason: "Simple 'sluchac o' generated a likely 'listen about' calque." });
  }
  if (/\by['’]all\b/i.test(record.targetText)) {
    issues.push({ code: "target_yall_leak", reason: "The Polish target contains the English pronoun \"Y'all\"." });
  }
  if (/^(?:I|you|he|she|it|we|they|y['’]all)\s+(?:gived|given|taked|taken|thinked|eated|eaten|drinked|drunk|writed|written|seed|seen|speaked|spoken|knowed|known|doed|done|beed|been|goed|gone)\b/i.test(record.sourceText)) {
    issues.push({ code: "malformed_english_past", reason: "Top-line English translation uses a bare participle or malformed simple past, e.g. 'I given' instead of 'I gave'." });
  }
  return issues;
}

async function runGeminiAudit(records) {
  const outFile = path.join(outDir, "gemini-results.jsonl");
  const completed = readJsonlById(outFile);
  const results = [...completed.values()];
  for (let start = 0; start < records.length; start += batchSize) {
    const batch = records.slice(start, start + batchSize).filter((record) => !completed.has(record.id));
    if (batch.length === 0) continue;
    console.error(`Gemini audit ${start + 1}-${Math.min(start + batchSize, records.length)} / ${records.length}`);
    const verdicts = await auditBatch(batch);
    for (const verdict of verdicts) {
      const record = batch.find((candidate) => candidate.id === verdict.id);
      if (!record) continue;
      const merged = { ...record, gemini: normalizeVerdict(verdict) };
      fs.appendFileSync(outFile, `${JSON.stringify(merged)}\n`);
      completed.set(record.id, merged);
      results.push(merged);
    }
  }
  return [...completed.values()];
}

async function auditBatch(records) {
  const prompt = buildAuditPrompt(records);
  const raw = await postJson(`${apiBase.replace(/\/+$/, "")}/v1/gemini/generate`, {
    model: "gemini-3.5-flash",
    prompt,
  });
  const text = raw?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (!text) throw new Error("Gemini audit response text missing");
  const parsed = parseJsonText(text);
  if (!Array.isArray(parsed)) throw new Error("Gemini audit response was not an array");
  return parsed;
}

function buildAuditPrompt(records) {
  return [
    "You are auditing LangBangML learner phrases for commonness and naturalness.",
    "The requirement is strict: a phrase may be grammatically correct and still fail if it sounds awkward, rare, contrived, overly specific, or translated from Polish/English rather than something ordinary people commonly say.",
    "A passing phrase should be high-frequency beginner language: ordinary family, food, work, home, travel, errands, feelings, basic needs, simple plans, or common conversation.",
    "Reject phrases created only to satisfy grammar/preposition/case coverage. Commonness outranks coverage.",
    "Pay special attention to collocations, grammatical English top-line translations, and language separation. Examples that fail: 'I given', 'I am listening about a sick cat', 'I have quickly', 'health doctor', 'difficult weather', 'important key', 'easy gift', 'quick book', 'long coffee', 'tall apple', 'watch a painting', and any Polish target that contains the English pronoun 'Y'all'.",
    "Return ONLY a JSON array. For each input return exactly:",
    '{"id":"...","verdict":"pass|rewrite|drop","severity":0,"issueCodes":["not_common"],"reason":"short","suggestedSource":"","suggestedTarget":""}',
    "Use severity 0 for pass, 1 for slightly odd, 2 for should rewrite, 3 for definitely bad.",
    "Use verdict rewrite when the teaching target can be preserved with a natural phrase. Use drop when the scene itself is not useful.",
    "Inputs:",
    JSON.stringify(records.map((record) => ({
      id: record.id,
      source: record.sourceText,
      target: record.targetText,
      literal: record.literal,
      location: record.occurrences?.[0] || locationOf(record),
    }))),
  ].join("\n");
}

function normalizeVerdict(value) {
  const verdict = ["pass", "rewrite", "drop"].includes(value?.verdict) ? value.verdict : "rewrite";
  const severity = Number.isFinite(Number(value?.severity)) ? Number(value.severity) : (verdict === "pass" ? 0 : 2);
  return {
    verdict,
    severity,
    issueCodes: Array.isArray(value?.issueCodes) ? value.issueCodes.map(String) : [],
    reason: clean(value?.reason),
    suggestedSource: clean(value?.suggestedSource),
    suggestedTarget: clean(value?.suggestedTarget),
  };
}

function summarize(records, deterministic, geminiResults) {
  const badGemini = geminiResults.filter((record) => record.gemini?.verdict && record.gemini.verdict !== "pass");
  const byOrigin = countBy(records, (record) => record.origin);
  const deterministicByCode = countBy(deterministic.flatMap((record) => record.issues.map((issue) => issue.code)), (code) => code);
  const geminiByVerdict = countBy(geminiResults, (record) => record.gemini?.verdict || "missing");
  return {
    generatedAt: new Date().toISOString(),
    outDir,
    r2Version,
    totalUniqueRecords: records.length,
    totalOccurrences: records.reduce((sum, record) => sum + (record.occurrences?.length || 1), 0),
    byOrigin,
    deterministicFlagged: deterministic.length,
    deterministicByCode,
    geminiAudited: geminiResults.length,
    geminiByVerdict,
    geminiFlagged: badGemini.length,
    topGeminiFlags: badGemini
      .sort((a, b) => (b.gemini.severity || 0) - (a.gemini.severity || 0))
      .slice(0, 80)
      .map((record) => ({
        id: record.id,
        severity: record.gemini.severity,
        verdict: record.gemini.verdict,
        reason: record.gemini.reason,
        source: record.sourceText,
        target: record.targetText,
        location: record.occurrences?.[0],
        suggestedSource: record.gemini.suggestedSource,
        suggestedTarget: record.gemini.suggestedTarget,
      })),
  };
}

function writeMarkdown(summary, deterministic, geminiResults) {
  const lines = [];
  lines.push("# LangBangML Common Phrase Audit");
  lines.push("");
  lines.push(`Generated: ${summary.generatedAt}`);
  lines.push(`R2 sentence version: v${summary.r2Version}`);
  lines.push(`Unique phrase pairs: ${summary.totalUniqueRecords}`);
  lines.push(`Deterministic flags: ${summary.deterministicFlagged}`);
  lines.push(`Gemini audited: ${summary.geminiAudited}`);
  lines.push(`Gemini flagged: ${summary.geminiFlagged}`);
  lines.push("");
  lines.push("## Deterministic Flags");
  for (const record of deterministic.slice(0, 80)) {
    lines.push(`- ${record.id} ${record.issues.map((issue) => issue.code).join(", ")}: ${record.sourceText} / ${record.targetText} (${record.occurrences[0]})`);
  }
  const badGemini = geminiResults.filter((record) => record.gemini?.verdict && record.gemini.verdict !== "pass");
  lines.push("");
  lines.push("## Gemini Flags");
  for (const record of badGemini
    .sort((a, b) => (b.gemini.severity || 0) - (a.gemini.severity || 0))
    .slice(0, 120)) {
    lines.push(`- S${record.gemini.severity} ${record.gemini.verdict} ${record.id}: ${record.sourceText} / ${record.targetText}`);
    lines.push(`  Reason: ${record.gemini.reason}`);
    if (record.gemini.suggestedSource || record.gemini.suggestedTarget) {
      lines.push(`  Suggestion: ${record.gemini.suggestedSource} / ${record.gemini.suggestedTarget}`);
    }
    lines.push(`  Location: ${record.occurrences[0]}`);
  }
  fs.writeFileSync(path.join(outDir, "report.md"), `${lines.join("\n")}\n`);
}

function locationOf(record) {
  return [
    record.origin,
    record.contentVersionId,
    record.lessonId,
    record.groupId,
    `#${record.index}`,
  ].filter(Boolean).join(":");
}

function readJsonlById(file) {
  const out = new Map();
  if (!fs.existsSync(file)) return out;
  for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    if (!line.trim()) continue;
    const value = JSON.parse(line);
    out.set(value.id, value);
  }
  return out;
}

async function fetchJson(url) {
  const response = await fetch(url, { headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error(`GET ${url} failed: ${response.status} ${await response.text()}`);
  return response.json();
}

async function postJson(url, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify(body),
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`POST ${url} failed: ${response.status} ${text}`);
  return JSON.parse(text);
}

function parseJsonText(text) {
  const cleaned = String(text || "")
    .trim()
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/i, "")
    .trim();
  return JSON.parse(cleaned);
}

function writeJson(name, value) {
  fs.writeFileSync(path.join(outDir, name), `${JSON.stringify(value, null, 2)}\n`);
}

function countBy(values, keyFn) {
  const out = {};
  for (const value of values) {
    const key = keyFn(value);
    out[key] = (out[key] || 0) + 1;
  }
  return out;
}

function clean(value) {
  return typeof value === "string" ? value.trim() : "";
}

function normalize(value) {
  return clean(value).toLowerCase().replace(/\s+/g, " ");
}

function parseArgs(raw) {
  const out = {};
  for (let i = 0; i < raw.length; i += 1) {
    const arg = raw[i];
    if (!arg.startsWith("--")) continue;
    const key = arg.slice(2);
    const [name, inline] = key.split("=", 2);
    out[toCamel(name)] = inline ?? raw[++i] ?? "true";
  }
  return out;
}

function toCamel(value) {
  return value.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
}

function fail(message) {
  console.error(message);
  process.exit(2);
}
