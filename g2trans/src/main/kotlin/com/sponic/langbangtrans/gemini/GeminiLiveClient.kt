package com.sponic.langbangtrans.gemini

import android.util.Base64
import com.sponic.langbangtrans.AudioConfig
import com.sponic.langbangtrans.BridgeStatusBus
import com.sponic.langbangtrans.GeminiConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "LBG2gemini"

data class GeminiEvent(
    val inputText: String? = null,
    val outputText: String? = null,
    val outputAudio: ByteArray? = null,
    val turnComplete: Boolean = false,
)

class GeminiLiveClient(
    private val config: GeminiConfig,
    private val audioConfig: AudioConfig,
) {
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    suspend fun run(
        audioInput: ReceiveChannel<ByteArray>,
        events: SendChannel<GeminiEvent>,
    ): Unit = coroutineScope {
        BridgeStatusBus.set(
            "Gemini",
            "${config.model} -> ${config.targetLanguageCode}, modalities=${config.responseModalities.joinToString(",")}",
        )

        val opened = CompletableDeferred<WebSocket>()
        val closed = CompletableDeferred<Unit>()
        val listenerScope: CoroutineScope = this
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "ws OPEN http=${response.code}")
                webSocket.send(setupMessage().toString())
                opened.complete(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i(TAG, "ws TEXT ${text.take(180)}")
                for (event in parseEvents(text)) {
                    listenerScope.launch { events.send(event) }
                }
            }

            // Gemini Live delivers its JSON server messages as BINARY frames, so handle those too.
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val s = bytes.utf8()
                Log.i(TAG, "ws BIN ${s.take(180)}")
                for (event in parseEvents(s)) {
                    listenerScope.launch { events.send(event) }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "ws CLOSED $code $reason")
                closed.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "ws FAIL ${t.message} http=${response?.code}", t)
                if (!opened.isCompleted) opened.completeExceptionally(t)
                if (!closed.isCompleted) closed.completeExceptionally(t)
            }
        }

        val request = Request.Builder()
            .url(config.websocketUrl)
            .apply { if (config.proxyToken.isNotBlank()) header("Authorization", "Bearer ${config.proxyToken}") }
            .build()
        Log.i(TAG, "connecting ws ${config.websocketUrl} (proxy=${config.proxyToken.isNotBlank()})")
        httpClient.newWebSocket(request, listener)
        val webSocket = opened.await()
        Log.i(TAG, "ws ready, streaming audio")

        val framesPerSend = maxOf(1, audioConfig.geminiAudioChunkMs / audioConfig.captureFrameMs)
        launch {
            val pending = ArrayList<ByteArray>(framesPerSend)
            var sent = 0
            for (chunk in audioInput) {
                pending += chunk
                if (pending.size < framesPerSend) continue
                webSocket.send(audioMessage(pending.concat(), audioConfig.captureSampleRate).toString())
                pending.clear()
                if (++sent % 25 == 0) Log.i(TAG, "sent $sent audio chunks")
                if (!isActive) break
            }
        }

        withContext(Dispatchers.IO) { closed.await() }
    }

    private fun setupMessage(): JSONObject =
        JSONObject().put(
            "setup",
            JSONObject()
                .put("model", "models/${config.model}")
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("responseModalities", JSONArray(config.responseModalities))
                        .put(
                            "translationConfig",
                            JSONObject()
                                .put("targetLanguageCode", config.targetLanguageCode)
                                // Don't echo input already in the target language, so re-speaking the
                                // translation aloud doesn't show up again.
                                .put("echoTargetLanguage", false),
                        ),
                )
                // Transcription configs are setup-level siblings of generationConfig — Gemini
                // rejects the setup if they're nested inside generationConfig (verified on-wire).
                .put("inputAudioTranscription", JSONObject())
                .put("outputAudioTranscription", JSONObject()),
        )

    private fun audioMessage(chunk: ByteArray, sampleRate: Int): JSONObject =
        JSONObject().put(
            "realtimeInput",
            JSONObject().put(
                "audio",
                JSONObject()
                    .put("data", Base64.encodeToString(chunk, Base64.NO_WRAP))
                    .put("mimeType", "audio/pcm;rate=$sampleRate"),
            ),
        )

    private fun parseEvents(text: String): List<GeminiEvent> {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val serverContent = root.optJSONObject("serverContent") ?: root.optJSONObject("server_content") ?: return emptyList()
        val events = mutableListOf<GeminiEvent>()

        val input = transcriptionText(serverContent, "input")
        val output = transcriptionText(serverContent, "output")
        if (input != null || output != null) {
            events += GeminiEvent(inputText = input, outputText = output)
        }

        if (serverContent.optBoolean("turnComplete", false) || serverContent.optBoolean("turn_complete", false)) {
            events += GeminiEvent(turnComplete = true)
        }

        val modelTurn = serverContent.optJSONObject("modelTurn") ?: serverContent.optJSONObject("model_turn")
        val parts = modelTurn?.optJSONArray("parts")
        if (parts != null) {
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data") ?: continue
                val data = inline.optString("data")
                if (data.isNotBlank()) {
                    runCatching { Base64.decode(data, Base64.DEFAULT) }
                        .getOrNull()
                        ?.let { events += GeminiEvent(outputAudio = it) }
                }
            }
        }
        return events
    }

    private fun transcriptionText(serverContent: JSONObject, side: String): String? {
        val camel = serverContent.optJSONObject("${side}Transcription")
        val snake = serverContent.optJSONObject("${side}_transcription")
        return (camel ?: snake)?.optString("text")?.takeIf { it.isNotBlank() }
    }

    private fun List<ByteArray>.concat(): ByteArray {
        val total = sumOf { it.size }
        val result = ByteArray(total)
        var offset = 0
        for (chunk in this) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }
}

