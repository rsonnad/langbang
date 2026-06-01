package com.sponic.langbang.integrations

import com.sponic.langbang.BuildConfig
import com.sponic.langbang.data.model.AdjectiveEntry
import com.sponic.langbang.data.model.AdverbEntry
import com.sponic.langbang.data.model.NounEntry
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

    companion object {
        const val TENSE_PRESENT = "present"
        const val TENSE_PAST = "past"

        /**
         * Path key for the server-side sentence bundle tree in R2
         * (`langbang/sentences/v{N}/...`). Bump when ANY prompt changes so the
         * client downloader pulls from a fresh tree even if only one type bumped.
         * Cheap — Gemini calls for the whole canonical set are pennies.
         */
        const val SENTENCE_PROMPT_VERSION = 4

        /**
         * Per-type wipe versions. Bump ONE of these when its prompt changes so
         * the local on-device cache for that type gets cleared on next launch
         * without nuking caches the user already paid for. Pair with a
         * [SENTENCE_PROMPT_VERSION] bump above so the R2 tree has fresh content
         * matching the new prompt; the on-launch downloader then refills the
         * cleared store from the new tree.
         *
         * Wrong-blast-radius incident on 2026-05-28: v0.1.7.70 bumped a single
         * global `SENTENCE_PROMPT_VERSION` from 1 → 2 because the adj+adv
         * prompts added a `words` field, but the migration wiped verbs too —
         * costing the user ~45 min of on-device verb regen for content that
         * was already correct. These per-type keys make the wipe surgical.
         *
         * v4 (2026-05-31): all four sentence prompts (verb/adj/adv/noun) now ask
         * Gemini to tag each noun/pronoun/adjective token with "gender" (m/f/n)
         * and "caseKey" (nom/acc/gen/…) so the Now Voicing panel can identify
         * inflected grammar tokens. All four wipe versions bumped because
         * every prompt's `words` shape changed; the matching Edge Function + R2 v4
         * tree are regenerated in the same change.
         *
         * v3 (2026-05-28): adj+adv prompts gained the "10-year-old test" plus
         * explicit bad examples (difficult weather, important key, easy gift,
         * health doctor). Verb prompt unchanged.
         *
         * 2026-05-28 (later): all three wipe versions bumped because the parallel
         * R2 downloader in [SentenceRegenService] was racing on the per-store JSON
         * writes — only ~15 of 92 downloaded bundles actually survived on disk.
         * Mutex added in the same change; bumping the wipe versions forces every
         * device to throw away the partially-corrupted local caches and pull a
         * clean copy from R2 with the race-free path. Verb prompt content is
         * unchanged (R2 v3 verb bundles are identical bytes copied from v2), so users
         * don't get worse content — they just get all of it instead of 15 of it.
         */
        const val VERB_WIPE_VERSION = 3
        const val ADJECTIVE_WIPE_VERSION = 5
        const val ADVERB_WIPE_VERSION = 5

        /** Bumped to 2 in v4 (gender/case token tagging — see SENTENCE_PROMPT_VERSION). */
        const val NOUN_WIPE_VERSION = 2
    }

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
     *
     * [tense] selects which conjugation set to use ("present" → verb.forms, "past" →
     * verb.past_forms). Past requests fail fast if the verb has no past_forms.
     */
    suspend fun generateSentences(
        verb: VerbEntry,
        allowedPersonKeys: Set<String>? = null,
        tense: String = TENSE_PRESENT
    ): Result<List<SentenceExample>> =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("GEMINI_API_KEY not set in local.properties")
                )
            }
            if (tense == TENSE_PAST && verb.past_forms.isNullOrEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Verb ${verb.lemma} has no past_forms")
                )
            }
            try {
                val raw = postPrompt(buildSentencePrompt(verb, allowedPersonKeys, tense))
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
        allowedPersonKeys: Set<String>?,
        tense: String
    ): String {
        val isPast = tense == TENSE_PAST
        val sourceForms = if (isPast) verb.past_forms.orEmpty() else verb.forms
        val effectiveKeys = allowedPersonKeys
            ?.takeIf { it.isNotEmpty() }
            ?: sourceForms.keys
        val allowedForms = sourceForms.filterKeys { it in effectiveKeys }
        val formsBlurb = allowedForms.entries.joinToString(", ") { "${it.key}=${it.value}" }
        val tenseInstruction = if (isPast) {
            "Every sentence must be in the PAST TENSE. " +
                "STRICT REQUIREMENT: every sentence's main verb must be one of these " +
                "past-tense conjugations — do NOT use any other conjugation and do NOT " +
                "fall back to present tense: $formsBlurb. " +
                "These are the collapsed masculine-singular (1sg/2sg/3sg) and virile " +
                "masculine-personal plural (1pl/2pl/3pl) past forms. Use them exactly. " +
                "Vary which of those allowed forms you use across the 40 sentences. " +
                "The English translations must also be in the simple past tense " +
                "(e.g. \"I drank water\", not \"I drink water\"). "
        } else {
            "STRICT REQUIREMENT: every sentence's verb must be one of these conjugated " +
                "forms — do NOT use any other conjugation: $formsBlurb. " +
                "Vary which of those allowed forms you use across the 40 sentences. "
        }
        return "Write 40 very simple example Polish sentences that use the verb \"" +
            "${verb.lemma}\" (English: ${verb.en}). " +
            "EVERYDAY-PHRASE REQUIREMENT (the most important rule): every sentence must " +
            "be a HIGH-FREQUENCY phrase that adults actually say multiple times a week — " +
            "the kind of phrase that would appear in the top 500 sentences of a beginner " +
            "phrasebook. Aim for the everyday register of \"I'm hungry\", \"I'm tired\", " +
            "\"I want to eat\", \"I'm going to the restaurant\", \"I saw her at the gym\", " +
            "\"I'm cold\", \"I need coffee\", \"I have a headache\", \"do you understand\", " +
            "\"see you later\", \"I'll be right back\", \"I forgot my keys\", \"are you " +
            "ready\", \"let's go home\", \"I'm running late\". Hit body states (hungry, " +
            "thirsty, sleepy, hot, cold, tired, sick), basic intentions (want, need, going " +
            "to), common places (home, work, store, restaurant, school, gym, park), and " +
            "common objects (keys, phone, money, food, coffee, water, bread). " +
            "Use only the most common everyday vocabulary that a beginner would know " +
            "(food, family, basic objects, places, body states). " +
            "CRITICAL NATURALNESS RULE: every sentence must be something a real person " +
            "would actually say in ordinary conversation. Grammatical correctness is NOT " +
            "enough — the sentence must be idiomatic and commonplace in BOTH Polish AND " +
            "English. Before including a sentence, ask yourself: \"Have I heard a native " +
            "speaker say this in real life this week?\" If the answer is no, pick a " +
            "different one. Use the right verb collocation for the noun (you LOOK AT a " +
            "painting, you don't WATCH it; you LISTEN TO music, you don't HEAR-TO it). " +
            "REJECT contrived, textbook-flavored, or whimsical scenarios — no \"The cat " +
            "is on the table\" filler. Prefer phrases someone would actually text a " +
            "friend or say to a family member (\"I'm leaving now\", \"call me later\", " +
            "\"don't forget bread\", \"I drank coffee already\"). " +
            tenseInstruction +
            "Keep each Polish sentence to 5 words or fewer. " +
            "PREPOSITION COVERAGE: at least 10 of the 40 sentences must use a Polish " +
            "preposition. Include AT LEAST ONE sentence for each of these five common " +
            "prepositions: w (in/at), na (on/at), do (to), z (with/from), o (about). " +
            "Examples of natural preposition use: \"Jestem w domu\" (w + locative), " +
            "\"Książka leży na stole\" (na + locative), \"Idę do sklepu\" (do + genitive), " +
            "\"Rozmawiam z bratem\" (z + instrumental), \"Mówię o pogodzie\" (o + locative). " +
            "Use the correct case for each preposition. " +
            "For each sentence, return THREE fields: " +
            "(1) \"pl\": the Polish sentence with correct diacritics and natural capitalization. " +
            "(2) \"en\": a GRAMMATICALLY CORRECT, NATURAL English translation. " +
            "    Use proper English articles (a / an / the) where English requires them — " +
            "    e.g. \"To jest stół\" → \"This is a table\", NOT \"This is table\". " +
            "    Use natural English prepositions — e.g. \"Jestem w domu\" → \"I am at home\", " +
            "    NOT \"I am in home\". Do NOT paraphrase to a different scene; keep the same " +
            "    subject and meaning, just render it in idiomatic English. " +
            "    For the 2pl form (wy / 2pl), render the English subject as \"Y'all\" at " +
            "    the start of a sentence and \"y'all\" mid-sentence — NEVER write \"You " +
            "    (plural)\", \"you (pl.)\", or any \"(plural)\" annotation in the English. " +
            "    For 1pl use \"We\"; for 3pl use \"They\". " +
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
            "For each NOUN, PRONOUN, or ADJECTIVE token, ALSO include \"gender\" (\"m\", " +
            "\"f\", or \"n\") and \"caseKey\" (one of \"nom\", \"acc\", \"gen\", \"dat\", " +
            "\"inst\", \"loc\", \"voc\"); OMIT both keys for verbs, prepositions, adverbs, " +
            "and particles. " +
            "Return ONLY a JSON array (no prose, no markdown fence) where each element has " +
            "the exact shape {\"pl\":\"...\",\"en\":\"...\",\"literal\":\"...\"," +
            "\"words\":[{\"pl\":\"...\",\"en\":\"...\",\"gender\":\"m|f|n|omit\"," +
            "\"caseKey\":\"nom|acc|gen|dat|inst|loc|voc|omit\"}]}."
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
                val wgender = wo["gender"]?.jsonPrimitive?.content?.trim()?.lowercase()
                    ?.takeIf { it.isNotEmpty() }
                val wcase = wo["caseKey"]?.jsonPrimitive?.content?.trim()?.lowercase()
                    ?.takeIf { it.isNotEmpty() }
                val variableStart = wo["variableStart"]?.jsonPrimitive?.intOrNull
                val variableEnd = wo["variableEnd"]?.jsonPrimitive?.intOrNull
                val variableKind = wo["variableKind"]?.jsonPrimitive?.content?.trim()?.lowercase()
                    ?.takeIf { it.isNotEmpty() }
                if (wpl.isEmpty()) null
                else TokenPair(
                    pl = wpl,
                    en = wen,
                    gender = wgender,
                    caseKey = wcase,
                    variableStart = variableStart,
                    variableEnd = variableEnd,
                    variableKind = variableKind
                )
            }?.takeIf { it.isNotEmpty() }
            if (pl.isEmpty() || en.isEmpty()) null
            else SentenceExample(pl, en, literal, words)
        }
        require(sentences.isNotEmpty()) { "Gemini returned no usable sentences" }
        return sentences.take(40)
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
        return "Write 40 very simple example Polish sentences that use the adjective \"" +
            "${adj.lemma}\" (English: ${adj.en}). " +
            "EVERYDAY-PHRASE REQUIREMENT (the most important rule): every sentence must " +
            "be a HIGH-FREQUENCY phrase that adults actually say multiple times a week. " +
            "Aim for the everyday register of \"I want hot coffee\", \"she has a big " +
            "house\", \"I bought a new phone\", \"do you have small change\", \"I need a " +
            "warm jacket\", \"that was a long day\", \"this is good bread\". Avoid " +
            "textbook filler. " +
            "VERB POOL: use ONLY these common Polish verbs in the chosen tense/person — " +
            "mieć (to have), widzieć (to see), lubić (to like), kochać (to love), " +
            "kupować (to buy), czytać (to read), jeść (to eat), pić (to drink), " +
            "chcieć (to want), potrzebować (to need), znać (to know), pamiętać (to remember), " +
            "robić (to do), pisać (to write), mówić (to speak), słuchać (to listen to), " +
            "oglądać (to watch), iść (to go), być (to be), spotykać (to meet). " +
            "NOUN POOL: use ONLY ordinary everyday concrete nouns the adjective can " +
            "TRULY describe. Examples: kawa, herbata, woda, chleb, jabłko, obiad, " +
            "śniadanie, kolacja, dom, mieszkanie, samochód, pies, kot, książka, telefon, " +
            "kurtka, sweter, koszula, sukienka, klucze, pieniądze, prezent, ogród, " +
            "ulica, sklep, restauracja, hotel, szkoła, pokój, łóżko, stół, krzesło, " +
            "miasto, dzień, wieczór, pogoda, lekarz, sąsiad, przyjaciel, dziecko. " +
            "CRITICAL NATURALNESS RULE: each sentence must be something a real person would " +
            "actually say in ordinary conversation, in BOTH Polish AND English. Grammatical " +
            "correctness is NOT enough — the adjective must NATURALLY describe the noun and " +
            "the verb must NATURALLY apply to that noun. Before including a sentence, ask " +
            "yourself two questions: (a) \"Does ${adj.en} actually describe this noun in " +
            "everyday English?\" and (b) \"Have I heard a native speaker say this in real " +
            "life this week?\" If either answer is no, pick a different noun. " +
            "THE 10-YEAR-OLD TEST (apply to every sentence): could an English-speaking " +
            "10-year-old plausibly say this exact sentence to their parent, sibling, " +
            "friend, or teacher in real life? If you have to invent a contrived " +
            "scenario to justify it, DROP THE SENTENCE and pick a different noun. " +
            "If the English sentence sounds like a translated phrasebook entry rather " +
            "than something an actual kid or adult would text, REJECT IT. " +
            "REJECT awkward adjective+noun pairings even if grammatical. Bad examples " +
            "(do NOT produce sentences like these — every one of these was generated by " +
            "a previous version of this prompt and got flagged by a real user): " +
            "\"the low building\" / \"niski budynek\" (we say 'tall' or 'short' for " +
            "buildings, not 'low' — and 'low building' is not a phrase anyone uses), " +
            "\"an expensive shop\" / \"drogi sklep\" (shops aren't usually called " +
            "'expensive'; expensive describes items inside the shop — pick 'I'm going " +
            "to a small shop' or 'I bought an expensive coffee'), " +
            "\"I am talking about difficult weather\" (no one says this — say 'It's bad " +
            "weather' or 'The weather is bad'), " +
            "\"he has an important key in the room\" (keys aren't 'important' in casual " +
            "speech — say 'he found the key' or 'he has my keys'), " +
            "\"I remember about an easy gift\" ('easy gift' isn't a phrase — say 'nice " +
            "gift' / 'small gift' / 'perfect gift'; and people 'remember a gift', not " +
            "'remember about a gift'), " +
            "\"I am going to a health doctor\" (just say 'doctor' — 'health doctor' is " +
            "not a phrase in English), " +
            "\"a difficult word\" (people don't 'write words'), " +
            "\"watch a big painting\" (paintings aren't 'big' colloquially — and you LOOK " +
            "AT, not WATCH, a painting), \"read a quick book\" ('quick' doesn't describe " +
            "a book — say 'short' instead), \"eat a tall apple\", \"drink a long coffee\", " +
            "\"see a fast car\" (only OK if the car is actually moving fast), " +
            "\"eat a bad mouse\" (people don't eat mice), \"eat a small bread\" (bread is " +
            "uncountable — say 'a small piece of bread' or pick a different noun). " +
            "If the adjective doesn't pair naturally with a given noun, CHANGE THE NOUN. " +
            "Stick to ordinary, boring, everyday pairings (a big house, a small dog, hot " +
            "coffee, an old book, a red car, a good friend, a new phone, a long day). " +
            "Make the adjective agree correctly with the noun's gender and case " +
            "(nominative: $nomBlurb; accusative: $accBlurb). Use the accusative form when the " +
            "noun is the direct object of the verb (most of these examples). " +
            "Vary the subject pronoun across sentences. Keep each sentence to 6 words or fewer. " +
            "For the 2pl subject (wy), render the English subject as \"Y'all\" at the start " +
            "of a sentence and \"y'all\" mid-sentence — NEVER write \"You (plural)\", \"you " +
            "(pl.)\", or any \"(plural)\" annotation in the English. " +
            "PREPOSITION COVERAGE: at least 10 of the 40 sentences must use a Polish " +
            "preposition with the adjective+noun. Include AT LEAST ONE sentence for each " +
            "of these five common prepositions: w (in/at), na (on/at), do (to), z (with/" +
            "from), o (about). Examples: \"Mam ciepłą kawę w domu\", \"Widzę dużego psa na " +
            "ulicy\", \"Idę do nowego sklepu\", \"Piję herbatę z dobrym przyjacielem\", " +
            "\"Czytam książkę o starym mieście\". Use the correct case for each preposition. " +
            "For each sentence, return FOUR fields: " +
            "(1) \"pl\": the Polish sentence with correct diacritics and natural capitalization. " +
            "(2) \"en\": a GRAMMATICALLY CORRECT, NATURAL English translation. " +
            "    Use proper English articles (a / an / the) where English requires them. " +
            "    For 2pl render the subject as \"Y'all\" / \"y'all\"; never \"You (plural)\". " +
            "(3) \"literal\": a WORD-FOR-WORD gloss preserving Polish word order. Skip " +
            "    articles that don't exist in Polish. Keep prepositions literal (e.g. " +
            "    \"Mam ciepłą kawę\" → \"I-have warm coffee\"). " +
            "(4) \"words\": an ARRAY of per-token mappings in the same left-to-right order " +
            "    as the Polish sentence. Each element is {\"pl\":\"polish-token\",\"en\":" +
            "    \"english-gloss\"}. Tokens correspond to whitespace-separated Polish words. " +
            "    When a Polish word maps to a multi-word English gloss use a hyphen " +
            "    (\"I-have\"). The number of \"words\" entries MUST equal the Polish word " +
            "    count exactly — every Polish token gets its own gloss. " +
            "For each NOUN, PRONOUN, or ADJECTIVE token, ALSO include \"gender\" (\"m\", " +
            "\"f\", or \"n\") and \"caseKey\" (one of \"nom\", \"acc\", \"gen\", \"dat\", " +
            "\"inst\", \"loc\", \"voc\"); OMIT both keys for verbs, prepositions, adverbs, " +
            "and particles. " +
            "Return ONLY a JSON array (no prose, no markdown fence) where each element has " +
            "the exact shape {\"pl\":\"...\",\"en\":\"...\",\"literal\":\"...\"," +
            "\"words\":[{\"pl\":\"...\",\"en\":\"...\",\"gender\":\"m|f|n|omit\"," +
            "\"caseKey\":\"nom|acc|gen|dat|inst|loc|voc|omit\"}]}."
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
        "Write 40 very simple example Polish sentences that use the adverb \"${adv.lemma}\" " +
        "(English: ${adv.en}). " +
        "EVERYDAY-PHRASE REQUIREMENT (the most important rule): every sentence must be a " +
        "HIGH-FREQUENCY phrase that adults actually say multiple times a week. Aim for " +
        "everyday phrases like \"I'm running late\", \"I sleep well\", \"speak slowly " +
        "please\", \"come quickly\", \"work hard\", \"eat slowly\". No textbook filler. " +
        "VERB POOL: use ONLY common Polish verbs the adverb can naturally modify. " +
        "Good candidates: iść, robić, jeść, pić, mówić, pracować, spać, czytać, pisać, " +
        "śpiewać, biec, chodzić, słuchać, oglądać, uczyć się, gotować, jechać. " +
        "Vary the subject pronoun. Keep each sentence to 6 words or fewer. " +
        "CRITICAL NATURALNESS RULE: each sentence must be something a real person would " +
        "actually say in ordinary conversation, in BOTH Polish AND English. Grammatical " +
        "correctness is NOT enough — the adverb must NATURALLY modify the chosen verb. " +
        "Before including a sentence, ask yourself: \"Have I heard a native speaker say " +
        "this in real life this week?\" If no, pick a different verb. " +
        "THE 10-YEAR-OLD TEST (apply to every sentence): could an English-speaking " +
        "10-year-old plausibly say this exact sentence to their parent, sibling, friend, " +
        "or teacher in real life? If you have to invent a contrived scenario to justify " +
        "it, DROP THE SENTENCE and pick a different verb. " +
        "Reject any verb+adverb combination that sounds odd. Bad examples (do NOT produce " +
        "sentences like these): \"I have quickly\" ('have' isn't done quickly), \"she sees " +
        "slowly\" (you don't see slowly), \"we like loudly\" (liking isn't loud). " +
        "Prefer common verb+adverb collocations native speakers actually use (walk slowly, " +
        "speak quietly, eat quickly, sing loudly, work hard, sleep well). " +
        "For the 2pl subject (wy), render the English subject as \"Y'all\" at the start " +
        "of a sentence and \"y'all\" mid-sentence — NEVER write \"You (plural)\", \"you " +
        "(pl.)\", or any \"(plural)\" annotation in the English. " +
        "PREPOSITION COVERAGE: at least 10 of the 40 sentences must use a Polish preposition. " +
        "Include AT LEAST ONE sentence for each of these five common prepositions: w (in/at), " +
        "na (on/at), do (to), z (with/from), o (about). Use the correct case for each. " +
        "For each sentence, return FOUR fields: " +
        "(1) \"pl\": the Polish sentence with correct diacritics and natural capitalization. " +
        "(2) \"en\": a GRAMMATICALLY CORRECT, NATURAL English translation. For 2pl render " +
        "    the subject as \"Y'all\" / \"y'all\"; never \"You (plural)\". " +
        "(3) \"literal\": a WORD-FOR-WORD gloss preserving Polish word order. Skip articles " +
        "    that don't exist in Polish. Keep prepositions literal. " +
        "(4) \"words\": an ARRAY of per-token mappings in the same left-to-right order as " +
        "    the Polish sentence. Each element is {\"pl\":\"polish-token\",\"en\":" +
        "    \"english-gloss\"}. Tokens correspond to whitespace-separated Polish words. " +
        "    When a Polish word maps to a multi-word English gloss use a hyphen " +
        "    (\"I-am\"). The number of \"words\" entries MUST equal the Polish word count " +
        "    exactly — every Polish token gets its own gloss. " +
        "For each NOUN, PRONOUN, or ADJECTIVE token, ALSO include \"gender\" (\"m\", " +
        "\"f\", or \"n\") and \"caseKey\" (one of \"nom\", \"acc\", \"gen\", \"dat\", " +
        "\"inst\", \"loc\", \"voc\"); OMIT both keys for verbs, prepositions, adverbs, " +
        "and particles. " +
        "Return ONLY a JSON array (no prose, no markdown fence) where each element has " +
        "the exact shape {\"pl\":\"...\",\"en\":\"...\",\"literal\":\"...\"," +
        "\"words\":[{\"pl\":\"...\",\"en\":\"...\",\"gender\":\"m|f|n|omit\"," +
        "\"caseKey\":\"nom|acc|gen|dat|inst|loc|voc|omit\"}]}."

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

    // ── Nouns ───────────────────────────────────────────────────────────────────

    /**
     * Translate an English noun and produce its nominative + accusative + genitive
     * paradigm (singular & plural) plus gender. Mirrors [translateAdjective].
     */
    suspend fun translateNoun(englishNoun: String): Result<NounEntry> =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("GEMINI_API_KEY not set in local.properties")
                )
            }
            try {
                val raw = postPrompt(buildNounPrompt(englishNoun))
                Result.success(parseNounResponse(raw, englishNoun))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    /**
     * Asks Gemini for 20-40 short example sentences that use [noun] across its cases —
     * subject (nominative), direct object (accusative), and "of"/negation (genitive).
     */
    suspend fun generateNounSentences(noun: NounEntry): Result<List<SentenceExample>> =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("GEMINI_API_KEY not set in local.properties")
                )
            }
            try {
                val raw = postPrompt(buildNounSentencePrompt(noun))
                Result.success(parseSentenceResponse(raw))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private fun buildNounPrompt(english: String): String =
        "Translate the English noun \"$english\" into Polish, then give its declension in the " +
        "nominative, accusative, and genitive cases — each in BOTH singular and plural. Return " +
        "ONLY a JSON object with this exact shape (no prose, no markdown fence): " +
        "{\"lemma\":\"polish_nom_singular\",\"en\":\"$english\",\"gender\":\"m|f|n\"," +
        "\"nom\":{\"sg\":\"\",\"pl\":\"\"}," +
        "\"acc\":{\"sg\":\"\",\"pl\":\"\"}," +
        "\"gen\":{\"sg\":\"\",\"pl\":\"\"}}. " +
        "\"gender\" is the grammatical gender: \"m\" (masculine), \"f\" (feminine), or \"n\" " +
        "(neuter). \"lemma\" is the nominative singular (the dictionary form). " +
        "For the masculine accusative singular, use the ANIMATE form (= genitive singular) " +
        "for people and animals (e.g. widzę psa, widzę syna), but for INANIMATE masculine " +
        "nouns the accusative singular equals the nominative singular (e.g. widzę dom). " +
        "For the accusative plural, masculine-personal (people) nouns use the genitive plural " +
        "form; everything else (animals, all feminine, all neuter) uses the nominative plural " +
        "form. Use lowercase Polish with correct diacritics. If the noun is normally only used " +
        "in the singular, still give the grammatically-correct plural forms."

    private fun buildNounSentencePrompt(noun: NounEntry): String {
        val nomBlurb = noun.nom.entries.joinToString(", ") { "${it.key}=${it.value}" }
        val accBlurb = noun.acc.entries.joinToString(", ") { "${it.key}=${it.value}" }
        val genBlurb = noun.gen.entries.joinToString(", ") { "${it.key}=${it.value}" }
        return "Write 40 very simple example Polish sentences that use the noun \"" +
            "${noun.lemma}\" (English: ${noun.en}, gender: ${noun.gender}). " +
            "EVERYDAY-PHRASE REQUIREMENT (the most important rule): every sentence must " +
            "be a HIGH-FREQUENCY phrase that adults actually say multiple times a week. " +
            "Aim for the everyday register of \"I have a dog\", \"the coffee is hot\", " +
            "\"I'm going home\", \"where is the car\", \"I don't have money\", \"this is my " +
            "brother\", \"I love my mom\", \"we have two children\". Avoid textbook filler. " +
            "VERB POOL: use ONLY these common Polish verbs in the chosen tense/person — " +
            "mieć (to have), widzieć (to see), lubić (to like), kochać (to love), " +
            "kupować (to buy), czytać (to read), jeść (to eat), pić (to drink), " +
            "chcieć (to want), potrzebować (to need), znać (to know), pamiętać (to remember), " +
            "robić (to do), pisać (to write), mówić (to speak), szukać (to look for), " +
            "iść (to go), być (to be), spotykać (to meet), dawać (to give). " +
            "CASE COVERAGE — this is critical. Distribute the 40 sentences across all three " +
            "cases of \"${noun.lemma}\", using the EXACT inflected forms below and no others:\n" +
            "  • NOMINATIVE (subject of the sentence, or after 'to jest' / 'to są'): $nomBlurb. " +
            "Examples: \"To jest ${noun.nom["sg"]}\", \"${noun.nom["pl"]} są tutaj\".\n" +
            "  • ACCUSATIVE (direct object of the verb — the most common case for these): " +
            "$accBlurb. Examples: \"Mam ${noun.acc["sg"]}\", \"Widzę ${noun.acc["pl"]}\".\n" +
            "  • GENITIVE (after 'nie ma' / negated verbs, after 'do', 'od', 'z', 'dla', " +
            "'bez', and to mean 'of'): $genBlurb. Examples: \"Nie mam ${noun.gen["sg"]}\", " +
            "\"Idę do ${noun.gen["sg"]}\" (if it makes sense), \"szukam ${noun.gen["sg"]}\". " +
            "Aim for roughly 15 accusative, 13 nominative, and 12 genitive sentences. Every " +
            "sentence's form of \"${noun.lemma}\" MUST be one of the forms listed above — never " +
            "invent a different inflection. " +
            "CRITICAL NATURALNESS RULE: each sentence must be something a real person would " +
            "actually say in ordinary conversation, in BOTH Polish AND English. Grammatical " +
            "correctness is NOT enough. Before including a sentence, ask yourself: \"Have I " +
            "heard a native speaker say this in real life this week?\" If no, pick a different " +
            "one. " +
            "THE 10-YEAR-OLD TEST (apply to every sentence): could an English-speaking " +
            "10-year-old plausibly say this exact sentence to their parent, sibling, friend, " +
            "or teacher in real life? If you have to invent a contrived scenario to justify " +
            "it, DROP THE SENTENCE. " +
            "Vary the subject pronoun across sentences. Keep each sentence to 6 words or fewer. " +
            "For the 2pl subject (wy), render the English subject as \"Y'all\" at the start " +
            "of a sentence and \"y'all\" mid-sentence — NEVER write \"You (plural)\", \"you " +
            "(pl.)\", or any \"(plural)\" annotation in the English. " +
            "For each sentence, return FOUR fields: " +
            "(1) \"pl\": the Polish sentence with correct diacritics and natural capitalization. " +
            "(2) \"en\": a GRAMMATICALLY CORRECT, NATURAL English translation. " +
            "    Use proper English articles (a / an / the) where English requires them. " +
            "    For 2pl render the subject as \"Y'all\" / \"y'all\"; never \"You (plural)\". " +
            "(3) \"literal\": a WORD-FOR-WORD gloss preserving Polish word order. Skip " +
            "    articles that don't exist in Polish. Keep prepositions literal (e.g. " +
            "    \"Idę do sklepu\" → \"I-go to shop\"). " +
            "(4) \"words\": an ARRAY of per-token mappings in the same left-to-right order " +
            "    as the Polish sentence. Each element is {\"pl\":\"polish-token\",\"en\":" +
            "    \"english-gloss\"}. Tokens correspond to whitespace-separated Polish words. " +
            "    When a Polish word maps to a multi-word English gloss use a hyphen " +
            "    (\"I-have\"). The number of \"words\" entries MUST equal the Polish word " +
            "    count exactly — every Polish token gets its own gloss. " +
            "For each NOUN, PRONOUN, or ADJECTIVE token, ALSO include \"gender\" (\"m\", " +
            "\"f\", or \"n\") and \"caseKey\" (one of \"nom\", \"acc\", \"gen\", \"dat\", " +
            "\"inst\", \"loc\", \"voc\"); OMIT both keys for verbs, prepositions, adverbs, " +
            "and particles. " +
            "Return ONLY a JSON array (no prose, no markdown fence) where each element has " +
            "the exact shape {\"pl\":\"...\",\"en\":\"...\",\"literal\":\"...\"," +
            "\"words\":[{\"pl\":\"...\",\"en\":\"...\",\"gender\":\"m|f|n|omit\"," +
            "\"caseKey\":\"nom|acc|gen|dat|inst|loc|voc|omit\"}]}."
    }

    private fun parseNounResponse(raw: String, englishFallback: String): NounEntry {
        val root = json.parseToJsonElement(raw).jsonObject
        val candidates = root["candidates"] as? JsonArray ?: error("Gemini: candidates missing")
        val first = candidates.firstOrNull()?.jsonObject ?: error("Gemini: empty candidates")
        val parts = first["content"]?.jsonObject?.get("parts") as? JsonArray
            ?: error("Gemini: parts missing")
        val text = parts.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: error("Gemini: text missing")
        val inner = json.parseToJsonElement(text.trim()).jsonObject
        val noun = NounEntry(
            lemma = inner["lemma"]?.jsonPrimitive?.content?.trim().orEmpty(),
            en = inner["en"]?.jsonPrimitive?.content?.trim() ?: englishFallback,
            gender = inner["gender"]?.jsonPrimitive?.content?.trim()?.lowercase() ?: "",
            nom = inner["nom"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }
                ?: emptyMap(),
            acc = inner["acc"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }
                ?: emptyMap(),
            gen = inner["gen"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }
                ?: emptyMap()
        )
        require(noun.lemma.isNotEmpty()) { "Gemini returned empty noun lemma" }
        require(noun.nom.size == 2) { "Gemini returned ${noun.nom.size} nom forms, expected 2 (sg, pl)" }
        require(noun.acc.size == 2) { "Gemini returned ${noun.acc.size} acc forms, expected 2 (sg, pl)" }
        require(noun.gen.size == 2) { "Gemini returned ${noun.gen.size} gen forms, expected 2 (sg, pl)" }
        return noun
    }
}
