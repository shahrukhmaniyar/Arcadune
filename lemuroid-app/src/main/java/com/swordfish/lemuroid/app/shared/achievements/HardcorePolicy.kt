package com.swordfish.lemuroid.app.shared.achievements

import com.swordfish.lemuroid.lib.achievements.RetroAchievementsConsoleMap
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.libretrodroid.LibretroDroid
import java.util.concurrent.atomic.AtomicLong

/**
 * Single authoritative boundary policy for RetroAchievements Hardcore Mode.
 *
 * Every user-facing, shortcut-driven, background, or delayed emulator operation must pass
 * through this policy at the point where it actually executes (and native C++ enforces the
 * matching invariants under `coreLock` inside `LibretroDroid`).
 */
object HardcorePolicy {
    /**
     * Monotonic fence incremented whenever a Hardcore enable/disable transition begins.
     * Any asynchronous save-state load or cheat write captures [currentEpoch] when queued and
     * must verify [isEpochValid] immediately before touching the emulator core.
     */
    class TransitionFence(initialEpoch: Long = 0L) {
        private val epoch = AtomicLong(initialEpoch)

        fun currentEpoch(): Long = epoch.get()

        fun bumpEpoch(): Long = epoch.incrementAndGet()

        fun isEpochValid(capturedEpoch: Long): Boolean = capturedEpoch == epoch.get()
    }

    data class LaunchDecision(
        val effectiveHardcore: Boolean,
        val loadQuickSaveSnapshot: Boolean,
        val switchedToCasualForQuickResume: Boolean,
        val coreHardcoreSupported: Boolean,
        val coreUnsupportedReason: String,
    )

    /**
     * Resolves the effective session mode and save-state autoload behavior at game launch BEFORE
     * `AchievementsConfig` or `GLRetroView` is initialized.
     *
     * - Normal launches (`explicitQuickResume = false`) keep Hardcore active when eligible and
     *   skip automatic save-state restoration (`loadQuickSaveSnapshot = false`), loading only
     *   ordinary in-game SRAM/memory-card saves.
     * - Explicit Quick Resume (`explicitQuickResume = true`) restores an existing auto-save
     *   snapshot ONLY after switching the effective session and RetroAchievements runtime config
     *   to Casual (`effectiveHardcore = false`, `switchedToCasualForQuickResume = true`).
     */
    fun resolveLaunchDecision(
        raEnabledAndSignedIn: Boolean,
        hardcoreApproved: Boolean,
        hardcorePreferenceEnabled: Boolean,
        coreId: CoreID,
        systemId: SystemID,
        requestedLoadSave: Boolean,
        explicitQuickResume: Boolean,
        hasAutoSaveSnapshot: Boolean,
    ): LaunchDecision {
        val unsupportedReason = RetroAchievementsConsoleMap.hardcoreUnsupportedReason(coreId, systemId).orEmpty()
        val coreSupported = unsupportedReason.isEmpty()

        val eligibleForHardcore =
            raEnabledAndSignedIn &&
                hardcoreApproved &&
                hardcorePreferenceEnabled &&
                coreSupported

        if (!eligibleForHardcore) {
            return LaunchDecision(
                effectiveHardcore = false,
                loadQuickSaveSnapshot = requestedLoadSave,
                switchedToCasualForQuickResume = false,
                coreHardcoreSupported = coreSupported,
                coreUnsupportedReason = unsupportedReason,
            )
        }

        if (explicitQuickResume && requestedLoadSave && hasAutoSaveSnapshot) {
            return LaunchDecision(
                effectiveHardcore = false,
                loadQuickSaveSnapshot = true,
                switchedToCasualForQuickResume = true,
                coreHardcoreSupported = true,
                coreUnsupportedReason = "",
            )
        }

        return LaunchDecision(
            effectiveHardcore = true,
            loadQuickSaveSnapshot = false,
            switchedToCasualForQuickResume = false,
            coreHardcoreSupported = true,
            coreUnsupportedReason = "",
        )
    }

