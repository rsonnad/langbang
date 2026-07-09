package com.sponic.langbangtrans

data class GeminiConfig(
    val apiKey: String,
    val proxyToken: String,
    val model: String,
    val wsEndpoint: String,
    val textEndpoint: String,
    val targetLanguageCode: String,
    val responseModalities: List<String>,
    val playOutputAudio: Boolean,
) {
    /** True when routing through the worker proxy (proxy token present) instead of direct. */
    val usesProxy: Boolean get() = proxyToken.isNotBlank()

    val websocketUrl: String
        get() {
            // Through the worker proxy the Gemini key lives server-side, so the URL carries no key;
            // auth is the Authorization: Bearer <proxyToken> header set by GeminiLiveClient.
            if (usesProxy) return wsEndpoint
            val separator = if ("?" in wsEndpoint) "&" else "?"
            return "$wsEndpoint${separator}key=$apiKey"
        }
}

data class AudioConfig(
    val captureSampleRate: Int,
    val captureFrameMs: Int,
    val geminiAudioChunkMs: Int,
    val outputSampleRate: Int,
)

data class G2Config(
    val dryRun: Boolean,
    val nameRegex: String,
    val serviceUuid: String,
    val writeCharUuid: String,
    val notifyCharUuid: String,
)

data class BridgeConfig(
    val gemini: GeminiConfig,
    val audio: AudioConfig,
    val g2: G2Config,
) {
    fun validationProblems(): List<String> = buildList {
        if (gemini.proxyToken.isBlank() && gemini.apiKey.isBlank()) {
            add("Missing Gemini proxy token (or direct API key) in local.properties")
        }
        if (audio.geminiAudioChunkMs % audio.captureFrameMs != 0) {
            add("Gemini audio chunk must be divisible by capture frame duration")
        }
        // The G2 HUD now uses the real EvenHub protocol (G2HudLink), which hardcodes the command
        // UUIDs and framing — no per-device hardware UUID / CRC config is required anymore.
    }

    companion object {
        fun fromBuildConfig(): BridgeConfig =
            BridgeConfig(
                gemini = GeminiConfig(
                    apiKey = BuildConfig.GEMINI_API_KEY,
                    proxyToken = BuildConfig.GEMINI_PROXY_TOKEN,
                    model = BuildConfig.GEMINI_MODEL,
                    wsEndpoint = BuildConfig.GEMINI_WS_ENDPOINT,
                    textEndpoint = BuildConfig.GEMINI_TEXT_ENDPOINT,
                    targetLanguageCode = BuildConfig.TARGET_LANGUAGE_CODE,
                    responseModalities = BuildConfig.RESPONSE_MODALITIES
                        .split(",")
                        .map { it.trim().uppercase() }
                        .filter { it.isNotBlank() },
                    playOutputAudio = BuildConfig.PLAY_OUTPUT_AUDIO,
                ),
                audio = AudioConfig(
                    captureSampleRate = BuildConfig.CAPTURE_SAMPLE_RATE,
                    captureFrameMs = BuildConfig.CAPTURE_FRAME_MS,
                    geminiAudioChunkMs = BuildConfig.GEMINI_AUDIO_CHUNK_MS,
                    outputSampleRate = BuildConfig.OUTPUT_SAMPLE_RATE,
                ),
                g2 = G2Config(
                    dryRun = BuildConfig.G2_DRY_RUN,
                    nameRegex = BuildConfig.G2_NAME_REGEX,
                    serviceUuid = BuildConfig.G2_SERVICE_UUID,
                    writeCharUuid = BuildConfig.G2_WRITE_CHAR_UUID,
                    notifyCharUuid = BuildConfig.G2_NOTIFY_CHAR_UUID,
                ),
            )
    }
}
