package com.sponic.langbangtrans.hud

data class HudTextState(
    var sourceText: String = "",
    var targetText: String = "",
) {
    fun apply(inputText: String?, outputText: String?) {
        if (!inputText.isNullOrBlank()) sourceText = appendTranscript(sourceText, inputText)
        if (!outputText.isNullOrBlank()) targetText = appendTranscript(targetText, outputText)
    }
}

class TextLayoutManager(
    private val columns: Int = 28,
    private val lines: Int = 10,
) {
    /**
     * Render the target translation. For Russian (Cyrillic) also show an English-pronunciation
     * (transliteration) line beneath it, since Cyrillic isn't readable at a glance.
     */
    fun render(state: HudTextState, targetLabel: String, statusText: String? = null): String {
        val header = if (statusText.isNullOrBlank()) "G2Trans" else "G2Trans: $statusText"
        val frame = mutableListOf(header.take(columns))
        val remaining = lines - frame.size
        if (targetLabel == "RU" && state.targetText.isNotBlank()) {
            val ru = wrapBlock(targetLabel, state.targetText)
            val say = wrapBlock("say", transliterateCyrillic(state.targetText))
            val ruBudget = maxOf(1, remaining / 2)
            val sayBudget = maxOf(1, remaining - ruBudget)
            frame += ru.takeLast(ruBudget)
            frame += say.takeLast(sayBudget)
        } else {
            frame += wrapBlock(targetLabel, state.targetText.ifBlank { "..." }).takeLast(remaining)
        }
        return frame.take(lines).joinToString("\n") { it.take(columns) }
    }

    private fun wrapBlock(label: String, text: String): List<String> {
        val prefix = "$label: "
        val words = clean(text).split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return listOf(prefix.trim())

        val result = mutableListOf<String>()
        var line = prefix
        for (word in words) {
            if (line.length + word.length + 1 <= columns) {
                line += if (line == prefix) word else " $word"
            } else {
                result += line
                line = " ".repeat(prefix.length) + word
                while (line.length > columns) {
                    result += line.take(columns)
                    line = " ".repeat(prefix.length) + line.drop(columns)
                }
            }
        }
        if (line.isNotBlank()) result += line
        return result
    }
}

private const val MAX_TRANSCRIPT_CHARS = 400

/**
 * Merge a new transcription fragment into the running text. Gemini Live sends incremental DELTAS
 * (verified on-wire), so appending is the normal path; this also defends against any cumulative
 * re-send and dedups identical fragments so transcripts never repeat. Shapes handled:
 *  - cumulative growth (incoming starts with current) → replace with the latest,
 *  - already-present fragment (suffix/contains) → ignore,
 *  - genuine new delta → append.
 * Capped so the HUD text can't grow without bound.
 */
private fun appendTranscript(current: String, incoming: String): String {
    val inc = clean(incoming)
    if (inc.isBlank()) return current
    val merged = when {
        current.isBlank() -> inc
        inc == current -> current
        inc.startsWith(current) -> inc
        current.endsWith(inc) || current.contains(inc) -> current
        else -> "$current $inc"
    }
    return merged.takeLast(MAX_TRANSCRIPT_CHARS)
}

private fun clean(text: String): String = text.replace(Regex("[^\\S\\n]+"), " ").trim()
