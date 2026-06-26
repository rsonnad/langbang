package com.sponic.langbang.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sponic.langbang.LangbangApplication

class LangbangMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        (applicationContext as? LangbangApplication)?.pushManager?.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"].orEmpty()
        if (type !in REFRESH_TYPES) return
        PushRefreshWorker.enqueue(
            context = applicationContext,
            instanceId = data["instanceId"].orEmpty(),
            reason = data["reason"].orEmpty().ifBlank { type },
            includeUserContent = type == TYPE_USER_CONTENT_REFRESH ||
                data["includeUserContent"].equals("true", ignoreCase = true)
        )
    }

    companion object {
        const val TYPE_CONTENT_REFRESH = "content_refresh"
        const val TYPE_USER_CONTENT_REFRESH = "user_content_refresh"
        private val REFRESH_TYPES = setOf(TYPE_CONTENT_REFRESH, TYPE_USER_CONTENT_REFRESH)
    }
}
