const SLOW_50_SUFFIX = "|slow50v3";
const SLOW_60_SUFFIX = "|slow60v1";
const SLOW_ART_SUFFIX = "|slowart1";
const DEFAULT_AUDIO_WARM_LIMIT = 40;
const MAX_AUDIO_WARM_LIMIT = 80;
const DEFAULT_GEMINI_MODEL = "gemini-3.5-flash";
const MAX_GEMINI_PROMPT_CHARS = 20000;
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
const DEFAULT_AI_PHRASE_QUOTA = 50;
const MAX_AI_PHRASE_PROMPT_CHARS = 1200;
const MAX_AI_PHRASES_PER_REQUEST = 10;
const DEFAULT_AI_PHRASE_QUOTA_EMAIL = "rahulioson@gmail.com";
const MAX_ANALYTICS_EVENTS_PER_BATCH = 100;
const MAX_ANALYTICS_PROPERTIES_CHARS = 4000;
const DEFAULT_ANALYTICS_ADMIN_EMAIL = "rahulioson@gmail.com";
const MAX_PUSH_TOKEN_CHARS = 4096;
const MAX_PUSH_INSTALLATION_ID_CHARS = 160;
const MAX_PUSH_APP_PACKAGE_CHARS = 160;
const MAX_PUSH_VERSION_CHARS = 80;
const MAX_PUSH_LOCALE_CHARS = 40;
const MAX_PUSH_SEND_TOKENS = 500;

