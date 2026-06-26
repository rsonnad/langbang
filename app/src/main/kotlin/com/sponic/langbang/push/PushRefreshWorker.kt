package com.sponic.langbang.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sponic.langbang.LangbangApplication

class PushRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? LangbangApplication ?: return Result.failure()
        val requestedInstanceId = inputData.getString(KEY_INSTANCE_ID).orEmpty()
        val currentInstanceId = app.cloudConfig.state.value.selectedInstanceId
        if (requestedInstanceId.isNotBlank() && requestedInstanceId != currentInstanceId) {
            return Result.success()
        }
        val ok = app.refreshFromPush(
            reason = inputData.getString(KEY_REASON).orEmpty().ifBlank { "push" },
            includeUserContent = inputData.getBoolean(KEY_INCLUDE_USER_CONTENT, true)
        )
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "push-refresh"
        private const val KEY_INSTANCE_ID = "instanceId"
        private const val KEY_REASON = "reason"
        private const val KEY_INCLUDE_USER_CONTENT = "includeUserContent"

        fun enqueue(
            context: Context,
            instanceId: String,
            reason: String,
            includeUserContent: Boolean
        ) {
            val request = OneTimeWorkRequestBuilder<PushRefreshWorker>()
                .setInputData(
                    workDataOf(
                        KEY_INSTANCE_ID to instanceId,
                        KEY_REASON to reason,
                        KEY_INCLUDE_USER_CONTENT to includeUserContent
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
