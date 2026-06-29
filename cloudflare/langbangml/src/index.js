const SLOW_50_SUFFIX = "|slow50v3";
const SLOW_60_SUFFIX = "|slow60v1";
const SLOW_ART_SUFFIX = "|slowart1";
const DEFAULT_AUDIO_WARM_LIMIT = 40;
const MAX_AUDIO_WARM_LIMIT = 80;
const DEFAULT_GEMINI_MODEL = "gemini-3.5-flash";
const MAX_GEMINI_PROMPT_CHARS = 20000;
const MAX_G2_TRANSLATE_CHARS = 500;
const MAX_PHRASE_FIELD_CHARS = 600;
const GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const MAX_ID_TOKEN_CHARS = 12000;
const MAX_EMAIL_CHARS = 254;
const EMAIL_CODE_TTL_MINUTES = 10;
const DEFAULT_SESSION_TTL_DAYS = 90;
const MAX_SYNC_GROUPS = 200;
const MAX_SYNC_SENTENCES_PER_GROUP = 300;
const MAX_SYNC_STARS = 2000;
const DEFAULT_AGENT_DAILY_LIMIT = 100;
const MAX_AGENT_BODY_CHARS = 64 * 1024;
const MAX_AGENT_LABEL_CHARS = 80;
const MAX_AGENT_GROUP_TITLE_CHARS = 48;
const MAX_AGENT_GROUP_SUBTITLE_CHARS = 120;
const MAX_AGENT_PHRASE_INPUTS = 10;
const MAX_AGENT_PHRASE_OUTPUTS = 25;
const MAX_AGENT_PHRASE_TARGET_WORDS = 10;
const MAX_AGENT_PHRASE_TARGET_CHARS = 90;
const DEFAULT_AI_PHRASE_QUOTA = 50;
const MAX_AI_PHRASE_PROMPT_CHARS = 1200;
const MAX_AI_PHRASES_PER_REQUEST = 10;
const DEFAULT_AI_PHRASE_QUOTA_EMAIL = "rahulioson@gmail.com";
const MAX_ANALYTICS_EVENTS_PER_BATCH = 100;
const MAX_ANALYTICS_PROPERTIES_CHARS = 4000;
const DEFAULT_ANALYTICS_ADMIN_EMAIL = "rahulioson@gmail.com";

// --- Abuse controls (plan Phase 0 / BE-1 / BE-2) ---------------------------
// Per-IP / per-user fixed-window limits, generous enough that normal app + web
// usage never trips them but tight enough to cap cost-amplification abuse of the
// paid Gemini / Azure backends. Edge WAF rules (plan BE-0a) are the first line
// of defense; these are the application-layer backstop. Tune from real traffic.
const RL = {
  // LLM text-gen limits tripled for the owner's personal use (2026-06-29).
  geminiIpPerHour: 180,
  geminiUserPerDay: 1200,
  completeIpPerHour: 180,
  completeUserPerDay: 1200,
  audioIpPerHour: 600,
  emailStartIpPerHour: 20,
  emailStartEmailPerHour: 5,
  emailVerifyIpPerHour: 60,
  emailVerifyEmailPer10Min: 10,
  analyticsIpPerMin: 120,
};
// app batches audio at R2AudioDownloader.BATCH_SIZE (40); 100 = safe headroom.
const MAX_AUDIO_MANIFEST_PHRASES = 100;
// Browser origins allowed to call this API cross-origin. The web SPA normally
// uses the same-origin langbang.org proxy, so this stays tight. Native apps and
// curl ignore CORS. Override with the CORS_ALLOWED_ORIGINS var.
const DEFAULT_CORS_ORIGINS = [
  "https://langbang.org",
  "https://www.langbang.org",
  "https://langbangml-api.langbangml.workers.dev",
];
// Minimal Gemini safety config (BLOCK_ONLY_HIGH avoids over-blocking legitimate
// language-learning content while limiting the owner account's exposure to
// disallowed-content generation). Applied to all generate calls.
const GEMINI_SAFETY_SETTINGS = [
  { category: "HARM_CATEGORY_HARASSMENT", threshold: "BLOCK_ONLY_HIGH" },
  { category: "HARM_CATEGORY_HATE_SPEECH", threshold: "BLOCK_ONLY_HIGH" },
  { category: "HARM_CATEGORY_SEXUALLY_EXPLICIT", threshold: "BLOCK_ONLY_HIGH" },
  { category: "HARM_CATEGORY_DANGEROUS_CONTENT", threshold: "BLOCK_ONLY_HIGH" },
];

export default {
  async fetch(request, env) {
    const allowOrigin = allowedOrigin(request.headers.get("Origin") || "", env);
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders(allowOrigin) });
    }

    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";

    try {
      const response = await (async () => {
      if (request.method === "GET" && path === "/health") {
        return json({ ok: true, service: "langbangml-api" });
      }
      if (request.method === "GET" && (path === "/agent" || path === "/agent/instructions")) {
        return agentInstructionsPage(env);
      }
      if (request.method === "GET" && path === "/admin/analytics") {
        return analyticsAdminPage(env);
      }
      if (request.method === "POST" && path === "/v1/analytics/events") {
        return await ingestAnalytics(request, env);
      }
      if (request.method === "GET" && path === "/v1/admin/analytics/summary") {
        const admin = await requireAnalyticsAdmin(request, env);
        await recordAnalyticsAdminAccess(env, admin.email, path, true);
        return await adminAnalyticsSummary(request, env);
      }
      if (request.method === "GET" && path === "/v1/admin/analytics/events") {
        const admin = await requireAnalyticsAdmin(request, env);
        await recordAnalyticsAdminAccess(env, admin.email, path, true);
        return await adminAnalyticsEvents(request, env);
      }
      if (request.method === "GET" && path === "/v1/instances") {
        return await listInstances(env);
      }
      if (request.method === "POST" && path === "/v1/gemini/generate") {
        return await geminiGenerate(request, env);
      }
      if (request.method === "POST" && path === "/v1/gemini/g2-translate") {
        return await geminiG2Translate(request, env);
      }
      if (path === "/v1/gemini/live" && (request.headers.get("Upgrade") || "").toLowerCase() === "websocket") {
        return await geminiLiveRelay(request, env);
      }
      if (request.method === "POST" && path === "/v1/phrases/complete") {
        return await completePhrase(request, env);
      }
      if (request.method === "POST" && path === "/v1/auth/google") {
        return await authGoogle(request, env);
      }
      if (request.method === "POST" && path === "/v1/auth/email/start") {
        return await authEmailStart(request, env);
      }
      if (request.method === "POST" && path === "/v1/auth/email/verify") {
        return await authEmailVerify(request, env);
      }
      if (request.method === "POST" && path === "/v1/auth/test-login") {
        return await authTestLogin(request, env);
      }
      if (request.method === "POST" && path === "/v1/auth/sign-out") {
        const user = await requireUser(request, env);
        return await signOut(request, env, user);
      }
      if (request.method === "GET" && path === "/v1/me") {
        const user = await requireUser(request, env);
        return json({ user: publicUser(user) });
      }
      if (request.method === "POST" && path === "/v1/me/agent-token") {
        const user = await requireUser(request, env);
        return await createAgentToken(request, env, user);
      }
      if (path === "/v1/me/content") {
        const user = await requireUser(request, env);
        return await userContent(request, env, user);
      }
      if (path === "/v1/me/phrases/ai-generate") {
        const user = await requireUser(request, env);
        return await userAiPhraseGenerate(request, env, user);
      }
      if (path === "/v1/me/phrases/ai-quota-request") {
        const user = await requireUser(request, env);
        return await userAiPhraseQuotaRequest(request, env, user);
      }
      if (request.method === "GET" && path === "/v1/me/phrases/ai-quota") {
        const user = await requireUser(request, env);
        return await userAiPhraseQuota(request, env, user);
      }
      if (path === "/v1/me/phrases") {
        const user = await requireUser(request, env);
        return await userPhrases(request, env, user);
      }
      if (request.method === "GET" && path === "/v1/agent/status") {
        return await agentApiRequest(request, env, "status", agentStatus);
      }
      if (path === "/v1/agent/phrases") {
        return await agentApiRequest(request, env, "phrases", agentPhrases);
      }
      if (path === "/v1/agent/words") {
        return await agentApiRequest(request, env, "words", agentWords);
      }
      const adminAudioWarmMatch = path.match(/^\/v1\/admin\/content\/([^/]+)\/audio\/warm-missing$/);
      if (request.method === "POST" && adminAudioWarmMatch) {
        requireAdmin(request, env);
        return await warmMissingAudio(request, env, decodeURIComponent(adminAudioWarmMatch[1]));
      }
      const adminLessonsMatch = path.match(/^\/v1\/admin\/content\/([^/]+)\/lessons$/);
      if (request.method === "GET" && adminLessonsMatch) {
        requireAdmin(request, env);
        return await adminLessons(env, decodeURIComponent(adminLessonsMatch[1]));
      }
      const adminLessonItemsMatch = path.match(/^\/v1\/admin\/content\/([^/]+)\/lessons\/([^/]+)\/items$/);
      if (adminLessonItemsMatch) {
        requireAdmin(request, env);
        return await adminLessonItems(request, env, decodeURIComponent(adminLessonItemsMatch[1]), decodeURIComponent(adminLessonItemsMatch[2]));
      }
      const adminLessonReorderMatch = path.match(/^\/v1\/admin\/content\/([^/]+)\/lessons\/([^/]+)\/reorder$/);
      if (request.method === "POST" && adminLessonReorderMatch) {
        requireAdmin(request, env);
        return await adminReorderItems(request, env, decodeURIComponent(adminLessonReorderMatch[1]), decodeURIComponent(adminLessonReorderMatch[2]));
      }
      const adminLessonMatch = path.match(/^\/v1\/admin\/content\/([^/]+)\/lessons\/([^/]+)$/);
      if (request.method === "GET" && adminLessonMatch) {
        requireAdmin(request, env);
        return await adminLesson(env, decodeURIComponent(adminLessonMatch[1]), decodeURIComponent(adminLessonMatch[2]));
      }
      const bootstrapMatch = path.match(/^\/v1\/instances\/([^/]+)\/bootstrap$/);
      if (request.method === "GET" && bootstrapMatch) {
        return await bootstrap(env, decodeURIComponent(bootstrapMatch[1]));
      }
      const labelsMatch = path.match(/^\/v1\/labels\/([^/]+)$/);
      if (request.method === "GET" && labelsMatch) {
        return await labelsFor(env, decodeURIComponent(labelsMatch[1]));
      }
      if (request.method === "POST" && path === "/v1/audio/manifest") {
        return await audioManifest(request, env);
      }
      return json({ error: "not found" }, 404);
      })();
      return withCors(response, allowOrigin);
    } catch (error) {
      if (error instanceof HttpError) {
        return withCors(json({ error: error.message, details: error.details }, error.status), allowOrigin);
      }
      return withCors(json({ error: error?.message || String(error) }, 500), allowOrigin);
    }
  },
};

