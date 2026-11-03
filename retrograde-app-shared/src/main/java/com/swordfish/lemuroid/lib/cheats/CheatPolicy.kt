package com.swordfish.lemuroid.lib.cheats

/**
 * Decides how a cheat code is handed to an emulator core.
 *
 * Some cores copy a code into a fixed 256 byte buffer and abort the whole game when it does not
 * fit (the NES core does this for codes of 256 characters or more). A code longer than
 * [Cheat.MAX_CODE_LENGTH] is therefore only sent when it can be cut into shorter pieces without
 * changing what it does, and never otherwise.
 *
 * The NES, SNES and Sega (Genesis, Master System, Game Gear, Sega CD) cores read a code as several
 * independent codes joined by "+", so on those systems a long code can be sent as several shorter
 * ones. On other systems the parts of a code can depend on each other (for example a condition
 * followed by the line it controls), so they are not split.
 */
object CheatPolicy {
    // Values of SystemID.dbname. Each of these cores was checked in its source code.
    private val SYSTEMS_WITH_INDEPENDENT_PARTS = setOf("nes", "snes", "md", "sms", "gg", "scd", "pce")

    // Cores whose retro_cheat_set does nothing at all (empty function in their source): Neo Geo Pocket,
    // Atari 2600, Atari 7800, Lynx and the MAME arcade core.
    private val SYSTEMS_WITHOUT_CHEAT_SUPPORT = setOf("ngp", "ngc", "atari2600", "atari7800", "lynx", "mame2003plus")

    /** True when the emulator of this system has no cheat support, so no code can ever work. */
    fun systemIgnoresCheats(systemId: String): Boolean = systemId in SYSTEMS_WITHOUT_CHEAT_SUPPORT

    /** The pieces to send to the core, or null if the code cannot be sent safely. */
    fun chunks(
        code: String,
        systemId: String,
    ): List<String>? {
        if (systemIgnoresCheats(systemId)) return null
        if (code.length <= Cheat.MAX_CODE_LENGTH) return listOf(code)
        if (systemId !in SYSTEMS_WITH_INDEPENDENT_PARTS) return null

        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        for (part in code.split('+')) {
            if (part.length > Cheat.MAX_CODE_LENGTH) return null

            val lengthWithPart = if (current.isEmpty()) part.length else current.length + 1 + part.length
            if (lengthWithPart > Cheat.MAX_CODE_LENGTH) {
                chunks += current.toString()
                current = StringBuilder(part)
            } else {
                if (current.isNotEmpty()) current.append('+')
                current.append(part)
            }
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }

    fun isSupported(
        code: String,
        systemId: String,
    ): Boolean = chunks(code, systemId) != null

    /**
     * Some cheat files contain a "master" code, named for example "Enable Code (Must Be On)". It is
     * written for the real cheat cartridge, which needs it to hook into the game.
     */
    fun isMasterCode(description: String): Boolean {
        val name = description.lowercase()
        return "must be on" in name || "master code" in name || "enable code" in name
    }

    /** Systems whose emulator applies cheats by itself and must not be given hook (master) codes. */
    private val SYSTEMS_WITHOUT_HOOK_CODES = setOf("gba")

    /**
     * True for master codes on systems that do not need them. On the GBA the emulator (mGBA) turns the
     * hook line of a master code into a patch of the game's program at a fixed address. That address
     * only fits one version of the game, and on another version the patch corrupts the game and can
     * leave the emulator in an endless loop, so these cheats are never sent.
     */
    fun isNotNeeded(
        description: String,
        systemId: String,
    ): Boolean = systemId in SYSTEMS_WITHOUT_HOOK_CODES && isMasterCode(description)

    /**
     * Removes the hook and game id lines of Code Breaker codes (8 hex digits, then 4, starting with 0 or 1)
     * from a GBA code made of address and value parts joined with "+". Other codes are returned as they are.
     */
    fun withoutHookLines(
        code: String,
        systemId: String,
    ): String {
        if (systemId !in SYSTEMS_WITHOUT_HOOK_CODES) return code

        val parts = code.split('+')
        if (parts.size % 2 != 0) return code

        val kept = mutableListOf<String>()
        for (i in parts.indices step 2) {
            val address = parts[i]
            val value = parts[i + 1]
            val isCodeBreakerLine = address.length == 8 && value.length == 4 && (address + value).all { it.isHex() }
            if (isCodeBreakerLine && (address[0] == '0' || address[0] == '1')) continue
            kept += address
            kept += value
        }
        return kept.joinToString("+")
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
