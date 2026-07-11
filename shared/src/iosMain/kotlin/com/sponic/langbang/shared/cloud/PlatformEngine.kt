package com.sponic.langbang.shared.cloud

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS (Darwin) engine for Ktor.
 */
actual fun createPlatformHttpEngine(): HttpClientEngine = Darwin.create()
