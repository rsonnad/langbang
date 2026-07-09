package com.sponic.langbangtrans

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class BridgeStatus(
    val running: Boolean = false,
    val stage: String = "Idle",
    val details: String = "",
    val lastPolish: String = "",
    val lastEnglish: String = "",
    val lastDiscovery: String = "",
)

object BridgeStatusBus {
    private val mutableStatus = MutableStateFlow(BridgeStatus())
    val status: StateFlow<BridgeStatus> = mutableStatus

    fun set(stage: String, details: String = "", running: Boolean? = null) {
        mutableStatus.update {
            it.copy(
                running = running ?: it.running,
                stage = stage,
                details = details,
            )
        }
    }

    fun transcripts(polish: String, english: String) {
        mutableStatus.update {
            it.copy(lastPolish = polish.takeLast(240), lastEnglish = english.takeLast(240))
        }
    }

    fun discovery(report: String) {
        mutableStatus.update {
            it.copy(lastDiscovery = report.takeLast(2_000))
        }
    }
}