    fun canLoadSaveState(
        hardcoreActive: Boolean,
        transitionInProgress: Boolean = false,
        commandEpoch: Long = 0L,
        currentEpoch: Long = 0L,
    ): Boolean {
        if (hardcoreActive || transitionInProgress) return false
        if (commandEpoch != currentEpoch) return false
        return true
    }

    /**
     * Creating save states (slots, quick save, exit auto-save) remains available in Hardcore;
     * reloading them while Hardcore is active is strictly blocked by [canLoadSaveState].
     */
    fun canSaveState(transitionInProgress: Boolean = false): Boolean = !transitionInProgress

    fun canApplyCheats(
        hardcoreActive: Boolean,
        transitionInProgress: Boolean = false,
        commandEpoch: Long = 0L,
        currentEpoch: Long = 0L,
    ): Boolean {
        if (hardcoreActive || transitionInProgress) return false
        if (commandEpoch != currentEpoch) return false
        return true
    }

    fun canRewind(
        hardcoreActive: Boolean,
        transitionInProgress: Boolean = false,
    ): Boolean = !hardcoreActive && !transitionInProgress

    /**
     * Documented fast-forward (`>= 1x`) is allowed in Hardcore; slowdown (`< 1x`), zero-speed
     * freeze, and negative reverse stepping are strictly clamped out in all modes.
     */
    fun clampFrameSpeed(
        requestedSpeed: Int,
        maxSpeed: Int = Int.MAX_VALUE,
    ): Int {
        val safeMax = maxSpeed.coerceAtLeast(1)
        return requestedSpeed.coerceIn(1, safeMax)
    }

    fun canSlowdown(hardcoreActive: Boolean): Boolean = false

    fun canFrameAdvance(hardcoreActive: Boolean): Boolean = false

    fun canUseDebuggerOrScriptPlayback(hardcoreActive: Boolean): Boolean = false

    fun canPause(
        hardcoreActive: Boolean,
        remainingCooldownFrames: Int,
    ): Boolean {
        if (!hardcoreActive) return true
        return remainingCooldownFrames <= 0
    }

    /**
     * External save/state mutations (backup restore, single-save import, cloud save-sync) from
     * the main process are blocked whenever `:game` holds [com.swordfish.lemuroid.app.shared.game.GameProcessLock].
     */
    fun canMutateSaveFilesExternally(gameProcessRunning: Boolean): Boolean = !gameProcessRunning

    /**
     * Checks whether setting [variableKey] = [variableValue] is permitted in Hardcore Mode for
     * the given [coreId] and [systemId]. Delegates to native `rc_libretro` + `Environment` when
     * `libretrodroid` JNI is loaded, and falls back to an identical pure-Kotlin mirror of
     * `rc_libretro` + `kArcaduneSupplementDisallowedSettings` when JNI is unavailable.
     */
    fun isCoreOptionValueAllowed(
        coreId: CoreID,
        systemId: SystemID,
        variableKey: String,
        variableValue: String,
    ): Boolean {
        if (!RetroAchievementsConsoleMap.isCoreHardcoreCompliant(coreId, systemId)) {
            return false
        }
        val libraryName = RetroAchievementsConsoleMap.libretroLibraryName(coreId)
        val consoleId = RetroAchievementsConsoleMap.getConsoleId(systemId)

        runCatching {
            return LibretroDroid.isCoreSettingAllowedInHardcore(
                libraryName,
                consoleId,
                variableKey,
                variableValue,
            )
        }

        return isCoreOptionAllowedMirror(libraryName, consoleId, variableKey, variableValue)
    }

    private data class DisallowedRule(
        val keyPattern: String,
        val valuePattern: String,
    )

    private data class CoreRules(
        val libraryName: String,
        val systemId: Int,
        val rules: List<DisallowedRule>,
    )

