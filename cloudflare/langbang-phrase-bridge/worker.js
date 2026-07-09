const DEFAULT_LANGBANG_API_BASE = "https://langbang.org";
const DEFAULT_APPROVAL_URL = "https://langbang.org/bridge/approve";
const MAX_BODY_CHARS = 64 * 1024;
const MAX_PHRASES_PER_REQUEST = 50;
const MAX_PHRASE_CHARS = 1000;
const APPROVAL_SCOPE = "phrase-bridge";
const DEFAULT_APPROVAL_MINUTES = 60;
const MAX_APPROVAL_MINUTES = 60;

export default {
  async fetch(request, env) {
    try {
      if (request.method === "OPTIONS") {
        return new Response(null, { status: 204, headers: corsHeaders(env) });
      }

      const url = new URL(request.url);
      if (url.pathname === "/health") {
        const approval = await activeApproval(env);
        return jsonResponse({
          ok: true,
          service: "langbang-phrase-bridge",
          langBangTokenConfigured: Boolean(cleanString(env.LANGBANG_AGENT_TOKEN)),
          publicWritesEnabled: publicWritesEnabled(env),
          approvalRequired: !publicWritesEnabled(env),
          approvalActive: Boolean(approval),
          approvedUntil: approval?.approved_until || null,
        }, 200, env);
      }

      if (url.pathname === "/approval-status") {
        return await approvalStatus(request, env);
      }

      if (url.pathname === "/approve") {
        return await approveWrites(request, env);
      }

      if (url.pathname === "/revoke") {
        return await revokeApproval(request, env);
      }

      if (url.pathname === "/" || url.pathname === "/add-phrases") {
        return await addPhrases(request, env);
      }

      return errorResponse(env, 404, "not_found", "Use POST /add-phrases.");
    } catch (error) {
      if (error instanceof Response) return withCors(error, env);
      console.error(error);
      return errorResponse(env, 500, "internal_error", "Unexpected bridge error.");
    }
  },
};

async function addPhrases(request, env) {
  if (request.method !== "POST") {
    return errorResponse(env, 405, "method_not_allowed", "Use POST /add-phrases.");
  }
  if (!cleanString(env.LANGBANG_AGENT_TOKEN)) {
    return errorResponse(env, 503, "missing_langbang_token", "Worker secret LANGBANG_AGENT_TOKEN is not configured.");
  }
  if (!publicWritesEnabled(env) && !bridgeTokenAccepted(request, env)) {
    const approval = await activeApproval(env);
    if (!approval) {
      return errorResponse(env, 403, "approval_required", "Bridge writes require signed-in approval before clients can add phrases.", {
        approvalRequired: true,
        approvalUrl: approvalUrl(env),
        approvalDurationMinutes: MAX_APPROVAL_MINUTES,
      });
    }
  }

  const body = await readJsonBody(request, env);
  const groupTitle = cleanString(body.groupTitle || body.groupName);
  if (!groupTitle) {
    return errorResponse(env, 400, "missing_group_title", "Provide groupTitle.");
  }

  const phrases = normalizePhrases(body.phrases ?? body.sentences ?? body.items);
  if (phrases.length === 0) {
    return errorResponse(env, 400, "missing_phrases", "Provide a non-empty phrases array.");
  }

  const dryRun = truthy(body.dryRun);
  const basePayload = buildLangBangBasePayload(body, groupTitle);
  const upstreamUrl = `${langBangApiBase(env)}/v1/agent/phrases`;
  const results = [];
  let langBangAdded = 0;
  let langBangReplaced = 0;

  for (let index = 0; index < phrases.length; index += 1) {
    const upstreamBody = {
      ...basePayload,
      phrases: [phrases[index]],
      atomic: true,
    };

    if (dryRun) {
      results.push({ index, ok: true, dryRun: true, request: upstreamBody });
      continue;
    }

    const upstream = await postLangBangPhrase(upstreamUrl, env.LANGBANG_AGENT_TOKEN, upstreamBody);
    if (upstream.ok) {
      langBangAdded += Number(upstream.body?.added || 0);
      langBangReplaced += Number(upstream.body?.replaced || 0);
      results.push({
        index,
        ok: true,
        status: upstream.status,
        added: upstream.body?.added || 0,
        replaced: upstream.body?.replaced || 0,
        phrase: upstream.body?.phrase,
      });
    } else {
      results.push({
        index,
        ok: false,
        status: upstream.status,
        error: upstream.body?.error || upstream.body?.message || upstream.text || "LangBang request failed",
        details: upstream.body,
      });
      if (truthy(body.stopOnError)) break;
    }
  }

  const failed = results.filter((result) => !result.ok).length;
  const succeeded = results.length - failed;
  return jsonResponse({
    ok: failed === 0,
    service: "langbang-phrase-bridge",
    upstream: "/v1/agent/phrases",
    dryRun,
    groupTitle,
    requested: phrases.length,
    attempted: results.length,
    succeeded,
    failed,
    langBangAdded,
    langBangReplaced,
    results,
  }, failed === 0 ? 200 : succeeded > 0 ? 207 : 502, env);
}

