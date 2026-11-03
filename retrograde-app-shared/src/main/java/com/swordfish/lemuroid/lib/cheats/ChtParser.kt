package com.swordfish.lemuroid.lib.cheats

import java.util.TreeMap

/**
 * Reads cheat files in the libretro ".cht" format:
 *
 *     cheats = 2
 *     cheat0_desc = "Infinite Lives"
 *     cheat0_code = "0033:09"
 *     cheat0_enable = false
 *
 * Imported cheats always start switched off, whatever the file says.
 */
object ChtParser {
    private val LINE = Regex("""^\s*cheat(\d+)_(desc|code)\s*=\s*(.*?)\s*$""")

    fun parse(text: String): List<Cheat> {
        val descriptions = TreeMap<Int, String>()
        val codes = TreeMap<Int, String>()

        for (line in text.lineSequence()) {
            val match = LINE.matchEntire(line) ?: continue
            val index = match.groupValues[1].toIntOrNull() ?: continue
            val value = unquote(match.groupValues[3])
            when (match.groupValues[2]) {
                "desc" -> descriptions[index] = value
                "code" -> codes[index] = value
            }
        }

        return codes
            .filterValues { it.isNotBlank() }
            .map { (index, code) ->
                val description = descriptions[index]?.takeIf { it.isNotBlank() } ?: "Cheat ${index + 1}"
                Cheat(description = description, code = code, enabled = false)
            }
    }

    private fun unquote(value: String): String = value.removeSurrounding("\"").trim()
}