    // Exact mirror of rcheevos/src/rc_libretro.c + environment.cpp kArcaduneSupplementDisallowedSettings
    private val supplementRules =
        listOf(
            DisallowedRule("dosbox_pure_strict_mode", "false"),
            DisallowedRule("dosbox_pure_savestate", "!disabled"),
            DisallowedRule("fbneo-diagnostic-input", "!None"),
            DisallowedRule("fbneo-cpu-speed-adjust", ",?%,??%"),
            DisallowedRule("fbneo-allow-patched-romsets", "enabled"),
            DisallowedRule("fbneo-cheat-*", "!,Disabled,0 - Disabled"),
            DisallowedRule("fbneo-dipswitch-*", "Universe BIOS*"),
            DisallowedRule("fbneo-neogeo-mode", "UNIBIOS"),
            DisallowedRule("opera_cpu_overclock", "0.*"),
            DisallowedRule("picodrive_overclk68k", "-*"),
            DisallowedRule("fmsx_video_mode", "PAL"),
            DisallowedRule("vice_autostart", "disabled"),
            DisallowedRule("vice_reset", "!autostart"),
            DisallowedRule("vice_mapper_warp_mode", "!---"),
            DisallowedRule("vice_mapper_datasette_rewind", "!---"),
            DisallowedRule("pce_fast_disable_softreset", "disabled"),
            DisallowedRule("pce_disable_softreset", "disabled"),
            DisallowedRule("ppsspp_cheats", "enabled"),
            DisallowedRule("fceumm_game_genie", "!disabled"),
            DisallowedRule("genesis_plus_gx_lock_on", ",action replay (pro),game genie"),
            DisallowedRule("melonds_console_mode", "dsi"),
            DisallowedRule("pcsx_rearmed_psxclock", ",!auto,<55"),
            DisallowedRule("reicast_sh4clock", "<200"),
            DisallowedRule("armgba_cpu_clock", "<100"),
            DisallowedRule("armgba_cpu_clock_boot", "<100"),
            DisallowedRule("armgba_action_replay_game", "!none"),
            DisallowedRule("armgba_game_genie_code_*", "!none"),
            DisallowedRule("armgba_cheat_import", "!off"),
            DisallowedRule("armgba_action_replay_hold", "!off"),
            DisallowedRule("armgba_action_replay_press", "!off"),
            DisallowedRule("bsnes_region", "pal"),
            DisallowedRule("supafaust_region", ",pal,force_pal"),
            DisallowedRule("nestopia_favored_system", ",pal,dendy"),
            DisallowedRule("gearsystem_timing", "PAL (50 Hz)"),
            DisallowedRule("gearsystem_region", ",PAL*,Europe*"),
            DisallowedRule("blastem_region", ",PAL*,Europe*"),
            DisallowedRule("swanstation_CDROM_LoadImagePatches", "true"),
            DisallowedRule("swanstation_Console_Region", "PAL"),
            DisallowedRule("swanstation_Console_Enable8MBRAM", "true"),
            DisallowedRule("beetle_saturn_region", ",Europe,Asia (PAL)"),
            DisallowedRule("beetle_saturn_cart", "Action Replay*"),
            DisallowedRule("vbam_layer_*", "disabled"),
        )