async function approvalStatus(request, env) {
  if (request.method !== "GET") {
    return errorResponse(env, 405, "method_not_allowed", "Use GET /approval-status.");
  }
  const approval = await activeApproval(env);
  return jsonResponse({
    ok: true,
    service: "langbang-phrase-bridge",
    approvalRequired: !publicWritesEnabled(env),
    approvalActive: Boolean(approval),
    approvedBy: approval?.email || null,
    approvedUntil: approval?.approved_until || null,
    approvalUrl: approvalUrl(env),
  }, 200, env);
}

async function approveWrites(request, env) {
  if (request.method !== "POST") {
    return errorResponse(env, 405, "method_not_allowed", "Use POST /approve.");
  }
  const user = await requireApprover(request, env);
  const body = await readOptionalJsonBody(request, env);
  const durationMinutes = normalizeApprovalMinutes(body.durationMinutes || body.minutes);
  const approvedUntil = sqliteDateTime(new Date(Date.now() + durationMinutes * 60 * 1000));
  await ensureApprovalSchema(env);
  await env.DB.prepare(`
    INSERT INTO phrase_bridge_approvals (scope, email, approved_until, created_at, updated_at)
    VALUES (?, ?, ?, datetime('now'), datetime('now'))
    ON CONFLICT(scope) DO UPDATE SET
      email = excluded.email,
      approved_until = excluded.approved_until,
      updated_at = datetime('now')
  `).bind(APPROVAL_SCOPE, user.email, approvedUntil).run();
  return jsonResponse({
    ok: true,
    action: "approve_bridge_writes",
    service: "langbang-phrase-bridge",
    approvedBy: user.email,
    approvedUntil,
    durationMinutes,
  }, 200, env);
}

async function revokeApproval(request, env) {
  if (request.method !== "POST") {
    return errorResponse(env, 405, "method_not_allowed", "Use POST /revoke.");
  }
  const user = await requireApprover(request, env);
  await ensureApprovalSchema(env);
  await env.DB.prepare(`
    UPDATE phrase_bridge_approvals
    SET approved_until = datetime('now'), email = ?, updated_at = datetime('now')
    WHERE scope = ?
  `).bind(user.email, APPROVAL_SCOPE).run();
  return jsonResponse({
    ok: true,
    action: "revoke_bridge_writes",
    service: "langbang-phrase-bridge",
    revokedBy: user.email,
  }, 200, env);
}

async function activeApproval(env) {
  if (publicWritesEnabled(env)) return { email: "public", approved_until: null };
  if (!env.DB) return null;
  await ensureApprovalSchema(env);
  return await env.DB.prepare(`
    SELECT email, approved_until
    FROM phrase_bridge_approvals
    WHERE scope = ?
      AND approved_until > datetime('now')
    LIMIT 1
  `).bind(APPROVAL_SCOPE).first();
}

async function ensureApprovalSchema(env) {
  if (!env.DB) throw new Error("DB binding is not configured");
  await env.DB.prepare(`
    CREATE TABLE IF NOT EXISTS phrase_bridge_approvals (
      scope TEXT PRIMARY KEY,
      email TEXT NOT NULL,
      approved_until TEXT NOT NULL,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now'))
    )
  `).run();
}

async function requireApprover(request, env) {
  const auth = request.headers.get("Authorization") || "";
  const token = auth.match(/^Bearer\s+(.+)$/i)?.[1] || "";
  if (!token) {
    throw errorResponse(env, 401, "sign_in_required", "Open the approval page while signed in to LangBang.");
  }
  const response = await fetch(`${langBangApiBase(env)}/v1/me`, {
    headers: {
      "Authorization": `Bearer ${token}`,
      "Accept": "application/json",
    },
  });
  const body = await response.json().catch(() => null);
  if (!response.ok || !body?.user?.email) {
    throw errorResponse(env, 401, "invalid_session", "LangBang sign-in is missing or expired.");
  }
  const email = cleanString(body.user.email).toLowerCase();
  if (!approverEmails(env).has(email)) {
    throw errorResponse(env, 403, "not_allowed", "This LangBang account cannot approve bridge writes.", { email });
  }
  return { ...body.user, email };
}

function approverEmails(env) {
  return new Set(cleanString(env.APPROVER_EMAILS)
    .split(",")
    .map((email) => email.trim().toLowerCase())
    .filter(Boolean));
}

function normalizeApprovalMinutes(value) {
  const parsed = Number(value || DEFAULT_APPROVAL_MINUTES);
  if (!Number.isFinite(parsed)) return DEFAULT_APPROVAL_MINUTES;
  return Math.max(1, Math.min(MAX_APPROVAL_MINUTES, Math.trunc(parsed)));
}

