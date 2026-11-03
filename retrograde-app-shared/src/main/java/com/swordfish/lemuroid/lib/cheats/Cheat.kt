package com.swordfish.lemuroid.lib.cheats

import kotlinx.serialization.Serializable

/** One cheat code of a game: a name, the code as the emulator core expects it, and whether it is on. */
@Serializable
data class Cheat(
    val description: String,
    val code: String,
    val enabled: Boolean = false,
) {
    companion object {
        /** Longest code that is sent to a core in one piece. See [CheatPolicy]. */
        const val MAX_CODE_LENGTH = 255
    }
}