let fcmAccessTokenCache = null;

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }

    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";

    try {
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
      if (request.method === "POST" && path === "/v1/push/register") {
        const user = await optionalUser(request, env);
        return await registerPushToken(request, env, user);
      }
      if (request.method === "POST" && path === "/v1/push/unregister") {
        const user = await optionalUser(request, env);
        return await unregisterPushToken(request, env, user);
      }
      if (request.method === "POST" && path === "/v1/admin/push/refresh") {
        requireAdmin(request, env);
        return await adminPushRefresh(request, env);
      }
      if (request.method === "GET" && path === "/v1/instances") {
        return await listInstances(env);
      }
      if (request.method === "POST" && path === "/v1/gemini/generate") {
        return await geminiGenerate(request, env);
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
    } catch (error) {
      if (error instanceof HttpError) {
        return json({ error: error.message, details: error.details }, error.status);
      }
      return json({ error: error?.message || String(error) }, 500);
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

async function optionalUser(request, env) {
  const auth = request.headers.get("Authorization") || "";
  if (!/^Bearer\s+/i.test(auth)) return null;
  try {
    return await requireUser(request, env);
  } catch (_) {
    return null;
  }
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

async function registerPushToken(request, env, user) {
  const body = await readJsonLimited(request, 16 * 1024);
  const token = boundedString(body.token, "token", MAX_PUSH_TOKEN_CHARS);
  const tokenHash = await sha256Hex(token);
  const instanceId = normalizeInstanceId(body.instanceId || "langbangml-en-pl");
  const platform = boundedString(body.platform || "android", "platform", 40, true) || "android";
  const installationId = boundedString(body.installationId || "", "installationId", MAX_PUSH_INSTALLATION_ID_CHARS, true);
  const appPackage = boundedString(body.appPackage || "", "appPackage", MAX_PUSH_APP_PACKAGE_CHARS, true);
  const appVersionCode = Number.isFinite(Number(body.appVersionCode)) ? Math.max(0, Math.trunc(Number(body.appVersionCode))) : 0;
  const appVersionName = boundedString(body.appVersionName || "", "appVersionName", MAX_PUSH_VERSION_CHARS, true);
  const buildNumber = Number.isFinite(Number(body.buildNumber)) ? Math.max(0, Math.trunc(Number(body.buildNumber))) : 0;
  const locale = boundedString(body.locale || "", "locale", MAX_PUSH_LOCALE_CHARS, true);
  await env.DB.prepare(`
    INSERT INTO push_device_tokens
      (token_hash, token, platform, user_id, instance_id, installation_id, app_package,
       app_version_code, app_version_name, build_number, locale, enabled,
       last_registered_at, last_seen_at, last_error)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, datetime('now'), datetime('now'), NULL)
    ON CONFLICT(token_hash) DO UPDATE SET
      token = excluded.token,
      platform = excluded.platform,
      user_id = excluded.user_id,
      instance_id = excluded.instance_id,
      installation_id = excluded.installation_id,
      app_package = excluded.app_package,
      app_version_code = excluded.app_version_code,
      app_version_name = excluded.app_version_name,
      build_number = excluded.build_number,
      locale = excluded.locale,
      enabled = 1,
      last_registered_at = datetime('now'),
      last_seen_at = datetime('now'),
      last_error = NULL
  `).bind(
    tokenHash,
    token,
    platform,
    user?.user_id || null,
    instanceId,
    installationId,
    appPackage,
    appVersionCode,
    appVersionName,
    buildNumber,
    locale,
  ).run();
  return json({ ok: true, enabled: true, instanceId, tokenHash });
}

async function unregisterPushToken(request, env, user) {
  const body = await readOptionalJson(request);
  const token = boundedString(body.token || "", "token", MAX_PUSH_TOKEN_CHARS, true);
  const installationId = boundedString(body.installationId || "", "installationId", MAX_PUSH_INSTALLATION_ID_CHARS, true);
  if (!token && !installationId) throw new HttpError(400, "token or installationId is required");
  if (token) {
    const tokenHash = await sha256Hex(token);
    await env.DB.prepare(`
      UPDATE push_device_tokens
      SET enabled = 0, last_seen_at = datetime('now')
      WHERE token_hash = ? AND (? IS NULL OR user_id = ?)
    `).bind(tokenHash, user?.user_id || null, user?.user_id || null).run();
  } else {
    await env.DB.prepare(`
      UPDATE push_device_tokens
      SET enabled = 0, last_seen_at = datetime('now')
      WHERE installation_id = ? AND (? IS NULL OR user_id = ?)
    `).bind(installationId, user?.user_id || null, user?.user_id || null).run();
  }
  return json({ ok: true });
}

async function adminPushRefresh(request, env) {
  const body = await readOptionalJson(request);
  const instanceId = normalizeInstanceId(body.instanceId || "langbangml-en-pl");
  const type = normalizePushType(body.type || (body.userId ? "user_content_refresh" : "content_refresh"));
  const reason = boundedString(body.reason || "admin.push.refresh", "reason", 120, true) || "admin.push.refresh";
  const dryRun = body.dryRun === true;
  const result = await notifyContentRefresh(env, {
    instanceId,
    userId: cleanString(body.userId),
    type,
    reason,
    contentVersionId: cleanString(body.contentVersionId),
    dryRun,
    limit: Number(body.limit || MAX_PUSH_SEND_TOKENS),
  });
  return json({ ok: true, ...result });
}

async function createAgentToken(request, env, user) {
  const body = await readOptionalJson(request);
  const revokeExisting = body.revokeExisting === true || body.rotate === true;
  const label = boundedString(body.label || "Claude/Codex", "label", MAX_AGENT_LABEL_CHARS, true) || "Claude/Codex";
  const defaultInstanceId = normalizeInstanceId(body.instanceId || "langbangml-en-pl");
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
    instructionsUrl: `${publicApiBase(env)}/agent`,
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
	    await safeNotifyContentRefresh(env, {
	      instanceId,
	      userId: user.user_id,
	      type: "user_content_refresh",
	      reason: "user.phrases.sync",
	    });
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
  await safeNotifyContentRefresh(env, {
    instanceId,
    userId: user.user_id,
    type: "user_content_refresh",
    reason: "user.ai.phrases.generate",
  });
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
      ORDER BY sort_order ASC, updated_at ASC
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
    instructionsUrl: `${publicApiBase(env)}/agent`,
    quota,
  });
}

async function agentPhrases(request, env, agent, quota) {
  if (request.method === "GET") {
    const instanceId = normalizeAgentInstanceId(new URL(request.url).searchParams.get("instanceId"), agent);
    return json({ ok: true, ...(await loadUserPhrases(env, agent.user_id, instanceId)), quota });
  }
  if (request.method === "POST") {
    const body = await readJsonLimited(request, MAX_AGENT_BODY_CHARS);
    const instanceId = normalizeAgentInstanceId(body.instanceId, agent);
    const groupId = normalizePhraseGroupId(body.groupId || body.group?.id || body.groupTitle || "agent-phrases");
    const groupTitle = boundedString(body.groupTitle || body.group?.title || groupId, "groupTitle", 200, true) || groupId;
    const groupSubtitle = boundedString(body.groupSubtitle || body.group?.subtitle || "", "groupSubtitle", 400, true);
    const sentence = normalizeSentenceExample(body.phrase || body.sentence || body.item || body);
    const current = await loadUserPhrases(env, agent.user_id, instanceId);
    const groups = current.groups.slice();
    const index = groups.findIndex((group) => group.id.toLowerCase() === groupId.toLowerCase());
    const existing = index >= 0
      ? groups[index]
      : { id: groupId, title: groupTitle, subtitle: groupSubtitle, sentences: [] };
    const sentences = Array.isArray(existing.sentences) ? existing.sentences.slice() : [];
    const key = sentenceKey(sentence);
    const existingSentenceIndex = sentences.findIndex((item) => sentenceKey(item) === key);
    if (existingSentenceIndex >= 0) {
      sentences[existingSentenceIndex] = sentence;
    } else {
      sentences.push(sentence);
    }
    const updated = {
      ...existing,
      title: existing.title || groupTitle,
      subtitle: existing.subtitle || groupSubtitle,
      sentences,
    };
	    if (index >= 0) groups[index] = updated;
	    else groups.unshift(updated);
	    await upsertUserPhraseGroup(env, agent.user_id, instanceId, updated, index >= 0 ? index : 0);
	    await safeNotifyContentRefresh(env, {
	      instanceId,
	      userId: agent.user_id,
	      type: "user_content_refresh",
	      reason: "agent.phrases.upsert",
	    });
	    return json({
      ok: true,
      action: existingSentenceIndex >= 0 ? "replace_phrase" : "add_phrase",
      instanceId,
      group: updated,
      phrase: sentence,
      quota,
    });
  }
  if (request.method === "DELETE") {
    const body = await readOptionalJson(request);
    const instanceId = normalizeAgentInstanceId(body.instanceId || new URL(request.url).searchParams.get("instanceId"), agent);
    const groupId = normalizePhraseGroupId(body.groupId || new URL(request.url).searchParams.get("groupId"));
    const phraseKey = cleanString(body.phraseKey || body.key || new URL(request.url).searchParams.get("phraseKey"));
    const pl = cleanString(body.pl || body.target || new URL(request.url).searchParams.get("pl"));
    const en = cleanString(body.en || body.source || new URL(request.url).searchParams.get("en"));
	    if (!phraseKey && !pl && !en) {
	      await env.DB.prepare(`
	        UPDATE user_phrase_groups
	        SET deleted_at = datetime('now'), updated_at = datetime('now')
	        WHERE user_id = ? AND instance_id = ? AND group_id = ? AND deleted_at IS NULL
	      `).bind(agent.user_id, instanceId, groupId).run();
	      await safeNotifyContentRefresh(env, {
	        instanceId,
	        userId: agent.user_id,
	        type: "user_content_refresh",
	        reason: "agent.phrases.delete_group",
	      });
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
	    await safeNotifyContentRefresh(env, {
	      instanceId,
	      userId: agent.user_id,
	      type: "user_content_refresh",
	      reason: "agent.phrases.delete_phrase",
	    });
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
    const instanceId = normalizeAgentInstanceId(new URL(request.url).searchParams.get("instanceId"), agent);
    return json({ ok: true, instanceId, words: await loadUserWords(env, agent.user_id, instanceId), quota });
  }
  if (request.method === "POST") {
    const body = await readJsonLimited(request, MAX_AGENT_BODY_CHARS);
    const instanceId = normalizeAgentInstanceId(body.instanceId, agent);
	    const wordType = normalizeWordType(body.type || body.wordType || body.section);
	    const item = normalizeAgentWordItem(wordType, body.item || body.word || body);
	    await upsertUserWord(env, agent.user_id, instanceId, wordType, item);
	    await safeNotifyContentRefresh(env, {
	      instanceId,
	      userId: agent.user_id,
	      type: "user_content_refresh",
	      reason: "agent.words.upsert",
	    });
	    return json({ ok: true, action: "upsert_word", instanceId, type: wordType, item, quota });
  }
  if (request.method === "DELETE") {
    const body = await readOptionalJson(request);
    const params = new URL(request.url).searchParams;
    const instanceId = normalizeAgentInstanceId(body.instanceId || params.get("instanceId"), agent);
    const wordType = normalizeWordType(body.type || body.wordType || body.section || params.get("type"));
    const lemma = boundedString(body.lemma || params.get("lemma"), "lemma", 200);
	    await env.DB.prepare(`
	      UPDATE user_custom_words
	      SET deleted_at = datetime('now'), updated_at = datetime('now')
	      WHERE user_id = ? AND instance_id = ? AND word_type = ? AND lower(lemma) = lower(?)
	    `).bind(agent.user_id, instanceId, wordType, lemma).run();
	    await safeNotifyContentRefresh(env, {
	      instanceId,
	      userId: agent.user_id,
	      type: "user_content_refresh",
	      reason: "agent.words.delete",
	    });
	    return json({ ok: true, action: "delete_word", instanceId, type: wordType, lemma, quota });
  }
  return json({ error: "method not allowed" }, 405);
}

async function upsertUserPhraseGroup(env, userId, instanceId, group, sortOrder) {
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
  return normalizeInstanceId(value || agent.default_instance_id || "langbangml-en-pl");
}

function normalizePhraseGroupId(value) {
  const id = cleanString(value);
  if (!/^[A-Za-z0-9._-]{1,120}$/.test(id)) {
    throw new HttpError(400, "valid groupId is required");
  }
  return id;
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

function base64UrlEncodeJson(value) {
  return base64UrlEncodeBytes(new TextEncoder().encode(JSON.stringify(value)));
}

function base64UrlEncodeBytes(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
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
  return {
    id,
    title,
    subtitle: boundedString(raw.subtitle || "", "group.subtitle", 400, true),
    sentences: sentences.map(normalizeSentenceExample),
  };
}

function normalizeSentenceExample(raw) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    throw new HttpError(400, "sentence object is required");
  }
  const pl = boundedString(raw.pl, "sentence.pl", 1200);
  const en = boundedString(raw.en, "sentence.en", 1200);
  const literal = boundedString(raw.literal || "", "sentence.literal", 1600, true);
  const out = { pl, en };
  if (literal) out.literal = literal;
  if (Array.isArray(raw.words)) {
    const words = raw.words.map(normalizeTokenPair).filter(Boolean);
    if (words.length > 0) out.words = words.slice(0, 200);
  }
  return out;
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

async function completePhrase(request, env) {
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

async function geminiGenerateText(env, prompt, model = DEFAULT_GEMINI_MODEL) {
  return geminiTextFromRaw(await geminiGenerateRaw(env, prompt, model));
}

async function geminiGenerateRaw(env, prompt, model = DEFAULT_GEMINI_MODEL) {
  const key = env.GEMINI_API_KEY;
  if (!key) throw new HttpError(503, "GEMINI_API_KEY is not configured");
  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(key)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: { temperature: 0.1, responseMimeType: "application/json" },
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
    "Fill any missing source, target, and literal fields. If the user provided more than " +
    "one field, verify that the provided values describe the same phrase. If they conflict " +
    "semantically, grammatically, or the literal gloss does not match the target word order, " +
    "return consistent:false and explain the problem in issue. Do not silently accept " +
    "contradictory fields. Minor punctuation, capitalization, or literal-gloss precision " +
    "fixes are allowed when consistent remains true. " +
    "The phrase must be common, natural learner language in both languages, not merely " +
    "grammatical. Reject awkward, rare, contrived, translated-sounding, or coverage-driven " +
    "phrases. Examples that fail: \"I am listening about a sick cat\", \"health doctor\", " +
    "\"difficult weather\", \"important key\", \"easy gift\", \"quick book\", \"long coffee\", " +
    "and any Polish target that contains the English pronoun \"Y'all\". " +
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

async function safeNotifyContentRefresh(env, options) {
  try {
    return await notifyContentRefresh(env, options);
  } catch (_) {
    return { ok: false, sent: 0 };
  }
}

async function safeNotifyContentVersionRefresh(env, contentVersionId, options = {}) {
  try {
    const rows = await env.DB.prepare(`
      SELECT id
      FROM app_instances
      WHERE content_version_id = ? AND active = 1
    `).bind(contentVersionId).all();
    const results = [];
    for (const row of rows.results || []) {
      results.push(await notifyContentRefresh(env, {
        ...options,
        instanceId: row.id,
        type: "content_refresh",
        contentVersionId,
      }));
    }
    return { ok: true, results };
  } catch (_) {
    return { ok: false, results: [] };
  }
}

async function notifyContentRefresh(env, {
  instanceId,
  userId = "",
  type = "content_refresh",
  reason = "content.refresh",
  contentVersionId = "",
  dryRun = false,
  limit = MAX_PUSH_SEND_TOKENS,
} = {}) {
  const normalizedInstanceId = normalizeInstanceId(instanceId || "langbangml-en-pl");
  const normalizedType = normalizePushType(type);
  const boundedLimit = Math.max(1, Math.min(MAX_PUSH_SEND_TOKENS, Number.isFinite(limit) ? Math.trunc(limit) : MAX_PUSH_SEND_TOKENS));
  const params = [normalizedInstanceId];
  let userSql = "";
  const cleanedUserId = cleanString(userId);
  if (cleanedUserId) {
    userSql = "AND user_id = ?";
    params.push(cleanedUserId);
  }
  params.push(boundedLimit);
  const rows = await env.DB.prepare(`
    SELECT token_hash, token, user_id, instance_id
    FROM push_device_tokens
    WHERE enabled = 1
      AND instance_id = ?
      ${userSql}
    ORDER BY last_seen_at DESC
    LIMIT ?
  `).bind(...params).all();
  const tokens = rows.results || [];
  const configured = fcmConfigured(env);
  const data = {
    type: normalizedType,
    instanceId: normalizedInstanceId,
    reason: boundedString(reason || "content.refresh", "reason", 120, true) || "content.refresh",
    includeUserContent: String(normalizedType === "user_content_refresh"),
    contentVersionId: cleanString(contentVersionId),
  };
  if (dryRun || !configured) {
    return {
      dryRun,
      configured,
      matched: tokens.length,
      sent: 0,
      skipped: tokens.length,
      type: normalizedType,
      instanceId: normalizedInstanceId,
    };
  }
  let sent = 0;
  let failed = 0;
  let disabled = 0;
  for (const token of tokens) {
    const result = await sendFcmDataMessage(env, token.token, data);
    if (result.ok) {
      sent += 1;
      await env.DB.prepare(`
        UPDATE push_device_tokens
        SET last_sent_at = datetime('now'), last_error = NULL
        WHERE token_hash = ?
      `).bind(token.token_hash).run();
    } else {
      failed += 1;
      if (result.invalidToken) disabled += 1;
      await env.DB.prepare(`
        UPDATE push_device_tokens
        SET enabled = CASE WHEN ? THEN 0 ELSE enabled END,
            last_error = ?,
            last_seen_at = datetime('now')
        WHERE token_hash = ?
      `).bind(result.invalidToken ? 1 : 0, result.error || `FCM HTTP ${result.status || 0}`, token.token_hash).run();
    }
    await recordPushSendEvent(env, token, data, result);
  }
  return {
    configured,
    matched: tokens.length,
    sent,
    failed,
    disabled,
    type: normalizedType,
    instanceId: normalizedInstanceId,
  };
}

function normalizePushType(value) {
  const type = cleanString(value);
  if (type === "user_content_refresh" || type === "content_refresh") return type;
  throw new HttpError(400, "unsupported push type", { type });
}

function fcmConfigured(env) {
  return Boolean(
    cleanString(env.FCM_PROJECT_ID) &&
    cleanString(env.FCM_SERVICE_ACCOUNT_EMAIL) &&
    cleanString(env.FCM_SERVICE_ACCOUNT_PRIVATE_KEY)
  );
}

async function sendFcmDataMessage(env, token, data) {
  try {
    const accessToken = await fcmAccessToken(env);
    const response = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(env.FCM_PROJECT_ID)}/messages:send`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token,
          data: Object.fromEntries(
            Object.entries(data)
              .filter(([, value]) => value !== undefined && value !== null)
              .map(([key, value]) => [key, String(value)])
          ),
          android: {
            priority: "HIGH",
            ttl: "3600s",
          },
        },
      }),
    });
    const raw = await response.text();
    if (response.ok) return { ok: true, status: response.status, response: raw.slice(0, 500) };
    const invalidToken = response.status === 404 || /UNREGISTERED|INVALID_ARGUMENT|registration token/i.test(raw);
    return { ok: false, status: response.status, error: raw.slice(0, 500), invalidToken };
  } catch (error) {
    return { ok: false, status: 0, error: error?.message || String(error), invalidToken: false };
  }
}

async function fcmAccessToken(env) {
  const nowMs = Date.now();
  if (fcmAccessTokenCache && fcmAccessTokenCache.expiresAtMs > nowMs + 60000) {
    return fcmAccessTokenCache.token;
  }
  if (!fcmConfigured(env)) throw new HttpError(503, "FCM service account is not configured");
  const now = Math.floor(nowMs / 1000);
  const header = base64UrlEncodeJson({ alg: "RS256", typ: "JWT" });
  const claims = base64UrlEncodeJson({
    iss: env.FCM_SERVICE_ACCOUNT_EMAIL,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  });
  const signingInput = `${header}.${claims}`;
  const signature = await signServiceAccountJwt(signingInput, env.FCM_SERVICE_ACCOUNT_PRIVATE_KEY);
  const assertion = `${signingInput}.${signature}`;
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const raw = await response.text();
  if (!response.ok) throw new HttpError(502, `FCM token exchange HTTP ${response.status}`, { body: raw.slice(0, 500) });
  const parsed = JSON.parse(raw);
  if (!parsed.access_token) throw new HttpError(502, "FCM token exchange returned no access_token");
  fcmAccessTokenCache = {
    token: parsed.access_token,
    expiresAtMs: nowMs + Math.max(60, Number(parsed.expires_in || 3600) - 60) * 1000,
  };
  return fcmAccessTokenCache.token;
}

async function signServiceAccountJwt(input, privateKeyPem) {
  const pem = String(privateKeyPem || "").replace(/\\n/g, "\n");
  const base64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  const key = await crypto.subtle.importKey(
    "pkcs8",
    bytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(input),
  );
  return base64UrlEncodeBytes(new Uint8Array(signature));
}

async function recordPushSendEvent(env, token, data, result) {
  try {
    await env.DB.prepare(`
      INSERT INTO push_send_events
        (id, token_hash, user_id, instance_id, push_type, reason, status, response_json, error)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
      crypto.randomUUID(),
      token.token_hash,
      token.user_id || null,
      token.instance_id,
      data.type,
      data.reason,
      result.status || 0,
      result.response || "",
      result.ok ? "" : (result.error || ""),
    ).run();
  } catch (_) {
    // Push audit logging must not block content sync.
  }
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
	  await safeNotifyContentVersionRefresh(env, lesson.contentVersionId, {
	    reason: eventType,
	    lessonId: lesson.lessonId,
	  });
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
  const body = await request.json();
  const phrases = Array.isArray(body.phrases) ? body.phrases : [];
  if (phrases.length === 0) {
    return json({ error: "phrases[] required" }, 400);
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
  const sourceUrl = sourceCodeUrl(env);
  const addPhrase = JSON.stringify({
    instanceId: "langbangml-en-pl",
    groupId: "my-phrases",
    groupTitle: "My phrases",
    phrase: {
      pl: "Lubię uczyć się polskiego.",
      en: "I like learning Polish.",
      literal: "I-like to-learn self Polish.",
    },
  }, null, 2);
  const addNoun = JSON.stringify({
    instanceId: "langbangml-en-pl",
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
    instanceId: "langbangml-en-pl",
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
  <title>LangBangML Agent API</title>
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
    <h1>LangBangML Agent API</h1>
    <p class="muted">Use this with the personal token copied from the Android Settings screen.</p>
  </header>
  <main>
    <section class="warn">
      <h2>Agent Setup Prompt</h2>
      <p>Paste this into Claude, Codex, or another coding agent together with the token from the app:</p>
      <pre>Use the LangBangML Agent API to edit my personal study content.
API base: ${escapeHtml(apiBase)}
Instructions: ${escapeHtml(apiBase)}/agent
Token: PASTE_TOKEN_HERE

Use Authorization: Bearer PASTE_TOKEN_HERE on every /v1/agent request.
Do not print or store the token in project files, git commits, logs, or screenshots.
Daily limit: ${agentDailyLimit(env)} authenticated agent API calls per token.
Use app-native JSON fields: phrases use pl/en/literal; words use lemma/en and the type-specific forms below.
Never ask for the LangBangML admin content token.</pre>
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
        <li><code>POST /v1/agent/phrases</code> adds or replaces one phrase sentence in a personal phrase group.</li>
        <li><code>DELETE /v1/agent/phrases</code> deletes a phrase sentence, or deletes a group when no phrase is provided.</li>
        <li><code>POST /v1/agent/words</code> adds or replaces a personal word in Verbs, Nouns, Adj, or Adv.</li>
        <li><code>DELETE /v1/agent/words</code> deletes a personal word by type and lemma.</li>
      </ul>
      <p class="muted">All edits are user-owned content tied to the signed-in app account. They do not mutate global LangBangML lessons.</p>
    </section>
    <section>
      <h2>In-App AI Phrase Generation</h2>
      <p>The Android app also has a built-in AI phrase generator under Phrases +. It uses the same Worker-side Gemini Flash path, so a learner does not need Claude, Codex, or their own API key for simple phrase creation.</p>
      <p>Default account quota: <code>${DEFAULT_AI_PHRASE_QUOTA}</code> generated custom phrases. When that quota is reached, the app can send a quota request email to <code>${escapeHtml(env.AI_PHRASE_QUOTA_EMAIL || DEFAULT_AI_PHRASE_QUOTA_EMAIL)}</code>.</p>
    </section>
    <section>
      <h2>Examples</h2>
      <h3>Add A Phrase</h3>
      <pre>curl -sS ${escapeHtml(apiBase)}/v1/agent/phrases \\
  -H "Authorization: Bearer $LANGBANGML_AGENT_TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '${escapeHtml(addPhrase)}'</pre>
      <h3>Delete A Phrase</h3>
      <pre>curl -sS -X DELETE ${escapeHtml(apiBase)}/v1/agent/phrases \\
  -H "Authorization: Bearer $LANGBANGML_AGENT_TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '{"instanceId":"langbangml-en-pl","groupId":"my-phrases","pl":"Lubię uczyć się polskiego."}'</pre>
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
      <pre>curl -sS -X DELETE "${escapeHtml(apiBase)}/v1/agent/words?instanceId=langbangml-en-pl&type=noun&lemma=ogr%C3%B3d" \\
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

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
  };
}