function buildLangBangBasePayload(body, groupTitle) {
  const payload = { groupTitle };
  for (const key of ["groupId", "groupSubtitle", "version", "instanceId"]) {
    const value = cleanString(body[key]);
    if (value) payload[key] = value;
  }
  return payload;
}

async function postLangBangPhrase(url, token, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json",
      "Accept": "application/json",
    },
    body: JSON.stringify(body),
  });
  const text = await response.text();
  let parsed = null;
  try {
    parsed = text ? JSON.parse(text) : null;
  } catch {
    parsed = null;
  }
  return {
    ok: response.ok,
    status: response.status,
    body: parsed,
    text: parsed ? "" : text.slice(0, 2000),
  };
}

async function readJsonBody(request, env) {
  const text = await request.text();
  if (text.length > MAX_BODY_CHARS) {
    throw errorResponse(env, 413, "request_too_large", "Request body is too large.", { limit: MAX_BODY_CHARS });
  }
  try {
    const parsed = JSON.parse(text || "{}");
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("object required");
    return parsed;
  } catch {
    throw errorResponse(env, 400, "invalid_json", "Request body must be a JSON object.");
  }
}

async function readOptionalJsonBody(request, env) {
  const text = await request.text();
  if (!text.trim()) return {};
  if (text.length > MAX_BODY_CHARS) {
    throw errorResponse(env, 413, "request_too_large", "Request body is too large.", { limit: MAX_BODY_CHARS });
  }
  try {
    const parsed = JSON.parse(text);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("object required");
    return parsed;
  } catch {
    throw errorResponse(env, 400, "invalid_json", "Request body must be a JSON object.");
  }
}

function normalizePhrases(value) {
  const raw = Array.isArray(value) ? value : value ? [value] : [];
  if (raw.length > MAX_PHRASES_PER_REQUEST) {
    throw errorResponse({}, 400, "too_many_phrases", "Too many phrases in one bridge request.", {
      limit: MAX_PHRASES_PER_REQUEST,
    });
  }
  return raw.map(normalizePhrase).filter(Boolean);
}

function normalizePhrase(value) {
  if (typeof value === "string") {
    const text = cleanString(value);
    if (!text) return null;
    if (text.length > MAX_PHRASE_CHARS) throw phraseTooLong();
    return text;
  }

  if (value && typeof value === "object" && !Array.isArray(value)) {
    const out = {};
    for (const key of [
      "source",
      "sourceText",
      "target",
      "targetText",
      "english",
      "englishText",
      "polish",
      "polishText",
      "en",
      "pl",
      "literal",
      "literalText",
      "gloss",
      "words",
    ]) {
      if (value[key] !== undefined) out[key] = value[key];
    }
    if (JSON.stringify(out).length > MAX_PHRASE_CHARS * 2) throw phraseTooLong();
    return Object.keys(out).length > 0 ? out : null;
  }

  return null;
}

function phraseTooLong() {
  return errorResponse({}, 400, "phrase_too_long", "Phrase is too long.", { limit: MAX_PHRASE_CHARS });
}

function bridgeTokenAccepted(request, env) {
  const required = cleanString(env.BRIDGE_TOKEN);
  if (!required) return false;
  const auth = request.headers.get("Authorization") || "";
  const bearer = auth.match(/^Bearer\s+(.+)$/i)?.[1] || "";
  return cleanString(bearer) === required || cleanString(request.headers.get("X-Bridge-Token")) === required;
}

function publicWritesEnabled(env) {
  return truthy(env.ALLOW_PUBLIC_WRITES);
}

function langBangApiBase(env) {
  return cleanString(env.LANGBANG_API_BASE || DEFAULT_LANGBANG_API_BASE).replace(/\/+$/, "");
}

function approvalUrl(env) {
  return cleanString(env.APPROVAL_URL || DEFAULT_APPROVAL_URL);
}

function sqliteDateTime(date) {
  return date.toISOString().slice(0, 19).replace("T", " ");
}

function corsHeaders(env = {}) {
  return {
    "Access-Control-Allow-Origin": cleanString(env.CORS_ORIGIN || "*"),
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Bridge-Token",
  };
}

function truthy(value) {
  return value === true || /^(1|true|yes|y)$/i.test(cleanString(value));
}

function cleanString(value) {
  return String(value || "").trim();
}

function withCors(response, env) {
  const headers = new Headers(response.headers);
  for (const [key, value] of Object.entries(corsHeaders(env))) {
    headers.set(key, value);
  }
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

function jsonResponse(body, status, env = {}) {
  return new Response(JSON.stringify(body, null, 2), {
    status,
    headers: {
      ...corsHeaders(env),
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

function errorResponse(env, status, code, message, extra = {}) {
  return jsonResponse({ ok: false, error: code, message, ...extra }, status, env);
}