class HttpError extends Error {
  constructor(status, message, details = undefined) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

function requireAdmin(request, env) {
  const expected = env.CONTENT_API_TOKEN || env.ADMIN_API_TOKEN;
  if (!expected) {
    throw new HttpError(503, "CONTENT_API_TOKEN is not configured");
  }
  const auth = request.headers.get("Authorization") || "";
  const token = auth.match(/^Bearer\s+(.+)$/i)?.[1]?.trim();
  if (token !== expected) {
    throw new HttpError(401, "admin token required");
  }
}

// Non-throwing admin check — exempts the owner's own tooling (bulk content
// generation, audio warming) from the end-user rate limits below.
function isAdmin(request, env) {
  try {
    requireAdmin(request, env);
    return true;
  } catch (_) {
    return false;
  }
}

async function requireUser(request, env) {
  const auth = request.headers.get("Authorization") || "";
  const token = auth.match(/^Bearer\s+(.+)$/i)?.[1]?.trim();
  if (!token) throw new HttpError(401, "sign-in required");
  const tokenHash = await sha256Hex(token);
  const row = await env.DB.prepare(`
    SELECT s.id AS session_id, s.user_id, u.email, u.email_normalized, u.email_verified,
           u.display_name, u.picture_url
    FROM auth_sessions s
    JOIN users u ON u.id = s.user_id
    WHERE s.token_hash = ?
      AND s.revoked_at IS NULL
      AND s.expires_at > datetime('now')
  `).bind(tokenHash).first();
  if (!row) throw new HttpError(401, "invalid or expired session");
  await env.DB.prepare(`
    UPDATE auth_sessions
    SET last_seen_at = datetime('now')
    WHERE id = ?
  `).bind(row.session_id).run();
  return row;
}

// Returns the authenticated user row if a valid Bearer session is present, else
// null — never throws on missing/invalid auth. Lets endpoints meter signed-in
// users without forcing login (e.g. audio that must work pre-login).
async function optionalUser(request, env) {
  const auth = request.headers.get("Authorization") || "";
  if (!/^Bearer\s+/i.test(auth)) return null;
  try {
    return await requireUser(request, env);
  } catch (_) {
    return null;
  }
}

function clientIp(request) {
  return (
    request.headers.get("CF-Connecting-IP") ||
    (request.headers.get("X-Forwarded-For") || "").split(",")[0].trim() ||
    "unknown"
  );
}

function truthy(value) {
  return value === true || value === "true" || value === "1" || value === 1;
}

// D1-backed fixed-window rate limit. Increments the counter for the current
// window and throws HttpError(429) once the limit is exceeded. Fails OPEN on any
// D1 error (incl. migration 013 not yet applied) so abuse control never takes an
// endpoint down. One write + one read per guarded call — front with an edge WAF
// rule (plan BE-0a) for true flood protection.
async function enforceRateLimit(env, bucket, limit, windowSeconds, details = {}) {
  if (!env.DB || !Number.isFinite(limit) || limit <= 0) return;
  const nowSec = Math.floor(Date.now() / 1000);
  const windowStart = nowSec - (nowSec % windowSeconds);
  try {
    await env.DB.prepare(`
      INSERT INTO rate_limit_counters (bucket, window_start, count, updated_at)
      VALUES (?, ?, 1, datetime('now'))
      ON CONFLICT(bucket, window_start) DO UPDATE SET
        count = count + 1,
        updated_at = datetime('now')
    `).bind(bucket, windowStart).run();
    const row = await env.DB.prepare(
      `SELECT count FROM rate_limit_counters WHERE bucket = ? AND window_start = ?`,
    ).bind(bucket, windowStart).first();
    const used = Number(row?.count || 0);
    if (used > limit) {
      throw new HttpError(429, "rate limit exceeded", {
        ...details,
        limit,
        windowSeconds,
        retryAfterSeconds: Math.max(1, windowStart + windowSeconds - nowSec),
      });
    }
  } catch (err) {
    if (err instanceof HttpError) throw err;
    // Any D1 failure (missing table, transient error) → fail open.
    return;
  }
}

// Gate for the paid-backend endpoints (Gemini generate / phrase completion).
// Meters per signed-in user when a session is present and always caps per IP.
// When LLM_REQUIRE_AUTH is set (plan BE-1 end state, after clients send tokens)
// anonymous callers are rejected outright.
async function guardLlmEndpoint(request, env, name, ipPerHour, userPerDay) {
  // The owner's own tooling authenticates with the admin token and is exempt.
  if (isAdmin(request, env)) return null;
  const user = await optionalUser(request, env);
  if (!user && truthy(env.LLM_REQUIRE_AUTH)) {
    throw new HttpError(401, "sign-in required");
  }
  await enforceRateLimit(env, `${name}:ip:${clientIp(request)}`, ipPerHour, 3600, { scope: "ip" });
  if (user) {
    await enforceRateLimit(env, `${name}:user:${user.user_id}`, userPerDay, 86400, { scope: "user" });
  }
  return user;
}

async function authGoogle(request, env) {
  const body = await request.json();
  const idToken = boundedString(body.idToken, "idToken", MAX_ID_TOKEN_CHARS);
  const nonce = boundedString(body.nonce, "nonce", 256, true);
  const claims = await verifyGoogleIdToken(idToken, env, nonce || undefined);
  const email = normalizeEmail(claims.email);
  if (!email) throw new HttpError(400, "Google token did not include an email");
  const user = await upsertUserIdentity(env, {
    provider: "google",
    providerSubject: boundedString(claims.sub, "sub", 256),
    email,
    emailVerified: claims.email_verified === true || claims.email_verified === "true",
    displayName: cleanString(claims.name),
    pictureUrl: cleanString(claims.picture),
  });
  const session = await createSession(env, user.id);
  return json(authResponse(user, session));
}

async function authEmailStart(request, env) {
  const body = await request.json();
  const email = normalizeEmail(boundedString(body.email, "email", MAX_EMAIL_CHARS));
  if (!email) throw new HttpError(400, "valid email is required");
  await enforceRateLimit(env, `email-start:ip:${clientIp(request)}`, RL.emailStartIpPerHour, 3600, { scope: "ip" });
  await enforceRateLimit(env, `email-start:addr:${email}`, RL.emailStartEmailPerHour, 3600, { scope: "email" });
  const pepper = env.EMAIL_LOGIN_PEPPER;
  if (!pepper) throw new HttpError(503, "EMAIL_LOGIN_PEPPER is not configured");

  const code = randomNumericCode();
  const codeHash = await hashEmailCode(email, code, pepper);
  const expiresAt = sqliteDateTimeFromNow(EMAIL_CODE_TTL_MINUTES * 60 * 1000);
  await env.DB.prepare(`
    INSERT INTO email_login_codes (id, email, email_normalized, code_hash, expires_at)
    VALUES (?, ?, ?, ?, ?)
  `).bind(crypto.randomUUID(), email, email, codeHash, expiresAt).run();

  const sent = await sendLoginEmail(env, email, code);
  const response = { ok: true, email, sent, expiresInMinutes: EMAIL_CODE_TTL_MINUTES };
  if (!sent && env.EMAIL_LOGIN_DEV_RETURN_CODE === "1") {
    response.devCode = code;
  }
  if (!sent && env.EMAIL_LOGIN_DEV_RETURN_CODE !== "1") {
    throw new HttpError(503, "email sender is not configured");
  }
  return json(response);
}

async function authEmailVerify(request, env) {
  const body = await request.json();
  const email = normalizeEmail(boundedString(body.email, "email", MAX_EMAIL_CHARS));
  const code = String(body.code || "").replace(/\D/g, "");
  if (!email) throw new HttpError(400, "valid email is required");
  if (!/^\d{6}$/.test(code)) throw new HttpError(400, "six-digit code is required");
  await enforceRateLimit(env, `email-verify:ip:${clientIp(request)}`, RL.emailVerifyIpPerHour, 3600, { scope: "ip" });
  await enforceRateLimit(env, `email-verify:addr:${email}`, RL.emailVerifyEmailPer10Min, 600, { scope: "email" });
  const pepper = env.EMAIL_LOGIN_PEPPER;
  if (!pepper) throw new HttpError(503, "EMAIL_LOGIN_PEPPER is not configured");
  const codeHash = await hashEmailCode(email, code, pepper);
  const row = await env.DB.prepare(`
    SELECT id
    FROM email_login_codes
    WHERE email_normalized = ?
      AND code_hash = ?
      AND consumed_at IS NULL
      AND expires_at > datetime('now')
    ORDER BY created_at DESC
    LIMIT 1
  `).bind(email, codeHash).first();
  if (!row) throw new HttpError(401, "invalid or expired code");
  await env.DB.prepare(`
    UPDATE email_login_codes
    SET consumed_at = datetime('now')
    WHERE id = ?
  `).bind(row.id).run();

  const user = await upsertUserIdentity(env, {
    provider: "email",
    providerSubject: email,
    email,
    emailVerified: true,
    displayName: "",
    pictureUrl: "",
  });
  const session = await createSession(env, user.id);
  return json(authResponse(user, session));
}

// Scoped username+password login for a single test/review account (e.g. Play Store
// review, automated QA). Disabled unless BOTH TEST_LOGIN_EMAIL and TEST_LOGIN_PASSWORD
// secrets are set. This is intentionally separate from the real email-code and Google
// flows — it adds no password surface for ordinary users.
async function authTestLogin(request, env) {
  const expectedEmail = normalizeEmail(env.TEST_LOGIN_EMAIL || "");
  const expectedPassword = env.TEST_LOGIN_PASSWORD || "";
  if (!expectedEmail || !expectedPassword) {
    throw new HttpError(404, "not found");
  }
  const body = await request.json();
  const email = normalizeEmail(boundedString(body.email, "email", MAX_EMAIL_CHARS));
  const password = String(body.password || "");
  const emailOk = !!email && email === expectedEmail;
  const passwordOk = await constantTimeEqual(password, expectedPassword);
  if (!emailOk || !passwordOk) {
    throw new HttpError(401, "invalid test credentials");
  }
  const user = await upsertUserIdentity(env, {
    provider: "email",
    providerSubject: expectedEmail,
    email: expectedEmail,
    emailVerified: true,
    displayName: "Test Account",
    pictureUrl: "",
  });
  const session = await createSession(env, user.id);
  return json(authResponse(user, session));
}

async function signOut(request, env, user) {
  const auth = request.headers.get("Authorization") || "";
  const token = auth.match(/^Bearer\s+(.+)$/i)?.[1]?.trim();
  if (token) {
    await env.DB.prepare(`
      UPDATE auth_sessions
      SET revoked_at = datetime('now')
      WHERE token_hash = ? AND user_id = ?
    `).bind(await sha256Hex(token), user.user_id).run();
  }
  return json({ ok: true });
}

async function createAgentToken(request, env, user) {
  const body = await readOptionalJson(request);
  const revokeExisting = body.revokeExisting === true || body.rotate === true;
  const label = boundedString(body.label || "Claude/Codex", "label", MAX_AGENT_LABEL_CHARS, true) || "Claude/Codex";
  const defaultInstanceId = normalizeInstanceId(
    languageVersionToInstanceId(body.instanceId || body.version || body.languageVersion || body.langVersion || body.language_version) ||
      body.instanceId ||
      "langbangml-en-pl",
  );
  if (revokeExisting) {
    await env.DB.prepare(`
      UPDATE user_agent_tokens
      SET revoked_at = datetime('now')
      WHERE user_id = ? AND revoked_at IS NULL
    `).bind(user.user_id).run();
  }

  const token = `lba_${randomBase64Url(32)}`;
  const id = crypto.randomUUID();
  const tokenHash = await sha256Hex(token);
  const tokenPrefix = token.slice(0, 12);
  await env.DB.prepare(`
    INSERT INTO user_agent_tokens
      (id, user_id, token_hash, token_prefix, label, default_instance_id)
    VALUES (?, ?, ?, ?, ?, ?)
  `).bind(id, user.user_id, tokenHash, tokenPrefix, label, defaultInstanceId).run();

  return json({
    ok: true,
    token,
    tokenPrefix,
    label,
    defaultInstanceId,
    dailyLimit: agentDailyLimit(env),
    apiBase: publicApiBase(env),
    instructionsUrl: publicAgentDocsUrl(env),
    createdAt: new Date().toISOString(),
  });
}

async function userPhrases(request, env, user) {
  if (request.method === "GET") {
    const instanceId = new URL(request.url).searchParams.get("instanceId") || "";
    return json(await loadUserPhrases(env, user.user_id, normalizeInstanceId(instanceId)));
  }
  if (request.method === "PUT") {
    const body = await request.json();
    const instanceId = normalizeInstanceId(body.instanceId);
    const groups = normalizePhraseGroups(body.groups);
    const starredPhrases = normalizeStarredPhrases(body.starredPhrases);
    const replace = body.replace === true;

    if (replace) {
      const groupIds = groups.map((group) => group.id);
      if (groupIds.length === 0) {
        await env.DB.prepare(`
          UPDATE user_phrase_groups
          SET deleted_at = datetime('now'), updated_at = datetime('now')
          WHERE user_id = ? AND instance_id = ? AND deleted_at IS NULL
        `).bind(user.user_id, instanceId).run();
      } else {
        const placeholders = groupIds.map(() => "?").join(",");
        await env.DB.prepare(`
          UPDATE user_phrase_groups
          SET deleted_at = datetime('now'), updated_at = datetime('now')
          WHERE user_id = ? AND instance_id = ? AND deleted_at IS NULL
            AND group_id NOT IN (${placeholders})
        `).bind(user.user_id, instanceId, ...groupIds).run();
      }
      await env.DB.prepare(`
        UPDATE user_starred_phrases
        SET starred = 0, updated_at = datetime('now')
        WHERE user_id = ? AND instance_id = ?
      `).bind(user.user_id, instanceId).run();
    }

    for (const [index, group] of groups.entries()) {
      await env.DB.prepare(`
        INSERT INTO user_phrase_groups
          (user_id, instance_id, group_id, sort_order, group_json, updated_at, deleted_at)
        VALUES (?, ?, ?, ?, ?, datetime('now'), NULL)
        ON CONFLICT(user_id, instance_id, group_id) DO UPDATE SET
          sort_order = excluded.sort_order,
          group_json = excluded.group_json,
          updated_at = datetime('now'),
          deleted_at = NULL
      `).bind(user.user_id, instanceId, group.id, index, JSON.stringify(group)).run();
    }

    for (const phraseKey of starredPhrases) {
      await env.DB.prepare(`
        INSERT INTO user_starred_phrases
          (user_id, instance_id, phrase_key, starred, updated_at)
        VALUES (?, ?, ?, 1, datetime('now'))
        ON CONFLICT(user_id, instance_id, phrase_key) DO UPDATE SET
          starred = 1,
          updated_at = datetime('now')
      `).bind(user.user_id, instanceId, phraseKey).run();
    }

    await env.DB.prepare(`
      INSERT INTO sync_events (id, instance_id, event_type, payload_json)
      VALUES (?, ?, ?, ?)
    `).bind(
      crypto.randomUUID(),
      `user:${instanceId}`,
      "user.phrases.sync",
      JSON.stringify({ userId: user.user_id, groupCount: groups.length, starCount: starredPhrases.length, replace }),
    ).run();
    return json(await loadUserPhrases(env, user.user_id, instanceId));
  }
  return json({ error: "method not allowed" }, 405);
}

async function userContent(request, env, user) {
  if (request.method !== "GET") {
    return json({ error: "method not allowed" }, 405);
  }
  const instanceId = new URL(request.url).searchParams.get("instanceId") || "";
  return json(await loadUserContent(env, user.user_id, normalizeInstanceId(instanceId)));
}

async function userAiPhraseQuota(request, env, user) {
  const quota = await loadAiPhraseQuota(env, user.user_id);
  return json({ ok: true, quota });
}

async function userAiPhraseQuotaRequest(request, env, user) {
  if (request.method !== "POST") {
    return json({ error: "method not allowed" }, 405);
  }
  const body = await readOptionalJson(request);
  const instanceId = body.instanceId ? normalizeInstanceId(body.instanceId) : null;
  const message = boundedString(body.message || "", "message", 1200, true);
  const quota = await loadAiPhraseQuota(env, user.user_id);
  const sent = await sendAiPhraseQuotaEmail(env, {
    email: user.email,
    displayName: user.display_name || "",
    userId: user.user_id,
    instanceId,
    quota,
    message,
  });
  await env.DB.prepare(`
    INSERT INTO user_ai_phrase_quota_requests
      (id, user_id, email, instance_id, current_quota, generated_count, message, sent)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `).bind(
    crypto.randomUUID(),
    user.user_id,
    user.email,
    instanceId,
    quota.limit,
    quota.used,
    message,
    sent ? 1 : 0,
  ).run();
  return json({ ok: true, sent, quota });
}

async function userAiPhraseGenerate(request, env, user) {
  if (request.method !== "POST") {
    return json({ error: "method not allowed" }, 405);
  }
  const body = await readJsonLimited(request, MAX_AGENT_BODY_CHARS);
  const instanceId = normalizeInstanceId(body.instanceId || "langbangml-en-pl");
  const groupId = normalizePhraseGroupId(body.groupId || "ai-phrases");
  const groupTitle = boundedString(body.groupTitle || "AI phrases", "groupTitle", 200, true) || "AI phrases";
  const groupSubtitle = boundedString(body.groupSubtitle || "Generated phrase practice", "groupSubtitle", 400, true);
  const prompt = boundedString(body.prompt || body.topic || "", "prompt", MAX_AI_PHRASE_PROMPT_CHARS);
  const parsedCount = Number(body.count || 5);
  const requestedCount = Number.isFinite(parsedCount)
    ? Math.max(1, Math.min(MAX_AI_PHRASES_PER_REQUEST, Math.trunc(parsedCount)))
    : 5;
  const quota = await loadAiPhraseQuota(env, user.user_id);
  if (quota.remaining <= 0) {
    throw new HttpError(429, "AI phrase quota reached", {
      quota,
      canRequestMore: true,
      requestEndpoint: "/v1/me/phrases/ai-quota-request",
    });
  }
  const count = Math.min(requestedCount, quota.remaining);
  const content = await loadContentForInstance(env, instanceId);
  const generated = await generateAiPhraseBatch(env, {
    prompt,
    count,
    pair: content.languagePair,
  });
  const current = await loadUserPhrases(env, user.user_id, instanceId);
  const groups = current.groups.slice();
  const index = groups.findIndex((group) => group.id.toLowerCase() === groupId.toLowerCase());
  const existing = index >= 0
    ? groups[index]
    : { id: groupId, title: groupTitle, subtitle: groupSubtitle, sentences: [] };
  const sentences = Array.isArray(existing.sentences) ? existing.sentences.slice() : [];
  for (const sentence of generated) {
    const key = sentenceKey(sentence);
    if (!sentences.some((candidate) => sentenceKey(candidate) === key)) {
      sentences.push(sentence);
    }
  }
  const updated = {
    ...existing,
    title: existing.title || groupTitle,
    subtitle: existing.subtitle || groupSubtitle,
    sentences,
  };
  await upsertUserPhraseGroup(env, user.user_id, instanceId, updated, index >= 0 ? index : 0);
  const updatedQuota = await addAiPhraseUsage(env, user.user_id, generated.length);
  return json({
    ok: true,
    instanceId,
    group: updated,
    phrases: generated,
    quota: updatedQuota,
  });
}

async function loadAiPhraseQuota(env, userId) {
  await env.DB.prepare(`
    INSERT OR IGNORE INTO user_ai_phrase_quotas (user_id, quota)
    VALUES (?, ?)
  `).bind(userId, DEFAULT_AI_PHRASE_QUOTA).run();
  const row = await env.DB.prepare(`
    SELECT quota, generated_count
    FROM user_ai_phrase_quotas
    WHERE user_id = ?
  `).bind(userId).first();
  const limit = Math.max(0, Number(row?.quota || DEFAULT_AI_PHRASE_QUOTA));
  const used = Math.max(0, Number(row?.generated_count || 0));
  return {
    limit,
    used,
    remaining: Math.max(0, limit - used),
  };
}

async function addAiPhraseUsage(env, userId, count) {
  await env.DB.prepare(`
    UPDATE user_ai_phrase_quotas
    SET generated_count = generated_count + ?,
        updated_at = datetime('now')
    WHERE user_id = ?
  `).bind(Math.max(0, Number(count) || 0), userId).run();
  return loadAiPhraseQuota(env, userId);
}

async function loadUserPhrases(env, userId, instanceId) {
  const [groupRows, starRows] = await Promise.all([
    env.DB.prepare(`
      SELECT group_json
      FROM user_phrase_groups
      WHERE user_id = ? AND instance_id = ? AND deleted_at IS NULL
      ORDER BY
        CASE WHEN json_extract(group_json, '$.sortOrder') IS NULL THEN 1 ELSE 0 END ASC,
        CAST(json_extract(group_json, '$.sortOrder') AS INTEGER) ASC,
        COALESCE(
          CAST(json_extract(group_json, '$.createdAt') AS INTEGER),
          unixepoch(updated_at) * 1000
        ) DESC,
        sort_order ASC,
        updated_at DESC
    `).bind(userId, instanceId).all(),
    env.DB.prepare(`
      SELECT phrase_key
      FROM user_starred_phrases
      WHERE user_id = ? AND instance_id = ? AND starred = 1
      ORDER BY updated_at ASC
    `).bind(userId, instanceId).all(),
  ]);
  return {
    instanceId,
    groups: groupRows.results.map((row) => parseJson(row.group_json, null)).filter(Boolean),
    starredPhrases: starRows.results.map((row) => row.phrase_key),
    syncedAt: new Date().toISOString(),
  };
}

async function loadUserContent(env, userId, instanceId) {
  const [phrases, words, meta] = await Promise.all([
    loadUserPhrases(env, userId, instanceId),
    loadUserWords(env, userId, instanceId),
    userContentMeta(env, userId, instanceId),
  ]);
  return {
    ...phrases,
    words,
    hasRemoteContent: meta.rows > 0,
    hasRemotePhrases: meta.phraseRows > 0 || meta.starRows > 0,
    hasRemoteWords: meta.wordRows > 0,
  };
}

async function userContentMeta(env, userId, instanceId) {
  const [groups, stars, words] = await Promise.all([
    env.DB.prepare(`
      SELECT COUNT(*) AS rows
      FROM user_phrase_groups
      WHERE user_id = ? AND instance_id = ?
    `).bind(userId, instanceId).first(),
    env.DB.prepare(`
      SELECT COUNT(*) AS rows
      FROM user_starred_phrases
      WHERE user_id = ? AND instance_id = ?
    `).bind(userId, instanceId).first(),
    env.DB.prepare(`
      SELECT COUNT(*) AS rows
      FROM user_custom_words
      WHERE user_id = ? AND instance_id = ?
    `).bind(userId, instanceId).first(),
  ]);
  return {
    phraseRows: Number(groups?.rows || 0),
    starRows: Number(stars?.rows || 0),
    wordRows: Number(words?.rows || 0),
    rows: Number(groups?.rows || 0) + Number(stars?.rows || 0) + Number(words?.rows || 0),
  };
}

async function loadUserWords(env, userId, instanceId) {
  const rows = await env.DB.prepare(`
    SELECT word_type, item_json
    FROM user_custom_words
    WHERE user_id = ? AND instance_id = ? AND deleted_at IS NULL
    ORDER BY word_type, updated_at ASC
  `).bind(userId, instanceId).all();
  const words = emptyWordBuckets();
  for (const row of rows.results || []) {
    const bucket = wordBucketName(row.word_type);
    if (!bucket) continue;
    const item = parseJson(row.item_json, null);
    if (item) words[bucket].push(item);
  }
  return words;
}

async function agentApiRequest(request, env, operation, handler) {
  const agent = await requireAgentToken(request, env);
  let quota;
  try {
    quota = await consumeAgentQuota(env, agent);
  } catch (error) {
    await recordAgentApiCall(env, agent, request, operation, error instanceof HttpError ? error.status : 500);
    throw error;
  }
  try {
    const response = await handler(request, env, agent, quota);
    await recordAgentApiCall(env, agent, request, operation, response.status);
    return response;
  } catch (error) {
    await recordAgentApiCall(env, agent, request, operation, error instanceof HttpError ? error.status : 500);
    throw error;
  }
}

async function requireAgentToken(request, env) {
  const auth = request.headers.get("Authorization") || "";
  const token = auth.match(/^Bearer\s+(.+)$/i)?.[1]?.trim();
  if (!token) throw new HttpError(401, "agent token required");
  const tokenHash = await sha256Hex(token);
  const row = await env.DB.prepare(`
    SELECT t.id AS token_id, t.user_id, t.token_prefix, t.default_instance_id,
           u.email, u.display_name
    FROM user_agent_tokens t
    JOIN users u ON u.id = t.user_id
    WHERE t.token_hash = ?
      AND t.revoked_at IS NULL
      AND (t.expires_at IS NULL OR t.expires_at > datetime('now'))
  `).bind(tokenHash).first();
  if (!row) throw new HttpError(401, "invalid or revoked agent token");
  await env.DB.prepare(`
    UPDATE user_agent_tokens
    SET last_used_at = datetime('now')
    WHERE id = ?
  `).bind(row.token_id).run();
  return row;
}

async function consumeAgentQuota(env, agent) {
  const day = new Date().toISOString().slice(0, 10);
  const limit = agentDailyLimit(env);
  await env.DB.prepare(`
    INSERT INTO user_agent_token_usage (token_id, day, call_count, updated_at)
    VALUES (?, ?, 1, datetime('now'))
    ON CONFLICT(token_id, day) DO UPDATE SET
      call_count = call_count + 1,
      updated_at = datetime('now')
  `).bind(agent.token_id, day).run();
  const row = await env.DB.prepare(`
    SELECT call_count
    FROM user_agent_token_usage
    WHERE token_id = ? AND day = ?
  `).bind(agent.token_id, day).first();
  const used = Number(row?.call_count || 0);
  if (used > limit) {
    throw new HttpError(429, "daily agent API limit reached", {
      limit,
      used,
      remaining: 0,
      resetsAt: `${day}T23:59:59.999Z`,
    });
  }
  return {
    day,
    limit,
    used,
    remaining: Math.max(0, limit - used),
    resetsAt: `${day}T23:59:59.999Z`,
  };
}

async function recordAgentApiCall(env, agent, request, operation, status, instanceId = null) {
  try {
    const url = new URL(request.url);
    await env.DB.prepare(`
      INSERT INTO user_agent_api_call_events
        (id, token_id, user_id, instance_id, method, route, operation, status)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
      crypto.randomUUID(),
      agent?.token_id || null,
      agent?.user_id || null,
      instanceId,
      request.method,
      url.pathname,
      operation,
      status,
    ).run();
  } catch {
    // Agent API audit logging must not block the user's content edit.
  }
}

async function agentStatus(request, env, agent, quota) {
  return json({
    ok: true,
    user: {
      id: agent.user_id,
      email: agent.email,
      displayName: agent.display_name || "",
    },
    tokenPrefix: agent.token_prefix,
    defaultInstanceId: agent.default_instance_id,
    apiBase: publicApiBase(env),
    instructionsUrl: publicAgentDocsUrl(env),
    quota,
  });
}

async function agentPhrases(request, env, agent, quota) {
  if (request.method === "GET") {
    const params = new URL(request.url).searchParams;
    const instanceId = normalizeAgentInstanceIdFromParams(params, agent);
    const current = await loadUserPhrases(env, agent.user_id, instanceId);
    const requestedGroupId = requestedAgentPhraseGroupId(params);
    const groupsOnly = truthyParam(params.get("groupsOnly") || params.get("summary"));
    let groups = current.groups;
    if (requestedGroupId) {
      groups = groups.filter((group) => group.id.toLowerCase() === requestedGroupId.toLowerCase());
      if (groups.length === 0) throw new HttpError(404, "phrase group not found", { groupId: requestedGroupId });
    }
    const responseGroups = groupsOnly ? groups.map(agentPhraseGroupSummary) : groups;
    return json({
      ok: true,
      ...current,
      groups: responseGroups,
      groupsOnly,
      group: requestedGroupId && !groupsOnly ? groups[0] : undefined,
      quota,
    });
  }
  if (request.method === "POST") {
    const body = await readJsonLimited(request, MAX_AGENT_BODY_CHARS);
    const instanceId = normalizeAgentInstanceIdFromBody(body, agent);
    const content = await loadContentForInstance(env, instanceId);
    const groupTitleInput = cleanString(body.groupTitle || body.groupName || body.group?.title || body.group?.name);
    const groupIdInput = cleanString(body.groupId || body.group?.id);
    const groupTitle = boundedString(
      groupTitleInput || groupIdInput || "Agent phrases",
      "groupTitle",
      MAX_AGENT_GROUP_TITLE_CHARS,
      true,
    ) || "Agent phrases";
    const now = Date.now();
    const groupId = normalizePhraseGroupId(groupIdInput || timestampedPhraseGroupId(groupTitle, now));
    const groupSubtitle = boundedString(
      body.groupSubtitle || body.group?.subtitle || "",
      "groupSubtitle",
      MAX_AGENT_GROUP_SUBTITLE_CHARS,
      true,
    );
    const groupCollection = normalizeOptionalPhraseGroupCollection(
      body.collection || body.groupCollection || body.group?.collection,
    );
    const sortOrder = normalizeOptionalGroupSortOrder(body.sortOrder ?? body.order ?? body.group?.sortOrder ?? body.group?.order);
    const atomic = normalizeAgentAtomicFlag(body);
    const sentences = await normalizeAgentPhraseSentences(env, body, content.languagePair, atomic);
    const current = await loadUserPhrases(env, agent.user_id, instanceId);
    const groups = current.groups.slice();
    const index = groups.findIndex((group) => group.id.toLowerCase() === groupId.toLowerCase());
    const existing = index >= 0
      ? groups[index]
      : { id: groupId, title: groupTitle, subtitle: groupSubtitle, createdAt: now, sentences: [] };
    const nextSentences = Array.isArray(existing.sentences) ? existing.sentences.slice() : [];
    let replaced = 0;
    let added = 0;
    for (const sentence of sentences) {
      const key = sentenceKey(sentence);
      const existingSentenceIndex = nextSentences.findIndex((item) => sentenceKey(item) === key);
      if (existingSentenceIndex >= 0) {
        nextSentences[existingSentenceIndex] = sentence;
        replaced += 1;
      } else {
        nextSentences.push(sentence);
        added += 1;
      }
    }
    const updated = {
      ...existing,
      title: existing.title || groupTitle,
      subtitle: existing.subtitle || groupSubtitle,
      collection: groupCollection ?? existing.collection,
      createdAt: existing.createdAt || now,
      sortOrder: sortOrder ?? existing.sortOrder,
      sentences: nextSentences,
    };
    if (updated.collection === undefined) delete updated.collection;
    if (updated.sortOrder === undefined) delete updated.sortOrder;
    if (index >= 0) groups[index] = updated;
    else groups.unshift(updated);
    await upsertUserPhraseGroup(env, agent.user_id, instanceId, updated, index >= 0 ? index : 0);
    return json({
      ok: true,
      action: replaced > 0 && added === 0 ? "replace_phrase" : "add_phrase",
      instanceId,
      version: agentInstanceVersion(instanceId),
      atomic,
      group: updated,
      phrase: sentences[0],
      phrases: sentences,
      added,
      replaced,
      quota,
    });
  }
  if (request.method === "DELETE") {
    const body = await readOptionalJson(request);
    const params = new URL(request.url).searchParams;
    const instanceId = normalizeAgentInstanceIdFromBodyOrParams(body, params, agent);
    const deleteGroupTitle = cleanString(body.groupTitle || body.groupName || params.get("groupTitle") || params.get("groupName"));
    const groupId = normalizePhraseGroupId(body.groupId || params.get("groupId") || (deleteGroupTitle ? slug(deleteGroupTitle) : ""));
    const phraseKey = cleanString(body.phraseKey || body.key || params.get("phraseKey"));
    const fields = normalizeAgentPhraseLanguageFields(body, languagePairForInstanceId(instanceId));
    const pl = cleanString(fields.pl || body.pl || body.target || params.get("pl") || params.get("polish"));
    const en = cleanString(fields.en || body.en || body.source || params.get("en") || params.get("english"));
    if (!phraseKey && !pl && !en) {
      await env.DB.prepare(`
        UPDATE user_phrase_groups
        SET deleted_at = datetime('now'), updated_at = datetime('now')
        WHERE user_id = ? AND instance_id = ? AND group_id = ? AND deleted_at IS NULL
      `).bind(agent.user_id, instanceId, groupId).run();
      return json({ ok: true, action: "delete_phrase_group", instanceId, groupId, quota });
    }
    const current = await loadUserPhrases(env, agent.user_id, instanceId);
    const group = current.groups.find((candidate) => candidate.id.toLowerCase() === groupId.toLowerCase());
    if (!group) throw new HttpError(404, "phrase group not found", { groupId });
    const before = Array.isArray(group.sentences) ? group.sentences : [];
    const after = before.filter((sentence) => !sentenceMatchesDelete(sentence, { phraseKey, pl, en }));
    if (after.length === before.length) throw new HttpError(404, "phrase not found", { groupId, phraseKey, pl, en });
    const updated = { ...group, sentences: after };
    await upsertUserPhraseGroup(env, agent.user_id, instanceId, updated, 0);
    return json({
      ok: true,
      action: "delete_phrase",
      instanceId,
      groupId,
      removed: before.length - after.length,
      quota,
    });
  }
  return json({ error: "method not allowed" }, 405);
}

async function agentWords(request, env, agent, quota) {
  if (request.method === "GET") {
    const instanceId = normalizeAgentInstanceIdFromParams(new URL(request.url).searchParams, agent);
    return json({ ok: true, instanceId, words: await loadUserWords(env, agent.user_id, instanceId), quota });
  }
  if (request.method === "POST") {
    const body = await readJsonLimited(request, MAX_AGENT_BODY_CHARS);
    const instanceId = normalizeAgentInstanceIdFromBody(body, agent);
    const wordType = normalizeWordType(body.type || body.wordType || body.section);
    const item = normalizeAgentWordItem(wordType, body.item || body.word || body);
    await upsertUserWord(env, agent.user_id, instanceId, wordType, item);
    return json({ ok: true, action: "upsert_word", instanceId, type: wordType, item, quota });
  }
  if (request.method === "DELETE") {
    const body = await readOptionalJson(request);
    const params = new URL(request.url).searchParams;
    const instanceId = normalizeAgentInstanceIdFromBodyOrParams(body, params, agent);
    const wordType = normalizeWordType(body.type || body.wordType || body.section || params.get("type"));
    const lemma = boundedString(body.lemma || params.get("lemma"), "lemma", 200);
    await env.DB.prepare(`
      UPDATE user_custom_words
      SET deleted_at = datetime('now'), updated_at = datetime('now')
      WHERE user_id = ? AND instance_id = ? AND word_type = ? AND lower(lemma) = lower(?)
    `).bind(agent.user_id, instanceId, wordType, lemma).run();
    return json({ ok: true, action: "delete_word", instanceId, type: wordType, lemma, quota });
  }
  return json({ error: "method not allowed" }, 405);
}

async function generateLiteralForSentence(env, sentence) {
  const { pl, en } = sentence;
  if (!pl.includes(" ")) return en;
  if (!env.GEMINI_API_KEY) return null;
  try {
    const prompt =
      "Generate a word-for-word English gloss of a Polish phrase, preserving Polish word order. " +
      "Use hyphens to join multi-word glosses of a single token. " +
      `Polish: ${JSON.stringify(pl)}. English meaning: ${JSON.stringify(en)}. ` +
      'Return ONLY JSON: {"literal":"word-for-word gloss"}';
    const text = await geminiGenerateText(env, prompt);
    const parsed = JSON.parse(text);
    const lit = typeof parsed?.literal === "string" ? parsed.literal.trim() : "";
    return lit || null;
  } catch {
    return null;
  }
}

async function upsertUserPhraseGroup(env, userId, instanceId, group, sortOrder) {
  const sentences = Array.isArray(group.sentences) ? group.sentences : [];
  const needsLiteral = sentences.some((s) => !s.literal);
  if (needsLiteral) {
    const filled = await Promise.all(
      sentences.map(async (s) => {
        if (s.literal) return s;
        const literal = await generateLiteralForSentence(env, s);
        return literal ? { ...s, literal } : s;
      }),
    );
    group = { ...group, sentences: filled };
  }
  await env.DB.prepare(`
    INSERT INTO user_phrase_groups
      (user_id, instance_id, group_id, sort_order, group_json, updated_at, deleted_at)
    VALUES (?, ?, ?, ?, ?, datetime('now'), NULL)
    ON CONFLICT(user_id, instance_id, group_id) DO UPDATE SET
      sort_order = excluded.sort_order,
      group_json = excluded.group_json,
      updated_at = datetime('now'),
      deleted_at = NULL
  `).bind(userId, instanceId, group.id, sortOrder, JSON.stringify(group)).run();
}

async function upsertUserWord(env, userId, instanceId, wordType, item) {
  await env.DB.prepare(`
    INSERT INTO user_custom_words
      (user_id, instance_id, word_type, lemma, item_json, updated_at, deleted_at)
    VALUES (?, ?, ?, ?, ?, datetime('now'), NULL)
    ON CONFLICT(user_id, instance_id, word_type, lemma) DO UPDATE SET
      item_json = excluded.item_json,
      updated_at = datetime('now'),
      deleted_at = NULL
  `).bind(userId, instanceId, wordType, item.lemma, JSON.stringify(item)).run();
}

function normalizeAgentInstanceId(value, agent) {
  const requested = languageVersionToInstanceId(value) || cleanString(value);
  const tokenDefault = languageVersionToInstanceId(agent.default_instance_id) || cleanString(agent.default_instance_id);
  return normalizeInstanceId(requested || tokenDefault || "langbangml-en-pl");
}

function normalizeAgentInstanceIdFromBody(body, agent) {
  return normalizeAgentInstanceId(
    body.instanceId || body.instance || body.version || body.languageVersion || body.langVersion || body.language_version,
    agent,
  );
}

function normalizeAgentInstanceIdFromParams(params, agent) {
  return normalizeAgentInstanceId(
    params.get("instanceId") || params.get("instance") || params.get("version") || params.get("languageVersion") || params.get("langVersion"),
    agent,
  );
}

function normalizeAgentInstanceIdFromBodyOrParams(body, params, agent) {
  return normalizeAgentInstanceId(
    body.instanceId || body.instance || body.version || body.languageVersion || body.langVersion || body.language_version ||
      params.get("instanceId") || params.get("instance") || params.get("version") || params.get("languageVersion") || params.get("langVersion"),
    agent,
  );
}

function languageVersionToInstanceId(value) {
  const raw = cleanString(value);
  if (!raw) return "";
  const key = raw.toLowerCase().replace(/[^a-z0-9]/g, "");
  if (key === "enpl" || key === "englishpolish" || key === "langbangmlenpl" || key === "langbangenpl") {
    return "langbangml-en-pl";
  }
  if (key === "plen" || key === "polishenglish" || key === "langbangmlplen" || key === "langbangplen") {
    return "langbangml-pl-en";
  }
  return "";
}

function agentInstanceVersion(instanceId) {
  if (instanceId === "langbangml-pl-en") return "PLEN";
  return "ENPL";
}

function languagePairForInstanceId(instanceId) {
  if (instanceId === "langbangml-pl-en") {
    return { sourceLanguage: "Polish", targetLanguage: "English" };
  }
  return { sourceLanguage: "English", targetLanguage: "Polish" };
}

function normalizeAgentAtomicFlag(body) {
  const atomic = body.atomic;
  const breakDown = body.breakDown ?? body.breakdown ?? body.split ?? body.splitLongPhrases;
  if (atomic === false || breakDown === true) return false;
  if (typeof atomic === "string" && /^(false|no|split|breakdown|break-down)$/i.test(atomic.trim())) return false;
  if (typeof breakDown === "string" && /^(true|yes|split|breakdown|break-down)$/i.test(breakDown.trim())) return false;
  return true;
}

function requestedAgentPhraseGroupId(params) {
  const groupId = cleanString(params.get("groupId"));
  if (groupId) return normalizePhraseGroupId(groupId);
  const groupTitle = cleanString(params.get("groupTitle") || params.get("groupName"));
  return groupTitle ? normalizePhraseGroupId(slug(groupTitle)) : "";
}

function truthyParam(value) {
  return /^(1|true|yes|y|summary|groups)$/i.test(cleanString(value));
}

function agentPhraseGroupSummary(group) {
  const sentences = Array.isArray(group.sentences) ? group.sentences : [];
  return {
    id: group.id,
    title: group.title || group.id,
    subtitle: group.subtitle || "",
    collection: group.collection || "",
    sentenceCount: sentences.length,
  };
}

function normalizeAgentPhraseInputs(body) {
  const inputs = Array.isArray(body.phrases)
    ? body.phrases
    : Array.isArray(body.sentences)
      ? body.sentences
      : Array.isArray(body.items)
        ? body.items
        : [body.phrase || body.sentence || body.item || body];
  if (inputs.length === 0) throw new HttpError(400, "at least one phrase is required");
  if (inputs.length > MAX_AGENT_PHRASE_INPUTS) {
    throw new HttpError(400, "too many phrase inputs", { limit: MAX_AGENT_PHRASE_INPUTS });
  }
  return inputs;
}

async function normalizeAgentPhraseSentences(env, body, pair, atomic) {
  const inputs = normalizeAgentPhraseInputs(body);
  const out = [];
  for (const input of inputs) {
    out.push(...await generateAgentPhraseEntries(env, input, pair, atomic));
    if (out.length > MAX_AGENT_PHRASE_OUTPUTS) {
      throw new HttpError(400, "too many generated phrases", { limit: MAX_AGENT_PHRASE_OUTPUTS });
    }
  }
  if (out.length === 0) throw new HttpError(502, "Gemini returned no phrase entries");
  return out;
}

async function generateAgentPhraseEntries(env, raw, pair, atomic) {
  const fields = normalizeAgentPhraseLanguageFields(raw, pair);
  if (!fields.en && !fields.pl && !fields.literal && fields.words.length === 0) {
    throw new HttpError(400, "provide english, polish, en, pl, source, target, or literal phrase text");
  }
  if (hasCompleteAgentPhraseFields(fields)) {
    return [normalizeAgentPhraseEntry(raw, pair, atomic)];
  }
  const text = await geminiGenerateText(
    env,
    buildAgentPhraseEntriesPrompt({ fields, pair, atomic }),
    DEFAULT_GEMINI_MODEL,
  );
  const parsed = parseGeminiJsonText(text);
  const entries = Array.isArray(parsed?.phrases) ? parsed.phrases : Array.isArray(parsed) ? parsed : [];
  const normalized = entries.map((entry) => normalizeAgentPhraseEntry(entry, pair, atomic)).filter(Boolean);
  if (normalized.length === 0) throw new HttpError(502, "Gemini returned no usable phrase entries");
  if (atomic && normalized.length !== 1) return normalized.slice(0, 1);
  return normalized;
}

function hasCompleteAgentPhraseFields(fields) {
  return Boolean(fields.en && fields.pl && fields.literal && fields.words.length > 0);
}

function normalizeAgentPhraseLanguageFields(raw, pair) {
  const value = typeof raw === "string" ? { source: raw } : raw && typeof raw === "object" && !Array.isArray(raw) ? raw : {};
  const sourceIsEnglish = isEnglishLanguage(pair?.sourceLanguage);
  const targetIsEnglish = isEnglishLanguage(pair?.targetLanguage);
  const sourceText = firstString(value.source, value.sourceText, value.learningLanguage, value.learningText);
  const targetText = firstString(value.target, value.targetText, value.targetLanguage, value.targetLanguageText);
  const englishText = firstString(value.english, value.englishText);
  const polishText = firstString(value.polish, value.polishText);
  const nativeEn = cleanString(value.en);
  const nativePl = cleanString(value.pl);
  const en = nativeEn || sourceText || (sourceIsEnglish ? englishText : targetIsEnglish ? polishText : englishText);
  const pl = nativePl || targetText || (targetIsEnglish ? englishText : sourceIsEnglish ? polishText : polishText);
  return {
    en: boundedOptionalPhraseString(en, "phrase source"),
    pl: boundedOptionalPhraseString(pl, "phrase target"),
    literal: boundedOptionalPhraseString(value.literal || value.literalText || value.gloss, "phrase literal"),
    words: Array.isArray(value.words) ? value.words.map(normalizeTokenPair).filter(Boolean).slice(0, 200) : [],
    index: normalizeOptionalPhraseIndex(value.index ?? value.sortIndex ?? value.order ?? value.position),
  };
}

function firstString(...values) {
  for (const value of values) {
    const text = cleanString(value);
    if (text) return text;
  }
  return "";
}

function boundedOptionalPhraseString(value, label) {
  const text = cleanString(value);
  if (text.length > MAX_PHRASE_FIELD_CHARS) {
    throw new HttpError(400, `${label} is too long`, { limit: MAX_PHRASE_FIELD_CHARS });
  }
  return text;
}

function normalizeAgentPhraseEntry(entry, pair, atomic) {
  const fields = normalizeAgentPhraseLanguageFields(entry, pair);
  const sentence = normalizeSentenceExample({
    en: fields.en,
    pl: fields.pl,
    literal: fields.literal,
    words: fields.words,
    index: fields.index,
  });
  if (isPolishLanguage(pair?.targetLanguage)) {
    sentence.pl = scrubPolishYallText(sentence.pl);
    if (Array.isArray(sentence.words)) {
      sentence.words = sentence.words.map((word) => ({ ...word, pl: scrubPolishYallText(word.pl) }));
    }
  }
  if (!atomic) {
    const targetChars = sentence.pl.length;
    const targetWords = sentence.pl.split(/\s+/).filter(Boolean).length;
    if (targetChars > MAX_AGENT_PHRASE_TARGET_CHARS || targetWords > MAX_AGENT_PHRASE_TARGET_WORDS) {
      throw new HttpError(502, "Gemini returned a phrase that is still too long", {
        targetChars,
        targetWords,
        maxTargetChars: MAX_AGENT_PHRASE_TARGET_CHARS,
        maxTargetWords: MAX_AGENT_PHRASE_TARGET_WORDS,
      });
    }
  }
  return sentence;
}

function isEnglishLanguage(value) {
  return /\benglish\b|\ben\b/i.test(String(value || ""));
}

function buildAgentPhraseEntriesPrompt({ fields, pair, atomic }) {
  const sourceLanguage = pair?.sourceLanguage || "English";
  const targetLanguage = pair?.targetLanguage || "Polish";
  const mode = atomic
    ? "Return exactly one phrase entry. Preserve it as one atomic study item."
    : `Break the input into short display-safe study items. Each target answer must be at most ${MAX_AGENT_PHRASE_TARGET_WORDS} whitespace-separated tokens and ${MAX_AGENT_PHRASE_TARGET_CHARS} characters. Preserve meaning and order; do not add unrelated examples.`;
  return "Prepare LangBang custom phrase entries for one learner. " +
    `The source cue language is ${sourceLanguage}. The target answer language is ${targetLanguage}. ` +
    "The API may receive only English, only Polish, or both; infer and validate the missing side. " +
    "The app stores source cue text in a JSON field named en and target answer text in a JSON field named pl, " +
    "even when the target language is not Polish. " +
    `${mode} ` +
    `Input source cue=${JSON.stringify(fields.en)}, target answer=${JSON.stringify(fields.pl)}, ` +
    `literal=${JSON.stringify(fields.literal)}, words=${JSON.stringify(fields.words)}. ` +
    "Fill literal as a word-for-word gloss of the target answer, preserving target word order. " +
    "Fill words: one object per whitespace-separated target token, in target order; put the target token in pl " +
    "and the source-language gloss in en. If the target language is Polish and a token is a noun, pronoun, or " +
    "adjective, include gender (m/f/n) and caseKey (nom/acc/gen/dat/inst/loc/voc) when known. " +
    "Use common, natural learner language. Reject awkward literal translations, rare expressions, and contradictions " +
    "by returning an empty phrases array. Return ONLY JSON, no markdown, with this exact shape: " +
    "{\"phrases\":[{\"en\":\"source cue\",\"pl\":\"target answer\",\"literal\":\"target-order gloss\"," +
    "\"words\":[{\"pl\":\"target-token\",\"en\":\"source-gloss\"}]}]}.";
}

function normalizePhraseGroupId(value) {
  const id = cleanString(value);
  if (!/^[A-Za-z0-9._-]{1,120}$/.test(id)) {
    throw new HttpError(400, "valid groupId is required");
  }
  return id;
}

function timestampedPhraseGroupId(title, now = Date.now()) {
  return normalizePhraseGroupId(`${slug(title)}-${Math.trunc(now).toString(36)}`);
}

function sentenceKey(sentence) {
  return `${cleanString(sentence.pl).toLowerCase()}|${cleanString(sentence.en).toLowerCase()}`;
}

function sentenceMatchesDelete(sentence, query) {
  if (query.phraseKey && sentenceKey(sentence) === query.phraseKey.toLowerCase()) return true;
  const sentencePl = cleanString(sentence.pl).toLowerCase();
  const sentenceEn = cleanString(sentence.en).toLowerCase();
  const pl = cleanString(query.pl).toLowerCase();
  const en = cleanString(query.en).toLowerCase();
  if (pl && en) return sentencePl === pl && sentenceEn === en;
  if (pl) return sentencePl === pl;
  if (en) return sentenceEn === en;
  return false;
}

function emptyWordBuckets() {
  return { verbs: [], nouns: [], adjectives: [], adverbs: [] };
}

function wordBucketName(wordType) {
  if (wordType === "verb") return "verbs";
  if (wordType === "noun") return "nouns";
  if (wordType === "adjective") return "adjectives";
  if (wordType === "adverb") return "adverbs";
  return null;
}

function normalizeWordType(value) {
  const raw = cleanString(value).toLowerCase();
  if (["verb", "verbs", "v"].includes(raw)) return "verb";
  if (["noun", "nouns", "n"].includes(raw)) return "noun";
  if (["adjective", "adjectives", "adj"].includes(raw)) return "adjective";
  if (["adverb", "adverbs", "adv"].includes(raw)) return "adverb";
  throw new HttpError(400, "type must be verb, noun, adjective, or adverb");
}

function normalizeAgentWordItem(wordType, raw) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    throw new HttpError(400, "word item object is required");
  }
  const item = structuredClone(raw);
  item.lemma = boundedString(item.lemma, "lemma", 200);
  item.en = boundedString(item.en || item.gloss || item.meaning, "en", 400);
  delete item.type;
  delete item.wordType;
  delete item.section;
  delete item.item;
  delete item.word;

  if (wordType === "verb") {
    item.forms = normalizeStringMap(item.forms, "forms", true);
    if (item.pastForms && !item.past_forms) item.past_forms = item.pastForms;
    delete item.pastForms;
    if (item.past_forms !== undefined) item.past_forms = normalizeStringMap(item.past_forms, "past_forms", false);
  } else if (wordType === "noun") {
    item.gender = boundedString(item.gender, "gender", 20).toLowerCase();
    item.nom = normalizeStringMap(item.nom, "nom", true);
    item.acc = normalizeStringMap(item.acc, "acc", true);
    item.gen = normalizeStringMap(item.gen, "gen", true);
  } else if (wordType === "adjective") {
    item.nom = normalizeStringMap(item.nom, "nom", true);
    item.acc = normalizeStringMap(item.acc, "acc", true);
  }
  return item;
}

function normalizeStringMap(value, label, required) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    if (required) throw new HttpError(400, `${label} object is required`);
    return undefined;
  }
  const out = {};
  for (const [key, raw] of Object.entries(value)) {
    const cleanedKey = cleanString(key);
    const cleanedValue = cleanString(raw);
    if (cleanedKey && cleanedValue) out[cleanedKey] = cleanedValue;
  }
  if (required && Object.keys(out).length === 0) {
    throw new HttpError(400, `${label} object cannot be empty`);
  }
  return out;
}

function agentDailyLimit(env) {
  const parsed = Number(env.AGENT_API_DAILY_LIMIT || DEFAULT_AGENT_DAILY_LIMIT);
  if (!Number.isFinite(parsed)) return DEFAULT_AGENT_DAILY_LIMIT;
  return Math.max(1, Math.min(1000, Math.trunc(parsed)));
}

function authResponse(user, session) {
  return {
    user: publicUser({
      user_id: user.id,
      email: user.email,
      email_verified: user.emailVerified ? 1 : 0,
      display_name: user.displayName,
      picture_url: user.pictureUrl,
    }),
    session,
  };
}

function publicUser(row) {
  return {
    id: row.user_id,
    email: row.email,
    emailVerified: row.email_verified === 1 || row.email_verified === true,
    displayName: row.display_name || "",
    pictureUrl: row.picture_url || "",
  };
}

async function upsertUserIdentity(env, profile) {
  const existingIdentity = await env.DB.prepare(`
    SELECT u.id, u.email, u.email_verified, u.display_name, u.picture_url
    FROM auth_identities i
    JOIN users u ON u.id = i.user_id
    WHERE i.provider = ? AND i.provider_subject = ?
  `).bind(profile.provider, profile.providerSubject).first();

  const userId = existingIdentity?.id || (await findOrCreateUser(env, profile));
  await env.DB.prepare(`
    UPDATE users
    SET email = ?,
        email_normalized = ?,
        email_verified = MAX(email_verified, ?),
        display_name = CASE WHEN ? != '' THEN ? ELSE display_name END,
        picture_url = CASE WHEN ? != '' THEN ? ELSE picture_url END,
        updated_at = datetime('now')
    WHERE id = ?
  `).bind(
    profile.email,
    profile.email,
    profile.emailVerified ? 1 : 0,
    profile.displayName,
    profile.displayName,
    profile.pictureUrl,
    profile.pictureUrl,
    userId,
  ).run();
  await env.DB.prepare(`
    INSERT INTO auth_identities
      (provider, provider_subject, user_id, email_normalized, updated_at)
    VALUES (?, ?, ?, ?, datetime('now'))
    ON CONFLICT(provider, provider_subject) DO UPDATE SET
      email_normalized = excluded.email_normalized,
      updated_at = datetime('now')
  `).bind(profile.provider, profile.providerSubject, userId, profile.email).run();

  const row = await env.DB.prepare(`
    SELECT id, email, email_verified, display_name, picture_url
    FROM users
    WHERE id = ?
  `).bind(userId).first();
  return {
    id: row.id,
    email: row.email,
    emailVerified: row.email_verified === 1,
    displayName: row.display_name || "",
    pictureUrl: row.picture_url || "",
  };
}

const CALENDAR_BASICS_GROUP = {
  id: "calendar-basics",
  title: "Calendar Basics",
  subtitle: "Days, months, and time expressions",
  sentences: [
    { pl: "poniedziałek", en: "Monday", literal: "Monday" },
    { pl: "wtorek", en: "Tuesday", literal: "Tuesday" },
    { pl: "środa", en: "Wednesday", literal: "Wednesday" },
    { pl: "czwartek", en: "Thursday", literal: "Thursday" },
    { pl: "piątek", en: "Friday", literal: "Friday" },
    { pl: "sobota", en: "Saturday", literal: "Saturday" },
    { pl: "niedziela", en: "Sunday", literal: "Sunday" },
    { pl: "styczeń", en: "January", literal: "January" },
    { pl: "luty", en: "February", literal: "February" },
    { pl: "marzec", en: "March", literal: "March" },
    { pl: "kwiecień", en: "April", literal: "April" },
    { pl: "maj", en: "May", literal: "May" },
    { pl: "czerwiec", en: "June", literal: "June" },
    { pl: "lipiec", en: "July", literal: "July" },
    { pl: "sierpień", en: "August", literal: "August" },
    { pl: "wrzesień", en: "September", literal: "September" },
    { pl: "październik", en: "October", literal: "October" },
    { pl: "listopad", en: "November", literal: "November" },
    { pl: "grudzień", en: "December", literal: "December" },
    { pl: "dzisiaj", en: "today", literal: "today" },
    { pl: "jutro", en: "tomorrow", literal: "tomorrow" },
    { pl: "przedwczoraj", en: "day before yesterday", literal: "before-yesterday" },
    { pl: "w przyszłym tygodniu", en: "next week", literal: "in next week" },
    { pl: "w przyszłym miesiącu", en: "next month", literal: "in next month" },
    { pl: "w przyszłym roku", en: "next year", literal: "in next year" },
    { pl: "w tym roku", en: "this year", literal: "in this year" },
    { pl: "w tym miesiącu", en: "this month", literal: "in this month" },
    { pl: "w tym tygodniu", en: "this week", literal: "in this week" },
    { pl: "za chwilę", en: "in a while", literal: "for a-moment" },
    { pl: "drugiego lipca", en: "on the 2nd of July", literal: "second-of July" },
    { pl: "piątego lipca", en: "on the 5th of July", literal: "fifth-of July" },
    { pl: "Urodziny Soni są dwudziestego sierpnia.", en: "Sonia's birthday is on the 20th of August.", literal: "Birthday-of Sonia are twentieth-of August." },
  ],
};

async function findOrCreateUser(env, profile) {
  const existingUser = await env.DB.prepare(`
    SELECT id
    FROM users
    WHERE email_normalized = ?
  `).bind(profile.email).first();
  if (existingUser) return existingUser.id;
  const id = crypto.randomUUID();
  await env.DB.prepare(`
    INSERT INTO users
      (id, email, email_normalized, email_verified, display_name, picture_url)
    VALUES (?, ?, ?, ?, ?, ?)
  `).bind(
    id,
    profile.email,
    profile.email,
    profile.emailVerified ? 1 : 0,
    profile.displayName,
    profile.pictureUrl,
  ).run();
  await upsertUserPhraseGroup(env, id, "langbangml-en-pl", CALENDAR_BASICS_GROUP, 0);
  return id;
}

async function createSession(env, userId) {
  const token = `lb_${randomBase64Url(32)}`;
  const tokenHash = await sha256Hex(token);
  const days = Math.max(1, Math.min(365, Number(env.SESSION_TTL_DAYS || DEFAULT_SESSION_TTL_DAYS)));
  const id = crypto.randomUUID();
  const expiresAt = sqliteDateTimeFromNow(days * 24 * 60 * 60 * 1000);
  await env.DB.prepare(`
    INSERT INTO auth_sessions (id, user_id, token_hash, expires_at)
    VALUES (?, ?, ?, ?)
  `).bind(id, userId, tokenHash, expiresAt).run();
  const row = await env.DB.prepare(`
    SELECT expires_at
    FROM auth_sessions
    WHERE id = ?
  `).bind(id).first();
  return { token, expiresAt: row.expires_at };
}

async function requireAnalyticsAdmin(request, env) {
  const auth = request.headers.get("Authorization") || "";
  const token = auth.match(/^Bearer\s+(.+)$/i)?.[1]?.trim();
  if (!token) throw new HttpError(401, "admin auth required");

  const apiToken = env.ANALYTICS_ADMIN_TOKEN || env.CONTENT_API_TOKEN || env.ADMIN_API_TOKEN;
  if (apiToken && token === apiToken) {
    return { email: "api-token", method: "bearer" };
  }

  const google = await verifyGoogleIdToken(token, env);
  if (!google.email || !(google.email_verified === true || google.email_verified === "true")) {
    throw new HttpError(401, "verified Google email required");
  }
  const email = String(google.email).toLowerCase();
  if (!analyticsAdminEmails(env).has(email)) {
    throw new HttpError(403, "analytics admin not allowed");
  }
  return { email, method: "google" };
}

function analyticsAdminEmails(env) {
  const raw = env.ANALYTICS_ADMIN_EMAILS || DEFAULT_ANALYTICS_ADMIN_EMAIL;
  return new Set(String(raw).split(",").map((v) => v.trim().toLowerCase()).filter(Boolean));
}

async function verifyGoogleIdToken(first, second, nonce = undefined) {
  if (typeof first === "string") {
    return verifyGoogleIdTokenInfo(second, first, nonce);
  }
  return verifyGoogleIdTokenInfo(first, second, nonce);
}

async function verifyGoogleIdTokenInfo(env, token, nonce = undefined) {
  const audiences = String(env.GOOGLE_WEB_CLIENT_ID || env.GOOGLE_CLIENT_ID || env.ADMIN_GOOGLE_CLIENT_ID || "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
  if (audiences.length === 0) throw new HttpError(503, "GOOGLE_WEB_CLIENT_ID is not configured");
  const parts = String(token || "").split(".");
  if (parts.length !== 3) throw new HttpError(400, "invalid Google ID token");
  const header = parseBase64UrlJson(parts[0]);
  const body = parseBase64UrlJson(parts[1]);
  if (header.alg !== "RS256") throw new HttpError(401, "unsupported Google token algorithm");
  if (!header.kid) throw new HttpError(401, "Google token key id missing");
  const jwks = await fetchGoogleJwks();
  const jwk = jwks.keys?.find((key) => key.kid === header.kid);
  if (!jwk) throw new HttpError(401, "Google signing key not found");
  const key = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"],
  );
  const verified = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    key,
    base64UrlToBytes(parts[2]),
    new TextEncoder().encode(`${parts[0]}.${parts[1]}`),
  );
  if (!verified) throw new HttpError(401, "invalid Google token signature");
  if (body.iss !== "accounts.google.com" && body.iss !== "https://accounts.google.com") {
    throw new HttpError(401, "invalid Google token issuer");
  }
  if (!audiences.includes(String(body.aud || ""))) {
    throw new HttpError(401, "Google ID token audience mismatch");
  }
  const expiresAt = Number(body.exp || 0);
  if (!Number.isFinite(expiresAt) || expiresAt <= Math.floor(Date.now() / 1000)) {
    throw new HttpError(401, "Google ID token expired");
  }
  if (nonce && body.nonce !== nonce) {
    throw new HttpError(401, "Google ID token nonce mismatch");
  }
  return body;
}

async function fetchGoogleJwks() {
  const response = await fetch(GOOGLE_JWKS_URL, {
    headers: { "Accept": "application/json" },
    cf: { cacheTtl: 3600, cacheEverything: true },
  });
  if (!response.ok) throw new HttpError(502, `Google JWKS HTTP ${response.status}`);
  return response.json();
}

function parseBase64UrlJson(value) {
  return JSON.parse(new TextDecoder().decode(base64UrlToBytes(value)));
}

function base64UrlToBytes(value) {
  const raw = String(value || "");
  const padded = raw.replace(/-/g, "+").replace(/_/g, "/")
    .padEnd(Math.ceil(raw.length / 4) * 4, "=");
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

function randomBase64Url(byteLength) {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function randomNumericCode() {
  const bytes = new Uint32Array(1);
  crypto.getRandomValues(bytes);
  return String(100000 + (bytes[0] % 900000));
}

function sqliteDateTimeFromNow(offsetMs) {
  return new Date(Date.now() + offsetMs).toISOString().replace("T", " ").slice(0, 19);
}

async function hashEmailCode(email, code, pepper) {
  return sha256Hex(`${email}|${code}|${pepper}`);
}

// Length-independent equality: hash both sides, then compare digests without an
// early exit so the comparison time does not leak how much of the secret matched.
async function constantTimeEqual(a, b) {
  const ha = await sha256Hex(String(a));
  const hb = await sha256Hex(String(b));
  let diff = ha.length ^ hb.length;
  for (let i = 0; i < ha.length && i < hb.length; i++) {
    diff |= ha.charCodeAt(i) ^ hb.charCodeAt(i);
  }
  return diff === 0;
}

async function sendLoginEmail(env, email, code) {
  if (!env.RESEND_API_KEY || !env.EMAIL_FROM) return false;
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${env.RESEND_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: env.EMAIL_FROM,
      to: email,
      subject: "Your LangBang sign-in code",
      text: `Your LangBang sign-in code is ${code}. It expires in ${EMAIL_CODE_TTL_MINUTES} minutes.`,
    }),
  });
  if (!response.ok) {
    const body = await response.text();
    throw new HttpError(502, `email send failed: ${body.slice(0, 180)}`);
  }
  return true;
}

async function sendAiPhraseQuotaEmail(env, details) {
  if (!env.RESEND_API_KEY || !env.EMAIL_FROM) return false;
  const to = env.AI_PHRASE_QUOTA_EMAIL || DEFAULT_AI_PHRASE_QUOTA_EMAIL;
  const lines = [
    "LangBang AI phrase quota request",
    "",
    `User: ${details.displayName || "(no display name)"}`,
    `Email: ${details.email}`,
    `User ID: ${details.userId}`,
    `Instance: ${details.instanceId || "(not provided)"}`,
    `Quota: ${details.quota.used}/${details.quota.limit}`,
    "",
    "Message:",
    details.message || "(none)",
  ];
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${env.RESEND_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: env.EMAIL_FROM,
      to,
      subject: `LangBang AI phrase quota request: ${details.email}`,
      text: lines.join("\n"),
    }),
  });
  if (!response.ok) {
    const body = await response.text();
    throw new HttpError(502, `quota email send failed: ${body.slice(0, 180)}`);
  }
  return true;
}

function normalizeEmail(value) {
  const email = cleanString(value).toLowerCase();
  if (!email || email.length > MAX_EMAIL_CHARS) return null;
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return null;
  return email;
}

function normalizeInstanceId(value) {
  const instanceId = cleanString(value);
  if (!/^[A-Za-z0-9._-]{1,80}$/.test(instanceId)) {
    throw new HttpError(400, "valid instanceId is required");
  }
  return instanceId;
}

function normalizePhraseGroups(value) {
  if (!Array.isArray(value)) throw new HttpError(400, "groups[] is required");
  if (value.length > MAX_SYNC_GROUPS) throw new HttpError(400, "too many phrase groups", { limit: MAX_SYNC_GROUPS });
  return value.map(normalizePhraseGroup);
}

function normalizePhraseGroup(raw) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    throw new HttpError(400, "phrase group object is required");
  }
  const title = boundedString(raw.title || "", "group.title", 200);
  const id = cleanString(raw.id) || slug(title);
  if (!/^[A-Za-z0-9._-]{1,120}$/.test(id)) throw new HttpError(400, "invalid phrase group id", { id });
  const sentences = Array.isArray(raw.sentences) ? raw.sentences : [];
  if (sentences.length > MAX_SYNC_SENTENCES_PER_GROUP) {
    throw new HttpError(400, "too many phrase sentences", { groupId: id, limit: MAX_SYNC_SENTENCES_PER_GROUP });
  }
  const normalizedSentences = normalizePhraseSentenceIndexes(sentences.map(normalizeSentenceExample));
  return {
    id,
    title,
    subtitle: boundedString(raw.subtitle || "", "group.subtitle", 400, true),
    collection: normalizeOptionalPhraseGroupCollection(raw.collection),
    createdAt: normalizeOptionalGroupCreatedAt(raw.createdAt ?? raw.created_at),
    sortOrder: normalizeOptionalGroupSortOrder(raw.sortOrder ?? raw.sort_order ?? raw.order),
    sentences: normalizedSentences,
  };
}

function normalizeOptionalPhraseGroupCollection(value) {
  const collection = boundedString(value || "", "group.collection", 80, true);
  return collection || undefined;
}

function normalizeSentenceExample(raw) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    throw new HttpError(400, "sentence object is required");
  }
  const pl = boundedString(raw.pl, "sentence.pl", 1200);
  const en = boundedString(raw.en, "sentence.en", 1200);
  const literal = boundedString(raw.literal || "", "sentence.literal", 1600, true);
  const index = normalizeOptionalPhraseIndex(raw.index ?? raw.sortIndex ?? raw.order ?? raw.position);
  const out = { pl, en };
  if (index !== null) out.index = index;
  if (literal) out.literal = literal;
  if (Array.isArray(raw.words)) {
    const words = raw.words.map(normalizeTokenPair).filter(Boolean);
    if (words.length > 0) out.words = words.slice(0, 200);
  }
  return out;
}

function normalizeOptionalPhraseIndex(value) {
  if (value === undefined || value === null || value === "") return null;
  const index = Number(value);
  if (!Number.isInteger(index) || index < 1 || index > MAX_SYNC_SENTENCES_PER_GROUP) {
    throw new HttpError(400, "sentence.index must be a positive integer", { limit: MAX_SYNC_SENTENCES_PER_GROUP });
  }
  return index;
}

function normalizeOptionalGroupCreatedAt(value) {
  if (value === undefined || value === null || value === "") return undefined;
  const millis = typeof value === "string" && !/^\d+$/.test(value)
    ? Date.parse(value)
    : Number(value);
  if (!Number.isFinite(millis)) {
    throw new HttpError(400, "group.createdAt must be epoch millis or an ISO timestamp");
  }
  return Math.trunc(millis);
}

function normalizeOptionalGroupSortOrder(value) {
  if (value === undefined || value === null || value === "") return undefined;
  const order = Number(value);
  if (!Number.isInteger(order)) throw new HttpError(400, "group.sortOrder must be an integer");
  return order;
}

function normalizePhraseSentenceIndexes(sentences) {
  return sentences
    .map((sentence, fallback) => ({ ...sentence, index: sentence.index || fallback + 1 }))
    .sort((a, b) => a.index - b.index)
    .map((sentence, index) => ({ ...sentence, index: index + 1 }));
}

function normalizeStarredPhrases(value) {
  if (!Array.isArray(value)) return [];
  if (value.length > MAX_SYNC_STARS) throw new HttpError(400, "too many starred phrases", { limit: MAX_SYNC_STARS });
  return [...new Set(value.map((item) => cleanString(item)).filter(Boolean))];
}

async function listInstances(env) {
  const result = await env.DB.prepare(`
    SELECT i.id, i.display_name, i.ui_locale, i.content_version_id,
           p.id AS language_pair_id, p.source_language, p.target_language,
           p.source_locale, p.target_locale
    FROM app_instances i
    JOIN language_pairs p ON p.id = i.language_pair_id
    WHERE i.active = 1 AND p.active = 1
    ORDER BY i.display_name
  `).all();
  return json({ instances: result.results.map(rowToInstanceSummary) });
}

async function ingestAnalytics(request, env) {
  await enforceRateLimit(env, `analytics:ip:${clientIp(request)}`, RL.analyticsIpPerMin, 60, { scope: "ip" });
  const body = await readJsonLimited(request, 128 * 1024);
  const installationId = analyticsId(body.installationId, "installationId", 160);
  const sessionId = analyticsId(body.sessionId, "sessionId", 160);
  const events = Array.isArray(body.events) ? body.events : [];
  if (events.length === 0) throw new HttpError(400, "events[] required");
  if (events.length > MAX_ANALYTICS_EVENTS_PER_BATCH) {
    throw new HttpError(400, "too many analytics events", {
      limit: MAX_ANALYTICS_EVENTS_PER_BATCH,
    });
  }

  const app = normalizeAnalyticsApp(body.app || {}, body);
  const profile = normalizeAnalyticsProfile(body.profile || {}, installationId);
  const now = new Date().toISOString();
  const normalizedEvents = events.map((event) => normalizeAnalyticsEvent(event, now));
  const firstOccurredAt = normalizedEvents
    .map((event) => event.occurredAt)
    .sort()[0] || now;
  const lastOccurredAt = normalizedEvents
    .map((event) => event.occurredAt)
    .sort()
    .at(-1) || now;

  await upsertAnalyticsProfile(env, profile, now);
  await upsertAnalyticsInstallation(env, installationId, profile.profileId, app, now);
  await upsertAnalyticsSession(env, sessionId, installationId, profile.profileId, app, firstOccurredAt, lastOccurredAt);

  let inserted = 0;
  let duplicate = 0;
  let durationMs = 0;
  let endedAt = null;
  for (const event of normalizedEvents) {
    const wasInserted = await insertAnalyticsEvent(env, {
      ...event,
      sessionId,
      installationId,
      profileId: profile.profileId,
      instanceId: event.instanceId || app.instanceId,
      appVersionCode: app.appVersionCode,
      appVersionName: app.appVersionName,
    });
    if (wasInserted) {
      inserted += 1;
      durationMs += event.durationMs;
      if (event.name === "session_end") endedAt = event.occurredAt;
      await upsertAnalyticsFeatureDaily(env, {
        ...event,
        profileId: profile.profileId,
        installationId,
        instanceId: event.instanceId || app.instanceId,
      });
    } else {
      duplicate += 1;
    }
  }

  if (inserted > 0) {
    await env.DB.prepare(`
      UPDATE analytics_sessions
      SET event_count = event_count + ?,
          duration_ms = duration_ms + ?,
          last_seen_at = CASE WHEN ? > last_seen_at THEN ? ELSE last_seen_at END,
          ended_at = COALESCE(?, ended_at),
          updated_at = datetime('now')
      WHERE session_id = ?
    `).bind(inserted, durationMs, lastOccurredAt, lastOccurredAt, endedAt, sessionId).run();
    await env.DB.prepare(`
      UPDATE analytics_profiles
      SET last_seen_at = CASE WHEN ? > last_seen_at THEN ? ELSE last_seen_at END,
          updated_at = datetime('now')
      WHERE profile_id = ?
    `).bind(lastOccurredAt, lastOccurredAt, profile.profileId).run();
    await env.DB.prepare(`
      UPDATE analytics_installations
      SET last_seen_at = CASE WHEN ? > last_seen_at THEN ? ELSE last_seen_at END,
          updated_at = datetime('now')
      WHERE installation_id = ?
    `).bind(lastOccurredAt, lastOccurredAt, installationId).run();
  }

  return json({ ok: true, accepted: inserted, duplicate, profileId: profile.profileId });
}

function normalizeAnalyticsProfile(raw, installationId) {
  const provider = analyticsString(raw.provider, 40) || "anonymous";
  const providerSubject = analyticsString(raw.providerSubject || raw.sub, 180) || null;
  const email = normalizeEmail(raw.email);
  const profileId = analyticsId(
    raw.profileId || raw.id || (providerSubject ? `${provider}:${providerSubject}` : `install:${installationId}`),
    "profile.profileId",
    220,
  );
  const signupState = analyticsString(raw.signupState, 40) ||
    (providerSubject || email ? "signed_in" : "anonymous");
  return {
    profileId,
    provider,
    providerSubject,
    email,
    displayName: analyticsString(raw.displayName || raw.name, 160) || null,
    locale: analyticsString(raw.locale, 40) || null,
    signupState,
    propertiesJson: compactJsonObject(raw.properties || {}),
  };
}

function normalizeAnalyticsApp(raw, root) {
  const versionCode = intOrNull(raw.appVersionCode ?? raw.versionCode ?? root.appVersionCode);
  const buildNumber = intOrNull(raw.buildNumber ?? root.buildNumber ?? versionCode);
  return {
    platform: analyticsString(raw.platform, 40) || "android",
    appPackage: analyticsString(raw.appPackage || raw.packageName, 160) || null,
    appVersionCode: versionCode,
    appVersionName: analyticsString(raw.appVersionName || raw.versionName, 80) || null,
    buildNumber,
    flavor: analyticsString(raw.flavor, 80) || null,
    instanceId: analyticsString(raw.instanceId || root.instanceId, 160) || "unknown",
    deviceModel: analyticsString(raw.deviceModel, 160) || null,
    osVersion: analyticsString(raw.osVersion, 80) || null,
    locale: analyticsString(raw.locale, 40) || null,
    propertiesJson: compactJsonObject(raw.properties || {}),
  };
}

function normalizeAnalyticsEvent(raw, fallbackTime) {
  const name = analyticsName(raw.name || raw.eventName || raw.type, "event.name");
  const feature = analyticsString(raw.feature, 80) || "app";
  const action = analyticsString(raw.action, 80) || null;
  const screen = analyticsString(raw.screen, 80) || null;
  const occurredAt = analyticsTime(raw.occurredAt || raw.timestamp || fallbackTime);
  return {
    eventId: analyticsId(raw.id || raw.eventId || crypto.randomUUID(), "event.id", 180),
    name,
    feature,
    action,
    screen,
    instanceId: analyticsString(raw.instanceId, 160) || null,
    durationMs: Math.max(0, Math.min(intOrNull(raw.durationMs) || 0, 24 * 60 * 60 * 1000)),
    occurredAt,
    propertiesJson: compactJsonObject(raw.properties || raw.payload || {}),
  };
}

async function upsertAnalyticsProfile(env, profile, now) {
  await env.DB.prepare(`
    INSERT INTO analytics_profiles
      (profile_id, provider, provider_subject, email, display_name, locale, signup_state,
       properties_json, first_seen_at, last_seen_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
    ON CONFLICT(profile_id) DO UPDATE SET
      provider = excluded.provider,
      provider_subject = COALESCE(excluded.provider_subject, analytics_profiles.provider_subject),
      email = COALESCE(excluded.email, analytics_profiles.email),
      display_name = COALESCE(excluded.display_name, analytics_profiles.display_name),
      locale = COALESCE(excluded.locale, analytics_profiles.locale),
      signup_state = excluded.signup_state,
      properties_json = excluded.properties_json,
      last_seen_at = CASE WHEN excluded.last_seen_at > analytics_profiles.last_seen_at
        THEN excluded.last_seen_at ELSE analytics_profiles.last_seen_at END,
      updated_at = datetime('now')
  `).bind(
    profile.profileId,
    profile.provider,
    profile.providerSubject,
    profile.email,
    profile.displayName,
    profile.locale,
    profile.signupState,
    profile.propertiesJson,
    now,
    now,
  ).run();
}

async function upsertAnalyticsInstallation(env, installationId, profileId, app, now) {
  await env.DB.prepare(`
    INSERT INTO analytics_installations
      (installation_id, profile_id, platform, app_package, app_version_code,
       app_version_name, build_number, flavor, instance_id, device_model, os_version,
       locale, properties_json, first_seen_at, last_seen_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
    ON CONFLICT(installation_id) DO UPDATE SET
      profile_id = excluded.profile_id,
      platform = excluded.platform,
      app_package = excluded.app_package,
      app_version_code = excluded.app_version_code,
      app_version_name = excluded.app_version_name,
      build_number = excluded.build_number,
      flavor = excluded.flavor,
      instance_id = excluded.instance_id,
      device_model = excluded.device_model,
      os_version = excluded.os_version,
      locale = excluded.locale,
      properties_json = excluded.properties_json,
      last_seen_at = excluded.last_seen_at,
      updated_at = datetime('now')
  `).bind(
    installationId,
    profileId,
    app.platform,
    app.appPackage,
    app.appVersionCode,
    app.appVersionName,
    app.buildNumber,
    app.flavor,
    app.instanceId,
    app.deviceModel,
    app.osVersion,
    app.locale,
    app.propertiesJson,
    now,
    now,
  ).run();
}

async function upsertAnalyticsSession(env, sessionId, installationId, profileId, app, startedAt, lastSeenAt) {
  await env.DB.prepare(`
    INSERT INTO analytics_sessions
      (session_id, installation_id, profile_id, instance_id, platform, app_package,
       app_version_code, app_version_name, build_number, started_at, last_seen_at,
       properties_json, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
    ON CONFLICT(session_id) DO UPDATE SET
      profile_id = excluded.profile_id,
      instance_id = excluded.instance_id,
      app_version_code = excluded.app_version_code,
      app_version_name = excluded.app_version_name,
      build_number = excluded.build_number,
      last_seen_at = CASE WHEN excluded.last_seen_at > analytics_sessions.last_seen_at
        THEN excluded.last_seen_at ELSE analytics_sessions.last_seen_at END,
      updated_at = datetime('now')
  `).bind(
    sessionId,
    installationId,
    profileId,
    app.instanceId,
    app.platform,
    app.appPackage,
    app.appVersionCode,
    app.appVersionName,
    app.buildNumber,
    startedAt,
    lastSeenAt,
    app.propertiesJson,
  ).run();
}

async function insertAnalyticsEvent(env, event) {
  const result = await env.DB.prepare(`
    INSERT OR IGNORE INTO analytics_events
      (event_id, session_id, installation_id, profile_id, instance_id, name, feature,
       action, screen, duration_ms, app_version_code, app_version_name, occurred_at,
       properties_json)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).bind(
    event.eventId,
    event.sessionId,
    event.installationId,
    event.profileId,
    event.instanceId || "unknown",
    event.name,
    event.feature,
    event.action,
    event.screen,
    event.durationMs,
    event.appVersionCode,
    event.appVersionName,
    event.occurredAt,
    event.propertiesJson,
  ).run();
  return Number(result?.meta?.changes || result?.changes || 0) > 0;
}

async function upsertAnalyticsFeatureDaily(env, event) {
  const day = event.occurredAt.slice(0, 10);
  await env.DB.prepare(`
    INSERT INTO analytics_feature_daily
      (day, profile_id, installation_id, instance_id, feature, event_name,
       event_count, duration_ms, last_seen_at)
    VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)
    ON CONFLICT(day, profile_id, installation_id, instance_id, feature, event_name)
    DO UPDATE SET
      event_count = event_count + 1,
      duration_ms = duration_ms + excluded.duration_ms,
      last_seen_at = CASE WHEN excluded.last_seen_at > analytics_feature_daily.last_seen_at
        THEN excluded.last_seen_at ELSE analytics_feature_daily.last_seen_at END
  `).bind(
    day,
    event.profileId,
    event.installationId,
    event.instanceId || "unknown",
    event.feature || "app",
    event.name,
    event.durationMs,
    event.occurredAt,
  ).run();
}

async function adminAnalyticsSummary(request, env) {
  const url = new URL(request.url);
  const days = Math.max(1, Math.min(365, Number(url.searchParams.get("days") || 30)));
  const since = new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();

  const [totals, daily, features, profiles, versions, instances, agentApi, aiPhrases] = await Promise.all([
    env.DB.prepare(`
      SELECT COUNT(*) AS events,
             COUNT(DISTINCT profile_id) AS profiles,
             COUNT(DISTINCT installation_id) AS installations,
             COUNT(DISTINCT session_id) AS sessions,
             COALESCE(SUM(duration_ms), 0) AS durationMs
      FROM analytics_events
      WHERE occurred_at >= ?
    `).bind(since).first(),
    env.DB.prepare(`
      SELECT substr(occurred_at, 1, 10) AS day,
             COUNT(*) AS events,
             COUNT(DISTINCT profile_id) AS profiles,
             COUNT(DISTINCT session_id) AS sessions,
             COALESCE(SUM(duration_ms), 0) AS durationMs
      FROM analytics_events
      WHERE occurred_at >= ?
      GROUP BY day
      ORDER BY day
    `).bind(since).all(),
    env.DB.prepare(`
      SELECT COALESCE(feature, 'app') AS feature,
             name,
             COUNT(*) AS events,
             COUNT(DISTINCT profile_id) AS profiles,
             COUNT(DISTINCT session_id) AS sessions,
             COALESCE(SUM(duration_ms), 0) AS durationMs,
             MAX(occurred_at) AS lastSeenAt
      FROM analytics_events
      WHERE occurred_at >= ?
      GROUP BY feature, name
      ORDER BY events DESC, feature, name
      LIMIT 80
    `).bind(since).all(),
    env.DB.prepare(`
      SELECT p.profile_id AS profileId,
             p.email,
             p.display_name AS displayName,
             p.provider,
             p.signup_state AS signupState,
             p.locale,
             p.first_seen_at AS firstSeenAt,
             p.last_seen_at AS lastSeenAt,
             COUNT(e.event_id) AS events,
             COUNT(DISTINCT e.session_id) AS sessions,
             COALESCE(SUM(e.duration_ms), 0) AS durationMs,
             MAX(e.occurred_at) AS lastEventAt
      FROM analytics_profiles p
      LEFT JOIN analytics_events e
        ON e.profile_id = p.profile_id AND e.occurred_at >= ?
      GROUP BY p.profile_id
      ORDER BY COALESCE(lastEventAt, p.last_seen_at) DESC
      LIMIT 80
    `).bind(since).all(),
    env.DB.prepare(`
      SELECT COALESCE(app_version_name, 'unknown') AS appVersionName,
             COALESCE(app_version_code, 0) AS appVersionCode,
             COUNT(*) AS events,
             COUNT(DISTINCT installation_id) AS installations
      FROM analytics_events
      WHERE occurred_at >= ?
      GROUP BY appVersionName, appVersionCode
      ORDER BY appVersionCode DESC
      LIMIT 20
    `).bind(since).all(),
    env.DB.prepare(`
      SELECT COALESCE(instance_id, 'unknown') AS instanceId,
             COUNT(*) AS events,
             COUNT(DISTINCT profile_id) AS profiles,
             COUNT(DISTINCT session_id) AS sessions
      FROM analytics_events
      WHERE occurred_at >= ?
      GROUP BY instanceId
      ORDER BY events DESC
    `).bind(since).all(),
    env.DB.prepare(`
      SELECT COUNT(*) AS calls,
             COUNT(DISTINCT user_id) AS users,
             COUNT(DISTINCT token_id) AS tokens
      FROM user_agent_api_call_events
      WHERE created_at >= ?
    `).bind(since).first(),
    env.DB.prepare(`
      SELECT COALESCE(SUM(generated_count), 0) AS generated,
             COUNT(*) AS users
      FROM user_ai_phrase_quotas
    `).first(),
  ]);

  return json({
    ok: true,
    days,
    since,
    totals: {
      events: Number(totals?.events || 0),
      profiles: Number(totals?.profiles || 0),
      installations: Number(totals?.installations || 0),
      sessions: Number(totals?.sessions || 0),
      durationMs: Number(totals?.durationMs || 0),
      agentApiCalls: Number(agentApi?.calls || 0),
      agentApiUsers: Number(agentApi?.users || 0),
      agentApiTokens: Number(agentApi?.tokens || 0),
      aiPhrasesGenerated: Number(aiPhrases?.generated || 0),
      aiPhraseUsers: Number(aiPhrases?.users || 0),
    },
    daily: daily.results || [],
    features: features.results || [],
    profiles: profiles.results || [],
    versions: versions.results || [],
    instances: instances.results || [],
  });
}

async function adminAnalyticsEvents(request, env) {
  const url = new URL(request.url);
  const days = Math.max(1, Math.min(365, Number(url.searchParams.get("days") || 7)));
  const limit = Math.max(1, Math.min(500, Number(url.searchParams.get("limit") || 200)));
  const since = new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();
  const rows = await env.DB.prepare(`
    SELECT e.event_id AS eventId,
           e.occurred_at AS occurredAt,
           e.received_at AS receivedAt,
           e.profile_id AS profileId,
           p.email,
           p.signup_state AS signupState,
           e.installation_id AS installationId,
           e.session_id AS sessionId,
           e.instance_id AS instanceId,
           e.name,
           e.feature,
           e.action,
           e.screen,
           e.duration_ms AS durationMs,
           e.app_version_code AS appVersionCode,
           e.app_version_name AS appVersionName,
           e.properties_json AS propertiesJson
    FROM analytics_events e
    LEFT JOIN analytics_profiles p ON p.profile_id = e.profile_id
    WHERE e.occurred_at >= ?
    ORDER BY e.occurred_at DESC
    LIMIT ?
  `).bind(since, limit).all();
  return json({ ok: true, days, since, events: rows.results || [] });
}

async function recordAnalyticsAdminAccess(env, email, route, authorized) {
  try {
    await env.DB.prepare(`
      INSERT INTO analytics_admin_access_events (id, email, route, authorized)
      VALUES (?, ?, ?, ?)
    `).bind(crypto.randomUUID(), email || "unknown", route, authorized ? 1 : 0).run();
  } catch {
    // Analytics reads should not fail just because audit logging failed.
  }
}

async function bootstrap(env, instanceId) {
  const instance = await env.DB.prepare(`
    SELECT i.*, p.source_language, p.target_language, p.source_locale, p.target_locale,
           p.source_voice, p.target_voice, p.target_slow_voices_json, p.description AS pair_description
    FROM app_instances i
    JOIN language_pairs p ON p.id = i.language_pair_id
    WHERE i.id = ? AND i.active = 1 AND p.active = 1
  `).bind(instanceId).first();
  if (!instance) return json({ error: "instance not found" }, 404);

  const [labels, lessons] = await Promise.all([
    labelMap(env, instance.ui_locale),
    lessonsFor(env, instance.content_version_id),
  ]);

  return json({
    instance: {
      id: instance.id,
      displayName: instance.display_name,
      uiLocale: instance.ui_locale,
      settings: parseJson(instance.settings_json, {}),
      updatedAt: instance.updated_at,
    },
    languagePair: {
      id: instance.language_pair_id,
      sourceLanguage: instance.source_language,
      targetLanguage: instance.target_language,
      sourceLocale: instance.source_locale,
      targetLocale: instance.target_locale,
      sourceVoice: instance.source_voice,
      targetVoice: instance.target_voice,
      targetSlowVoices: parseJson(instance.target_slow_voices_json, []),
      description: instance.pair_description || "",
    },
    content: {
      versionId: instance.content_version_id,
      lessons,
    },
    labels,
    audio: {
      manifestEndpoint: "/v1/audio/manifest",
      publicR2Base: env.PUBLIC_R2_BASE,
      audioPrefix: env.AUDIO_PREFIX || "langbang/audio",
    },
    syncedAt: new Date().toISOString(),
  });
}

async function labelsFor(env, locale) {
  return json({ locale, labels: await labelMap(env, locale) });
}

async function geminiGenerate(request, env) {
  await guardLlmEndpoint(request, env, "gemini-generate", RL.geminiIpPerHour, RL.geminiUserPerDay);
  const body = await request.json();
  const prompt = boundedString(body.prompt, "prompt", MAX_GEMINI_PROMPT_CHARS);
  if (!prompt.trim()) throw new HttpError(400, "prompt is required");
  const model = normalizeGeminiModel(body.model || DEFAULT_GEMINI_MODEL);
  const raw = await geminiGenerateRaw(env, prompt, model);
  return new Response(raw, {
    status: 200,
    headers: { "Content-Type": "application/json; charset=utf-8", ...corsHeaders() },
  });
}

async function geminiG2Translate(request, env) {
  requireGeminiLiveToken(request, env);
  const body = await request.json();
  const english = boundedString(body.english || body.text, "english", MAX_G2_TRANSLATE_CHARS);
  const text = await geminiGenerateText(
    env,
    buildG2TranslatePrompt(english),
    DEFAULT_GEMINI_MODEL,
    env.GEMINI_LIVE_API_KEY || env.GEMINI_API_KEY,
  );
  const result = normalizeG2Translation(parseGeminiJsonText(text), english);
  return json({
    ok: true,
    ...result,
    displayText: formatG2TranslationDisplay(result),
  });
}

function requireGeminiLiveToken(request, env) {
  const expected = (env.GEMINI_LIVE_TOKEN || "").trim();
  const auth = request.headers.get("Authorization") || "";
  const token = auth.match(/^Bearer\s+(.+)$/i)?.[1]?.trim();
  if (!expected || token !== expected) {
    throw new HttpError(401, "unauthorized");
  }
}

// WebSocket relay for the Gemini Live API (live translation). The Android app connects here with a
// revocable proxy token; this worker holds the real GEMINI_API_KEY and pipes frames both ways, so
// the Gemini key never ships on-device. Bidirectional, opaque passthrough of setup/audio/transcripts.
async function geminiLiveRelay(request, env) {
  const url = new URL(request.url);
  const token =
    (request.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "").trim() ||
    (url.searchParams.get("token") || "").trim();
  const expected = (env.GEMINI_LIVE_TOKEN || "").trim();
  if (!expected || token !== expected) {
    return new Response("unauthorized", { status: 401 });
  }
  // Dedicated live-translate key (has gemini-3.5-live-translate-preview access); falls back to the
  // shared key. Kept separate so we never disturb the generate endpoint's GEMINI_API_KEY.
  const key = env.GEMINI_LIVE_API_KEY || env.GEMINI_API_KEY;
  if (!key) return new Response("GEMINI_LIVE_API_KEY not configured", { status: 503 });

  let upstream;
  try {
    const resp = await fetch(
      // Cloudflare's outbound WebSocket upgrade requires the https:// scheme (not wss://).
      `https://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=${encodeURIComponent(key)}`,
      { headers: { Upgrade: "websocket" } },
    );
    upstream = resp.webSocket;
    if (!upstream) {
      return new Response(`upstream did not upgrade: status=${resp.status}`, { status: 502 });
    }
  } catch (e) {
    return new Response("upstream connect failed: " + (e && e.message ? e.message : String(e)), { status: 502 });
  }
  upstream.accept();

  const pair = new WebSocketPair();
  const client = pair[0];
  const server = pair[1];
  server.accept();

  // client (phone) -> Gemini
  server.addEventListener("message", (e) => {
    try { upstream.send(e.data); } catch (_) {}
  });
  server.addEventListener("close", () => {
    try { upstream.close(); } catch (_) {}
  });
  server.addEventListener("error", () => {
    try { upstream.close(); } catch (_) {}
  });
  // Gemini -> client (phone)
  upstream.addEventListener("message", (e) => {
    try { server.send(e.data); } catch (_) {}
  });
  upstream.addEventListener("close", (e) => {
    try { server.close(e.code, e.reason); } catch (_) {}
  });
  upstream.addEventListener("error", () => {
    try { server.close(); } catch (_) {}
  });

  return new Response(null, { status: 101, webSocket: client });
}

async function completePhrase(request, env) {
  await guardLlmEndpoint(request, env, "phrases-complete", RL.completeIpPerHour, RL.completeUserPerDay);
  const body = await request.json();
  const sourceText = boundedString(body.sourceText, "sourceText", MAX_PHRASE_FIELD_CHARS, true);
  const targetText = boundedString(body.targetText, "targetText", MAX_PHRASE_FIELD_CHARS, true);
  const literalText = boundedString(body.literalText, "literalText", MAX_PHRASE_FIELD_CHARS, true);
  if (!sourceText.trim() && !targetText.trim() && !literalText.trim()) {
    throw new HttpError(400, "enter at least one phrase field");
  }
  const sourceLanguage = boundedString(body.sourceLanguage || "source language", "sourceLanguage", 80);
  const targetLanguage = boundedString(body.targetLanguage || "target language", "targetLanguage", 80);
  const text = await geminiGenerateText(
    env,
    buildPhraseCompletionPrompt({ sourceText, targetText, literalText, sourceLanguage, targetLanguage }),
    DEFAULT_GEMINI_MODEL,
  );
  return json(scrubPhraseTargetLanguageLeaks(
    normalizePhraseCompletion(parseGeminiJsonText(text)),
    targetLanguage,
  ));
}

async function generateAiPhraseBatch(env, { prompt, count, pair }) {
  const text = await geminiGenerateText(
    env,
    buildAiPhraseGenerationPrompt({ prompt, count, pair }),
    DEFAULT_GEMINI_MODEL,
  );
  const parsed = parseGeminiJsonText(text);
  const phrases = Array.isArray(parsed?.phrases) ? parsed.phrases : Array.isArray(parsed) ? parsed : [];
  if (phrases.length === 0) throw new HttpError(502, "Gemini returned no phrases");
  return phrases.slice(0, count).map(normalizeSentenceExample);
}

async function geminiGenerateText(env, prompt, model = DEFAULT_GEMINI_MODEL, keyOverride = null) {
  // Temporary failover: while the Gemini key's billing is depleted, route every JSON
  // text-generation call (phrase completion, AI phrase gen, agent phrases, G2 translate)
  // to Azure OpenAI when it is configured. Revert by deleting the AZURE_OPENAI_KEY
  // secret — this falls straight back to Gemini.
  if (env.AZURE_OPENAI_KEY && env.AZURE_OPENAI_ENDPOINT && env.AZURE_OPENAI_DEPLOYMENT) {
    return azureOpenAiGenerateText(env, prompt);
  }
  return geminiTextFromRaw(await geminiGenerateRaw(env, prompt, model, keyOverride));
}

// Azure OpenAI chat-completions with JSON mode — the temporary stand-in for Gemini
// (see geminiGenerateText). Returns the model's JSON text; callers parse it with
// parseGeminiJsonText exactly as they do for Gemini output.
async function azureOpenAiGenerateText(env, prompt) {
  const endpoint = String(env.AZURE_OPENAI_ENDPOINT || "").replace(/\/+$/, "");
  const deployment = env.AZURE_OPENAI_DEPLOYMENT;
  const apiVersion = env.AZURE_OPENAI_API_VERSION || "2024-10-21";
  const response = await fetch(
    `${endpoint}/openai/deployments/${encodeURIComponent(deployment)}/chat/completions?api-version=${apiVersion}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", "api-key": env.AZURE_OPENAI_KEY },
      body: JSON.stringify({
        messages: [
          {
            role: "system",
            content: "You are a precise language assistant. Respond with a single valid JSON object only — no markdown fences, no commentary.",
          },
          { role: "user", content: prompt },
        ],
        response_format: { type: "json_object" },
        // gpt-5.x reasoning model: keep latency down. "low" matches Gemini-tier
        // quality for these translation/validation prompts (supports low|medium|high).
        reasoning_effort: env.AZURE_OPENAI_REASONING_EFFORT || "low",
      }),
    },
  );
  const raw = await response.text();
  if (!response.ok) {
    throw new HttpError(502, `Azure OpenAI HTTP ${response.status}`, { body: raw.slice(0, 500) });
  }
  let root;
  try {
    root = JSON.parse(raw);
  } catch (_) {
    throw new HttpError(502, "Azure OpenAI response was not JSON");
  }
  const text = root?.choices?.[0]?.message?.content;
  if (typeof text !== "string" || !text.trim()) {
    throw new HttpError(502, "Azure OpenAI response text missing");
  }
  return text;
}

async function geminiGenerateRaw(env, prompt, model = DEFAULT_GEMINI_MODEL, keyOverride = null) {
  const key = keyOverride || env.GEMINI_API_KEY;
  if (!key) throw new HttpError(503, "GEMINI_API_KEY is not configured");
  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(key)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: { temperature: 0.1, responseMimeType: "application/json" },
        safetySettings: GEMINI_SAFETY_SETTINGS,
      }),
    },
  );
  const raw = await response.text();
  if (!response.ok) {
    throw new HttpError(502, `Gemini HTTP ${response.status}`, { body: raw.slice(0, 500) });
  }
  return raw;
}

function geminiTextFromRaw(raw) {
  const root = JSON.parse(raw);
  const text = root?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (typeof text !== "string" || !text.trim()) {
    throw new HttpError(502, "Gemini response text missing");
  }
  return text;
}

function buildPhraseCompletionPrompt({ sourceText, targetText, literalText, sourceLanguage, targetLanguage }) {
  return "Complete and validate one LangBang phrase entry. " +
    `The source cue language is ${sourceLanguage}. The target answer language is ${targetLanguage}. ` +
    "The app stores the source cue in a JSON field named \"source\" and the target answer " +
    "in a JSON field named \"target\". User-provided fields may be blank. " +
    `source=${JSON.stringify(sourceText)}, target=${JSON.stringify(targetText)}, ` +
    `literal=${JSON.stringify(literalText)}. ` +
    "Fill any missing source, target, and literal fields by translating faithfully. This " +
    "phrase was deliberately chosen by the learner, so translate it as-is even when it is " +
    "playful, informal, unusual, contrived, or uncommon, and silently correct obvious typos " +
    "or small grammar slips while preserving the intended meaning. Do NOT reject a phrase for " +
    "being awkward, rare, not \"natural learner language\", translated-sounding, or " +
    "coverage-driven. " +
    "Only set consistent:false when the user supplied BOTH a source and a target (or literal) " +
    "whose meanings genuinely and substantially contradict each other; a single provided " +
    "field, or merely unusual phrasing, is never inconsistent. When consistent:false, explain " +
    "the conflict in issue. Minor punctuation, capitalization, or literal-gloss precision " +
    "fixes are allowed when consistent remains true. " +
    "The target answer must be written in the target language. Do not copy source-language " +
    "words into the target except proper names or accepted loanwords. If the target language " +
    "is Polish and the subject is second-person plural, write \"Wy\" or omit the pronoun; " +
    "never write \"Y'all\" in target or words[].pl. " +
    "The literal field must be a word-for-word gloss of the target answer, preserving target " +
    "word order and using hyphens for multi-word glosses. Also return words: one object per " +
    "whitespace-separated target token, in target order. Because the Android model uses " +
    "historical field names, put each target token in the word object's \"pl\" property and " +
    "put its source-language gloss in the \"en\" property, even when the target is not Polish. " +
    "When the target token is a noun, pronoun, or adjective in Polish, include gender " +
    "(m/f/n) and caseKey (nom/acc/gen/dat/inst/loc/voc); omit those keys otherwise. " +
    "Return ONLY one JSON object, no markdown, with this exact shape: " +
    "{\"consistent\":true,\"issue\":\"\",\"source\":\"...\",\"target\":\"...\"," +
    "\"literal\":\"...\",\"words\":[{\"pl\":\"target-token\",\"en\":\"source-gloss\"}]}.";
}

function buildAiPhraseGenerationPrompt({ prompt, count, pair }) {
  const sourceLanguage = pair?.sourceLanguage || "English";
  const targetLanguage = pair?.targetLanguage || "Polish";
  return "Generate useful LangBang custom study phrases for one learner. " +
    `Create exactly ${count} short, natural phrases. ` +
    `The source cue language is ${sourceLanguage}. The target answer language is ${targetLanguage}. ` +
    `The user's requested topic or goal is: ${JSON.stringify(prompt)}. ` +
    "Make the phrases common, practical, and easy to reuse in real conversation. " +
    "Reject contrived coverage phrases, awkward literal translations, rare expressions, and inside jokes. " +
    "Each phrase should be one sentence or short utterance, not a paragraph. " +
    "The Android app uses historical JSON field names: put the target-language answer in \"pl\" " +
    "and the source-language cue in \"en\", even when the target language is not Polish. " +
    "Include literal as a word-for-word gloss of the target answer, preserving target word order. " +
    "Include words: one object per whitespace-separated target token, in target order; put the " +
    "target token in pl and the source gloss in en. If the target language is Polish and a token " +
    "is a noun, pronoun, or adjective, include gender (m/f/n) and caseKey (nom/acc/gen/dat/inst/loc/voc) when known. " +
    "Return ONLY JSON, no markdown, with this exact shape: " +
    "{\"phrases\":[{\"pl\":\"target answer\",\"en\":\"source cue\",\"literal\":\"target-order gloss\"," +
    "\"words\":[{\"pl\":\"target-token\",\"en\":\"source-gloss\"}]}]}.";
}

function buildG2TranslatePrompt(english) {
  return "Translate one English phrase for immediate display on Even Realities G2 glasses. " +
    "Return a concise, natural Polish translation and a plain ASCII English-speaker pronunciation " +
    "guide for the Polish. The pronunciation guide should preserve Polish word order, use hyphens " +
    "inside difficult words, avoid IPA symbols, and be readable on a tiny HUD. " +
    "Do not add explanations, alternatives, markdown, or quotes. " +
    `English input: ${JSON.stringify(english)}. ` +
    "Return ONLY JSON with this exact shape: " +
    "{\"english\":\"...\",\"polish\":\"...\",\"phonetics\":\"...\"}.";
}

function normalizeG2Translation(value, fallbackEnglish) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new HttpError(502, "Gemini returned an invalid G2 translation");
  }
  const english = cleanString(value.english) || cleanString(fallbackEnglish);
  const polish = cleanString(value.polish);
  const phonetics = cleanString(value.phonetics || value.pronunciation);
  if (!english || !polish || !phonetics) {
    throw new HttpError(502, "Gemini returned an incomplete G2 translation", {
      hasEnglish: Boolean(english),
      hasPolish: Boolean(polish),
      hasPhonetics: Boolean(phonetics),
    });
  }
  return {
    english: english.slice(0, MAX_G2_TRANSLATE_CHARS),
    polish: polish.slice(0, 500),
    phonetics: phonetics.slice(0, 500),
  };
}

function formatG2TranslationDisplay(result) {
  return [
    "G2Trans text",
    `EN: ${result.english}`,
    `PL: ${result.polish}`,
    `PH: ${result.phonetics}`,
  ].join("\n");
}

function parseGeminiJsonText(text) {
  const trimmed = String(text || "").trim()
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/i, "")
    .trim();
  return JSON.parse(trimmed);
}

function normalizePhraseCompletion(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new HttpError(502, "Gemini returned an invalid phrase object");
  }
  const issue = cleanString(value.issue);
  if (value.consistent === false) {
    return {
      consistent: false,
      issue: issue || "The filled phrase fields are inconsistent.",
      source: "",
      target: "",
      literal: null,
      words: [],
    };
  }
  const source = cleanString(value.source);
  const target = cleanString(value.target);
  if (!source || !target) {
    throw new HttpError(502, "Gemini returned an incomplete phrase", {
      hasSource: Boolean(source),
      hasTarget: Boolean(target),
    });
  }
  const literal = cleanString(value.literal);
  return {
    consistent: true,
    issue,
    source,
    target,
    literal: literal || null,
    words: Array.isArray(value.words) ? value.words.map(normalizeTokenPair).filter(Boolean) : [],
  };
}

function scrubPhraseTargetLanguageLeaks(phrase, targetLanguage) {
  if (!phrase.consistent || !isPolishLanguage(targetLanguage)) return phrase;
  return {
    ...phrase,
    target: scrubPolishYallText(phrase.target),
    words: phrase.words.map((word) => ({
      ...word,
      pl: scrubPolishYallText(word.pl),
    })),
  };
}

function isPolishLanguage(value) {
  return /\bpolish\b|\bpl\b/i.test(String(value || ""));
}

function scrubPolishYallText(value) {
  return String(value || "").replace(/\by['’]all\b/gi, (match, offset) => offset === 0 ? "Wy" : "wy");
}

function normalizeTokenPair(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const pl = cleanString(value.pl);
  if (!pl) return null;
  const out = { pl, en: cleanString(value.en) };
  for (const key of ["gender", "caseKey", "caseLabel", "numberLabel", "variableKind"]) {
    const cleaned = cleanString(value[key]);
    if (cleaned) out[key] = key === "gender" || key === "caseKey" || key === "variableKind"
      ? cleaned.toLowerCase()
      : cleaned;
  }
  for (const key of ["variableStart", "variableEnd"]) {
    const parsed = Number(value[key]);
    if (Number.isFinite(parsed)) out[key] = Math.trunc(parsed);
  }
  return out;
}

function normalizeGeminiModel(value) {
  const model = String(value || DEFAULT_GEMINI_MODEL).trim() || DEFAULT_GEMINI_MODEL;
  if (!/^gemini-[A-Za-z0-9._-]+$/.test(model)) {
    throw new HttpError(400, "unsupported Gemini model");
  }
  return model;
}

function boundedString(value, label, limit, optional = false) {
  if ((value === undefined || value === null) && optional) return "";
  const text = String(value ?? "");
  if (!optional && !text.trim()) throw new HttpError(400, `${label} is required`);
  if (text.length > limit) throw new HttpError(400, `${label} is too long`, { limit });
  return text;
}

function cleanString(value) {
  return typeof value === "string" ? value.trim() : "";
}

async function readJsonLimited(request, maxChars) {
  const text = await request.text();
  if (text.length > maxChars) {
    throw new HttpError(413, "request body is too large", { limit: maxChars });
  }
  if (!text.trim()) throw new HttpError(400, "JSON body required");
  return JSON.parse(text);
}

function analyticsId(value, label, limit) {
  const text = analyticsString(value, limit);
  if (!text) throw new HttpError(400, `${label} is required`);
  if (!/^[A-Za-z0-9:._@-]+$/.test(text)) {
    throw new HttpError(400, `${label} contains unsupported characters`);
  }
  return text;
}

function analyticsName(value, label) {
  const text = analyticsString(value, 80);
  if (!text) throw new HttpError(400, `${label} is required`);
  if (!/^[a-zA-Z0-9][a-zA-Z0-9._-]{0,79}$/.test(text)) {
    throw new HttpError(400, `${label} must be alphanumeric plus dot, dash, or underscore`);
  }
  return text;
}

function analyticsString(value, limit) {
  if (value === undefined || value === null) return "";
  return String(value).trim().slice(0, limit);
}

function analyticsTime(value) {
  const date = new Date(value || Date.now());
  if (Number.isNaN(date.getTime())) return new Date().toISOString();
  const now = Date.now();
  const time = Math.max(now - 90 * 24 * 60 * 60 * 1000, Math.min(date.getTime(), now + 5 * 60 * 1000));
  return new Date(time).toISOString();
}

function intOrNull(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.trunc(parsed) : null;
}

function compactJsonObject(value) {
  const object = value && typeof value === "object" && !Array.isArray(value) ? value : {};
  const text = JSON.stringify(object);
  if (text.length <= MAX_ANALYTICS_PROPERTIES_CHARS) return text;
  return JSON.stringify({ truncated: true, originalChars: text.length });
}

async function labelMap(env, locale) {
  const rows = await env.DB.prepare(`
    SELECT label_key, label_value
    FROM ui_labels
    WHERE locale = ?
  `).bind(locale).all();
  const labels = {};
  for (const row of rows.results) labels[row.label_key] = row.label_value;
  return labels;
}

async function lessonsFor(env, contentVersionId) {
  if (!contentVersionId) return [];
  const rows = await env.DB.prepare(`
    SELECT lesson_id, lesson_type, sort_order, title, summary, payload_json, updated_at
    FROM content_lessons
    WHERE content_version_id = ?
    ORDER BY sort_order
  `).bind(contentVersionId).all();
  return rows.results.map((row) => ({
    id: row.lesson_id,
    type: row.lesson_type,
    sortOrder: row.sort_order,
    title: row.title,
    summary: row.summary || "",
    payload: parseJson(row.payload_json, {}),
    updatedAt: row.updated_at,
  }));
}

async function adminLessons(env, contentVersionId) {
  const rows = await env.DB.prepare(`
    SELECT lesson_id, lesson_type, sort_order, title, summary, payload_json, updated_at
    FROM content_lessons
    WHERE content_version_id = ?
    ORDER BY sort_order
  `).bind(contentVersionId).all();
  return json({
    contentVersionId,
    lessons: rows.results.map((row) => {
      const payload = parseJson(row.payload_json, {});
      return {
        id: row.lesson_id,
        type: row.lesson_type,
        sortOrder: row.sort_order,
        title: row.title,
        summary: row.summary || "",
        updatedAt: row.updated_at,
        collections: collectionCounts(payload),
      };
    }),
  });
}

async function adminLesson(env, contentVersionId, lessonId) {
  const lesson = await loadLesson(env, contentVersionId, lessonId);
  return json(lessonResponse(lesson));
}

async function adminLessonItems(request, env, contentVersionId, lessonId) {
  const lesson = await loadLesson(env, contentVersionId, lessonId);
  if (request.method === "GET") {
    const url = new URL(request.url);
    const collection = collectionName(url.searchParams.get("collection") || defaultCollection(lesson));
    const groupId = url.searchParams.get("groupId") || undefined;
    const items = resolveCollection(lesson.payload, collection, groupId);
    return json({
      contentVersionId,
      lessonId,
      collection,
      groupId,
      items: items.map((item, index) => itemSummary(item, index)),
    });
  }

  if (request.method === "POST") {
    const body = await request.json();
    const collection = collectionName(body.collection || defaultCollection(lesson));
    const groupId = body.groupId || undefined;
    const item = normalizeEditableItem(collection, body.item || body.group || body.sentence);
    const dryRun = body.dryRun === true;
    const items = resolveCollection(lesson.payload, collection, groupId);
    const key = itemKey(item, body.keyField);
    const existingIndex = items.findIndex((candidate) => itemKey(candidate, body.keyField) === key);
    const mode = body.mode || "upsert";
    if (existingIndex >= 0 && mode === "error") {
      throw new HttpError(409, "item already exists", { key });
    }
    if (existingIndex >= 0 && mode !== "append") {
      items.splice(existingIndex, 1);
    }
    const insertAt = normalizeInsertPosition(body.position, items.length);
    items.splice(insertAt, 0, item);
    return dryRun
      ? json({ dryRun: true, action: existingIndex >= 0 && mode !== "append" ? "replace" : "insert", key, insertAt, lesson: lessonResponse(lesson) })
      : persistAndRespond(env, lesson, "content.item.upsert", { collection, groupId, key, insertAt });
  }

  if (request.method === "DELETE") {
    const body = await readOptionalJson(request);
    const collection = collectionName(body.collection || new URL(request.url).searchParams.get("collection") || defaultCollection(lesson));
    const groupId = body.groupId || new URL(request.url).searchParams.get("groupId") || undefined;
    const key = body.key || body.itemId || new URL(request.url).searchParams.get("key") || new URL(request.url).searchParams.get("itemId");
    const index = body.index ?? numericParam(new URL(request.url).searchParams.get("index"));
    const dryRun = body.dryRun === true;
    const items = resolveCollection(lesson.payload, collection, groupId);
    const removeIndex = index !== undefined && index !== null
      ? index
      : items.findIndex((candidate) => itemKey(candidate, body.keyField) === key);
    if (removeIndex < 0 || removeIndex >= items.length) {
      throw new HttpError(404, "item not found", { key, index });
    }
    const removed = items.splice(removeIndex, 1)[0];
    return dryRun
      ? json({ dryRun: true, action: "delete", removed: itemSummary(removed, removeIndex), lesson: lessonResponse(lesson) })
      : persistAndRespond(env, lesson, "content.item.delete", { collection, groupId, key: itemKey(removed), index: removeIndex });
  }

  return json({ error: "method not allowed" }, 405);
}

async function adminReorderItems(request, env, contentVersionId, lessonId) {
  const body = await request.json();
  const lesson = await loadLesson(env, contentVersionId, lessonId);
  const collection = collectionName(body.collection || defaultCollection(lesson));
  const groupId = body.groupId || undefined;
  const items = resolveCollection(lesson.payload, collection, groupId);
  const dryRun = body.dryRun === true;

  if (Array.isArray(body.order)) {
    const byKey = new Map(items.map((item) => [itemKey(item, body.keyField), item]));
    const picked = [];
    const seen = new Set();
    for (const key of body.order.map(String)) {
      const item = byKey.get(key);
      if (item && !seen.has(key)) {
        picked.push(item);
        seen.add(key);
      }
    }
    const rest = items.filter((item) => !seen.has(itemKey(item, body.keyField)));
    items.splice(0, items.length, ...picked, ...rest);
  } else {
    const fromIndex = body.fromIndex ?? items.findIndex((item) => itemKey(item, body.keyField) === (body.key || body.itemId));
    const toIndex = body.toIndex;
    if (fromIndex < 0 || fromIndex >= items.length || toIndex === undefined || toIndex === null) {
      throw new HttpError(400, "provide order[] or a valid fromIndex/key plus toIndex");
    }
    const [moved] = items.splice(fromIndex, 1);
    items.splice(Math.max(0, Math.min(Number(toIndex), items.length)), 0, moved);
  }

  return dryRun
    ? json({ dryRun: true, action: "reorder", lesson: lessonResponse(lesson) })
    : persistAndRespond(env, lesson, "content.item.reorder", { collection, groupId });
}

async function warmMissingAudio(request, env, contentVersionId) {
  const body = await readOptionalJson(request);
  const limit = Math.max(1, Math.min(MAX_AUDIO_WARM_LIMIT, Number(body.limit || DEFAULT_AUDIO_WARM_LIMIT)));
  const includeSourceSlow = body.includeSourceSlow === true;
  const dryRun = body.dryRun === true;
  const content = await loadContentVersion(env, contentVersionId);
  const lessons = await loadLessons(env, contentVersionId, body.lessonId);
  const allRequests = dedupePhrases(
    lessons.flatMap((lesson) => audioRequestsForPayload(content.languagePair, lesson.payload, { includeSourceSlow }))
  );

  const missing = await missingAudioByMetadata(env, allRequests);

  if (dryRun) {
    return json({
      dryRun: true,
      contentVersionId,
      lessonId: body.lessonId || null,
      totalRequired: allRequests.length,
      missing: missing.length,
      sample: missing.slice(0, limit),
    });
  }

  const batch = missing.slice(0, limit);
  const manifest = [];
  for (const phrase of batch) {
    manifest.push(await ensureAudio(env, phrase));
  }
  const summary = {
    required: allRequests.length,
    missingBeforeRun: missing.length,
    requested: manifest.length,
    synthesized: manifest.filter((m) => m.uploaded).length,
    cached: manifest.filter((m) => !m.uploaded && !m.error).length,
    failed: manifest.filter((m) => m.error).length,
    remainingEstimate: Math.max(0, missing.length - manifest.length),
  };
  return json({ contentVersionId, lessonId: body.lessonId || null, summary, manifest });
}

async function missingAudioByMetadata(env, phrases) {
  const rows = await env.DB.prepare(`
    SELECT sha1
    FROM audio_assets
    WHERE last_error IS NULL
  `).all();
  const existing = new Set(rows.results.map((row) => row.sha1));
  const missing = [];
  for (const phrase of phrases) {
    const sha1 = await sha1Hex(`${phrase.locale}|${phrase.voice}|${phrase.text}`);
    if (!existing.has(sha1)) missing.push(phrase);
  }
  return missing;
}

async function loadLesson(env, contentVersionId, lessonId) {
  const row = await env.DB.prepare(`
    SELECT content_version_id, lesson_id, lesson_type, sort_order, title, summary, payload_json, updated_at
    FROM content_lessons
    WHERE content_version_id = ? AND lesson_id = ?
  `).bind(contentVersionId, lessonId).first();
  if (!row) throw new HttpError(404, "lesson not found", { contentVersionId, lessonId });
  return {
    contentVersionId: row.content_version_id,
    lessonId: row.lesson_id,
    lessonType: row.lesson_type,
    sortOrder: row.sort_order,
    title: row.title,
    summary: row.summary || "",
    payload: parseJson(row.payload_json, {}),
    updatedAt: row.updated_at,
  };
}

async function loadLessons(env, contentVersionId, lessonId = undefined) {
  const sql = `
    SELECT content_version_id, lesson_id, lesson_type, sort_order, title, summary, payload_json, updated_at
    FROM content_lessons
    WHERE content_version_id = ? ${lessonId ? "AND lesson_id = ?" : ""}
    ORDER BY sort_order
  `;
  const statement = env.DB.prepare(sql);
  const rows = lessonId
    ? await statement.bind(contentVersionId, lessonId).all()
    : await statement.bind(contentVersionId).all();
  if (lessonId && rows.results.length === 0) throw new HttpError(404, "lesson not found", { contentVersionId, lessonId });
  return rows.results.map((row) => ({
    contentVersionId: row.content_version_id,
    lessonId: row.lesson_id,
    lessonType: row.lesson_type,
    sortOrder: row.sort_order,
    title: row.title,
    summary: row.summary || "",
    payload: parseJson(row.payload_json, {}),
    updatedAt: row.updated_at,
  }));
}

async function loadContentVersion(env, contentVersionId) {
  const row = await env.DB.prepare(`
    SELECT cv.id, cv.language_pair_id, p.source_language, p.target_language,
           p.source_locale, p.target_locale, p.source_voice, p.target_voice,
           p.target_slow_voices_json
    FROM content_versions cv
    JOIN language_pairs p ON p.id = cv.language_pair_id
    WHERE cv.id = ?
  `).bind(contentVersionId).first();
  if (!row) throw new HttpError(404, "content version not found", { contentVersionId });
  return {
    id: row.id,
    languagePair: {
      id: row.language_pair_id,
      sourceLanguage: row.source_language,
      targetLanguage: row.target_language,
      sourceLocale: row.source_locale,
      targetLocale: row.target_locale,
      sourceVoice: row.source_voice,
      targetVoice: row.target_voice,
      targetSlowVoices: parseJson(row.target_slow_voices_json, []),
    },
  };
}

async function loadContentForInstance(env, instanceId) {
  const row = await env.DB.prepare(`
    SELECT i.content_version_id, p.id AS language_pair_id, p.source_language, p.target_language,
           p.source_locale, p.target_locale, p.source_voice, p.target_voice,
           p.target_slow_voices_json
    FROM app_instances i
    JOIN language_pairs p ON p.id = i.language_pair_id
    WHERE i.id = ? AND i.active = 1 AND p.active = 1
  `).bind(instanceId).first();
  if (!row) throw new HttpError(404, "instance not found", { instanceId });
  return {
    contentVersionId: row.content_version_id,
    languagePair: {
      id: row.language_pair_id,
      sourceLanguage: row.source_language,
      targetLanguage: row.target_language,
      sourceLocale: row.source_locale,
      targetLocale: row.target_locale,
      sourceVoice: row.source_voice,
      targetVoice: row.target_voice,
      targetSlowVoices: parseJson(row.target_slow_voices_json, []),
    },
  };
}

async function persistAndRespond(env, lesson, eventType, eventPayload) {
  const payloadJson = JSON.stringify(lesson.payload);
  await env.DB.prepare(`
    UPDATE content_lessons
    SET title = ?, summary = ?, payload_json = ?, updated_at = datetime('now')
    WHERE content_version_id = ? AND lesson_id = ?
  `).bind(
    lesson.payload.title || lesson.title,
    lesson.payload.summary || lesson.summary || "",
    payloadJson,
    lesson.contentVersionId,
    lesson.lessonId,
  ).run();
  await env.DB.prepare(`
    INSERT INTO sync_events (id, instance_id, event_type, payload_json)
    VALUES (?, ?, ?, ?)
  `).bind(
    crypto.randomUUID(),
    `content:${lesson.contentVersionId}`,
    eventType,
    JSON.stringify({ lessonId: lesson.lessonId, ...eventPayload }),
  ).run();
  return adminLesson(env, lesson.contentVersionId, lesson.lessonId);
}

function lessonResponse(lesson) {
  return {
    contentVersionId: lesson.contentVersionId,
    lessonId: lesson.lessonId,
    lessonType: lesson.lessonType,
    sortOrder: lesson.sortOrder,
    title: lesson.payload.title || lesson.title,
    summary: lesson.payload.summary || lesson.summary || "",
    collections: collectionCounts(lesson.payload),
    updatedAt: lesson.updatedAt,
    payload: lesson.payload,
  };
}

function collectionCounts(payload) {
  const counts = {};
  for (const key of ["phonemes", "phrases", "verbs", "pronouns", "adjectives", "adverbs", "nouns", "groups"]) {
    if (Array.isArray(payload[key])) counts[key] = payload[key].length;
  }
  if (Array.isArray(payload.groups)) {
    counts.sentences = payload.groups.reduce((sum, group) => sum + (Array.isArray(group.sentences) ? group.sentences.length : 0), 0);
  }
  return counts;
}

function defaultCollection(lesson) {
  const payload = lesson.payload;
  for (const key of ["verbs", "adjectives", "adverbs", "nouns", "groups", "phrases", "phonemes", "pronouns"]) {
    if (Array.isArray(payload[key])) return key;
  }
  throw new HttpError(400, "lesson has no editable top-level collection");
}

function collectionName(value) {
  const name = String(value || "").trim();
  if (name === "phraseGroups") return "groups";
  if (name === "phraseSentences") return "sentences";
  if (!name) throw new HttpError(400, "collection is required");
  return name;
}

function resolveCollection(payload, collection, groupId = undefined) {
  if (collection === "sentences") {
    if (!Array.isArray(payload.groups)) throw new HttpError(400, "sentences collection requires a lesson with groups[]");
    const group = groupId
      ? payload.groups.find((candidate) => candidate.id === groupId)
      : payload.groups[0];
    if (!group) throw new HttpError(404, "phrase group not found", { groupId });
    if (!Array.isArray(group.sentences)) group.sentences = [];
    return group.sentences;
  }
  if (!Array.isArray(payload[collection])) {
    throw new HttpError(400, "collection is not editable on this lesson", { collection });
  }
  return payload[collection];
}

function normalizeEditableItem(collection, item) {
  if (!item || typeof item !== "object" || Array.isArray(item)) {
    throw new HttpError(400, "item object is required");
  }
  const out = structuredClone(item);
  if (["groups", "phrases", "sentences"].includes(collection) && !out.id) {
    out.id = slug(out.title || out.en || out.pl || out.source || out.target || crypto.randomUUID());
  }
  return out;
}

function itemSummary(item, index) {
  return {
    index,
    key: itemKey(item),
    label: item.title || item.lemma || item.en || item.pl || item.source || item.target || item.id || `item-${index}`,
    item,
  };
}

function itemKey(item, keyField = undefined) {
  if (keyField && item?.[keyField] !== undefined) return String(item[keyField]);
  if (item?.id !== undefined) return String(item.id);
  if (item?.lemma !== undefined) return String(item.lemma);
  if (item?.key !== undefined) return String(item.key);
  if (item?.pl !== undefined || item?.en !== undefined) return `${item.pl || ""}|${item.en || ""}`;
  if (item?.source !== undefined || item?.target !== undefined) return `${item.source || ""}|${item.target || ""}`;
  throw new HttpError(400, "item needs id, lemma, key, pl/en, or source/target for stable editing");
}

function normalizeInsertPosition(position, length) {
  if (position === undefined || position === null || position === "end") return length;
  if (position === "start") return 0;
  const parsed = Number(position);
  if (!Number.isFinite(parsed)) return length;
  return Math.max(0, Math.min(Math.trunc(parsed), length));
}

function numericParam(value) {
  if (value === null || value === undefined || value === "") return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function slug(value) {
  const cleaned = String(value || "")
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return cleaned || crypto.randomUUID();
}

async function readOptionalJson(request) {
  const text = await request.text();
  if (!text.trim()) return {};
  return JSON.parse(text);
}

async function audioManifest(request, env) {
  const admin = isAdmin(request, env);
  if (!admin) {
    await enforceRateLimit(env, `audio-manifest:ip:${clientIp(request)}`, RL.audioIpPerHour, 3600, { scope: "ip" });
  }
  const body = await request.json();
  const phrases = Array.isArray(body.phrases) ? body.phrases : [];
  if (phrases.length === 0) {
    return json({ error: "phrases[] required" }, 400);
  }
  if (!admin && phrases.length > MAX_AUDIO_MANIFEST_PHRASES) {
    return json({ error: `too many phrases (max ${MAX_AUDIO_MANIFEST_PHRASES} per request)` }, 413);
  }
  const manifest = [];
  for (const phrase of phrases) {
    const entry = await ensureAudio(env, normalizePhrase(phrase));
    manifest.push(entry);
  }
  const summary = {
    requested: manifest.length,
    synthesized: manifest.filter((m) => m.uploaded).length,
    cached: manifest.filter((m) => !m.uploaded && !m.error).length,
    failed: manifest.filter((m) => m.error).length,
  };
  return json({ summary, manifest });
}

function normalizePhrase(raw) {
  return {
    text: String(raw?.text || "").trim(),
    voice: String(raw?.voice || "").trim(),
    locale: String(raw?.locale || "").trim(),
  };
}

async function ensureAudio(env, phrase) {
  const sha1 = await sha1Hex(`${phrase.locale}|${phrase.voice}|${phrase.text}`);
  const key = `${env.AUDIO_PREFIX || "langbang/audio"}/${sha1}.mp3`;
  const url = publicUrl(env, key);
  if (!phrase.text || !phrase.voice || !phrase.locale) {
    return { ...phrase, sha1, url, uploaded: false, error: "text, voice, and locale are required" };
  }
  try {
    const existing = await env.BUCKET.head(key);
    if (existing) {
      await upsertAudio(env, phrase, sha1, key, url, existing.size, false, null);
      return { ...phrase, sha1, url, uploaded: false };
    }
    const mp3 = await synthesize(env, phrase.text, phrase.voice, phrase.locale);
    await env.BUCKET.put(key, mp3, {
      httpMetadata: { contentType: "audio/mpeg" },
    });
    await upsertAudio(env, phrase, sha1, key, url, mp3.byteLength, true, null);
    return { ...phrase, sha1, url, uploaded: true };
  } catch (error) {
    const message = error?.message || String(error);
    await upsertAudio(env, phrase, sha1, key, url, null, false, message);
    return { ...phrase, sha1, url, uploaded: false, error: message };
  }
}

async function audioExists(env, phrase) {
  const sha1 = await sha1Hex(`${phrase.locale}|${phrase.voice}|${phrase.text}`);
  const key = `${env.AUDIO_PREFIX || "langbang/audio"}/${sha1}.mp3`;
  return Boolean(await env.BUCKET.head(key));
}

function audioRequestsForPayload(pair, payload, options = {}) {
  const out = [];
  const fieldLocales = payload && typeof payload.fieldLocales === "object" && !Array.isArray(payload.fieldLocales)
    ? payload.fieldLocales
    : {};
  const sourceSlowVoices = options.includeSourceSlow
    ? [`${pair.sourceVoice}${SLOW_60_SUFFIX}`, `${pair.sourceVoice}${SLOW_ART_SUFFIX}`]
    : [];
  const targetSlowVoices = Array.isArray(pair.targetSlowVoices) ? pair.targetSlowVoices : [];

  const voiceByLocale = new Map([
    [pair.sourceLocale, { voice: pair.sourceVoice, slow: sourceSlowVoices }],
    [pair.targetLocale, { voice: pair.targetVoice, slow: targetSlowVoices }],
  ]);

  function add(text, locale, includeSlow = false) {
    text = String(text || "").trim();
    if (!text) return;
    const config = voiceByLocale.get(locale);
    if (!config?.voice) return;
    out.push({ text, locale, voice: config.voice });
    if (includeSlow) {
      for (const slowVoice of config.slow || []) out.push({ text, locale, voice: slowVoice });
    }
  }

  function localeForField(field, fallback) {
    const locale = String(fieldLocales[field] || "").trim();
    return locale || fallback;
  }

  function includeSlowForLocale(locale) {
    if (locale === pair.targetLocale) return true;
    return options.includeSourceSlow === true && locale === pair.sourceLocale;
  }

  function addKnownText(text, field) {
    if (field === "source") {
      add(text, pair.sourceLocale, options.includeSourceSlow === true);
    } else if (field === "target") {
      add(text, pair.targetLocale, true);
    } else if (field === "pl") {
      const locale = localeForField("pl", "pl-PL");
      add(text, locale, includeSlowForLocale(locale));
    } else if (field === "en") {
      const locale = localeForField("en", "en-US");
      add(text, locale, includeSlowForLocale(locale));
    }
  }

  function addTarget(value) {
    add(value, pair.targetLocale, true);
  }

  function walk(value, parentKey = "") {
    if (Array.isArray(value)) {
      for (const item of value) walk(item, parentKey);
      return;
    }
    if (!value || typeof value !== "object") return;

    if (typeof value.source === "string" || typeof value.target === "string") {
      addKnownText(value.source, "source");
      addKnownText(value.target, "target");
    }
    if (typeof value.pl === "string" || typeof value.en === "string") {
      addKnownText(value.pl, "pl");
      addKnownText(value.en, "en");
    }

    if (typeof value.lemma === "string" && ["verbs", "adjectives", "adverbs", "nouns"].includes(parentKey)) {
      addTarget(value.lemma);
    }
    for (const key of ["forms", "past_forms", "nom", "acc", "gen", "case_forms"]) {
      addStringLeaves(value[key], addTarget);
    }

    for (const [key, child] of Object.entries(value)) {
      if ([
        "source", "target", "pl", "en", "lemma", "forms", "past_forms", "nom", "acc", "gen", "case_forms", "literal", "words",
        "schema", "sourceLocale", "targetLocale", "sourceField", "targetField", "fieldLocales",
      ].includes(key)) {
        continue;
      }
      walk(child, key);
    }
  }

  walk(payload);
  return dedupePhrases(out);
}

function addStringLeaves(value, add) {
  if (typeof value === "string") {
    add(value);
    return;
  }
  if (Array.isArray(value)) {
    for (const item of value) addStringLeaves(item, add);
    return;
  }
  if (value && typeof value === "object") {
    for (const child of Object.values(value)) addStringLeaves(child, add);
  }
}

function dedupePhrases(phrases) {
  const seen = new Set();
  const out = [];
  for (const phrase of phrases) {
    const text = String(phrase.text || "").trim();
    const locale = String(phrase.locale || "").trim();
    const voice = String(phrase.voice || "").trim();
    if (!text || !locale || !voice) continue;
    const key = `${locale}|${voice}|${text}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({ text, locale, voice });
  }
  return out;
}

async function synthesize(env, text, voice, locale) {
  const key = env.AZURE_SPEECH_KEY;
  if (!key) throw new Error("AZURE_SPEECH_KEY is not configured");
  const region = env.AZURE_SPEECH_REGION || "eastus";
  const { ssml } = buildSsml(text, voice, locale);
  const response = await fetch(`https://${region}.tts.speech.microsoft.com/cognitiveservices/v1`, {
    method: "POST",
    headers: {
      "Ocp-Apim-Subscription-Key": key,
      "Content-Type": "application/ssml+xml",
      "X-Microsoft-OutputFormat": "audio-24khz-48kbitrate-mono-mp3",
      "User-Agent": "langbangml-cloudflare/0.1",
    },
    body: ssml,
  });
  if (!response.ok) {
    throw new Error(`Azure TTS ${response.status}: ${(await response.text()).slice(0, 200)}`);
  }
  return await response.arrayBuffer();
}

function buildSsml(text, voice, locale) {
  const slowArt = voice.endsWith(SLOW_ART_SUFFIX);
  const slow60 = !slowArt && voice.endsWith(SLOW_60_SUFFIX);
  const slow50 = !slowArt && !slow60 && voice.endsWith(SLOW_50_SUFFIX);
  const realVoice = slowArt
    ? voice.slice(0, -SLOW_ART_SUFFIX.length)
    : slow60
      ? voice.slice(0, -SLOW_60_SUFFIX.length)
      : slow50
        ? voice.slice(0, -SLOW_50_SUFFIX.length)
        : voice;
  let body;
  if (slowArt) {
    body = text.split(/\s+/).filter(Boolean).map(escapeXml).join('<break time="250ms"/>');
    body = `<prosody rate="-10%">${body}</prosody>`;
  } else if (slow60) {
    body = `<prosody rate="-60%">${escapeXml(text)}</prosody>`;
  } else if (slow50) {
    body = `<prosody rate="-50%">${escapeXml(text)}</prosody>`;
  } else {
    body = escapeXml(text);
  }
  return {
    realVoice,
    ssml: `<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="${locale}"><voice name="${realVoice}">${body}</voice></speak>`,
  };
}

async function upsertAudio(env, phrase, sha1, key, url, bytes, uploaded, error) {
  await env.DB.prepare(`
    INSERT INTO audio_assets (sha1, text, locale, voice, r2_key, public_url, bytes, uploaded, last_error, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
    ON CONFLICT(sha1) DO UPDATE SET
      text = excluded.text,
      locale = excluded.locale,
      voice = excluded.voice,
      r2_key = excluded.r2_key,
      public_url = excluded.public_url,
      bytes = COALESCE(excluded.bytes, audio_assets.bytes),
      uploaded = excluded.uploaded,
      last_error = excluded.last_error,
      updated_at = datetime('now')
  `).bind(
    sha1,
    phrase.text,
    phrase.locale,
    phrase.voice,
    key,
    url,
    bytes,
    uploaded ? 1 : 0,
    error,
  ).run();
}

function rowToInstanceSummary(row) {
  return {
    id: row.id,
    displayName: row.display_name,
    uiLocale: row.ui_locale,
    contentVersionId: row.content_version_id,
    languagePair: {
      id: row.language_pair_id,
      sourceLanguage: row.source_language,
      targetLanguage: row.target_language,
      sourceLocale: row.source_locale,
      targetLocale: row.target_locale,
    },
  };
}

function publicUrl(env, key) {
  return `${String(env.PUBLIC_R2_BASE || "").replace(/\/+$/, "")}/${key}`;
}

function publicApiBase(env) {
  return env.PUBLIC_API_BASE || "https://langbangml-api.langbangml.workers.dev";
}

function publicAgentDocsUrl(env) {
  return env.PUBLIC_AGENT_DOCS_URL || "https://langbang.org/api";
}

function sourceCodeUrl(env) {
  return env.SOURCE_CODE_URL || "https://github.com/rsonnad/langbang/tree/codex/langbangml";
}

function parseJson(raw, fallback) {
  try {
    return JSON.parse(raw || "");
  } catch {
    return fallback;
  }
}

function agentInstructionsPage(env) {
  const apiBase = publicApiBase(env);
  const docsUrl = publicAgentDocsUrl(env);
  const sourceUrl = sourceCodeUrl(env);
  const addPhrase = JSON.stringify({
    groupTitle: "Discussion Conversation",
    atomic: true,
    phrase: {
      english: "I like learning Polish.",
      polish: "Lubię uczyć się polskiego.",
    },
  }, null, 2);
  const splitPhrase = JSON.stringify({
    version: "ENPL",
    groupId: "restaurant",
    groupTitle: "Restaurant",
    atomic: false,
    phrase: {
      english: "Could we sit by the window, and can I see the menu before we order?",
    },
  }, null, 2);
  const addNoun = JSON.stringify({
    type: "noun",
    item: {
      lemma: "ogród",
      en: "garden",
      gender: "m",
      nom: { sg: "ogród", pl: "ogrody" },
      acc: { sg: "ogród", pl: "ogrody" },
      gen: { sg: "ogrodu", pl: "ogrodów" },
    },
  }, null, 2);
  const addVerb = JSON.stringify({
    type: "verb",
    item: {
      lemma: "uczyć się",
      en: "to learn",
      forms: {
        "1sg": "uczę się",
        "2sg": "uczysz się",
        "3sg": "uczy się",
        "1pl": "uczymy się",
        "2pl": "uczycie się",
        "3pl": "uczą się",
      },
    },
  }, null, 2);
  return new Response(`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>LangBang Agent API</title>
  <style>
    :root { color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    body { margin: 0; background: #f7f4ee; color: #1f2933; line-height: 1.5; }
    header { background: #1f3d34; color: white; padding: 24px 28px; }
    main { max-width: 980px; margin: 0 auto; padding: 24px 28px 48px; }
    section { background: white; border: 1px solid #e3ded3; border-radius: 8px; padding: 16px; margin: 0 0 16px; }
    h1 { margin: 0; font-size: 24px; letter-spacing: 0; }
    h2 { margin: 0 0 8px; font-size: 18px; }
    h3 { margin: 16px 0 6px; font-size: 15px; }
    code, pre { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
    code { background: #f3f0ea; border-radius: 4px; padding: 1px 4px; }
    pre { overflow-x: auto; background: #1f2933; color: #f8fafc; border-radius: 8px; padding: 12px; font-size: 12px; }
    .muted { color: #687785; }
    .warn { border-color: #f3d199; background: #fff9ed; }
    ul, ol { padding-left: 20px; }
  </style>
</head>
<body>
  <header>
    <h1>LangBang Agent API</h1>
    <p class="muted">Use this with the personal token copied from the Android Settings screen.</p>
  </header>
  <main>
    <section class="warn">
      <h2>Agent Setup Prompt</h2>
      <p>Paste this into Claude, Codex, or another coding agent together with the token from the app:</p>
      <pre>Use the LangBang Agent API to edit my personal study content.
API base: ${escapeHtml(apiBase)}
Instructions: ${escapeHtml(docsUrl)}
Token: PASTE_TOKEN_HERE

Use Authorization: Bearer PASTE_TOKEN_HERE on every /v1/agent request.
Do not print or store the token in project files, git commits, logs, or screenshots.
Daily limit: ${agentDailyLimit(env)} authenticated agent API calls per token.
Before adding content, GET /v1/agent/phrases?groupsOnly=true to see existing groups for the token's default language pair.
Omit version/instanceId to use the token's default language pair. Set version "ENPL" or "PLEN" only when you need a different direction.
For phrases, you may send english, polish, or both. LangBang will fill the missing side plus literal and word alignment.
Set atomic:true to keep one phrase, or atomic:false to let LangBang split long text into short display-safe phrases.
Use groupTitle/groupName for a human phrase-group name. Keep it to ${MAX_AGENT_GROUP_TITLE_CHARS} characters or less.
Omit groupId for normal new groups; LangBang will create a timestamped id so groups sort newest-first. Provide sortOrder only when you need an explicit order override.
For fastest phrase writes, provide pl, en, literal, and words[]; incomplete phrase entries require synchronous LLM completion before saving.
Words use lemma/en and the type-specific forms below.
Never ask for the LangBang admin content token.</pre>
    </section>
    <section>
      <h2>Source Code And License</h2>
      <p>LangBangML is released under the <code>AGPL-3.0</code> license.</p>
      <p>Source code: <a href="${escapeHtml(sourceUrl)}">${escapeHtml(sourceUrl)}</a></p>
    </section>
    <section>
      <h2>Endpoints</h2>
      <ul>
        <li><code>GET /v1/agent/status</code> checks the token, default instance, and remaining daily quota.</li>
        <li><code>GET /v1/agent/phrases</code> lists existing custom phrase groups and phrases for a version.</li>
        <li><code>POST /v1/agent/phrases</code> adds or replaces one or more phrase sentences in a personal phrase group.</li>
        <li><code>DELETE /v1/agent/phrases</code> deletes a phrase sentence, or deletes a group when no phrase is provided.</li>
        <li><code>POST /v1/agent/words</code> adds or replaces a personal word in Verbs, Nouns, Adj, or Adv.</li>
        <li><code>DELETE /v1/agent/words</code> deletes a personal word by type and lemma.</li>
      </ul>
      <p class="muted">All edits are user-owned content tied to the signed-in app account. They do not mutate global LangBangML lessons.</p>
    </section>
    <section>
      <h2>Phrase Rules</h2>
      <ul>
        <li>Omit <code>version</code> and <code>instanceId</code> to use the token's default language pair. Set <code>version:"ENPL"</code> for English cue to Polish answer, or <code>version:"PLEN"</code> for Polish cue to English answer. The older <code>instanceId</code> values still work.</li>
        <li>The selected version controls which Android app flavor downloads the content. Signed-in APKs pull their version's custom phrases on launch, sign-in, instance switch, and the Settings/Phrases sync actions.</li>
        <li>Use <code>groupTitle</code> or <code>groupName</code> for readable names such as <code>Discussion Conversation</code>. The limit is <code>${MAX_AGENT_GROUP_TITLE_CHARS}</code> characters.</li>
        <li>Default ordering is reverse chronological. Omit <code>groupId</code> for a new group and LangBang generates a timestamped id plus <code>createdAt</code>. Send <code>sortOrder</code> only when you want an explicit order override.</li>
        <li>Phrase input may use real-language fields: <code>english</code>, <code>polish</code>, or both. App-native <code>en</code>/<code>pl</code> also works for existing integrations.</li>
        <li>Fast path: provide <code>pl</code>, <code>en</code>, <code>literal</code>, and non-empty <code>words[]</code>; the API saves the structured phrase directly.</li>
        <li>LLM path: when translation, literal gloss, or word alignment is missing, LangBang uses Gemini Flash before saving. This is useful for rough input, but slower and more failure-prone for poetic fragments.</li>
        <li>Use <code>atomic:true</code> when the text should stay as one phrase. Use <code>atomic:false</code> to split long or compound input into short display-safe phrases before saving.</li>
      </ul>
    </section>
    <section>
      <h2>In-App AI Phrase Generation</h2>
      <p>The Android app also has a built-in AI phrase generator under Phrases +. It uses the same Worker-side Gemini Flash path, so a learner does not need Claude, Codex, or their own API key for simple phrase creation.</p>
      <p>Default account quota: <code>${DEFAULT_AI_PHRASE_QUOTA}</code> generated custom phrases. When that quota is reached, the app can send a quota request email to <code>${escapeHtml(env.AI_PHRASE_QUOTA_EMAIL || DEFAULT_AI_PHRASE_QUOTA_EMAIL)}</code>.</p>
    </section>
    <section>
      <h2>Examples</h2>
      <h3>List Phrase Groups</h3>
      <pre>curl -sS "${escapeHtml(apiBase)}/v1/agent/phrases?groupsOnly=true" \\
  -H "Authorization: Bearer $LANGBANGML_AGENT_TOKEN"</pre>
      <h3>List One Phrase Group With Phrases</h3>
      <pre>curl -sS "${escapeHtml(apiBase)}/v1/agent/phrases?groupTitle=Discussion%20Conversation" \\
  -H "Authorization: Bearer $LANGBANGML_AGENT_TOKEN"</pre>
      <h3>Add A Phrase</h3>
      <pre>curl -sS ${escapeHtml(apiBase)}/v1/agent/phrases \\
  -H "Authorization: Bearer $LANGBANGML_AGENT_TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '${escapeHtml(addPhrase)}'</pre>
      <h3>Add And Split A Longer Phrase</h3>
      <pre>curl -sS ${escapeHtml(apiBase)}/v1/agent/phrases \\
  -H "Authorization: Bearer $LANGBANGML_AGENT_TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '${escapeHtml(splitPhrase)}'</pre>
      <h3>Delete A Phrase</h3>
      <pre>curl -sS -X DELETE ${escapeHtml(apiBase)}/v1/agent/phrases \\
  -H "Authorization: Bearer $LANGBANGML_AGENT_TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '{"groupTitle":"Discussion Conversation","polish":"Lubię uczyć się polskiego."}'</pre>
      <h3>Add A Noun</h3>
      <pre>curl -sS ${escapeHtml(apiBase)}/v1/agent/words \\
  -H "Authorization: Bearer $LANGBANGML_AGENT_TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '${escapeHtml(addNoun)}'</pre>
      <h3>Add A Verb</h3>
      <pre>curl -sS ${escapeHtml(apiBase)}/v1/agent/words \\
  -H "Authorization: Bearer $LANGBANGML_AGENT_TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '${escapeHtml(addVerb)}'</pre>
      <h3>Delete A Word</h3>
      <pre>curl -sS -X DELETE "${escapeHtml(apiBase)}/v1/agent/words?version=ENPL&type=noun&lemma=ogr%C3%B3d" \\
  -H "Authorization: Bearer $LANGBANGML_AGENT_TOKEN"</pre>
    </section>
    <section>
      <h2>Word Shapes</h2>
      <ul>
        <li><code>type:"verb"</code>: <code>lemma</code>, <code>en</code>, <code>forms</code>, optional <code>past_forms</code>. Form keys are usually <code>1sg</code>, <code>2sg</code>, <code>3sg</code>, <code>1pl</code>, <code>2pl</code>, <code>3pl</code>.</li>
        <li><code>type:"noun"</code>: <code>lemma</code>, <code>en</code>, <code>gender</code>, and case maps <code>nom</code>, <code>acc</code>, <code>gen</code> with <code>sg</code>/<code>pl</code>.</li>
        <li><code>type:"adjective"</code>: <code>lemma</code>, <code>en</code>, <code>nom</code>, and <code>acc</code> maps.</li>
        <li><code>type:"adverb"</code>: <code>lemma</code> and <code>en</code>.</li>
      </ul>
    </section>
  </main>
</body>
</html>`, {
    status: 200,
    headers: { "Content-Type": "text/html; charset=utf-8", ...corsHeaders() },
  });
}

function analyticsAdminPage(env) {
  const clientId = env.GOOGLE_CLIENT_ID || env.GOOGLE_WEB_CLIENT_ID || env.ADMIN_GOOGLE_CLIENT_ID || "";
  const apiBase = publicApiBase(env);
  const adminEmails = [...analyticsAdminEmails(env)].join(", ") || DEFAULT_ANALYTICS_ADMIN_EMAIL;
  const authBlock = clientId
    ? `<div class="login-option">
         <h2>Google Admin Login</h2>
         <p class="muted">Sign in with an allowed admin Google account: <code>${escapeHtml(adminEmails)}</code>.</p>
         <div id="g_id_onload" data-client_id="${escapeHtml(clientId)}" data-callback="onGoogleCredential"></div>
         <div class="g_id_signin" data-type="standard" data-size="large" data-theme="outline"></div>
       </div>`
    : `<div class="login-option warning">
         <h2>Google Login Not Configured</h2>
         <p class="muted">The analytics backend is live, but the Google Web Client ID is not set yet. Use the bearer-token login below, or follow the setup checklist on this page to enable the Google button.</p>
       </div>`;
  return new Response(`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>LangBangML Analytics</title>
  ${clientId ? '<script src="https://accounts.google.com/gsi/client" async defer></script>' : ''}
  <style>
    :root { color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    body { margin: 0; background: #f7f4ee; color: #1f2933; }
    header { background: #1f3d34; color: white; padding: 22px 28px; display: flex; gap: 16px; align-items: center; justify-content: space-between; }
    h1 { margin: 0; font-size: 22px; letter-spacing: 0; }
    main { padding: 22px 28px 40px; max-width: 1180px; margin: 0 auto; }
    .auth, .panel { background: white; border: 1px solid #e3ded3; border-radius: 8px; padding: 16px; margin-bottom: 16px; box-shadow: 0 1px 2px rgba(31, 41, 51, .04); }
    .auth { display: flex; gap: 14px; align-items: center; flex-wrap: wrap; }
    .auth { align-items: stretch; }
    .login-option { flex: 1 1 360px; border: 1px solid #e3ded3; border-radius: 8px; background: #fbfaf7; padding: 14px; }
    .login-option.warning { border-color: #f3d199; background: #fff9ed; }
    .token-login { flex: 1 1 420px; display: flex; gap: 12px; align-items: end; flex-wrap: wrap; }
    .guide { background: #fff; border: 1px solid #e3ded3; border-radius: 8px; padding: 16px; margin-bottom: 16px; box-shadow: 0 1px 2px rgba(31, 41, 51, .04); }
    .guide-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
    .guide-card { background: #fbfaf7; border: 1px solid #ece7dc; border-radius: 8px; padding: 14px; }
    .guide h2, .login-option h2 { margin: 0 0 8px; font-size: 16px; }
    .guide h3 { margin: 0 0 8px; font-size: 14px; color: #1f3d34; }
    .guide ol, .guide ul { margin: 8px 0 0 18px; padding: 0; }
    .guide li { margin: 5px 0; }
    code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; background: #f3f0ea; border-radius: 4px; padding: 1px 4px; }
    label { font-size: 12px; color: #52606d; font-weight: 700; }
    input, select, button { border: 1px solid #c7c2b8; border-radius: 6px; padding: 9px 10px; font: inherit; }
    button { background: #246b54; color: white; border-color: #246b54; cursor: pointer; font-weight: 700; }
    button.secondary { background: #fff; color: #246b54; }
    .grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
    .metric { background: #fbfaf7; border: 1px solid #ece7dc; border-radius: 8px; padding: 12px; }
    .metric div:first-child { color: #687785; font-size: 12px; font-weight: 700; text-transform: uppercase; }
    .metric div:last-child { font-size: 26px; font-weight: 800; margin-top: 4px; }
    table { width: 100%; border-collapse: collapse; font-size: 13px; }
    th, td { text-align: left; padding: 8px 7px; border-bottom: 1px solid #ece7dc; vertical-align: top; }
    th { color: #52606d; font-size: 11px; text-transform: uppercase; letter-spacing: .04em; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .muted { color: #687785; font-size: 13px; }
    .error { color: #b42318; font-weight: 700; }
    .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; }
    @media (max-width: 860px) { .grid, .two, .guide-grid { grid-template-columns: 1fr; } header { align-items: flex-start; flex-direction: column; } }
  </style>
</head>
<body>
  <header>
    <h1>LangBangML Analytics</h1>
    <div class="muted" id="status">Sign in to load analytics.</div>
  </header>
  <main>
    <section class="auth">
      ${authBlock}
      <div class="token-login">
        <label>Bearer token <input id="token" type="password" size="36" placeholder="paste admin token"></label>
        <label>Window
          <select id="days">
            <option value="7">7 days</option>
            <option value="30" selected>30 days</option>
            <option value="90">90 days</option>
          </select>
        </label>
        <button id="load">Load Analytics</button>
        <button id="clear" class="secondary">Clear Token</button>
      </div>
    </section>
    <section class="guide">
      <h2>Login And Google Setup</h2>
      <div class="guide-grid">
        <div class="guide-card">
          <h3>Use It Now With The Admin Token</h3>
          <ol>
            <li>Open Bitwarden item <code>Cloudflare — LangBangML Content API — New Account</code>.</li>
            <li>Copy the item password. Do not paste or share it anywhere except this admin page.</li>
            <li>Paste it into <strong>Bearer token</strong>, choose a window, and click <strong>Load Analytics</strong>.</li>
          </ol>
          <p class="muted">The token is stored only in this browser's local storage until you click Clear Token.</p>
        </div>
        <div class="guide-card">
          <h3>Enable Google Login</h3>
          <ol>
            <li>In Google Cloud, use the LangBang auth project that owns the configured Web client and Android app client entries.</li>
            <li>Open Google Auth Platform, configure Branding, and set authorized domain <code>langbang.org</code>.</li>
            <li>Create an OAuth client with application type <strong>Web application</strong>.</li>
            <li>Add Authorized JavaScript origins: <code>https://langbang.org</code> and <code>${escapeHtml(apiBase)}</code>.</li>
            <li>No redirect URI is required for this page because Google returns the ID token to the JavaScript callback.</li>
            <li>Set Worker var <code>GOOGLE_WEB_CLIENT_ID</code> to the Web client ID and redeploy <code>langbangml-api</code>.</li>
            <li>Add the same <code>GOOGLE_WEB_CLIENT_ID</code> to Android <code>local.properties</code>, then rebuild/publish the app signup flow.</li>
          </ol>
          <p class="muted">Allowed analytics admin email is currently <code>${escapeHtml(adminEmails)}</code>.</p>
        </div>
      </div>
    </section>
    <section class="panel">
      <div class="grid" id="metrics"></div>
    </section>
    <div class="two">
      <section class="panel"><h2>Feature Usage</h2><div id="features"></div></section>
      <section class="panel"><h2>Instances</h2><div id="instances"></div></section>
    </div>
    <section class="panel"><h2>Profiles</h2><div id="profiles"></div></section>
    <section class="panel"><h2>Recent Events</h2><div id="events"></div></section>
  </main>
  <script>
    const CONFIGURED_API_BASE = "${escapeHtml(apiBase)}";
    let authToken = localStorage.getItem("langbangml.analytics.token") || "";
    const tokenInput = document.getElementById("token");
    tokenInput.value = authToken;
    document.getElementById("load").onclick = () => {
      authToken = tokenInput.value.trim() || authToken;
      if (authToken) localStorage.setItem("langbangml.analytics.token", authToken);
      loadAnalytics();
    };
    document.getElementById("clear").onclick = () => {
      authToken = "";
      tokenInput.value = "";
      localStorage.removeItem("langbangml.analytics.token");
      setStatus("Token cleared.");
    };
    window.onGoogleCredential = (response) => {
      if (!response || !response.credential) {
        setStatus("Google sign-in did not return an ID token.", true);
        return;
      }
      authToken = response.credential;
      localStorage.setItem("langbangml.analytics.token", authToken);
      tokenInput.value = "";
      setStatus("Google sign-in received. Loading analytics...");
      loadAnalytics();
    };
    function setStatus(text, error) {
      const el = document.getElementById("status");
      el.textContent = text;
      el.className = error ? "error" : "muted";
    }
    async function api(path) {
      if (!authToken) throw new Error("Sign in or enter an admin token.");
      let res;
      const base = adminApiBase().replace(/\\/+$/, "");
      try {
        res = await fetch(base + path, { headers: { Authorization: "Bearer " + authToken } });
      } catch (err) {
        throw new Error("Could not reach analytics API at " + (base || window.location.origin) + ": " + (err.message || String(err)));
      }
      let body = {};
      try {
        body = await res.json();
      } catch (err) {
        throw new Error("Analytics API returned a non-JSON response: HTTP " + res.status);
      }
      if (!res.ok) throw new Error(body.error || res.statusText);
      return body;
    }
    function adminApiBase() {
      if (window.location.hostname === "langbang.org" || window.location.hostname.endsWith(".langbang.org")) {
        return window.location.origin;
      }
      return CONFIGURED_API_BASE;
    }
    async function loadAnalytics() {
      try {
        const days = document.getElementById("days").value;
        setStatus("Loading...");
        const summary = await api("/v1/admin/analytics/summary?days=" + encodeURIComponent(days));
        const events = await api("/v1/admin/analytics/events?days=" + encodeURIComponent(days) + "&limit=120");
        renderSummary(summary);
        renderEvents(events.events || []);
        setStatus("Loaded " + summary.days + " day window.");
      } catch (err) {
        setStatus(err.message || String(err), true);
      }
    }
    function metric(label, value) {
      return '<div class="metric"><div>' + esc(label) + '</div><div>' + esc(String(value)) + '</div></div>';
    }
    function renderSummary(data) {
      const t = data.totals || {};
      document.getElementById("metrics").innerHTML =
        metric("Events", t.events || 0) +
        metric("Agent API Calls", t.agentApiCalls || 0) +
        metric("AI Phrases", t.aiPhrasesGenerated || 0) +
        metric("Profiles", t.profiles || 0) +
        metric("Sessions", t.sessions || 0) +
        metric("Hours", (((t.durationMs || 0) / 3600000).toFixed(1)));
      document.getElementById("features").innerHTML = table(
        ["Feature", "Event", "Events", "Profiles", "Hours"],
        (data.features || []).map(r => [r.feature, r.name, r.events, r.profiles, ((r.durationMs || 0) / 3600000).toFixed(2)])
      );
      document.getElementById("instances").innerHTML = table(
        ["Instance", "Events", "Profiles", "Sessions"],
        (data.instances || []).map(r => [r.instanceId, r.events, r.profiles, r.sessions])
      );
      document.getElementById("profiles").innerHTML = table(
        ["Profile", "Email", "State", "Events", "Sessions", "Last seen"],
        (data.profiles || []).map(r => [r.profileId, r.email || "", r.signupState || "", r.events, r.sessions, r.lastEventAt || r.lastSeenAt || ""])
      );
    }
    function renderEvents(rows) {
      document.getElementById("events").innerHTML = table(
        ["When", "Profile", "Feature", "Event", "Screen", "Version"],
        rows.map(r => [r.occurredAt, r.email || r.profileId, r.feature, r.name, r.screen || "", r.appVersionName || r.appVersionCode || ""])
      );
    }
    function table(headers, rows) {
      if (!rows.length) return '<p class="muted">No data in this window.</p>';
      return '<table><thead><tr>' + headers.map(h => '<th>' + esc(h) + '</th>').join("") +
        '</tr></thead><tbody>' + rows.map(row => '<tr>' + row.map(cell => '<td>' + esc(cell == null ? "" : String(cell)) + '</td>').join("") + '</tr>').join("") + '</tbody></table>';
    }
    function esc(value) {
      return String(value).replace(/[&<>"']/g, ch => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch]));
    }
  </script>
</body>
</html>`, {
    status: 200,
    headers: { "Content-Type": "text/html; charset=utf-8", ...corsHeaders() },
  });
}

async function sha1Hex(input) {
  const bytes = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-1", bytes);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function sha256Hex(input) {
  const bytes = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function escapeXml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", ...corsHeaders() },
  });
}

function allowedOrigin(origin, env) {
  if (!origin) return "";
  const configured = env && env.CORS_ALLOWED_ORIGINS
    ? env.CORS_ALLOWED_ORIGINS.split(",").map((s) => s.trim()).filter(Boolean)
    : DEFAULT_CORS_ORIGINS;
  if (configured.includes("*")) return "*";
  if (configured.includes(origin)) return origin;
  // Localhost dev origins (any port) for local web work.
  if (/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(origin)) return origin;
  return "";
}

// Adds the resolved CORS origin to a response. Leaves WebSocket upgrades and
// non-allowlisted origins untouched (no ACAO → the browser blocks the
// cross-origin read; native apps / same-origin / curl are unaffected).
function withCors(response, allowOrigin) {
  if (!allowOrigin) return response;
  if (response.status === 101 || response.webSocket) return response;
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", allowOrigin);
  headers.append("Vary", "Origin");
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

function corsHeaders(allowOrigin) {
  const headers = {
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
    "Access-Control-Max-Age": "86400",
  };
  if (allowOrigin) {
    headers["Access-Control-Allow-Origin"] = allowOrigin;
    headers["Vary"] = "Origin";
  }
  return headers;
}
