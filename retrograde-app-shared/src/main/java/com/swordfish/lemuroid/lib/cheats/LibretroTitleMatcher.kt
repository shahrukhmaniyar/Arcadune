package com.swordfish.lemuroid.lib.cheats

/**
 * Finds the libretro database entry that belongs to a game even when the names differ in region
 * tags, publisher tags, revisions, case or roman numerals, for example "Wario Land 4 (USA, Europe)"
 * and "Wario Land 4 (USA, Europe) (Code Breaker)".
 */
object LibretroTitleMatcher {
    private val PARENTHESES = Regex("\\s*\\([^)]*\\)")
    private val BRACKETS = Regex("\\s*\\[[^\\]]*\\]")
    private val REVISION = Regex("\\s+Rev\\s+\\S+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

    private val ROMAN_NUMERALS =
        mapOf(
            "i" to "1", "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5",
            "vi" to "6", "vii" to "7", "viii" to "8", "ix" to "9", "x" to "10",
        )

    /** The title without region, publisher, revision and dump tags. */
    fun baseTitle(title: String): String =
        title
            .replace(PARENTHESES, "")
            .replace(BRACKETS, "")
            .replace(REVISION, "")
            .trim()

    /** Lower case words with roman numerals turned into digits, so "IV" matches "4". */
    fun normalize(title: String): String =
        baseTitle(title)
            .lowercase()
            .split(NON_ALPHANUMERIC)
            .filter { it.isNotEmpty() }
            .joinToString(" ") { ROMAN_NUMERALS[it] ?: it }

    /** Prefers American, then world, then European releases. Lower is better. */
    fun regionRank(name: String): Int {
        val lower = name.lowercase()
        return when {
            "(usa" in lower || "(us)" in lower -> 0
            "(world" in lower -> 1
            "(europe" in lower || "(eu)" in lower -> 2
            else -> 3
        }
    }

    /** Returns the candidate that best matches [wanted], or null if none has the same base title. */
    fun findBest(
        candidates: Collection<String>,
        wanted: String,
    ): String? {
        val key = normalize(wanted)
        if (key.isEmpty()) return null

        return candidates
            .filter { normalize(it) == key }
            .minWithOrNull(compareBy({ it.contains('[') }, { regionRank(it) }, { it.length }))
    }
}
