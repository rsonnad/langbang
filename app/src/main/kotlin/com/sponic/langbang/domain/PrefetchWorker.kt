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

        // Phase 1 — pull pre-generated mp3s from R2 (fast, free, no Azure spend). This is
        // the "auto-download on launch" path: every launch tops up the on-device cache
        // from the shared R2 bucket, so the user never has to tap a button or use adb.
        // Best-effort — if the Edge Function is unreachable, fall through to Phase 2,
        // which synthesizes whatever's still missing on-device.
        runCatching {
            app.r2Audio.downloadAll { done, total, current ->
                setProgress(
                    workDataOf(
                        KEY_TOTAL to total,
                        KEY_DONE to done,
                        KEY_CURRENT to if (current.isEmpty()) "syncing audio…" else "↓ $current",
                        KEY_FINISHED to false
                    )
                )
            }
        }

        // Phase 2 — synthesize anything R2 didn't have (e.g. a brand-new voice style
        // not yet pre-generated) on-device. cache.has() skips every file Phase 1 already
        // pulled, so this only spends Azure on genuine gaps.
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
