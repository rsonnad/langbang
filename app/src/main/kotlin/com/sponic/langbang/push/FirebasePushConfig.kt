package com.sponic.langbang.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.sponic.langbang.BuildConfig

object FirebasePushConfig {
    fun initialize(context: Context): Boolean {
        if (!configured()) return false
        val appContext = context.applicationContext
        runCatching { FirebaseApp.getInstance() }.getOrNull()?.let { return true }
        val options = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setGcmSenderId(BuildConfig.FIREBASE_MESSAGING_SENDER_ID)
            .build()
        return runCatching {
            FirebaseApp.initializeApp(appContext, options) != null
        }.getOrDefault(false)
    }

    fun configured(): Boolean =
        BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.FIREBASE_APPLICATION_ID.isNotBlank() &&
            BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_MESSAGING_SENDER_ID.isNotBlank()
}
