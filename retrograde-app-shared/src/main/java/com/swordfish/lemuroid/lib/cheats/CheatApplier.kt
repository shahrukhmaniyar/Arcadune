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
 */
class CheatApplier(
    private val target: CheatTarget,
    private val systemId: String,
) {
    /** Returns how many cheats were switched on, or null if the emulator's cheats could not be cleared. */
    fun apply(cheats: List<Cheat>): Int? {
        if (!target.clear()) return null

        var active = 0
        var nextIndex = 0
        for (cheat in cheats) {
            if (!cheat.enabled || CheatPolicy.isNotNeeded(cheat.description, systemId)) continue

            val code = CheatPolicy.withoutHookLines(cheat.code, systemId)
            if (code.isBlank()) continue
            val chunks = CheatPolicy.chunks(code, systemId) ?: continue

            chunks.forEach { target.enable(nextIndex++, it) }
            active++
        }
        return active
    }
}
