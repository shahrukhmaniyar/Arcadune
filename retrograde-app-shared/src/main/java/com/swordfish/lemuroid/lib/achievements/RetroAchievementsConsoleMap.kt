package com.swordfish.lemuroid.lib.achievements

import com.swordfish.lemuroid.lib.library.SystemID

object RetroAchievementsConsoleMap {
    const val RC_CONSOLE_UNKNOWN = 0
    const val RC_CONSOLE_MEGA_DRIVE = 1
    const val RC_CONSOLE_NINTENDO_64 = 2
    const val RC_CONSOLE_SUPER_NINTENDO = 3
    const val RC_CONSOLE_GAMEBOY = 4
    const val RC_CONSOLE_GAMEBOY_ADVANCE = 5
    const val RC_CONSOLE_GAMEBOY_COLOR = 6
    const val RC_CONSOLE_NINTENDO = 7
    const val RC_CONSOLE_PC_ENGINE = 8
    const val RC_CONSOLE_SEGA_CD = 9
    const val RC_CONSOLE_MASTER_SYSTEM = 11
    const val RC_CONSOLE_PLAYSTATION = 12
    const val RC_CONSOLE_ATARI_LYNX = 13
    const val RC_CONSOLE_NEOGEO_POCKET = 14
    const val RC_CONSOLE_GAME_GEAR = 15
    const val RC_CONSOLE_NINTENDO_DS = 18
    const val RC_CONSOLE_ATARI_2600 = 25
    const val RC_CONSOLE_MS_DOS = 26
    const val RC_CONSOLE_ARCADE = 27
    const val RC_CONSOLE_PSP = 41
    const val RC_CONSOLE_ATARI_7800 = 51
    const val RC_CONSOLE_WONDERSWAN = 53

    fun getConsoleId(systemId: SystemID): Int {
        return when (systemId) {
            SystemID.NES -> RC_CONSOLE_NINTENDO
            SystemID.SNES -> RC_CONSOLE_SUPER_NINTENDO
            SystemID.GENESIS -> RC_CONSOLE_MEGA_DRIVE
            SystemID.GB -> RC_CONSOLE_GAMEBOY
            SystemID.GBC -> RC_CONSOLE_GAMEBOY_COLOR
            SystemID.GBA -> RC_CONSOLE_GAMEBOY_ADVANCE
            SystemID.N64 -> RC_CONSOLE_NINTENDO_64
            SystemID.SMS -> RC_CONSOLE_MASTER_SYSTEM
            SystemID.PSP -> RC_CONSOLE_PSP
            SystemID.NDS -> RC_CONSOLE_NINTENDO_DS
            SystemID.GG -> RC_CONSOLE_GAME_GEAR
            SystemID.ATARI2600 -> RC_CONSOLE_ATARI_2600
            SystemID.PSX -> RC_CONSOLE_PLAYSTATION
            SystemID.FBNEO -> RC_CONSOLE_ARCADE
            SystemID.MAME2003PLUS -> RC_CONSOLE_ARCADE
            SystemID.PC_ENGINE -> RC_CONSOLE_PC_ENGINE
            SystemID.LYNX -> RC_CONSOLE_ATARI_LYNX
            SystemID.ATARI7800 -> RC_CONSOLE_ATARI_7800
            SystemID.SEGACD -> RC_CONSOLE_SEGA_CD
            SystemID.NGP -> RC_CONSOLE_NEOGEO_POCKET
            SystemID.NGC -> RC_CONSOLE_NEOGEO_POCKET
            SystemID.WS -> RC_CONSOLE_WONDERSWAN
            SystemID.WSC -> RC_CONSOLE_WONDERSWAN
            SystemID.DOS -> RC_CONSOLE_MS_DOS
            SystemID.NINTENDO_3DS -> RC_CONSOLE_UNKNOWN
        }
    }

    fun isSupported(systemId: SystemID): Boolean {
        return getConsoleId(systemId) != RC_CONSOLE_UNKNOWN
    }
}
