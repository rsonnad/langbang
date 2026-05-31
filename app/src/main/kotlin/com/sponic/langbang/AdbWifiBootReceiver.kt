package com.sponic.langbang

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AdbWifiBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: "unknown"
        AdbWifiKeeper.enableIfGranted(context, action)
    }
}