    private val coreRuleTable =
        listOf(
            CoreRules(
                "armGBA",
                0,
                listOf(
                    DisallowedRule("armgba_cpu_clock", "<100"),
                    DisallowedRule("armgba_cpu_clock_boot", "<100"),
                    DisallowedRule("armgba_action_replay_game", "!none"),
                    DisallowedRule("armgba_game_genie_code_*", "!none"),
                    DisallowedRule("armgba_cheat_import", "!off"),
                    DisallowedRule("armgba_action_replay_hold", "!off"),
                    DisallowedRule("armgba_action_replay_press", "!off"),
                ),
            ),
            CoreRules("Beetle PCE", 0, listOf(DisallowedRule("pce_disable_softreset", "disabled"))),
            CoreRules("Beetle PCE Fast", 0, listOf(DisallowedRule("pce_fast_disable_softreset", "disabled"))),
            CoreRules("Beetle PSX", 0, listOf(DisallowedRule("beetle_psx_cpu_freq_scale", "<100"))),
            CoreRules(
                "Beetle Saturn",
                0,
                listOf(
                    DisallowedRule("beetle_saturn_region", ",Europe,Asia (PAL)"),
                    DisallowedRule("beetle_saturn_cart", "Action Replay*"),
                ),
            ),
            CoreRules("Beetle Supafaust", 0, listOf(DisallowedRule("supafaust_region", ",pal,force_pal"))),
            CoreRules("BlastEm", 0, listOf(DisallowedRule("blastem_region", ",PAL*,Europe*"))),
            CoreRules("bsnes", 0, listOf(DisallowedRule("bsnes_region", "pal"))),
            CoreRules(
                "DOSBox-pure",
                0,
                listOf(
                    DisallowedRule("dosbox_pure_strict_mode", "false"),
                    DisallowedRule("dosbox_pure_savestate", "!disabled"),
                ),
            ),
            CoreRules(
                "FCEUmm",
                0,
                listOf(
                    DisallowedRule("fceumm_game_genie", "!disabled"),
                    DisallowedRule("fceumm_region", ",PAL,Dendy"),
                ),
            ),
            CoreRules(
                "FinalBurn Neo",
                0,
                listOf(
                    DisallowedRule("fbneo-allow-patched-romsets", "enabled"),
                    DisallowedRule("fbneo-cheat-*", "!,Disabled,0 - Disabled"),
                    DisallowedRule("fbneo-cpu-speed-adjust", ",?%,??%"),
                    DisallowedRule("fbneo-dipswitch-*", "Universe BIOS*"),
                    DisallowedRule("fbneo-neogeo-mode", "UNIBIOS"),
                    DisallowedRule("fbneo-diagnostic-input", "!None"),
                ),
            ),
            CoreRules("Flycast", 0, listOf(DisallowedRule("reicast_sh4clock", "<200"))),
            CoreRules("fMSX", 0, listOf(DisallowedRule("fmsx_video_mode", "PAL"))),
            CoreRules(
                "Gearsystem",
                0,
                listOf(
                    DisallowedRule("gearsystem_timing", "PAL (50 Hz)"),
                    DisallowedRule("gearsystem_region", ",PAL*,Europe*"),
                ),
            ),
            CoreRules(
                "Genesis Plus GX",
                0,
                listOf(
                    DisallowedRule("genesis_plus_gx_lock_on", ",action replay (pro),game genie"),
                    DisallowedRule("genesis_plus_gx_region_detect", "pal"),
                ),
            ),
            CoreRules(
                "melonDS",
                RetroAchievementsConsoleMap.RC_CONSOLE_NINTENDO_DS,
                listOf(DisallowedRule("melonds_console_mode", "dsi")),
            ),
            CoreRules("Mesen", 0, listOf(DisallowedRule("mesen_region", ",PAL,Dendy"))),
            CoreRules("Nestopia", 0, listOf(DisallowedRule("nestopia_favored_system", ",pal,dendy"))),
            CoreRules("Opera", 0, listOf(DisallowedRule("opera_cpu_overclock", "0.*"))),
            CoreRules("PPSSPP", 0, listOf(DisallowedRule("ppsspp_cheats", "enabled"))),
            CoreRules(
                "PCSX-ReARMed",
                0,
                listOf(
                    DisallowedRule("pcsx_rearmed_psxclock", ",!auto,<55"),
                    DisallowedRule("pcsx_rearmed_region", "pal"),
                ),
            ),
            CoreRules(
                "PicoDrive",
                0,
                listOf(
                    DisallowedRule("picodrive_region", ",Europe,Japan PAL"),
                    DisallowedRule("picodrive_overclk68k", "-*"),
                ),
            ),
            CoreRules(
                "Snes9x",
                0,
                listOf(
                    DisallowedRule("snes9x_gfx_clip", "disabled"),
                    DisallowedRule("snes9x_gfx_transp", "disabled"),
                    DisallowedRule("snes9x_layer_*", "disabled"),
                    DisallowedRule("snes9x_region", "pal"),
                ),
            ),
            CoreRules(
                "SwanStation",
                0,
                listOf(
                    DisallowedRule("swanstation_CPU_Overclock", "<100"),
                    DisallowedRule("swanstation_CDROM_LoadImagePatches", "true"),
                    DisallowedRule("swanstation_Console_Region", "PAL"),
                    DisallowedRule("swanstation_Console_Enable8MBRAM", "true"),
                ),
            ),
            CoreRules("VBA-M", 0, listOf(DisallowedRule("vbam_layer_*", "disabled"))),
            CoreRules(
                "VICE x64",
                0,
                listOf(
                    DisallowedRule("vice_autostart", "disabled"),
                    DisallowedRule("vice_reset", "!autostart"),
                    DisallowedRule("vice_mapper_warp_mode", "!---"),
                    DisallowedRule("vice_mapper_datasette_rewind", "!---"),
                ),
            ),
        )

