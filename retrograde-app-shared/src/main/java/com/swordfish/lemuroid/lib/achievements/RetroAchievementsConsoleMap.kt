package com.swordfish.lemuroid.lib.achievements

import com.swordfish.lemuroid.lib.library.CoreID
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
    const val RC_CONSOLE_SEGA_32X = 10
    const val RC_CONSOLE_MASTER_SYSTEM = 11
    const val RC_CONSOLE_PLAYSTATION = 12
    const val RC_CONSOLE_ATARI_LYNX = 13
    const val RC_CONSOLE_NEOGEO_POCKET = 14
    const val RC_CONSOLE_GAME_GEAR = 15
    const val RC_CONSOLE_NINTENDO_DS = 18
    const val RC_CONSOLE_ATARI_2600 = 25
    const val RC_CONSOLE_MS_DOS = 26
    const val RC_CONSOLE_ARCADE = 27
    const val RC_CONSOLE_MSX = 29
    const val RC_CONSOLE_COMMODORE_64 = 30
    const val RC_CONSOLE_APPLE_II = 38
    const val RC_CONSOLE_SATURN = 39
    const val RC_CONSOLE_DREAMCAST = 40
    const val RC_CONSOLE_PSP = 41
    const val RC_CONSOLE_3DO = 43
    const val RC_CONSOLE_COLECOVISION = 44
    const val RC_CONSOLE_ATARI_5200 = 50
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
            SystemID.DREAMCAST -> RC_CONSOLE_DREAMCAST
            SystemID.THREEDO -> RC_CONSOLE_3DO
            SystemID.SATURN -> RC_CONSOLE_SATURN
            SystemID.SEGA32X -> RC_CONSOLE_SEGA_32X
            SystemID.ATARI5200 -> RC_CONSOLE_ATARI_5200
            SystemID.ATARI800 -> RC_CONSOLE_UNKNOWN
            SystemID.COLECOVISION -> RC_CONSOLE_COLECOVISION
            SystemID.C64 -> RC_CONSOLE_COMMODORE_64
            SystemID.APPLEII -> RC_CONSOLE_APPLE_II
            SystemID.MSX -> RC_CONSOLE_MSX
        }
    }

    fun isSupported(systemId: SystemID): Boolean {
        return getConsoleId(systemId) != RC_CONSOLE_UNKNOWN
    }

    fun isCoreHardcoreCompliant(
        coreId: CoreID,
        systemId: SystemID,
    ): Boolean = hardcoreUnsupportedReason(coreId, systemId) == null

    fun hardcoreUnsupportedReason(
        coreId: CoreID,
        systemId: SystemID,
    ): String? {
        val coreReason =
            when (coreId) {
                CoreID.CITRA, CoreID.AZAHAR ->
                    "${libretroLibraryName(
                        coreId,
                    )} (3DS) exposes zero RetroAchievements RAM and has no rcheevos 12.5 console memory map"
                CoreID.ATARI800 ->
                    "Atari800 has an internal F1 trainer/state OSD menu and no RetroAchievements console ID"
                CoreID.A5200 ->
                    "a5200 exposes zero RetroAchievements RAM and rcheevos 12.5 lacks Atari 5200 file hashing"
                CoreID.YABASANSHIRO ->
                    "YabaSanshiro exposes zero Saturn Work RAM — switch Saturn core to Beetle Saturn for Hardcore"
                CoreID.MAME2003PLUS ->
                    "MAME 2003-Plus exposes zero RetroAchievements RAM and has an unblocked internal cheat/DIP OSD"
                CoreID.BSNES ->
                    "bsnes exposes zero SNES Work RAM to RetroAchievements — switch to Snes9x or Supafaust"
                CoreID.RACE ->
                    "RACE maps Neo Geo Pocket RAM at 0x0000 instead of 0x4000 — switch to Beetle NeoPop"
                CoreID.GPSP ->
                    "gpSP is unsupported for Hardcore due to dynarec/HLE BIOS memory limits — switch to mGBA"
                CoreID.ARMGBA ->
                    "armGBA is not an approved RetroAchievements Hardcore core — switch to mGBA, Gambatte, or SameBoy"
                CoreID.BLASTEM ->
                    "BlastEm uses 16-bit word-swapped Genesis RAM — switch to Genesis Plus GX or PicoDrive for Hardcore"
                CoreID.PARALLEL_N64 ->
                    "ParaLLEl N64 is unsupported for Hardcore due to RDRAM/VI limits — switch to Mupen64Plus-Next"
                else -> null
            }
        if (coreReason != null) {
            return coreReason
        }
        if (!isSupported(systemId)) {
            return "${systemId.dbname} does not have RetroAchievements console support in rcheevos 12.5"
        }
        return null
    }

    fun libretroLibraryName(coreId: CoreID): String {
        return when (coreId) {
            CoreID.STELLA -> "Stella"
            CoreID.FCEUMM -> "FCEUmm"
            CoreID.SNES9X -> "Snes9x"
            CoreID.GENESIS_PLUS_GX -> "Genesis Plus GX"
            CoreID.GAMBATTE -> "Gambatte"
            CoreID.MGBA -> "mGBA"
            CoreID.MUPEN64_PLUS_NEXT -> "Mupen64Plus-Next"
            CoreID.PCSX_REARMED -> "PCSX-ReARMed"
            CoreID.PPSSPP -> "PPSSPP"
            CoreID.FBNEO -> "FinalBurn Neo"
            CoreID.MAME2003PLUS -> "MAME 2003-Plus"
            CoreID.MELONDS -> "melonDS"
            CoreID.HANDY -> "Handy"
            CoreID.MEDNAFEN_PCE_FAST -> "Beetle PCE Fast"
            CoreID.PROSYSTEM -> "ProSystem"
            CoreID.MEDNAFEN_NGP -> "Beetle NeoPop"
            CoreID.MEDNAFEN_WSWAN -> "Beetle WonderSwan"
            CoreID.CITRA -> "Citra"
            CoreID.DOSBOX_PURE -> "DOSBox-pure"
            CoreID.FLYCAST -> "Flycast"
            CoreID.OPERA -> "Opera"
            CoreID.YABASANSHIRO -> "YabaSanshiro"
            CoreID.PICODRIVE -> "PicoDrive"
            CoreID.A5200 -> "a5200"
            CoreID.ATARI800 -> "Atari800"
            CoreID.GEARCOLECO -> "Gearcoleco"
            CoreID.VICE_X64 -> "VICE x64"
            CoreID.APPLEWIN -> "AppleWin"
            CoreID.FMSX -> "fMSX"
            CoreID.ARMGBA -> "armGBA"
            CoreID.GPSP -> "gpSP"
            CoreID.VBAM -> "VBA-M"
            CoreID.MEDNAFEN_GBA -> "Beetle GBA"
            CoreID.SAMEBOY -> "SameBoy"
            CoreID.GEARBOY -> "Gearboy"
            CoreID.MESEN -> "Mesen"
            CoreID.NESTOPIA -> "Nestopia"
            CoreID.QUICKNES -> "QuickNES"
            CoreID.MEDNAFEN_SUPAFAUST -> "Beetle Supafaust"
            CoreID.BSNES -> "bsnes"
            CoreID.GEARSYSTEM -> "Gearsystem"
            CoreID.BLASTEM -> "BlastEm"
            CoreID.SWANSTATION -> "SwanStation"
            CoreID.MEDNAFEN_PSX -> "Beetle PSX"
            CoreID.PARALLEL_N64 -> "ParaLLEl N64"
            CoreID.DESMUME -> "DeSmuME"
            CoreID.AZAHAR -> "Azahar"
            CoreID.MEDNAFEN_SATURN -> "Beetle Saturn"
            CoreID.MEDNAFEN_PCE -> "Beetle PCE"
            CoreID.GEARLYNX -> "Gearlynx"
            CoreID.RACE -> "RACE"
            CoreID.STELLA2014 -> "Stella 2014"
        }
    }
}
