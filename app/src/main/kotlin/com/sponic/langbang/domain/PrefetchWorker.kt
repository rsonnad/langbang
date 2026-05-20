package com.sponic.langbang.domain

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sponic.langbang.LangbangApplication

class PrefetchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as LangbangApplication
        var finalTotal = 0
        return try {
            app.prefetch.prefetchLesson1 { progress ->
                finalTotal = progress.total
                setProgress(
                    workDataOf(
                        KEY_TOTAL to progress.total,
                        KEY_DONE to progress.done,
                        KEY_CURRENT to progress.current,
                        KEY_FINISHED to progress.finished
                    )
                )
            }
            // Stash final stats in outputData so the SUCCEEDED state can still report
            // a real "N/N" instead of "0/0" once progress data is cleared.
            Result.success(
                workDataOf(
                    KEY_TOTAL to finalTotal,
                    KEY_DONE to finalTotal,
                    KEY_FINISHED to true
                )
            )
        } catch (t: Throwable) {
            // Network blip / TTS hiccup → retry with backoff.
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "prefetch-lesson-01"
        const val KEY_TOTAL = "total"
        const val KEY_DONE = "done"
        const val KEY_CURRENT = "current"
        const val KEY_FINISHED = "finished"

        fun progressFrom(data: Data): PrefetchProgress = PrefetchProgress(
            total = data.getInt(KEY_TOTAL, 0),
            done = data.getInt(KEY_DONE, 0),
            current = data.getString(KEY_CURRENT) ?: "",
            finished = data.getBoolean(KEY_FINISHED, false)
        )
    }
}
