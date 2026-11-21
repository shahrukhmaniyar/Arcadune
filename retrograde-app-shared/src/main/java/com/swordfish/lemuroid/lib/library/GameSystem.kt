/*
 * GameSystem.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.lib.library

import androidx.annotation.StringRes
import com.swordfish.lemuroid.lib.R
import com.swordfish.lemuroid.lib.core.CoreVariable
import java.util.Locale

data class GameSystem(
    val id: SystemID,
    val libretroFullName: String,
    @StringRes
    val titleResId: Int,
    @StringRes
    val shortTitleResId: Int,
    val systemCoreConfigs: List<SystemCoreConfig>,
    val uniqueExtensions: List<String>,
    val scanOptions: ScanOptions = ScanOptions(),
    val supportedExtensions: List<String> = uniqueExtensions,
    val hasMultiDiskSupport: Boolean = false,
    val fastForwardSupport: Boolean = true,
    val maxFrameSpeed: Int = 8,
    val hasTouchScreen: Boolean = false,
) {
    companion object {
        private val SYSTEMS =
            listOf(
                GameSystem(
                    SystemID.ATARI2600,
                    "Atari - 2600",
                    R.string.game_system_title_atari2600,
                    R.string.game_system_abbr_atari2600,
                    listOf(
                        SystemCoreConfig(
                            coreID = CoreID.STELLA,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "stella_filter",
                                        R.string.setting_stella_filter,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "disabled",
                                                R.string.value_stella_filter_disabled,
                                            ),
                                            ExposedSetting.Value(
                                                "composite",
                                                R.string.value_stella_filter_composite,
                                            ),
                                            ExposedSetting.Value(
                                                "s-video",
                                                R.string.value_stella_filter_svideo,
                                            ),
                                            ExposedSetting.Value("rgb", R.string.value_stella_filter_rgb),
                                            ExposedSetting.Value(
                                                "badly adjusted",
                                                R.string.value_stella_filter_badlyadjusted,
                                            ),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "stella_crop_hoverscan",
                                        R.string.setting_stella_crop_hoverscan,
                                    ),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.ATARI_2600),
                                ),
                        ),
                        SystemCoreConfig(
                            coreID = CoreID.STELLA2014,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.ATARI_2600),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("a26"),
                ),
                GameSystem(
                    SystemID.NES,
                    "Nintendo - Nintendo Entertainment System",
                    R.string.game_system_title_nes,
                    R.string.game_system_abbr_nes,
                    listOf(
                        SystemCoreConfig(
                            CoreID.FCEUMM,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "fceumm_overscan_h",
                                        R.string.setting_fceumm_overscan_h,
                                    ),
                                    ExposedSetting(
                                        "fceumm_overscan_v",
                                        R.string.setting_fceumm_overscan_v,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "fceumm_nospritelimit",
                                        R.string.setting_fceumm_nospritelimit,
                                    ),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.NES),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.MESEN,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.NES),
                                    1 to arrayListOf(ControllerConfigs.NES),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.NESTOPIA,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.NES),
                                    1 to arrayListOf(ControllerConfigs.NES),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.QUICKNES,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.NES),
                                    1 to arrayListOf(ControllerConfigs.NES),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("nes", "fds", "unf", "unif"),
                ),
                GameSystem(
                    SystemID.SNES,
                    "Nintendo - Super Nintendo Entertainment System",
                    R.string.game_system_title_snes,
                    R.string.game_system_abbr_snes,
                    listOf(
                        SystemCoreConfig(
                            CoreID.SNES9X,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.SNES),
                                    1 to arrayListOf(ControllerConfigs.SNES),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.MEDNAFEN_SUPAFAUST,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.SNES),
                                    1 to arrayListOf(ControllerConfigs.SNES),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.BSNES,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.SNES),
                                    1 to arrayListOf(ControllerConfigs.SNES),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("smc", "sfc", "swc", "fig", "bs"),
                ),
                GameSystem(
                    SystemID.SMS,
                    "Sega - Master System - Mark III",
                    R.string.game_system_title_sms,
                    R.string.game_system_abbr_sms,
                    listOf(
                        SystemCoreConfig(
                            CoreID.GENESIS_PLUS_GX,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "genesis_plus_gx_blargg_ntsc_filter",
                                        R.string.setting_genesis_plus_gx_blargg_ntsc_filter,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "disabled",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_disabled,
                                            ),
                                            ExposedSetting.Value(
                                                "monochrome",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_monochrome,
                                            ),
                                            ExposedSetting.Value(
                                                "composite",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_composite,
                                            ),
                                            ExposedSetting.Value(
                                                "svideo",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_svideo,
                                            ),
                                            ExposedSetting.Value(
                                                "rgb",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_rgb,
                                            ),
                                        ),
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "genesis_plus_gx_no_sprite_limit",
                                        R.string.setting_genesis_plus_gx_no_sprite_limit,
                                    ),
                                    ExposedSetting(
                                        "genesis_plus_gx_overscan",
                                        R.string.setting_genesis_plus_gx_overscan,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "disabled",
                                                R.string.value_genesis_plus_gx_overscan_disabled,
                                            ),
                                            ExposedSetting.Value(
                                                "top/bottom",
                                                R.string.value_genesis_plus_gx_overscan_topbottom,
                                            ),
                                            ExposedSetting.Value(
                                                "left/right",
                                                R.string.value_genesis_plus_gx_overscan_leftright,
                                            ),
                                            ExposedSetting.Value(
                                                "full",
                                                R.string.value_genesis_plus_gx_overscan_full,
                                            ),
                                        ),
                                    ),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.SMS),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.GEARSYSTEM,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.SMS),
                                    1 to arrayListOf(ControllerConfigs.SMS),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.PICODRIVE,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "picodrive_frameskip",
                                        R.string.setting_picodrive_frameskip,
                                    ),
                                    ExposedSetting(
                                        "picodrive_sprlim",
                                        R.string.setting_picodrive_sprlim,
                                    ),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.SMS),
                                    1 to arrayListOf(ControllerConfigs.SMS),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("sms", "sg"),
                ),
                GameSystem(
                    SystemID.GENESIS,
                    "Sega - Mega Drive - Genesis",
                    R.string.game_system_title_genesis,
                    R.string.game_system_abbr_genesis,
                    listOf(
                        SystemCoreConfig(
                            CoreID.GENESIS_PLUS_GX,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "genesis_plus_gx_blargg_ntsc_filter",
                                        R.string.setting_genesis_plus_gx_blargg_ntsc_filter,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "disabled",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_disabled,
                                            ),
                                            ExposedSetting.Value(
                                                "monochrome",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_monochrome,
                                            ),
                                            ExposedSetting.Value(
                                                "composite",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_composite,
                                            ),
                                            ExposedSetting.Value(
                                                "svideo",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_svideo,
                                            ),
                                            ExposedSetting.Value(
                                                "rgb",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_rgb,
                                            ),
                                        ),
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "genesis_plus_gx_no_sprite_limit",
                                        R.string.setting_genesis_plus_gx_no_sprite_limit,
                                    ),
                                    ExposedSetting(
                                        "genesis_plus_gx_overscan",
                                        R.string.setting_genesis_plus_gx_overscan,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "disabled",
                                                R.string.value_genesis_plus_gx_overscan_disabled,
                                            ),
                                            ExposedSetting.Value(
                                                "top/bottom",
                                                R.string.value_genesis_plus_gx_overscan_topbottom,
                                            ),
                                            ExposedSetting.Value(
                                                "left/right",
                                                R.string.value_genesis_plus_gx_overscan_leftright,
                                            ),
                                            ExposedSetting.Value(
                                                "full",
                                                R.string.value_genesis_plus_gx_overscan_full,
                                            ),
                                        ),
                                    ),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to
                                        arrayListOf(
                                            ControllerConfigs.GENESIS_3,
                                            ControllerConfigs.GENESIS_6,
                                        ),
                                    1 to
                                        arrayListOf(
                                            ControllerConfigs.GENESIS_3,
                                            ControllerConfigs.GENESIS_6,
                                        ),
                                    2 to
                                        arrayListOf(
                                            ControllerConfigs.GENESIS_3,
                                            ControllerConfigs.GENESIS_6,
                                        ),
                                    3 to
                                        arrayListOf(
                                            ControllerConfigs.GENESIS_3,
                                            ControllerConfigs.GENESIS_6,
                                        ),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.BLASTEM,
                            controllerConfigs =
                                hashMapOf(
                                    0 to
                                        arrayListOf(
                                            ControllerConfigs.BLASTEM_3,
                                            ControllerConfigs.BLASTEM_6,
                                        ),
                                    1 to
                                        arrayListOf(
                                            ControllerConfigs.BLASTEM_3,
                                            ControllerConfigs.BLASTEM_6,
                                        ),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.PICODRIVE,
                            controllerConfigs =
                                hashMapOf(
                                    0 to
                                        arrayListOf(
                                            ControllerConfigs.PICODRIVE_3,
                                            ControllerConfigs.PICODRIVE_6,
                                        ),
                                    1 to
                                        arrayListOf(
                                            ControllerConfigs.PICODRIVE_3,
                                            ControllerConfigs.PICODRIVE_6,
                                        ),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "picodrive_frameskip",
                                        R.string.setting_picodrive_frameskip,
                                    ),
                                    ExposedSetting(
                                        "picodrive_sprlim",
                                        R.string.setting_picodrive_sprlim,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "picodrive_overclk68k",
                                        R.string.setting_picodrive_overclk68k,
                                    ),
                                    ExposedSetting(
                                        "picodrive_region",
                                        R.string.setting_picodrive_region,
                                    ),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("gen", "smd", "md", "mdx", "68k"),
                    supportedExtensions = listOf("gen", "smd", "md", "mdx", "68k", "bin"),
                ),
                GameSystem(
                    SystemID.SEGACD,
                    "Sega - Mega-CD - Sega CD",
                    R.string.game_system_title_scd,
                    R.string.game_system_abbr_scd,
                    listOf(
                        SystemCoreConfig(
                            CoreID.GENESIS_PLUS_GX,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "genesis_plus_gx_blargg_ntsc_filter",
                                        R.string.setting_genesis_plus_gx_blargg_ntsc_filter,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "disabled",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_disabled,
                                            ),
                                            ExposedSetting.Value(
                                                "monochrome",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_monochrome,
                                            ),
                                            ExposedSetting.Value(
                                                "composite",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_composite,
                                            ),
                                            ExposedSetting.Value(
                                                "svideo",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_svideo,
                                            ),
                                            ExposedSetting.Value(
                                                "rgb",
                                                R.string.value_genesis_plus_gx_blargg_ntsc_filter_rgb,
                                            ),
                                        ),
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "genesis_plus_gx_no_sprite_limit",
                                        R.string.setting_genesis_plus_gx_no_sprite_limit,
                                    ),
                                    ExposedSetting(
                                        "genesis_plus_gx_overscan",
                                        R.string.setting_genesis_plus_gx_overscan,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "disabled",
                                                R.string.value_genesis_plus_gx_overscan_disabled,
                                            ),
                                            ExposedSetting.Value(
                                                "top/bottom",
                                                R.string.value_genesis_plus_gx_overscan_topbottom,
                                            ),
                                            ExposedSetting.Value(
                                                "left/right",
                                                R.string.value_genesis_plus_gx_overscan_leftright,
                                            ),
                                            ExposedSetting.Value(
                                                "full",
                                                R.string.value_genesis_plus_gx_overscan_full,
                                            ),
                                        ),
                                    ),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to
                                        arrayListOf(
                                            ControllerConfigs.GENESIS_3,
                                            ControllerConfigs.GENESIS_6,
                                        ),
                                    1 to
                                        arrayListOf(
                                            ControllerConfigs.GENESIS_3,
                                            ControllerConfigs.GENESIS_6,
                                        ),
                                    2 to
                                        arrayListOf(
                                            ControllerConfigs.GENESIS_3,
                                            ControllerConfigs.GENESIS_6,
                                        ),
                                    3 to
                                        arrayListOf(
                                            ControllerConfigs.GENESIS_3,
                                            ControllerConfigs.GENESIS_6,
                                        ),
                                ),
                            regionalBIOSFiles =
                                mapOf(
                                    "Europe" to "bios_CD_E.bin",
                                    "Japan" to "bios_CD_J.bin",
                                    "USA" to "bios_CD_U.bin",
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.PICODRIVE,
                            controllerConfigs =
                                hashMapOf(
                                    0 to
                                        arrayListOf(
                                            ControllerConfigs.PICODRIVE_3,
                                            ControllerConfigs.PICODRIVE_6,
                                        ),
                                    1 to
                                        arrayListOf(
                                            ControllerConfigs.PICODRIVE_3,
                                            ControllerConfigs.PICODRIVE_6,
                                        ),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "picodrive_frameskip",
                                        R.string.setting_picodrive_frameskip,
                                    ),
                                    ExposedSetting(
                                        "picodrive_sprlim",
                                        R.string.setting_picodrive_sprlim,
                                    ),
                                ),
                            regionalBIOSFiles =
                                mapOf(
                                    "Europe" to "bios_CD_E.bin",
                                    "Japan" to "bios_CD_J.bin",
                                    "USA" to "bios_CD_U.bin",
                                ),
                            supportsLibretroVFS = false,
                        ),
                    ),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = false,
                            scanByPathAndSupportedExtensions = true,
                            scanBySimilarSerial = true,
                        ),
                    uniqueExtensions = listOf(),
                    supportedExtensions = listOf("cue", "iso", "chd", "m3u"),
                    hasMultiDiskSupport = true,
                ),
                GameSystem(
                    SystemID.GG,
                    "Sega - Game Gear",
                    R.string.game_system_title_gg,
                    R.string.game_system_abbr_gg,
                    listOf(
                        SystemCoreConfig(
                            CoreID.GENESIS_PLUS_GX,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "genesis_plus_gx_lcd_filter",
                                        R.string.setting_genesis_plus_gx_lcd_filter,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "genesis_plus_gx_no_sprite_limit",
                                        R.string.setting_genesis_plus_gx_no_sprite_limit,
                                    ),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GG),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.GEARSYSTEM,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GG),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("gg"),
                ),
                GameSystem(
                    SystemID.GB,
                    "Nintendo - Game Boy",
                    R.string.game_system_title_gb,
                    R.string.game_system_abbr_gb,
                    listOf(
                        SystemCoreConfig(
                            CoreID.GAMBATTE,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "gambatte_gb_colorization",
                                        R.string.setting_gambatte_gb_colorization,
                                    ),
                                    ExposedSetting(
                                        "gambatte_gb_internal_palette",
                                        R.string.setting_gambatte_gb_internal_palette,
                                    ),
                                    ExposedSetting(
                                        "gambatte_mix_frames",
                                        R.string.setting_gambatte_mix_frames,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "disabled",
                                                R.string.value_gambatte_mix_frames_disabled,
                                            ),
                                            ExposedSetting.Value(
                                                "mix",
                                                R.string.value_gambatte_mix_frames_mix,
                                            ),
                                            ExposedSetting.Value(
                                                "lcd_ghosting",
                                                R.string.value_gambatte_mix_frames_lcd_ghosting,
                                            ),
                                            ExposedSetting.Value(
                                                "lcd_ghosting_fast",
                                                R.string.value_gambatte_mix_frames_lcd_ghosting_fast,
                                            ),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "gambatte_dark_filter_level",
                                        R.string.setting_gambatte_dark_filter_level,
                                    ),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("gambatte_gb_colorization", "internal"),
                                    CoreVariable("gambatte_gb_internal_palette", "GB - Pocket"),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GB),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.ARMGBA,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "armgba_gb_screen",
                                        R.string.setting_armgba_gb_screen,
                                    ),
                                    ExposedSetting(
                                        "armgba_lcd_ghosting",
                                        R.string.setting_armgba_lcd_ghosting,
                                    ),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GB),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.SAMEBOY,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "sameboy_color_correction_mode",
                                        R.string.setting_sameboy_color_correction,
                                    ),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GB),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.GEARBOY,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GB),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.VBAM,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GB),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("gb", "dmg"),
                ),
                GameSystem(
                    SystemID.GBC,
                    "Nintendo - Game Boy Color",
                    R.string.game_system_title_gbc,
                    R.string.game_system_abbr_gbc,
                    listOf(
                        SystemCoreConfig(
                            CoreID.GAMBATTE,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "gambatte_mix_frames",
                                        R.string.setting_gambatte_mix_frames,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "disabled",
                                                R.string.value_gambatte_mix_frames_disabled,
                                            ),
                                            ExposedSetting.Value(
                                                "mix",
                                                R.string.value_gambatte_mix_frames_mix,
                                            ),
                                            ExposedSetting.Value(
                                                "lcd_ghosting",
                                                R.string.value_gambatte_mix_frames_lcd_ghosting,
                                            ),
                                            ExposedSetting.Value(
                                                "lcd_ghosting_fast",
                                                R.string.value_gambatte_mix_frames_lcd_ghosting_fast,
                                            ),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "gambatte_gbc_color_correction",
                                        R.string.setting_gambatte_gbc_color_correction,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "disabled",
                                                R.string.value_gambatte_gbc_color_correction_disabled,
                                            ),
                                            ExposedSetting.Value(
                                                "always",
                                                R.string.value_gambatte_gbc_color_correction_always,
                                            ),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "gambatte_dark_filter_level",
                                        R.string.setting_gambatte_dark_filter_level,
                                    ),
                                ),
                            rumbleSupported = true,
                            defaultSettings =
                                listOf(
                                    CoreVariable("gambatte_gbc_color_correction", "disabled"),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GB),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.ARMGBA,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "armgba_gb_color_correction",
                                        R.string.setting_armgba_color_correction,
                                    ),
                                    ExposedSetting(
                                        "armgba_lcd_ghosting",
                                        R.string.setting_armgba_lcd_ghosting,
                                    ),
                                ),
                            rumbleSupported = true,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GB),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.SAMEBOY,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "sameboy_color_correction_mode",
                                        R.string.setting_sameboy_color_correction,
                                    ),
                                ),
                            rumbleSupported = true,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GB),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.GEARBOY,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GB),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.VBAM,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GB),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("gbc", "cgb"),
                ),
                GameSystem(
                    SystemID.GBA,
                    "Nintendo - Game Boy Advance",
                    R.string.game_system_title_gba,
                    R.string.game_system_abbr_gba,
                    listOf(
                        SystemCoreConfig(
                            CoreID.MGBA,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "mgba_solar_sensor_level",
                                        R.string.setting_mgba_solar_sensor_level,
                                    ),
                                    ExposedSetting(
                                        "mgba_interframe_blending",
                                        R.string.setting_mgba_interframe_blending,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "OFF",
                                                R.string.value_mgba_interframe_blending_off,
                                            ),
                                            ExposedSetting.Value(
                                                "mix",
                                                R.string.value_mgba_interframe_blending_mix,
                                            ),
                                            ExposedSetting.Value(
                                                "lcd_ghosting",
                                                R.string.value_mgba_interframe_blending_lcd_ghosting,
                                            ),
                                            ExposedSetting.Value(
                                                "lcd_ghosting_fast",
                                                R.string.value_mgba_interframe_blending_lcd_ghosting_fast,
                                            ),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "mgba_frameskip",
                                        R.string.setting_mgba_frameskip,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "disabled",
                                                R.string.value_mgba_frameskip_disabled,
                                            ),
                                            ExposedSetting.Value("auto", R.string.value_mgba_frameskip_auto),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "mgba_color_correction",
                                        R.string.setting_mgba_color_correction,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "OFF",
                                                R.string.value_mgba_color_correction_off,
                                            ),
                                            ExposedSetting.Value(
                                                "GBA",
                                                R.string.value_mgba_color_correction_gba,
                                            ),
                                        ),
                                    ),
                                ),
                            rumbleSupported = true,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GBA),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.ARMGBA,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "armgba_skip_bios",
                                        R.string.setting_armgba_skip_bios,
                                    ),
                                    ExposedSetting(
                                        "armgba_color_correction",
                                        R.string.setting_armgba_color_correction,
                                    ),
                                    ExposedSetting(
                                        "armgba_lcd_ghosting",
                                        R.string.setting_armgba_lcd_ghosting,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "armgba_threaded_video",
                                        R.string.setting_armgba_threaded_video,
                                    ),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("armgba_skip_bios", "enabled"),
                                ),
                            rumbleSupported = true,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GBA),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.GPSP,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "gpsp_frameskip",
                                        R.string.setting_gpsp_frameskip,
                                    ),
                                    ExposedSetting(
                                        "gpsp_color_correction",
                                        R.string.setting_gpsp_color_correction,
                                    ),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("gpsp_bios", "builtin"),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GBA),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.VBAM,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GBA),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.MEDNAFEN_GBA,
                            requiredBIOSFiles = listOf("gba_bios.bin"),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.GBA),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("gba"),
                ),
                GameSystem(
                    SystemID.N64,
                    "Nintendo - Nintendo 64",
                    R.string.game_system_title_n64,
                    R.string.game_system_abbr_n64,
                    listOf(
                        SystemCoreConfig(
                            CoreID.MUPEN64_PLUS_NEXT,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "mupen64plus-43screensize",
                                        R.string.setting_mupen64plus_43screensize,
                                    ),
                                    ExposedSetting(
                                        "mupen64plus-cpucore",
                                        R.string.setting_mupen64plus_cpucore,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "dynamic_recompiler",
                                                R.string.value_mupen64plus_cpucore_dynamicrecompiler,
                                            ),
                                            ExposedSetting.Value(
                                                "pure_interpreter",
                                                R.string.value_mupen64plus_cpucore_pureinterpreter,
                                            ),
                                            ExposedSetting.Value(
                                                "cached_interpreter",
                                                R.string.value_mupen64plus_cpucore_cachedinterpreter,
                                            ),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "mupen64plus-BilinearMode",
                                        R.string.setting_mupen64plus_BilinearMode,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "standard",
                                                R.string.value_mupen64plus_bilinearmode_standard,
                                            ),
                                            ExposedSetting.Value(
                                                "3point",
                                                R.string.value_mupen64plus_bilinearmode_3point,
                                            ),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "mupen64plus-pak1",
                                        R.string.setting_mupen64plus_pak1,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "memory",
                                                R.string.value_mupen64plus_mupen64plus_pak1_memory,
                                            ),
                                            ExposedSetting.Value(
                                                "rumble",
                                                R.string.value_mupen64plus_mupen64plus_pak1_rumble,
                                            ),
                                            ExposedSetting.Value(
                                                "none",
                                                R.string.value_mupen64plus_mupen64plus_pak1_none,
                                            ),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "mupen64plus-pak2",
                                        R.string.setting_mupen64plus_pak2,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "none",
                                                R.string.value_mupen64plus_mupen64plus_pak2_none,
                                            ),
                                            ExposedSetting.Value(
                                                "rumble",
                                                R.string.value_mupen64plus_mupen64plus_pak2_rumble,
                                            ),
                                        ),
                                    ),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("mupen64plus-43screensize", "320x240"),
                                    CoreVariable("mupen64plus-FrameDuping", "True"),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.N64),
                                ),
                            rumbleSupported = true,
                            skipDuplicateFrames = false,
                        ),
                        SystemCoreConfig(
                            CoreID.PARALLEL_N64,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "parallel-n64-screensize",
                                        R.string.setting_parallel_n64_screensize,
                                    ),
                                    ExposedSetting(
                                        "parallel-n64-gfxplugin",
                                        R.string.setting_parallel_n64_gfxplugin,
                                    ),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.N64),
                                    1 to arrayListOf(ControllerConfigs.N64),
                                ),
                            rumbleSupported = true,
                            skipDuplicateFrames = false,
                        ),
                    ),
                    uniqueExtensions = listOf("n64", "z64", "v64"),
                    supportedExtensions = listOf("n64", "z64", "v64", "bin"),
                    // 8X crashes the N64 core (a native crash in its graphics code); 2X to 6X were tried and run.
                    maxFrameSpeed = 6,
                ),
                GameSystem(
                    SystemID.PSX,
                    "Sony - PlayStation",
                    R.string.game_system_title_psx,
                    R.string.game_system_abbr_psx,
                    listOf(
                        SystemCoreConfig(
                            CoreID.PCSX_REARMED,
                            controllerConfigs =
                                hashMapOf(
                                    0 to
                                        arrayListOf(
                                            ControllerConfigs.PSX_STANDARD,
                                            ControllerConfigs.PSX_DUALSHOCK,
                                        ),
                                    1 to
                                        arrayListOf(
                                            ControllerConfigs.PSX_STANDARD,
                                            ControllerConfigs.PSX_DUALSHOCK,
                                        ),
                                    2 to
                                        arrayListOf(
                                            ControllerConfigs.PSX_STANDARD,
                                            ControllerConfigs.PSX_DUALSHOCK,
                                        ),
                                    3 to
                                        arrayListOf(
                                            ControllerConfigs.PSX_STANDARD,
                                            ControllerConfigs.PSX_DUALSHOCK,
                                        ),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "pcsx_rearmed_frameskip",
                                        R.string.setting_pcsx_rearmed_frameskip,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "pcsx_rearmed_drc",
                                        R.string.setting_pcsx_rearmed_drc,
                                    ),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("pcsx_rearmed_drc", "disabled"),
                                    // Upstream R26+ defaults pcsx_rearmed_memcard2 to "shared" (pcsx-card2.mcd),
                                    // which would share Card 2 outside per-game .srm files; keep Card 2 disabled by default.
                                    CoreVariable("pcsx_rearmed_memcard2", "none"),
                                ),
                            rumbleSupported = true,
                            // LibretroDroid's VFS virtualOpen() duplicates FDs via dup(), sharing kernel f_pos;
                            // pcsx_rearmed's ISOopen() -> parsetoc() advances that shared offset before parsecue() reads,
                            // breaking SAF .cue/.bin loading when Direct game load is enabled.
                            supportsLibretroVFS = false,
                            skipDuplicateFrames = false,
                        ),
                        SystemCoreConfig(
                            CoreID.SWANSTATION,
                            controllerConfigs =
                                hashMapOf(
                                    0 to
                                        arrayListOf(
                                            ControllerConfigs.SWANSTATION_STANDARD,
                                            ControllerConfigs.SWANSTATION_DUALSHOCK,
                                        ),
                                    1 to
                                        arrayListOf(
                                            ControllerConfigs.SWANSTATION_STANDARD,
                                            ControllerConfigs.SWANSTATION_DUALSHOCK,
                                        ),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "swanstation_GPU_ResolutionScale",
                                        R.string.setting_swanstation_resolution_scale,
                                    ),
                                    ExposedSetting(
                                        "swanstation_GPU_WidescreenHack",
                                        R.string.setting_swanstation_widescreen_hack,
                                    ),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("swanstation_GPU_Renderer", "OpenGL"),
                                ),
                            anyOfBIOSFiles =
                                listOf(
                                    "scph5501.bin",
                                    "scph1001.bin",
                                    "scph7001.bin",
                                    "scph101.bin",
                                    "scph5500.bin",
                                    "scph5502.bin",
                                ),
                            rumbleSupported = true,
                            supportsLibretroVFS = false,
                            skipDuplicateFrames = false,
                        ),
                        SystemCoreConfig(
                            CoreID.MEDNAFEN_PSX,
                            controllerConfigs =
                                hashMapOf(
                                    0 to
                                        arrayListOf(
                                            ControllerConfigs.BEETLE_PSX_STANDARD,
                                            ControllerConfigs.BEETLE_PSX_DUALSHOCK,
                                        ),
                                    1 to
                                        arrayListOf(
                                            ControllerConfigs.BEETLE_PSX_STANDARD,
                                            ControllerConfigs.BEETLE_PSX_DUALSHOCK,
                                        ),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "beetle_psx_internal_resolution",
                                        R.string.setting_beetle_psx_internal_resolution,
                                    ),
                                    ExposedSetting(
                                        "beetle_psx_widescreen_hack",
                                        R.string.setting_beetle_psx_widescreen_hack,
                                    ),
                                ),
                            regionalBIOSFiles =
                                mapOf(
                                    "Japan" to "scph5500.bin",
                                    "USA" to "scph5501.bin",
                                    "Europe" to "scph5502.bin",
                                ),
                            rumbleSupported = true,
                            supportsLibretroVFS = false,
                            skipDuplicateFrames = false,
                        ),
                    ),
                    uniqueExtensions = listOf(),
                    supportedExtensions =
                        listOf("iso", "pbp", "chd", "cue", "m3u", "bin", "img", "ccd", "mds", "mdf"),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = false,
                            scanByPathAndSupportedExtensions = true,
                        ),
                    hasMultiDiskSupport = true,
                ),
                GameSystem(
                    SystemID.PSP,
                    "Sony - PlayStation Portable",
                    R.string.game_system_title_psp,
                    R.string.game_system_abbr_psp,
                    listOf(
                        SystemCoreConfig(
                            CoreID.PPSSPP,
                            defaultSettings =
                                listOf(
                                    CoreVariable("ppsspp_frame_duplication", "enabled"),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "ppsspp_auto_frameskip",
                                        R.string.setting_ppsspp_auto_frameskip,
                                    ),
                                    ExposedSetting(
                                        "ppsspp_frameskip",
                                        R.string.setting_mgba_frameskip,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "ppsspp_cpu_core",
                                        R.string.setting_ppsspp_cpu_core,
                                        arrayListOf(
                                            ExposedSetting.Value("JIT", R.string.value_ppsspp_cpu_core_jit),
                                            ExposedSetting.Value(
                                                "IR JIT",
                                                R.string.value_ppsspp_cpu_core_irjit,
                                            ),
                                            ExposedSetting.Value(
                                                "Interpreter",
                                                R.string.value_ppsspp_cpu_core_interpreter,
                                            ),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "ppsspp_internal_resolution",
                                        R.string.setting_ppsspp_internal_resolution,
                                    ),
                                    ExposedSetting(
                                        "ppsspp_texture_scaling_level",
                                        R.string.setting_ppsspp_texture_scaling_level,
                                    ),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.PSP),
                                ),
                            supportsLibretroVFS = true,
                        ),
                    ),
                    uniqueExtensions = listOf("cso"),
                    // Untested above 4X: the 3D renderer of the PSP core, like the N64 one, can crash at high speeds.
                    maxFrameSpeed = 4,
                    supportedExtensions = listOf("iso", "cso", "pbp", "chd"),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = true,
                            scanByPathAndSupportedExtensions = true,
                        ),
                ),
                GameSystem(
                    SystemID.FBNEO,
                    "FBNeo - Arcade Games",
                    R.string.game_system_title_arcade_fbneo,
                    R.string.game_system_abbr_arcade_fbneo,
                    listOf(
                        SystemCoreConfig(
                            CoreID.FBNEO,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "fbneo-frameskip-type",
                                        R.string.setting_fbneo_frameskip,
                                    ),
                                    ExposedSetting(
                                        "fbneo-cpu-speed-adjust",
                                        R.string.setting_fbneo_cpu_speed_adjust,
                                    ),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.FB_NEO_4, ControllerConfigs.FB_NEO_6),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf(),
                    supportedExtensions = listOf("zip"),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = false,
                            scanByPathAndFilename = true,
                            scanByPathAndSupportedExtensions = false,
                        ),
                ),
                GameSystem(
                    SystemID.MAME2003PLUS,
                    "MAME 2003-Plus",
                    R.string.game_system_title_arcade_mame2003_plus,
                    R.string.game_system_abbr_arcade_mame2003_plus,
                    listOf(
                        SystemCoreConfig(
                            CoreID.MAME2003PLUS,
                            statesSupported = false,
                            controllerConfigs =
                                hashMapOf(
                                    0 to
                                        arrayListOf(
                                            ControllerConfigs.MAME_2003_4,
                                            ControllerConfigs.MAME_2003_6,
                                        ),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf(),
                    supportedExtensions = listOf("zip"),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = false,
                            scanByPathAndFilename = true,
                            scanByPathAndSupportedExtensions = false,
                        ),
                ),
                GameSystem(
                    SystemID.NDS,
                    "Nintendo - Nintendo DS",
                    R.string.game_system_title_nds,
                    R.string.game_system_abbr_nds,
                    listOf(
                        SystemCoreConfig(
                            CoreID.MELONDS,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "melonds_screen_layout1",
                                        R.string.setting_melonds_screen_layout,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "top-bottom",
                                                R.string.value_melonds_screen_layout_topbottom,
                                            ),
                                            ExposedSetting.Value(
                                                "left-right",
                                                R.string.value_melonds_screen_layout_leftright,
                                            ),
                                        ),
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "melonds_threaded_renderer",
                                        R.string.setting_melonds_threaded_renderer,
                                    ),
                                    ExposedSetting(
                                        "melonds_jit_enable",
                                        R.string.setting_melonds_jit_enable,
                                    ),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("melonds_number_of_screen_layouts", "1"),
                                    // A gap between the two screens, where the console shell draws its hinge.
                                    CoreVariable("melonds_screen_gap", "16"),
                                    // Sideways (hybrid layout) the small screen shows only the other screen, not both again.
                                    CoreVariable("melonds_hybrid_small_screen", "one"),
                                    // The big screen three times the small one: the main screen as big as the wings allow.
                                    CoreVariable("melonds_hybrid_ratio", "3"),
                                    CoreVariable("melonds_touch_mode", "Touch"),
                                    // The microphone as Delta does it: no button, the phone's microphone simply
                                    // listens while the game runs (silence without the permission).
                                    CoreVariable("melonds_mic_input", "microphone"),
                                    CoreVariable("melonds_mic_input_active", "always"),
                                    CoreVariable("melonds_threaded_renderer", "enabled"),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.MELONDS),
                                ),
                            statesVersion = 2,
                            supportsMicrophone = true,
                        ),
                        SystemCoreConfig(
                            CoreID.DESMUME,
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "desmume_screens_layout",
                                        R.string.setting_desmume_screens_layout,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "top/bottom",
                                                R.string.value_desmume_layout_topbottom,
                                            ),
                                            ExposedSetting.Value(
                                                "left/right",
                                                R.string.value_desmume_layout_leftright,
                                            ),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "desmume_frameskip",
                                        R.string.setting_desmume_frameskip,
                                    ),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("desmume_screens_gap", "16"),
                                    CoreVariable("desmume_pointer_type", "touch"),
                                    CoreVariable("desmume_hybrid_layout_scale", "3"),
                                    CoreVariable("desmume_hybrid_showboth_screens", "disabled"),
                                    CoreVariable("desmume_hybrid_cursor_always_smallscreen", "disabled"),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.MELONDS),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("nds", "dsi", "ids"),
                    hasTouchScreen = true,
                    // Fast-forward steps 2X to 8X; how fast it really goes depends on the phone.
                ),
                GameSystem(
                    SystemID.ATARI7800,
                    "Atari - 7800",
                    R.string.game_system_title_atari7800,
                    R.string.game_system_abbr_atari7800,
                    listOf(
                        SystemCoreConfig(
                            CoreID.PROSYSTEM,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.ATARI7800),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("a78"),
                    supportedExtensions = listOf("a78", "bin"),
                ),
                GameSystem(
                    SystemID.LYNX,
                    "Atari - Lynx",
                    R.string.game_system_title_lynx,
                    R.string.game_system_abbr_lynx,
                    listOf(
                        SystemCoreConfig(
                            CoreID.HANDY,
                            requiredBIOSFiles =
                                listOf(
                                    "lynxboot.img",
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.LYNX),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "handy_rot",
                                        R.string.setting_handy_rot,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "None",
                                                R.string.value_handy_rot_none,
                                            ),
                                            ExposedSetting.Value(
                                                "90",
                                                R.string.value_handy_rot_90,
                                            ),
                                            ExposedSetting.Value(
                                                "270",
                                                R.string.value_handy_rot_270,
                                            ),
                                        ),
                                    ),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("handy_rot", "None"),
                                    CoreVariable("handy_refresh_rate", "60"),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.GEARLYNX,
                            requiredBIOSFiles =
                                listOf(
                                    "lynxboot.img",
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.LYNX),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("lnx", "lyx"),
                ),
                GameSystem(
                    SystemID.PC_ENGINE,
                    "NEC - PC Engine - TurboGrafx 16",
                    R.string.game_system_title_pce,
                    R.string.game_system_abbr_pce,
                    listOf(
                        SystemCoreConfig(
                            CoreID.MEDNAFEN_PCE_FAST,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.PCE),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.MEDNAFEN_PCE,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.PCE),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("pce", "sgx"),
                    supportedExtensions = listOf("pce", "sgx", "cue", "ccd", "chd", "m3u"),
                    hasMultiDiskSupport = true,
                ),
                GameSystem(
                    SystemID.NGP,
                    "SNK - Neo Geo Pocket",
                    R.string.game_system_title_ngp,
                    R.string.game_system_abbr_ngp,
                    listOf(
                        SystemCoreConfig(
                            CoreID.MEDNAFEN_NGP,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.NGP),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.RACE,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.NGP),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("ngp"),
                ),
                GameSystem(
                    SystemID.NGC,
                    "SNK - Neo Geo Pocket Color",
                    R.string.game_system_title_ngc,
                    R.string.game_system_abbr_ngc,
                    listOf(
                        SystemCoreConfig(
                            CoreID.MEDNAFEN_NGP,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.NGP),
                                ),
                        ),
                        SystemCoreConfig(
                            CoreID.RACE,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.NGP),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("ngc", "ngpc", "npc"),
                ),
                GameSystem(
                    SystemID.WS,
                    "Bandai - WonderSwan",
                    R.string.game_system_title_ws,
                    R.string.game_system_abbr_ws,
                    listOf(
                        SystemCoreConfig(
                            CoreID.MEDNAFEN_WSWAN,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.WS_LANDSCAPE, ControllerConfigs.WS_PORTRAIT),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "wswan_rotate_display",
                                        R.string.setting_wswan_rotate_display,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "landscape",
                                                R.string.value_wswan_rotate_display_landscape,
                                            ),
                                            ExposedSetting.Value(
                                                "portrait",
                                                R.string.value_wswan_rotate_display_portrait,
                                            ),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "wswan_mono_palette",
                                        R.string.setting_wswan_mono_palette,
                                    ),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("wswan_rotate_display", "landscape"),
                                    CoreVariable("wswan_mono_palette", "wonderswan"),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("ws", "pc2"),
                ),
                GameSystem(
                    SystemID.WSC,
                    "Bandai - WonderSwan Color",
                    R.string.game_system_title_wsc,
                    R.string.game_system_abbr_wsc,
                    listOf(
                        SystemCoreConfig(
                            CoreID.MEDNAFEN_WSWAN,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.WS_LANDSCAPE, ControllerConfigs.WS_PORTRAIT),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "wswan_rotate_display",
                                        R.string.setting_wswan_rotate_display,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "landscape",
                                                R.string.value_wswan_rotate_display_landscape,
                                            ),
                                            ExposedSetting.Value(
                                                "portrait",
                                                R.string.value_wswan_rotate_display_portrait,
                                            ),
                                        ),
                                    ),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("wswan_rotate_display", "landscape"),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("wsc"),
                ),
                GameSystem(
                    SystemID.DOS,
                    "DOS",
                    R.string.game_system_title_dos,
                    R.string.game_system_abbr_dos,
                    listOf(
                        SystemCoreConfig(
                            CoreID.DOSBOX_PURE,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.DOS_AUTO),
                                ),
                            statesSupported = false,
                        ),
                    ),
                    fastForwardSupport = false,
                    uniqueExtensions = listOf("dosz"),
                    supportedExtensions =
                        listOf(
                            "dosz",
                            "zip",
                            "exe",
                            "com",
                            "bat",
                            "iso",
                            "chd",
                            "cue",
                            "img",
                            "ima",
                            "vhd",
                            "m3u",
                            "conf",
                        ),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = true,
                            scanByPathAndFilename = false,
                            scanByPathAndSupportedExtensions = true,
                        ),
                ),
                GameSystem(
                    SystemID.NINTENDO_3DS,
                    "Nintendo - Nintendo 3DS",
                    R.string.game_system_title_3ds,
                    R.string.game_system_abbr_3ds,
                    listOf(
                        SystemCoreConfig(
                            CoreID.CITRA,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.NINTENDO_3DS),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("citra_use_acc_mul", "disabled"),
                                    CoreVariable("citra_touch_touchscreen", "enabled"),
                                    CoreVariable("citra_mouse_touchscreen", "disabled"),
                                    CoreVariable("citra_render_touchscreen", "disabled"),
                                    CoreVariable("citra_use_hw_shader_cache", "disabled"),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "citra_layout_option",
                                        R.string.setting_citra_layout_option,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "Default Top-Bottom Screen",
                                                R.string.value_citra_layout_option_topbottom,
                                            ),
                                            ExposedSetting.Value(
                                                "Side by Side",
                                                R.string.value_citra_layout_option_sidebyside,
                                            ),
                                            ExposedSetting.Value(
                                                "Large Screen, Small Screen",
                                                R.string.value_citra_layout_option_large,
                                            ),
                                            ExposedSetting.Value(
                                                "Single Screen Only",
                                                R.string.value_citra_layout_option_single,
                                            ),
                                        ),
                                    ),
                                    // Which screen is the big one (or the only one); the swap button changes it in game.
                                    ExposedSetting(
                                        "citra_swap_screen",
                                        R.string.setting_citra_swap_screen,
                                        arrayListOf(
                                            ExposedSetting.Value("Top", R.string.value_citra_swap_screen_top),
                                            ExposedSetting.Value("Bottom", R.string.value_citra_swap_screen_bottom),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "citra_analog_function",
                                        R.string.setting_citra_analog_function,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "C-Stick and Touchscreen Pointer",
                                                R.string.value_citra_analog_function_both,
                                            ),
                                            ExposedSetting.Value(
                                                "C-Stick",
                                                R.string.value_citra_analog_function_cstick,
                                            ),
                                            ExposedSetting.Value(
                                                "Touchscreen Pointer",
                                                R.string.value_citra_analog_function_pointer,
                                            ),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "citra_deadzone",
                                        R.string.setting_citra_deadzone,
                                    ),
                                    ExposedSetting(
                                        "citra_resolution_factor",
                                        R.string.setting_citra_resolution_factor,
                                    ),
                                    ExposedSetting(
                                        "citra_texture_filter",
                                        R.string.setting_citra_texture_filter,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "citra_use_hw_shader_cache",
                                        R.string.setting_citra_use_hw_shader_cache,
                                    ),
                                    ExposedSetting(
                                        "citra_is_new_3ds",
                                        R.string.setting_citra_is_new_3ds,
                                    ),
                                    ExposedSetting(
                                        "citra_region_value",
                                        R.string.setting_citra_region_value,
                                    ),
                                    ExposedSetting(
                                        "citra_language",
                                        R.string.setting_citra_language,
                                    ),
                                    ExposedSetting(
                                        "citra_use_acc_mul",
                                        R.string.setting_citra_use_acc_mul,
                                    ),
                                    ExposedSetting(
                                        "citra_use_acc_geo_shaders",
                                        R.string.setting_citra_use_acc_geo_shaders,
                                    ),
                                ),
                            statesSupported = true,
                            autoSaveSupported = false,
                            supportsLibretroVFS = true,
                            supportedOnlyArchitectures = setOf("arm64-v8a"),
                        ),
                        SystemCoreConfig(
                            CoreID.AZAHAR,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.NINTENDO_3DS),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("citra_graphics_api", "OpenGL"),
                                    CoreVariable("citra_use_acc_mul", "disabled"),
                                    CoreVariable("citra_touch_touchscreen", "enabled"),
                                    CoreVariable("citra_mouse_touchscreen", "disabled"),
                                    CoreVariable("citra_render_touchscreen", "disabled"),
                                    CoreVariable("citra_use_hw_shader_cache", "disabled"),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "citra_layout_option",
                                        R.string.setting_citra_layout_option,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "Default Top-Bottom Screen",
                                                R.string.value_citra_layout_option_topbottom,
                                            ),
                                            ExposedSetting.Value(
                                                "Side by Side",
                                                R.string.value_citra_layout_option_sidebyside,
                                            ),
                                            ExposedSetting.Value(
                                                "Large Screen, Small Screen",
                                                R.string.value_citra_layout_option_large,
                                            ),
                                            ExposedSetting.Value(
                                                "Single Screen Only",
                                                R.string.value_citra_layout_option_single,
                                            ),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "citra_swap_screen",
                                        R.string.setting_citra_swap_screen,
                                        arrayListOf(
                                            ExposedSetting.Value("Top", R.string.value_citra_swap_screen_top),
                                            ExposedSetting.Value("Bottom", R.string.value_citra_swap_screen_bottom),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "citra_analog_function",
                                        R.string.setting_citra_analog_function,
                                        arrayListOf(
                                            ExposedSetting.Value(
                                                "C-Stick and Touchscreen Pointer",
                                                R.string.value_citra_analog_function_both,
                                            ),
                                            ExposedSetting.Value(
                                                "C-Stick",
                                                R.string.value_citra_analog_function_cstick,
                                            ),
                                            ExposedSetting.Value(
                                                "Touchscreen Pointer",
                                                R.string.value_citra_analog_function_pointer,
                                            ),
                                        ),
                                    ),
                                    ExposedSetting(
                                        "citra_deadzone",
                                        R.string.setting_citra_deadzone,
                                    ),
                                    ExposedSetting(
                                        "citra_resolution_factor",
                                        R.string.setting_citra_resolution_factor,
                                    ),
                                    ExposedSetting(
                                        "citra_texture_filter",
                                        R.string.setting_citra_texture_filter,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "citra_use_hw_shader_cache",
                                        R.string.setting_citra_use_hw_shader_cache,
                                    ),
                                    ExposedSetting(
                                        "citra_is_new_3ds",
                                        R.string.setting_citra_is_new_3ds,
                                    ),
                                    ExposedSetting(
                                        "citra_region_value",
                                        R.string.setting_citra_region_value,
                                    ),
                                    ExposedSetting(
                                        "citra_language",
                                        R.string.setting_citra_language,
                                    ),
                                    ExposedSetting(
                                        "citra_use_acc_mul",
                                        R.string.setting_citra_use_acc_mul,
                                    ),
                                    ExposedSetting(
                                        "citra_use_acc_geo_shaders",
                                        R.string.setting_citra_use_acc_geo_shaders,
                                    ),
                                ),
                            statesSupported = true,
                            autoSaveSupported = false,
                            supportsLibretroVFS = true,
                            supportedOnlyArchitectures = setOf("arm64-v8a"),
                        ),
                    ),
                    uniqueExtensions = listOf("3ds", "3dsx", "cci", "cxi"),
                    hasTouchScreen = true,
                    // Phones run it at full speed at best: double speed gets no faster, so it is not offered.
                    fastForwardSupport = false,
                ),
                GameSystem(
                    SystemID.DREAMCAST,
                    "Sega - Dreamcast",
                    R.string.game_system_title_dreamcast,
                    R.string.game_system_abbr_dreamcast,
                    listOf(
                        SystemCoreConfig(
                            CoreID.FLYCAST,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.DREAMCAST),
                                    1 to arrayListOf(ControllerConfigs.DREAMCAST),
                                    2 to arrayListOf(ControllerConfigs.DREAMCAST),
                                    3 to arrayListOf(ControllerConfigs.DREAMCAST),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("reicast_per_content_vmus", "VMU A1"),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "reicast_internal_resolution",
                                        R.string.setting_flycast_internal_resolution,
                                    ),
                                    ExposedSetting(
                                        "reicast_auto_skip_frame",
                                        R.string.setting_flycast_auto_skip_frame,
                                    ),
                                    ExposedSetting(
                                        "reicast_widescreen_hack",
                                        R.string.setting_flycast_widescreen_hack,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "reicast_alpha_sorting",
                                        R.string.setting_flycast_alpha_sorting,
                                    ),
                                    ExposedSetting(
                                        "reicast_threaded_rendering",
                                        R.string.setting_flycast_threaded_rendering,
                                    ),
                                    ExposedSetting(
                                        "reicast_hle_bios",
                                        R.string.setting_flycast_hle_bios,
                                    ),
                                ),
                            rumbleSupported = true,
                            supportsLibretroVFS = false,
                            skipDuplicateFrames = false,
                        ),
                    ),
                    uniqueExtensions = listOf("cdi", "gdi"),
                    supportedExtensions = listOf("cdi", "gdi", "chd", "cue", "m3u"),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = true,
                            scanByPathAndSupportedExtensions = true,
                        ),
                    hasMultiDiskSupport = true,
                    maxFrameSpeed = 4,
                ),
                GameSystem(
                    SystemID.THREEDO,
                    "The 3DO Company - 3DO",
                    R.string.game_system_title_3do,
                    R.string.game_system_abbr_3do,
                    listOf(
                        SystemCoreConfig(
                            CoreID.OPERA,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.THREEDO),
                                    1 to arrayListOf(ControllerConfigs.THREEDO),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("opera_active_devices", "1"),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "opera_high_resolution",
                                        R.string.setting_opera_high_resolution,
                                    ),
                                    ExposedSetting(
                                        "opera_cpu_overclock",
                                        R.string.setting_opera_cpu_overclock,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "opera_active_devices",
                                        R.string.setting_opera_active_devices,
                                    ),
                                    ExposedSetting(
                                        "opera_dsp_threaded",
                                        R.string.setting_opera_dsp_threaded,
                                    ),
                                    ExposedSetting(
                                        "opera_swi_hle",
                                        R.string.setting_opera_swi_hle,
                                    ),
                                    ExposedSetting(
                                        "opera_nvram_storage",
                                        R.string.setting_opera_nvram_storage,
                                    ),
                                ),
                            supportsLibretroVFS = false,
                            skipDuplicateFrames = false,
                        ),
                    ),
                    uniqueExtensions = listOf(),
                    supportedExtensions = listOf("iso", "chd", "cue", "bin", "img", "m3u"),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = false,
                            scanByPathAndSupportedExtensions = true,
                        ),
                    hasMultiDiskSupport = true,
                ),
                GameSystem(
                    SystemID.SATURN,
                    "Sega - Saturn",
                    R.string.game_system_title_saturn,
                    R.string.game_system_abbr_saturn,
                    listOf(
                        SystemCoreConfig(
                            CoreID.YABASANSHIRO,
                            controllerConfigs =
                                hashMapOf(
                                    0 to
                                        arrayListOf(
                                            ControllerConfigs.SATURN_STANDARD,
                                            ControllerConfigs.SATURN_3D,
                                        ),
                                    1 to
                                        arrayListOf(
                                            ControllerConfigs.SATURN_STANDARD,
                                            ControllerConfigs.SATURN_3D,
                                        ),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "yabasanshiro_resolution_mode",
                                        R.string.setting_yabasanshiro_resolution_mode,
                                    ),
                                    ExposedSetting(
                                        "yabasanshiro_frameskip",
                                        R.string.setting_yabasanshiro_frameskip,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "yabasanshiro_force_hle_bios",
                                        R.string.setting_yabasanshiro_force_hle_bios,
                                    ),
                                    ExposedSetting(
                                        "yabasanshiro_polygon_mode",
                                        R.string.setting_yabasanshiro_polygon_mode,
                                    ),
                                    ExposedSetting(
                                        "yabasanshiro_addon_cart",
                                        R.string.setting_yabasanshiro_addon_cart,
                                    ),
                                ),
                            supportsLibretroVFS = false,
                            skipDuplicateFrames = false,
                        ),
                        SystemCoreConfig(
                            CoreID.MEDNAFEN_SATURN,
                            controllerConfigs =
                                hashMapOf(
                                    0 to
                                        arrayListOf(
                                            ControllerConfigs.BEETLE_SATURN_STANDARD,
                                            ControllerConfigs.BEETLE_SATURN_3D,
                                        ),
                                    1 to
                                        arrayListOf(
                                            ControllerConfigs.BEETLE_SATURN_STANDARD,
                                            ControllerConfigs.BEETLE_SATURN_3D,
                                        ),
                                ),
                            regionalBIOSFiles =
                                mapOf(
                                    "Japan" to "sega_101.bin",
                                    "USA" to "mpr-17933.bin",
                                    "Europe" to "mpr-17933.bin",
                                ),
                            supportsLibretroVFS = false,
                            skipDuplicateFrames = false,
                        ),
                    ),
                    uniqueExtensions = listOf(),
                    supportedExtensions =
                        listOf("iso", "chd", "cue", "mds", "mdf", "ccd", "bin", "img", "m3u"),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = false,
                            scanByPathAndSupportedExtensions = true,
                        ),
                    hasMultiDiskSupport = true,
                    maxFrameSpeed = 4,
                ),
                GameSystem(
                    SystemID.SEGA32X,
                    "Sega - 32X",
                    R.string.game_system_title_sega32x,
                    R.string.game_system_abbr_sega32x,
                    listOf(
                        SystemCoreConfig(
                            CoreID.PICODRIVE,
                            controllerConfigs =
                                hashMapOf(
                                    0 to
                                        arrayListOf(
                                            ControllerConfigs.PICODRIVE_6,
                                            ControllerConfigs.PICODRIVE_3,
                                        ),
                                    1 to
                                        arrayListOf(
                                            ControllerConfigs.PICODRIVE_6,
                                            ControllerConfigs.PICODRIVE_3,
                                        ),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("picodrive_input1", "6 button pad"),
                                    CoreVariable("picodrive_input2", "6 button pad"),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "picodrive_frameskip",
                                        R.string.setting_picodrive_frameskip,
                                    ),
                                    ExposedSetting(
                                        "picodrive_sprlim",
                                        R.string.setting_picodrive_sprlim,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "picodrive_overclk68k",
                                        R.string.setting_picodrive_overclk68k,
                                    ),
                                    ExposedSetting(
                                        "picodrive_region",
                                        R.string.setting_picodrive_region,
                                    ),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("32x"),
                    supportedExtensions = listOf("32x", "bin", "md"),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = true,
                            scanByPathAndSupportedExtensions = true,
                        ),
                ),
                GameSystem(
                    SystemID.ATARI5200,
                    "Atari - 5200",
                    R.string.game_system_title_atari5200,
                    R.string.game_system_abbr_atari5200,
                    listOf(
                        SystemCoreConfig(
                            CoreID.A5200,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.ATARI5200),
                                    1 to arrayListOf(ControllerConfigs.ATARI5200),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "a5200_artifacting_mode",
                                        R.string.setting_a5200_artifacting_mode,
                                    ),
                                    ExposedSetting(
                                        "a5200_mix_frames",
                                        R.string.setting_a5200_mix_frames,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "a5200_bios",
                                        R.string.setting_a5200_bios,
                                    ),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("a52"),
                    supportedExtensions = listOf("a52", "bin", "car"),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = true,
                            scanByPathAndSupportedExtensions = true,
                        ),
                ),
                GameSystem(
                    SystemID.ATARI800,
                    "Atari - 8-bit",
                    R.string.game_system_title_atari800,
                    R.string.game_system_abbr_atari800,
                    listOf(
                        SystemCoreConfig(
                            CoreID.ATARI800,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.ATARI800),
                                    1 to arrayListOf(ControllerConfigs.ATARI800),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "atari800_system",
                                        R.string.setting_atari800_system,
                                    ),
                                    ExposedSetting(
                                        "atari800_artifacting_mode",
                                        R.string.setting_atari800_artifacting_mode,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "atari800_internalbasic",
                                        R.string.setting_atari800_internalbasic,
                                    ),
                                    ExposedSetting(
                                        "atari800_sioaccel",
                                        R.string.setting_atari800_sioaccel,
                                    ),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("atr", "atx", "xfd", "dcm", "xex"),
                    supportedExtensions = listOf("atr", "atx", "xfd", "dcm", "xex", "car", "cas", "rom", "bin"),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = true,
                            scanByPathAndSupportedExtensions = true,
                        ),
                    hasMultiDiskSupport = true,
                ),
                GameSystem(
                    SystemID.COLECOVISION,
                    "Coleco - ColecoVision",
                    R.string.game_system_title_colecovision,
                    R.string.game_system_abbr_colecovision,
                    listOf(
                        SystemCoreConfig(
                            CoreID.GEARCOLECO,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.COLECOVISION),
                                    1 to arrayListOf(ControllerConfigs.COLECOVISION),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "gearcoleco_overscan",
                                        R.string.setting_gearcoleco_overscan,
                                    ),
                                    ExposedSetting(
                                        "gearcoleco_no_sprite_limit",
                                        R.string.setting_gearcoleco_no_sprite_limit,
                                    ),
                                ),
                            requiredBIOSFiles = listOf("colecovision.rom"),
                        ),
                    ),
                    uniqueExtensions = listOf("col", "cv"),
                    supportedExtensions = listOf("col", "cv", "bin", "rom"),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = true,
                            scanByPathAndSupportedExtensions = true,
                        ),
                ),
                GameSystem(
                    SystemID.C64,
                    "Commodore - 64",
                    R.string.game_system_title_c64,
                    R.string.game_system_abbr_c64,
                    listOf(
                        SystemCoreConfig(
                            CoreID.VICE_X64,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.C64),
                                    1 to arrayListOf(ControllerConfigs.C64),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "vice_joyport",
                                        R.string.setting_vice_joyport,
                                    ),
                                    ExposedSetting(
                                        "vice_autoloadwarp",
                                        R.string.setting_vice_autoloadwarp,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "vice_c64_model",
                                        R.string.setting_vice_c64_model,
                                    ),
                                    ExposedSetting(
                                        "vice_drive_true_emulation",
                                        R.string.setting_vice_drive_true_emulation,
                                    ),
                                ),
                        ),
                    ),
                    uniqueExtensions =
                        listOf(
                            "d64",
                            "d71",
                            "d80",
                            "d81",
                            "d82",
                            "g64",
                            "t64",
                            "tap",
                            "prg",
                            "crt",
                            "p00",
                            "vsf",
                        ),
                    supportedExtensions =
                        listOf(
                            "d64",
                            "d71",
                            "d80",
                            "d81",
                            "d82",
                            "g64",
                            "t64",
                            "tap",
                            "prg",
                            "crt",
                            "p00",
                            "vsf",
                            "m3u",
                        ),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = true,
                            scanByPathAndSupportedExtensions = true,
                        ),
                    hasMultiDiskSupport = true,
                ),
                GameSystem(
                    SystemID.APPLEII,
                    "Apple - II",
                    R.string.game_system_title_apple2,
                    R.string.game_system_abbr_apple2,
                    listOf(
                        SystemCoreConfig(
                            CoreID.APPLEWIN,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.APPLEII),
                                ),
                            defaultSettings =
                                listOf(
                                    CoreVariable("applewin_machine", "Apple //e (original)"),
                                    CoreVariable("applewin_joypad_start", "Enter"),
                                    CoreVariable("applewin_joypad_select", "Space"),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "applewin_machine",
                                        R.string.setting_applewin_machine,
                                    ),
                                    ExposedSetting(
                                        "applewin_video_mode",
                                        R.string.setting_applewin_video_mode,
                                    ),
                                    ExposedSetting(
                                        "applewin_joypad_start",
                                        R.string.setting_applewin_joypad_start,
                                    ),
                                    ExposedSetting(
                                        "applewin_joypad_select",
                                        R.string.setting_applewin_joypad_select,
                                    ),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("do", "po", "nib", "woz", "2mg", "2img", "hdv"),
                    supportedExtensions =
                        listOf("do", "po", "nib", "woz", "2mg", "2img", "hdv", "dsk", "m3u"),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = true,
                            scanByPathAndSupportedExtensions = true,
                        ),
                    hasMultiDiskSupport = true,
                ),
                GameSystem(
                    SystemID.MSX,
                    "Microsoft - MSX",
                    R.string.game_system_title_msx,
                    R.string.game_system_abbr_msx,
                    listOf(
                        SystemCoreConfig(
                            CoreID.FMSX,
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.MSX),
                                    1 to arrayListOf(ControllerConfigs.MSX),
                                ),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "fmsx_mode",
                                        R.string.setting_fmsx_mode,
                                    ),
                                    ExposedSetting(
                                        "fmsx_video_mode",
                                        R.string.setting_fmsx_video_mode,
                                    ),
                                    ExposedSetting(
                                        "fmsx_allsprites",
                                        R.string.setting_fmsx_allsprites,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "fmsx_mapper_type_mode",
                                        R.string.setting_fmsx_mapper_type_mode,
                                    ),
                                ),
                            requiredBIOSFiles = listOf("MSX.ROM", "MSX2.ROM", "MSX2EXT.ROM"),
                        ),
                    ),
                    uniqueExtensions = listOf("mx1", "mx2", "fdi"),
                    supportedExtensions = listOf("mx1", "mx2", "fdi", "rom", "dsk", "cas", "m3u"),
                    scanOptions =
                        ScanOptions(
                            scanByFilename = false,
                            scanByUniqueExtension = true,
                            scanByPathAndSupportedExtensions = true,
                        ),
                    hasMultiDiskSupport = true,
                ),
            )

        private val byIdCache by lazy { mapOf(*SYSTEMS.map { it.id.dbname to it }.toTypedArray()) }
        private val byExtensionCache by lazy {
            val mutableMap = mutableMapOf<String, GameSystem>()
            for (system in SYSTEMS) {
                for (extension in system.uniqueExtensions) {
                    mutableMap[extension.toLowerCase(Locale.US)] = system
                }
            }
            mutableMap.toMap()
        }

        fun findById(id: String): GameSystem = byIdCache.getValue(id)

        fun all() = SYSTEMS

        fun getSupportedExtensions(): List<String> {
            return SYSTEMS.flatMap { it.supportedExtensions }
        }

        fun findSystemForCore(coreID: CoreID): List<GameSystem> {
            return all().filter { system -> system.systemCoreConfigs.any { it.coreID == coreID } }
        }

        fun findByUniqueFileExtension(fileExtension: String): GameSystem? =
            byExtensionCache[fileExtension.toLowerCase(Locale.US)]

        data class ScanOptions(
            val scanByFilename: Boolean = true,
            val scanByUniqueExtension: Boolean = true,
            val scanByPathAndFilename: Boolean = false,
            val scanByPathAndSupportedExtensions: Boolean = true,
            val scanBySimilarSerial: Boolean = false,
        )
    }
}
