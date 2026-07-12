package com.sponic.langbang.cloud

import com.sponic.langbang.domain.ExternalNowVoicingCommandController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class NowVoicingControlState(
    val active: Boolean = false,
    val connected: Boolean = false,
    val sessionId: String = "",
    val code: String = "",
    val pairingUrl: String = "",
    val expiresAt: String = "",
    val status: String = ""
)

/** Foreground-lifetime device endpoint for the ephemeral Now Voicing control room. */
class NowVoicingControlClient(
    private val backend: CloudBackendClient,
    private val apiBase: String,
    private val scope: CoroutineScope
) {
    private val http = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
    private val mutableState = MutableStateFlow(NowVoicingControlState())
    val state: StateFlow<NowVoicingControlState> = mutableState.asStateFlow()

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var sessionToken = ""
    private var deviceToken = ""
    private var sessionId = ""
    private var lastSeq = 0L
    private var stopping = false
    private var reconnectDelayMs = 500L
    private var socketGeneration = 0L

    suspend fun start(userSessionToken: String, instanceId: String): Result<CloudNowVoicingControlResponse> {
        if (sessionId.isNotBlank() && sessionToken.isNotBlank()) stop()
        else stopSocketOnly()
        stopping = false
        return backend.createNowVoicingControl(userSessionToken, instanceId).fold(
            onSuccess = { response ->
                if (!response.ok || response.sessionId.isBlank() || response.deviceToken.isBlank()) {
                    val error = IllegalStateException("LangBang did not return a usable Now Voicing control session.")
                    mutableState.value = NowVoicingControlState(status = error.message.orEmpty())
                    Result.failure(error)
                } else {
                    sessionToken = userSessionToken
                    deviceToken = response.deviceToken
                    sessionId = response.sessionId
                    lastSeq = 0L
                    reconnectDelayMs = 500L
                    mutableState.value = NowVoicingControlState(
                        active = true,
                        sessionId = response.sessionId,
                        code = response.code,
                        pairingUrl = response.pairingUrl,
                        expiresAt = response.expiresAt,
                        status = "Waiting for an LLM to connect"
                    )
                    connect()
                    Result.success(response)
                }
            },
            onFailure = { error ->
                mutableState.value = NowVoicingControlState(status = error.message ?: "Could not start control")
                Result.failure(error)
            }
        )
    }

    suspend fun stop(): Result<CloudNowVoicingControlRevokeResponse> {
        val id = sessionId
        val ownerToken = sessionToken
        stopping = true
        stopSocketOnly()
        sessionId = ""
        deviceToken = ""
        lastSeq = 0L
        mutableState.value = NowVoicingControlState()
        if (id.isBlank() || ownerToken.isBlank()) return Result.success(CloudNowVoicingControlRevokeResponse(ok = true, revoked = true))
        return backend.revokeNowVoicingControl(ownerToken, id)
    }

    private fun connect() {
        val id = sessionId
        val token = deviceToken
        if (id.isBlank() || token.isBlank() || stopping) return
        socket?.close(1000, "replaced by reconnect")
        socket = null
        val generation = ++socketGeneration
        val socketBase = apiBase.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        val request = Request.Builder()
            .url("$socketBase/v1/now-voicing-control/ws?lastSeq=$lastSeq")
            .header("Authorization", "Bearer $token")
            .build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (id != sessionId || stopping || generation != socketGeneration) {
                    webSocket.close(1000, "stale control socket")
                    return
                }
                reconnectDelayMs = 500L
                mutableState.value = mutableState.value.copy(connected = true, status = "Device connected — share the pairing code")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (generation != socketGeneration || stopping) return
                handleSocketMessage(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                if (id != sessionId || stopping || generation != socketGeneration) return
                if (response?.code in setOf(401, 403, 410)) {
                    endRemoteSession("Control session is no longer available")
                    return
                }
                mutableState.value = mutableState.value.copy(connected = false, status = "Reconnecting control channel")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (id != sessionId || stopping || generation != socketGeneration) return
                if (code == 4001 || code == 4401) {
                    endRemoteSession("Control session ended")
                    return
                }
                mutableState.value = mutableState.value.copy(connected = false, status = "Reconnecting control channel")
                scheduleReconnect()
            }
        })
    }

    private fun handleSocketMessage(webSocket: WebSocket, text: String) {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (root.optString("type")) {
            "hello" -> {
                root.optJSONObject("snapshot")?.let { applyCommand(webSocket, it, replay = true) }
                root.optJSONArray("replay")?.let { replay ->
                    for (index in 0 until replay.length()) replay.optJSONObject(index)?.let { applyCommand(webSocket, it, replay = true) }
                }
            }
            "command" -> applyCommand(webSocket, root)
        }
    }

    private fun applyCommand(webSocket: WebSocket, command: JSONObject, replay: Boolean = false) {
        val seq = command.optLong("seq", 0L)
        if (seq <= lastSeq) return
        if (!ExternalNowVoicingCommandController.applyJson(command, focus = true, allowPlayback = !replay)) return
        lastSeq = seq
        webSocket.send(JSONObject().put("type", "ack").put("seq", seq).toString())
        mutableState.value = mutableState.value.copy(status = "Last command applied")
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true || stopping) return
        reconnectJob = scope.launch(Dispatchers.IO) {
            val delayMs = reconnectDelayMs
            reconnectDelayMs = min(reconnectDelayMs * 2, 15_000L)
            delay(delayMs)
            if (!stopping && sessionId.isNotBlank()) connect()
        }
    }

    private fun stopSocketOnly() {
        socketGeneration += 1
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, "control session stopped")
        socket = null
    }

    private fun endRemoteSession(status: String) {
        stopping = true
        stopSocketOnly()
        sessionId = ""
        deviceToken = ""
        lastSeq = 0L
        mutableState.value = NowVoicingControlState(status = status)
    }
}
