package com.sponic.langbang.shared.cloud

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android engine (OkHttp). Thin for Pass 1.
 */
actual fun createPlatformHttpEngine(): HttpClientEngine = OkHttp.create()
