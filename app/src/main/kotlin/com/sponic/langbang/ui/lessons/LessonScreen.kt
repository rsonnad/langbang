package com.sponic.langbang.ui.lessons

import androidx.compose.runtime.Composable
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.domain.PrefetchProgress

@Composable
fun LessonScreen(
    app: LangbangApplication,
    prefetch: PrefetchProgress,
    nowVoicing: @Composable () -> Unit = {}
) {
    VerbsTab(app, prefetch, nowVoicing)
}
