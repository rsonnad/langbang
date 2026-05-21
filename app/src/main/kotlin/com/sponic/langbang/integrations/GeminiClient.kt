package com.sponic.langbang.integrations

import com.sponic.langbang.BuildConfig
import com.sponic.langbang.data.model.AdjectiveEntry
import com.sponic.langbang.data.model.AdverbEntry
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.data.model.TokenPair
import com.sponic.langbang.data.model.VerbEntry
import com.sponic.langbang.domain.UsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Calls Google Gemini's REST `generateContent` endpoint to translate an English verb to its
 * Polish infinitive and produce all six present-tense forms. We ask Gemini to return JSON
 * only so parsing is a single decode.
 */
class GeminiClient(
    private val usage: UsageTracker? = null
) {

    private val key = BuildConfig.GEMINI_API_KEY
    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun translateVerb(englishInfinitive: String): Result<VerbEntry> =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("GEMINI_API_KEY not set in local.properties")
                )
            }
            try {
                val raw = postPrompt(buildPrompt(englishInfinitive))
                Result.success(parseResponse(raw, englishInfinitive))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    /**
     * Asks Gemini for 20 short example sentences using [verb]. Sentences favour very common
     * vocabulary so a beginner can read them, restricted to [allowedPersonKeys] — only
     * subjects/conjugations matching those person keys (e.g. "1sg", "2sg") will appear.
     * Empty or null means "all forms allowed".
     */
    suspend fun generateSentences(
        verb: VerbEntry,
        allowedPersonKeys: Set<String>? = null
    ): Result<List<SentenceExample>> =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("GEMINI_API_KEY not set in local.properties")
                )
            }
            try {
                val raw = postPrompt(buildSentencePrompt(verb, allowedPersonKeys))
                Result.success(parseSentenceResponse(raw))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    /**
     * Translate an English adjective and produce its full nominative + accusative paradigm.
     */
    suspend fun translateAdjective(englishAdjective: String): Result<AdjectiveEntry> =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("GEMINI_API_KEY not set in local.properties")
                )
            }
            try {
                val raw = postPrompt(buildAdjectivePrompt(englishAdjective))
                Result.success(parseAdjectiveResponse(raw, englishAdjective))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    /**
     * Asks Gemini for 20 short example sentences that combine a common verb + the adjective
     * + a noun (e.g. "I see a big table" / "Widzę duży stół"). Sentences are intentionally
     * built around the verbs the learner already knows from Lesson 2.
     */
    suspend fun generateAdjectiveSentences(adjective: AdjectiveEntry): Result<List<SentenceExample>> =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("GEMINI_API_KEY not set in local.properties")
                )
            }
            try {
                val raw = postPrompt(buildAdjectiveSentencePrompt(adjective))
                Result.success(parseSentenceResponse(raw))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private fun postPrompt(prompt: String): String {
        val conn = URL("$endpoint?key=$key").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("Content-Type", "application/json")

        conn.outputStream.use {
            it.write(buildRequestBody(prompt).toByteArray(Charsets.UTF_8))
        }

        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw RuntimeException("Gemini ${conn.responseCode}: $err")
        }
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        recordUsageFromResponse(raw)
        return raw
    }

    // Gemini returns usageMetadata.{promptTokenCount, totalTokenCount, …} on every
    // successful call. We derive output = total - prompt so thinking tokens
    // (which 2.5-flash bills as output) are included automatically. Wrapped in
    // try/catch so a malformed usage block never breaks an otherwise good response.
    private fun recordUsageFromResponse(raw: String) {
        val tracker = usage ?: return
        try {
            val meta = json.parseToJsonElement(raw).jsonObject["usageMetadata"]?.jsonObject
                ?: return
            val prompt = meta["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
            val total = meta["totalTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
            val output = (total - prompt).coerceAtLeast(0)
            tracker.recordGeminiTokens(prompt, output)
        } catch (_: Throwable) {
        }
    }

    private fun buildPrompt(english: String): String =
        "Translate the English infinitive verb \"$english\" into Polish, then conjugate it " +
        "in BOTH the present tense and the past tense. Return ONLY a JSON object with this " +
        "exact shape (no prose, no markdown fence): " +
        "{\"lemma\":\"polish_infinitive\",\"en\":\"to $english\"," +
        "\"forms\":{\"1sg\":\"\",\"2sg\":\"\",\"3sg\":\"\",\"1pl\":\"\",\"2pl\":\"\",\"3pl\":\"\"}," +
        "\"past_forms\":{\"1sg\":\"\",\"2sg\":\"\",\"3sg\":\"\",\"1pl\":\"\",\"2pl\":\"\",\"3pl\":\"\"}}. " +
        "For past_forms use the MASCULINE-SINGULAR forms for 1sg/2sg/3sg (e.g. byłem, byłeś, " +
        "był) and VIRILE (masculine-personal) plural forms for 1pl/2pl/3pl (e.g. byliśmy, " +
        "byliście, byli). Use lowercase Polish with correct diacritics. The lemma must be " +
        "the standard imperfective infinitive when one exists."

    private fun buildRequestBody(prompt: String): String {
        val escaped = prompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return """
            {
              "contents":[{"parts":[{"text":"$escaped"}]}],
              "generationConfig":{"temperature":0.1,"responseMimeType":"application/json"}
            }
        """.trimIndent()
    }

    private fun buildSentencePrompt(
        verb: VerbEntry,
        allowedPersonKeys: Set<String>?
    ): String {
        val effectiveKeys = allowedPersonKeys
            ?.takeIf { it.isNotEmpty() }
            ?: verb.forms.keys
        val allowedForms = verb.forms.filterKeys { it in effectiveKeys }
        val formsBlurb = allowedForms.entries.joinToString(", ") { "${it.key}=${it.value}" }
        return "Write 20 very simple example Polish sentences that use the verb \"" +
            "${verb.lemma}\" (English: ${verb.en}). " +
            "Use only the most common everyday vocabulary that a beginner would know " +
            "(food, family, basic objects, places). " +
            "STRICT REQUIREMENT: every sentence's verb must be one of these conjugated " +
            "forms — do NOT use any other conjugation: $formsBlurb. " +
            "Vary which of those allowed forms you use across the 20 sentences. " +
            "Keep each Polish sentence to 5 words or fewer. " +
            "For each sentence, return THREE fields: " +
            "(1) \"pl\": the Polish sentence with correct diacritics and natural capitalization. " +
            "(2) \"en\": a GRAMMATICALLY CORRECT, NATURAL English translation. " +
            "    Use proper English articles (a / an / the) where English requires them — " +
            "    e.g. \"To jest stół\" → \"This is a table\", NOT \"This is table\". " +
            "    Use natural English prepositions — e.g. \"Jestem w domu\" → \"I am at home\", " +
            "    NOT \"I am in home\". Do NOT paraphrase to a different scene; keep the same " +
            "    subject and meaning, just render it in idiomatic English. " +
            "(3) \"literal\": a WORD-FOR-WORD gloss that preserves the Polish word order and " +
            "    matches each Polish word to its closest English equivalent. Skip articles " +
            "    that don't exist in the Polish (Polish has no a/an/the). Keep the literal " +
            "    Polish prepositions — e.g. \"Jestem w domu\" → \"I-am in home\"; " +
            "    \"To jest stół\" → \"This is table\"; \"On pije wodę\" → \"He drinks water\". " +
            "    This lets the learner see what each Polish word means individually. " +
            "(4) \"words\": an ARRAY of per-token mappings in the same left-to-right order " +
            "    as the Polish sentence. Each element is {\"pl\":\"polish-token\",\"en\":" +
            "    \"english-gloss\"}. Tokens correspond to whitespace-separated Polish words " +
            "    (collapse contractions into one token). When a Polish word maps to a multi-" +
            "    word English gloss use a hyphen (\"I-am\"). The number of \"words\" entries " +
            "    MUST equal the Polish word count exactly. " +
            "Return ONLY a JSON array (no prose, no markdown fence) where each element has " +
            "the exact shape {\"pl\":\"...\",\"en\":\"...\",\"literal\":\"...\"," +
            "\"words\":[{\"pl\":\"...\",\"en\":\"...\"}]}."
    }

    private fun parseSentenceResponse(raw: String): List<SentenceExample> {
        val root = json.parseToJsonElement(raw).jsonObject
        val candidates = root["candidates"] as? JsonArray ?: error("Gemini: candidates missing")
        val first = candidates.firstOrNull()?.jsonObject ?: error("Gemini: empty candidates")
        val parts = first["content"]?.jsonObject?.get("parts") as? JsonArray
            ?: error("Gemini: parts missing")
        val text = parts.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: error("Gemini: text missing")
        val arr = json.parseToJsonElement(text.trim()).jsonArray
        val sentences = arr.mapNotNull { el ->
            val obj = el.jsonObject
            val pl = obj["pl"]?.jsonPrimitive?.content?.trim().orEmpty()
            val en = obj["en"]?.jsonPrimitive?.content?.trim().orEmpty()
            val literal = obj["literal"]?.jsonPrimitive?.content?.trim()
                ?.takeIf { it.isNotEmpty() }
            val words = (obj["words"] as? JsonArray)?.mapNotNull { w ->
                val wo = w.jsonObject
                val wpl = wo["pl"]?.jsonPrimitive?.content?.trim().orEmpty()
                val wen = wo["en"]?.jsonPrimitive?.content?.trim().orEmpty()
                if (wpl.isEmpty()) null else TokenPair(wpl, wen)
            }?.takeIf { it.isNotEmpty() }
            if (pl.isEmpty() || en.isEmpty()) null
            else SentenceExample(pl, en, literal, words)
        }
        require(sentences.isNotEmpty()) { "Gemini returned no usable sentences" }
        return sentences.take(20)
    }

    private fun buildAdjectivePrompt(english: String): String =
        "Translate the English adjective \"$english\" into Polish, then give its full " +
        "nominative and accusative paradigm. Return ONLY a JSON object with this exact " +
        "shape (no prose, no markdown fence): " +
        "{\"lemma\":\"polish_masc_nom\",\"en\":\"$english\"," +
        "\"nom\":{\"m\":\"\",\"f\":\"\",\"n\":\"\",\"mp\":\"\",\"other\":\"\"}," +
        "\"acc\":{\"m\":\"\",\"f\":\"\",\"n\":\"\",\"mp\":\"\",\"other\":\"\"}}. " +
        "Use lowercase Polish forms with correct diacritics. \"mp\" is the masculine-personal " +
        "(virile) plural form (e.g. dobrzy). \"other\" is the non-masculine-personal plural " +
        "(e.g. dobre). For the masculine accusative (\"acc.m\") use the animate form " +
        "(genitive-like, e.g. dobrego) — the inanimate form is identical to the nominative " +
        "and does not need a separate entry."

    private fun buildAdjectiveSentencePrompt(adj: AdjectiveEntry): String {
        val nomBlurb = adj.nom.entries.joinToString(", ") { "${it.key}=${it.value}" }
        val accBlurb = adj.acc.entries.joinToString(", ") { "${it.key}=${it.value}" }
        return "Write 20 very simple example Polish sentences that use the adjective \"" +
            "${adj.lemma}\" (English: ${adj.en}). " +
            "Each sentence must combine a common Polish verb (e.g. mieć, widzieć, lubić, " +
            "kupować, czytać, jeść) with the adjective and a beginner-level noun (e.g. stół, " +
            "dom, kot, pies, książka, kawa, jabłko). The English side should read like " +
            "'I see a big table' or 'She has a small dog'. " +
            "Make the adjective agree correctly with the noun's gender and case " +
            "(nominative: $nomBlurb; accusative: $accBlurb). Use the accusative form when the " +
            "noun is the direct object of the verb (most of these examples). " +
            "Vary the subject pronoun across sentences. Keep each sentence to 6 words or fewer. " +
            "Return ONLY a JSON array (no prose, no markdown fence) where each element has " +
            "the exact shape {\"pl\":\"polish sentence\",\"en\":\"english translation\"}. " +
            "Polish sentences must use correct diacritics and natural capitalization."
    }

    private fun parseAdjectiveResponse(raw: String, englishFallback: String): AdjectiveEntry {
        val root = json.parseToJsonElement(raw).jsonObject
        val candidates = root["candidates"] as? JsonArray ?: error("Gemini: candidates missing")
        val first = candidates.firstOrNull()?.jsonObject ?: error("Gemini: empty candidates")
        val parts = first["content"]?.jsonObject?.get("parts") as? JsonArray
            ?: error("Gemini: parts missing")
        val text = parts.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: error("Gemini: text missing")
        val inner = json.parseToJsonElement(text.trim()).jsonObject
        val adj = AdjectiveEntry(
            lemma = inner["lemma"]?.jsonPrimitive?.content?.trim().orEmpty(),
            en = inner["en"]?.jsonPrimitive?.content?.trim() ?: englishFallback,
            nom = inner["nom"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }
                ?: emptyMap(),
            acc = inner["acc"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }
                ?: emptyMap()
        )
        require(adj.lemma.isNotEmpty()) { "Gemini returned empty adjective lemma" }
        require(adj.nom.size == 5) { "Gemini returned ${adj.nom.size} nom forms, expected 5" }
        require(adj.acc.size == 5) { "Gemini returned ${adj.acc.size} acc forms, expected 5" }
        return adj
    }

    private fun parseResponse(raw: String, englishFallback: String): VerbEntry {
        val root = json.parseToJsonElement(raw).jsonObject
        val candidates = root["candidates"] as? JsonArray ?: error("Gemini: candidates missing")
        val first = candidates.firstOrNull()?.jsonObject ?: error("Gemini: empty candidates")
        val parts = first["content"]?.jsonObject?.get("parts") as? JsonArray
            ?: error("Gemini: parts missing")
        val text = parts.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: error("Gemini: text missing")
        val inner = json.parseToJsonElement(text.trim()).jsonObject
        val pastForms = inner["past_forms"]?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.content }
            ?.takeIf { it.size == 6 }
        val verb = VerbEntry(
            lemma = inner["lemma"]?.jsonPrimitive?.content?.trim().orEmpty(),
            en = inner["en"]?.jsonPrimitive?.content?.trim() ?: "to $englishFallback",
            forms = inner["forms"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }
                ?: emptyMap(),
            past_forms = pastForms
        )
        require(verb.lemma.isNotEmpty()) { "Gemini returned empty lemma" }
        require(verb.forms.size == 6) { "Gemini returned ${verb.forms.size} forms, expected 6" }
        return verb
    }

    // ── Adverbs ───────────────────────────────────────────────────────────────

    suspend fun translateAdverb(englishAdverb: String): Result<AdverbEntry> =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("GEMINI_API_KEY not set in local.properties")
                )
            }
            try {
                val raw = postPrompt(buildAdverbPrompt(englishAdverb))
                Result.success(parseAdverbResponse(raw, englishAdverb))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    suspend fun generateAdverbSentences(adverb: AdverbEntry): Result<List<SentenceExample>> =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("GEMINI_API_KEY not set in local.properties")
                )
            }
            try {
                val raw = postPrompt(buildAdverbSentencePrompt(adverb))
                Result.success(parseSentenceResponse(raw))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private fun buildAdverbPrompt(english: String): String =
        "Translate the English adverb \"$english\" into Polish. Polish adverbs are " +
        "uninflected — one form only. Return ONLY a JSON object with this exact shape " +
        "(no prose, no markdown fence): " +
        "{\"lemma\":\"polish_adverb\",\"en\":\"$english\"}. " +
        "Use lowercase Polish with correct diacritics."

    private fun buildAdverbSentencePrompt(adv: AdverbEntry): String =
        "Write 20 very simple example Polish sentences that use the adverb \"${adv.lemma}\" " +
        "(English: ${adv.en}). Each sentence must combine the adverb with a common Polish " +
        "verb (e.g. iść, robić, jeść, lubić, mieć) and beginner vocabulary. Vary the " +
        "subject pronoun. Keep each sentence to 6 words or fewer. " +
        "Return ONLY a JSON array (no prose, no markdown fence) where each element has " +
        "the shape {\"pl\":\"polish sentence\",\"en\":\"english translation\"," +
        "\"literal\":\"word-for-word gloss\"}."

    private fun parseAdverbResponse(raw: String, englishFallback: String): AdverbEntry {
        val root = json.parseToJsonElement(raw).jsonObject
        val candidates = root["candidates"] as? JsonArray ?: error("Gemini: candidates missing")
        val first = candidates.firstOrNull()?.jsonObject ?: error("Gemini: empty candidates")
        val parts = first["content"]?.jsonObject?.get("parts") as? JsonArray
            ?: error("Gemini: parts missing")
        val text = parts.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: error("Gemini: text missing")
        val inner = json.parseToJsonElement(text.trim()).jsonObject
        val adv = AdverbEntry(
            lemma = inner["lemma"]?.jsonPrimitive?.content?.trim().orEmpty(),
            en = inner["en"]?.jsonPrimitive?.content?.trim() ?: englishFallback
        )
        require(adv.lemma.isNotEmpty()) { "Gemini returned empty adverb lemma" }
        return adv
    }
}
