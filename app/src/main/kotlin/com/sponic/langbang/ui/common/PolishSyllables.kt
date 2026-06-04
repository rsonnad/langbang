package com.sponic.langbang.ui.common

/**
 * Lightweight Polish syllabifier used only as a *visual* pronunciation aid (the Now
 * Voicing syllable shading). It is intentionally a heuristic, not a dictionary:
 *
 *  1. Each maximal run of vowels is one syllable nucleus. Treating a run as a single
 *     nucleus also folds the softening / glide `i` into its neighbour, so `nie`,
 *     `cia`, `bie`, `io` stay as one chunk.
 *  2. The consonants between two nuclei are split using the sonority principle: the
 *     onset of the following syllable is the longest suffix of the cluster whose
 *     sonority rises toward the vowel (e.g. `pr`, `tr`, `kl` stay together), and the
 *     leftover consonants close the preceding syllable (e.g. `mat-ka`, `mart-wy`).
 *  3. Polish digraphs (`ch cz sz rz dz dż dź`) are treated as single units so they
 *     are never split across a boundary (`ko-cha`, not `koc-ha`).
 *
 * The concatenation of the returned pieces is exactly the input, so callers can map
 * each piece back onto the original character ranges (case, punctuation, etc.).
 */
fun polishSyllables(word: String): List<String> {
    if (word.length < 2) return listOf(word)
    val lower = word.lowercase()
    val isVowel = BooleanArray(word.length) { lower[it] in POLISH_VOWELS }

    // 1) Nuclei: inclusive [start, end] of each maximal vowel run.
    val nuclei = ArrayList<IntArray>()
    var i = 0
    while (i < word.length) {
        if (isVowel[i]) {
            val start = i
            while (i < word.length && isVowel[i]) i++
            nuclei.add(intArrayOf(start, i - 1))
        } else {
            i++
        }
    }
    if (nuclei.size < 2) return listOf(word)

    // 2) For each gap between consecutive nuclei pick the index where the next
    //    syllable begins (somewhere inside the separating consonant run).
    val cuts = ArrayList<Int>()
    for (n in 0 until nuclei.size - 1) {
        val clusterStart = nuclei[n][1] + 1
        val clusterEnd = nuclei[n + 1][0] // exclusive

        // Split the consonant run into units, keeping digraphs intact.
        val unitStarts = ArrayList<Int>()
        var p = clusterStart
        while (p < clusterEnd) {
            unitStarts.add(p)
            val pair = if (p + 1 < clusterEnd) lower.substring(p, p + 2) else ""
            p += if (pair in POLISH_DIGRAPHS) 2 else 1
        }

        // Onset = maximal suffix of units whose sonority strictly rises toward the
        // vowel. The unit touching the vowel is always part of the onset.
        var onset = unitStarts.size - 1
        while (onset > 0) {
            val left = unitStarts[onset - 1]
            val mid = unitStarts[onset]
            val rightEnd = if (onset + 1 < unitStarts.size) unitStarts[onset + 1] else clusterEnd
            val leftUnit = lower.substring(left, mid)
            val rightUnit = lower.substring(mid, rightEnd)
            if (consonantSonority(leftUnit) < consonantSonority(rightUnit)) onset-- else break
        }
        cuts.add(unitStarts[onset])
    }

    // 3) Slice the original word on the chosen boundaries.
    val pieces = ArrayList<String>(cuts.size + 1)
    var prev = 0
    for (cut in cuts) {
        pieces.add(word.substring(prev, cut))
        prev = cut
    }
    pieces.add(word.substring(prev))
    return pieces
}

private val POLISH_VOWELS = setOf('a', 'ą', 'e', 'ę', 'i', 'o', 'ó', 'u', 'y')

private val POLISH_DIGRAPHS = setOf("ch", "cz", "sz", "rz", "dz", "dż", "dź")

/**
 * Coarse sonority rank (higher = more sonorous). Only the relative ordering matters:
 * it decides whether a two-consonant junction is a rising onset (kept together) or a
 * coda+onset split. Affricates are bucketed with stops; non-letters fall to the floor
 * so stray punctuation closes the preceding syllable.
 */
private fun consonantSonority(unit: String): Int = when (unit) {
    "j" -> 5
    "ł", "l", "r" -> 4 // liquids / glide
    "m", "n", "ń" -> 3 // nasals
    "w", "f", "s", "z", "ś", "ź", "ż", "h", "sz", "rz", "ch" -> 2 // fricatives
    "p", "b", "t", "d", "k", "g", "c", "ć", "dz", "dż", "dź", "cz" -> 1 // stops + affricates
    else -> 0
}
