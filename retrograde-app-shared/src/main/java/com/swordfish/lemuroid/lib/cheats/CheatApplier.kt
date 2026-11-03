package com.swordfish.lemuroid.lib.cheats

/** The part of the emulator that cheats are sent to. */
interface CheatTarget {
    /** Removes every cheat from the emulator. Returns false if that was not possible. */
    fun clear(): Boolean

    /** Adds one active cheat. */
    fun enable(
        index: Int,
        code: String,
    )
}

/**
 * Makes the emulator's cheats match the player's list.
 *
 * The "enabled" flag of the libretro cheat call cannot be trusted: some cores (the NES core and the
 * GBA core, for two) ignore it and treat every call as "add this cheat". The only reliable way to
 * switch a cheat off is therefore to clear all cheats and add again only the ones that are on.
 * Cheats that are switched off, that a system does not need, or that cannot be sent safely
 * (see [CheatPolicy]), are never sent.
 *
 * Removing a cheat does not undo what it already wrote into the game. That matters for cheats that
 * change the game's program (common on the PSP): the changed program stays. Cheat files therefore
 * often hold an "[Disable]" twin that writes the original back, and when a cheat that was on is
 * switched off its twin is sent once in its place.
 */
class CheatApplier(
    private val target: CheatTarget,
    private val systemId: String,
) {
    /** The cheats of [cheats] that are sent to the emulator (switched on and safe to send). */
    fun activeCheats(cheats: List<Cheat>): List<Cheat> = cheats.filter { it.enabled && chunksOf(it) != null }

    /**
     * [previouslyApplied] are the cheats that were on at the last apply. Returns how many cheats were
     * switched on, or null if the emulator's cheats could not be cleared.
     */
    fun apply(
        cheats: List<Cheat>,
        previouslyApplied: List<Cheat> = emptyList(),
    ): Int? {
        if (!target.clear()) return null

        var nextIndex = 0
        val active = activeCheats(cheats)
        for (cheat in active) {
            chunksOf(cheat)?.forEach { target.enable(nextIndex++, it) }
        }

        for (twin in undoCheatsFor(previouslyApplied, cheats, active)) {
            chunksOf(twin.copy(enabled = true))?.forEach { target.enable(nextIndex++, it) }
        }
        return active.size
    }

    /** The "[Disable]" twins of cheats that were on and are now off. */
    private fun undoCheatsFor(
        previouslyApplied: List<Cheat>,
        cheats: List<Cheat>,
        active: List<Cheat>,
    ): List<Cheat> {
        val activeCodes = active.map { it.code }.toSet()
        return previouslyApplied
            .filter { it.code !in activeCodes }
            .mapNotNull { switchedOff ->
                val twinNames = undoNames(switchedOff.description)
                cheats.firstOrNull { it.description.trim().lowercase() in twinNames && it.code !in activeCodes }
            }
            .distinctBy { it.code }
    }

    private fun undoNames(description: String): Set<String> {
        val base = description.trim().lowercase()
        return setOf("[disable]", "[disabled]", "(disable)", "(disabled)", "[off]", "(off)")
            .map { "$base $it" }
            .toSet()
    }

    /** What to send for a cheat, or null if it must not be sent. */
    private fun chunksOf(cheat: Cheat): List<String>? {
        if (CheatPolicy.isNotNeeded(cheat.description, systemId)) return null

        val code = CheatPolicy.withoutHookLines(cheat.code, systemId)
        if (code.isBlank()) return null
        return CheatPolicy.chunks(code, systemId)
    }
}