    internal fun isCoreOptionAllowedMirror(
        libraryName: String,
        consoleId: Int,
        key: String,
        value: String,
    ): Boolean {
        for (entry in coreRuleTable) {
            if (entry.libraryName.equals(libraryName, ignoreCase = true) &&
                (entry.systemId == 0 || entry.systemId == consoleId)
            ) {
                if (!areRulesSatisfied(entry.rules, key, value)) return false
            }
        }
        if (!areRulesSatisfied(supplementRules, key, value)) return false
        return true
    }

    private fun areRulesSatisfied(
        rules: List<DisallowedRule>,
        key: String,
        value: String,
    ): Boolean {
        for (rule in rules) {
            val keyMatched =
                if (rule.keyPattern.endsWith("*")) {
                    key.startsWith(rule.keyPattern.dropLast(1))
                } else {
                    key == rule.keyPattern
                }
            if (keyMatched && matchesDisallowedValue(value, rule.valuePattern)) {
                return false
            }
        }
        return true
    }

    private fun matchesDisallowedValue(
        value: String,
        pattern: String,
    ): Boolean {
        if (pattern.startsWith(",")) {
            val tokens = pattern.drop(1).split(",")
            for (token in tokens) {
                val matched = matchToken(value, token)
                if (matched != null) return matched
            }
            return false
        }
        if (pattern.startsWith("!")) {
            return !matchesDisallowedValue(value, pattern.drop(1))
        }
        return matchToken(value, pattern) ?: false
    }

    private fun matchToken(
        value: String,
        token: String,
    ): Boolean? {
        if (token.startsWith("!")) {
            if (matchToken(value, token.drop(1)) != null) {
                return false
            }
        }
        if (token.startsWith("<")) {
            val threshold = token.drop(1).trim().toIntOrNull()
            val parsedVal = value.takeWhile { it.isDigit() || it == '-' }.toIntOrNull()
            if (threshold != null && parsedVal != null && parsedVal < threshold) {
                return true
            }
        }
        if (value == token) return true
        if (wildcardEqualsNoCase(value, token)) return true
        return null
    }

    private fun wildcardEqualsNoCase(
        test: String,
        match: String,
    ): Boolean {
        var ti = 0
        var mi = 0
        while (ti < test.length) {
            if (mi >= match.length) return false
            val c1 = test[ti].lowercaseChar()
            val c2 = match[mi].lowercaseChar()
            ti++
            mi++
            if (c1 != c2 && c2 != '?') {
                return c2 == '*'
            }
        }
        return mi == match.length
    }
}
