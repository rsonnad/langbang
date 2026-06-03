const SLOW_50_SUFFIX = "|slow50v3";
const SLOW_60_SUFFIX = "|slow60v1";
const SLOW_ART_SUFFIX = "|slowart1";
const DEFAULT_AUDIO_WARM_LIMIT = 40;
const MAX_AUDIO_WARM_LIMIT = 80;
const DEFAULT_GEMINI_MODEL = "gemini-3.5-flash";
const MAX_GEMINI_PROMPT_CHARS = 20000;
const MAX_PHRASE_FIELD_CHARS = 600;

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
      if (request.method === "GET" && path === "/v1/instances") {
        return listInstances(env);
      }
      if (request.method === "POST" && path === "/v1/gemini/generate") {
        return geminiGenerate(request, env);
      }
      if (request.method === "POST" && path === "/v1/phrases/complete") {
        return completePhrase(request, env);
      }
      const adminAudioWarmMatch = path.match(/^\/v1\/admin\/content\/([^/]+)\/audio\/warm-missing$/);
      if (request.method === "POST" && adminAudioWarmMatch) {
        requireAdmin(request, env);
        return warmMissingAudio(request, env, decodeURIComponent(adminAudioWarmMatch[1]));
      }
      const adminLessonsMatch = path.match(/^\/v1\/admin\/content\/([^/]+)\/lessons$/);
      if (request.method === "GET" && adminLessonsMatch) {
        requireAdmin(request, env);
        return adminLessons(env, decodeURIComponent(adminLessonsMatch[1]));
      }
      const adminLessonItemsMatch = path.match(/^\/v1\/admin\/content\/([^/]+)\/lessons\/([^/]+)\/items$/);
      if (adminLessonItemsMatch) {
        requireAdmin(request, env);
        return adminLessonItems(request, env, decodeURIComponent(adminLessonItemsMatch[1]), decodeURIComponent(adminLessonItemsMatch[2]));
      }
      const adminLessonReorderMatch = path.match(/^\/v1\/admin\/content\/([^/]+)\/lessons\/([^/]+)\/reorder$/);
      if (request.method === "POST" && adminLessonReorderMatch) {
        requireAdmin(request, env);
        return adminReorderItems(request, env, decodeURIComponent(adminLessonReorderMatch[1]), decodeURIComponent(adminLessonReorderMatch[2]));
      }
      const adminLessonMatch = path.match(/^\/v1\/admin\/content\/([^/]+)\/lessons\/([^/]+)$/);
      if (request.method === "GET" && adminLessonMatch) {
        requireAdmin(request, env);
        return adminLesson(env, decodeURIComponent(adminLessonMatch[1]), decodeURIComponent(adminLessonMatch[2]));
      }
      const bootstrapMatch = path.match(/^\/v1\/instances\/([^/]+)\/bootstrap$/);
      if (request.method === "GET" && bootstrapMatch) {
        return bootstrap(env, decodeURIComponent(bootstrapMatch[1]));
      }
      const labelsMatch = path.match(/^\/v1\/labels\/([^/]+)$/);
      if (request.method === "GET" && labelsMatch) {
        return labelsFor(env, decodeURIComponent(labelsMatch[1]));
      }
      if (request.method === "POST" && path === "/v1/audio/manifest") {
        return audioManifest(request, env);
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
  return json(normalizePhraseCompletion(parseGeminiJsonText(text)));
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

function parseJson(raw, fallback) {
  try {
    return JSON.parse(raw || "");
  } catch {
    return fallback;
  }
}

async function sha1Hex(input) {
  const bytes = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-1", bytes);
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

function json(data, status = 200) {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", ...corsHeaders() },
  });
}

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
  };
}
