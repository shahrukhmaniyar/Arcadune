package com.swordfish.lemuroid.app.shared.achievements

import com.swordfish.lemuroid.lib.achievements.RetroAchievementsConsoleMap
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.libretrodroid.AchievementDef
import com.swordfish.libretrodroid.AchievementsBridge
import com.swordfish.libretrodroid.AchievementsSnapshot
import com.swordfish.libretrodroid.LeaderboardDef
import com.swordfish.libretrodroid.LeaderboardTracker
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HardcoreComplianceRegressionTest {
    /**
     * Deterministic simulation harness modeling the exact transition ordering enforced by
     * `BaseGameScreenViewModel.enableHardcoreWithCleanRestart`, `switchToCasualImmediate`,
     * `HardcorePolicy`, and native `LibretroDroid::setHardcoreMode(true)`.
     */
    private class SimulatedEmulatorSession(
        var hardcorePreference: Boolean = false,
        var hardcoreActive: Boolean = false,
        var sramBytes: ByteArray = byteArrayOf(0x41, 0x52, 0x43, 0x41),
    ) {
        val fence = HardcorePolicy.TransitionFence()
        val auditLog = mutableListOf<String>()

        var transitionInProgress: Boolean = false
        var rcClientEvaluationSuspended: Boolean = false
        var rcClientHardcoreEvaluationActive: Boolean = false
        var activeCheats: MutableList<String> = mutableListOf()
        var rewindFramesBuffered: Int = 0
        var pendingLatchedButtons: MutableSet<Int> = mutableSetOf()
        var frameSpeed: Int = 1
        var loadedSaveStateBytes: ByteArray? = null
        var savedSlotStateBytes: ByteArray? = null
        var cleanBootCount: Int = 0
        var lastError: String = ""
        var pendingEnableConfirmation: Boolean = false

        fun requestEnableHardcoreMidGame() {
            pendingEnableConfirmation = true
            auditLog += "prompt_confirmation"
        }

        fun cancelEnableHardcoreDialog() {
            if (!pendingEnableConfirmation) return
            pendingEnableConfirmation = false
            auditLog += "cancel_confirmation"
        }

        fun confirmEnableHardcore(
            simulateCoreReloadFailure: Boolean = false,
            midTransitionHook: ((SimulatedEmulatorSession) -> Unit)? = null,
        ): Boolean {
            if (!pendingEnableConfirmation) return false
            pendingEnableConfirmation = false
            hardcorePreference = true

            // 1. Bump monotonic fence & suspend emulation + achievement evaluation BEFORE cleanup.
            fence.bumpEpoch()
            transitionInProgress = true
            rcClientEvaluationSuspended = true
            rcClientHardcoreEvaluationActive = false
            auditLog += "suspend_evaluation_and_bump_fence"

            // Allow test to fire stale async state load / cheat commands mid-transition!
            midTransitionHook?.invoke(this)

            // 2. Clear cheats, rewind history, pending inputs, and clamp speed.
            activeCheats.clear()
            rewindFramesBuffered = 0
            pendingLatchedButtons.clear()
            frameSpeed = HardcorePolicy.clampFrameSpeed(frameSpeed)
            auditLog += "clear_cheats_rewind_inputs"

            // 3. Snapshot ordinary SRAM, perform clean core boot from pristine ROM, skip state autoload.
            val preservedSram = sramBytes.copyOf()
            if (simulateCoreReloadFailure) {
                transitionInProgress = false
                hardcoreActive = false
                rcClientHardcoreEvaluationActive = false
                rcClientEvaluationSuspended = false
                lastError = "Clean core restart failed; Hardcore evaluation blocked."
                auditLog += "abort_hardcore_on_reload_failure"
                return false
            }

            loadedSaveStateBytes = null
            sramBytes = preservedSram
            cleanBootCount++
            auditLog += "clean_pristine_boot_restore_sram_only"

            // 4. Refresh memory maps, call rc_client_reset(), and resume Hardcore evaluation LAST.
            hardcoreActive = true
            rcClientEvaluationSuspended = false
            rcClientHardcoreEvaluationActive = true
            transitionInProgress = false
            lastError = ""
            auditLog += "resume_hardcore_evaluation"
            return true
        }

        fun leaveHardcoreImmediate() {
            fence.bumpEpoch()
            hardcorePreference = false
            hardcoreActive = false
            rcClientHardcoreEvaluationActive = false
            auditLog += "switch_to_casual_immediate"
        }

        fun tryExecuteQueuedLoadState(
            capturedEpoch: Long,
            statePayload: ByteArray,
        ): Boolean {
            val allowed =
                HardcorePolicy.canLoadSaveState(
                    hardcoreActive = hardcoreActive,
                    transitionInProgress = transitionInProgress,
                    commandEpoch = capturedEpoch,
                    currentEpoch = fence.currentEpoch(),
                )
            if (!allowed) {
                auditLog += "blocked_load_state"
                return false
            }
            loadedSaveStateBytes = statePayload.copyOf()
            auditLog += "applied_load_state"
            return true
        }

        fun tryCreateSaveState(statePayload: ByteArray): Boolean {
            if (!HardcorePolicy.canSaveState(transitionInProgress)) {
                return false
            }
            savedSlotStateBytes = statePayload.copyOf()
            auditLog += "created_save_state"
            return true
        }

        fun tryExecuteQueuedCheatWrite(
            capturedEpoch: Long,
            cheatCode: String,
        ): Boolean {
            val allowed =
                HardcorePolicy.canApplyCheats(
                    hardcoreActive = hardcoreActive,
                    transitionInProgress = transitionInProgress,
                    commandEpoch = capturedEpoch,
                    currentEpoch = fence.currentEpoch(),
                )
            if (!allowed) {
                auditLog += "blocked_cheat_write"
                return false
            }
            activeCheats.add(cheatCode)
            auditLog += "applied_cheat_write"
            return true
        }
    }

    @Before
    fun resetSessionHolder() {
        RetroAchievementsSessionHolder.reset()
    }

    @Test
    fun casualWithCheats_toHardcore_cancelsCleanly_orResetsClearsCheatsPreservesSramAndEvaluatesOnlyAfterBoot() {
        val session = SimulatedEmulatorSession()
        val initialEpoch = session.fence.currentEpoch()
        assertTrue(session.tryExecuteQueuedCheatWrite(initialEpoch, "00A1-FF99"))
        assertTrue(session.tryExecuteQueuedLoadState(initialEpoch, byteArrayOf(1, 2, 3)))
        session.rewindFramesBuffered = 180
        session.pendingLatchedButtons.add(96)

        // 1. User requests Hardcore -> cancels dialog -> Casual session + cheats + loaded state stay intact!
        session.requestEnableHardcoreMidGame()
        assertTrue(session.pendingEnableConfirmation)
        session.cancelEnableHardcoreDialog()
        assertFalse(session.hardcoreActive)
        assertEquals(listOf("00A1-FF99"), session.activeCheats)
        assertArrayEquals(byteArrayOf(1, 2, 3), session.loadedSaveStateBytes)
        assertEquals(0, session.cleanBootCount)

        // 2. Queue stale async state load + stale async cheat BEFORE confirming transition.
        val staleEpoch = session.fence.currentEpoch()
        session.requestEnableHardcoreMidGame()
        val ok =
            session.confirmEnableHardcore { midSession ->
                // During transition, Hardcore evaluation MUST NOT be active yet, and stale commands MUST be rejected!
                assertTrue(midSession.rcClientEvaluationSuspended)
                assertFalse(midSession.rcClientHardcoreEvaluationActive)
                assertFalse(midSession.tryExecuteQueuedLoadState(staleEpoch, byteArrayOf(9, 9)))
                assertFalse(midSession.tryExecuteQueuedCheatWrite(staleEpoch, "DEAD-BEEF"))
            }

        assertTrue(ok)
        assertTrue(session.hardcoreActive)
        assertTrue(session.rcClientHardcoreEvaluationActive)
        assertTrue(session.activeCheats.isEmpty())
        assertEquals(0, session.rewindFramesBuffered)
        assertTrue(session.pendingLatchedButtons.isEmpty())
        assertEquals(null, session.loadedSaveStateBytes)
        assertArrayEquals(byteArrayOf(0x41, 0x52, 0x43, 0x41), session.sramBytes)
        assertEquals(1, session.cleanBootCount)

        // Verify strict ordering: clean boot happened BEFORE resuming Hardcore evaluation!
        val bootIdx = session.auditLog.indexOf("clean_pristine_boot_restore_sram_only")
        val resumeIdx = session.auditLog.indexOf("resume_hardcore_evaluation")
        assertTrue(bootIdx in 0 until resumeIdx)

        // Stale commands arriving AFTER transition completes are also rejected!
        assertFalse(session.tryExecuteQueuedLoadState(staleEpoch, byteArrayOf(7, 7)))
        assertFalse(session.tryExecuteQueuedCheatWrite(staleEpoch, "CAFE-BABE"))
    }

    @Test
    fun repeatedModeToggles_andResetFailure_blockEvaluationOnErrorAndRequireFreshResetOnReEnable() {
        val session = SimulatedEmulatorSession()
        session.requestEnableHardcoreMidGame()
        assertTrue(session.confirmEnableHardcore())
        assertEquals(1, session.cleanBootCount)
        assertTrue(session.hardcoreActive)

        // Leaving Hardcore takes effect immediately without resetting the game, restoring Casual features!
        session.leaveHardcoreImmediate()
        assertFalse(session.hardcoreActive)
        assertFalse(session.rcClientHardcoreEvaluationActive)
        assertEquals(1, session.cleanBootCount)
        val casualEpoch = session.fence.currentEpoch()
        assertTrue(session.tryExecuteQueuedCheatWrite(casualEpoch, "CASUAL-CHEAT"))
        assertTrue(session.tryExecuteQueuedLoadState(casualEpoch, byteArrayOf(4, 5, 6)))

        // Turning Hardcore back on when core reload fails MUST block Hardcore evaluation and surface error!
        session.requestEnableHardcoreMidGame()
        assertFalse(session.confirmEnableHardcore(simulateCoreReloadFailure = true))
        assertFalse(session.hardcoreActive)
        assertFalse(session.rcClientHardcoreEvaluationActive)
        assertTrue(session.lastError.isNotBlank())

        // Subsequent successful re-enable performs a second full clean reset!
        session.requestEnableHardcoreMidGame()
        assertTrue(session.confirmEnableHardcore(simulateCoreReloadFailure = false))
        assertTrue(session.hardcoreActive)
        assertTrue(session.rcClientHardcoreEvaluationActive)
        assertEquals(2, session.cleanBootCount)
        assertTrue(session.activeCheats.isEmpty())
    }

    @Test
    fun boundaryPolicy_enforcesStateSaveVsLoad_rewind_slowdown_fastForward_pauseCooldown_andExternalSaveLock() {
        val session = SimulatedEmulatorSession()
        session.requestEnableHardcoreMidGame()
        assertTrue(session.confirmEnableHardcore())
        val hcEpoch = session.fence.currentEpoch()

        // Creating save states is allowed in Hardcore, while EVERY load path is blocked!
        assertTrue(session.tryCreateSaveState(byteArrayOf(0x10, 0x20)))
        assertArrayEquals(byteArrayOf(0x10, 0x20), session.savedSlotStateBytes)
        assertFalse(session.tryExecuteQueuedLoadState(hcEpoch, byteArrayOf(0x10, 0x20)))

        // Rewind, slowdown (< 1x), frame advance, debugger/TAS playback are all blocked.
        assertFalse(HardcorePolicy.canRewind(hardcoreActive = true))
        assertTrue(HardcorePolicy.canRewind(hardcoreActive = false))
        assertFalse(HardcorePolicy.canSlowdown(hardcoreActive = true))
        assertFalse(HardcorePolicy.canFrameAdvance(hardcoreActive = true))
        assertFalse(HardcorePolicy.canUseDebuggerOrScriptPlayback(hardcoreActive = true))

        // Speed clamping blocks 0x/negative slowdown while keeping documented fast-forward (>= 1x) available!
        assertEquals(1, HardcorePolicy.clampFrameSpeed(0, maxSpeed = 8))
        assertEquals(1, HardcorePolicy.clampFrameSpeed(-2, maxSpeed = 8))
        assertEquals(1, HardcorePolicy.clampFrameSpeed(1, maxSpeed = 8))
        assertEquals(4, HardcorePolicy.clampFrameSpeed(4, maxSpeed = 8))
        assertEquals(6, HardcorePolicy.clampFrameSpeed(10, maxSpeed = 6))

        // Pause anti-abuse cooldown from rc_client_can_pause:
        assertFalse(HardcorePolicy.canPause(hardcoreActive = true, remainingCooldownFrames = 90))
        assertTrue(HardcorePolicy.canPause(hardcoreActive = true, remainingCooldownFrames = 0))
        assertTrue(HardcorePolicy.canPause(hardcoreActive = false, remainingCooldownFrames = 90))

        // External save sync / import / backup restore blocked whenever :game holds GameProcessLock:
        assertFalse(HardcorePolicy.canMutateSaveFilesExternally(gameProcessRunning = true))
        assertTrue(HardcorePolicy.canMutateSaveFilesExternally(gameProcessRunning = false))
    }

    @Test
    fun launchDecision_normalPlayStartsHardcoreWithoutSnapshot_whileExplicitQuickResumeSwitchesToCasualFirst() {
        // 1. Normal launch (card tap / Play / external launcher shortcut) in Hardcore:
        //    keeps Hardcore active and skips quickSaveData autoload!
        val normalDecision =
            HardcorePolicy.resolveLaunchDecision(
                raEnabledAndSignedIn = true,
                hardcoreApproved = true,
                hardcorePreferenceEnabled = true,
                coreId = CoreID.SNES9X,
                systemId = SystemID.SNES,
                requestedLoadSave = true,
                explicitQuickResume = false,
                hasAutoSaveSnapshot = true,
            )
        assertTrue(normalDecision.effectiveHardcore)
        assertFalse(normalDecision.loadQuickSaveSnapshot)
        assertFalse(normalDecision.switchedToCasualForQuickResume)
        assertTrue(normalDecision.coreHardcoreSupported)

        // 2. Explicit Quick Resume ("Resume" action) when an auto-save snapshot exists:
        //    switches effective session & RA config to Casual BEFORE restoring the snapshot!
        val quickResumeDecision =
            HardcorePolicy.resolveLaunchDecision(
                raEnabledAndSignedIn = true,
                hardcoreApproved = true,
                hardcorePreferenceEnabled = true,
                coreId = CoreID.SNES9X,
                systemId = SystemID.SNES,
                requestedLoadSave = true,
                explicitQuickResume = true,
                hasAutoSaveSnapshot = true,
            )
        assertFalse(quickResumeDecision.effectiveHardcore)
        assertTrue(quickResumeDecision.loadQuickSaveSnapshot)
        assertTrue(quickResumeDecision.switchedToCasualForQuickResume)

        // 3. Non-compliant core (e.g. MAME 2003-Plus or Citra) forces Casual and reports exact reason!
        val unsupportedCoreDecision =
            HardcorePolicy.resolveLaunchDecision(
                raEnabledAndSignedIn = true,
                hardcoreApproved = true,
                hardcorePreferenceEnabled = true,
                coreId = CoreID.MAME2003PLUS,
                systemId = SystemID.MAME2003PLUS,
                requestedLoadSave = false,
                explicitQuickResume = false,
                hasAutoSaveSnapshot = false,
            )
        assertFalse(unsupportedCoreDecision.effectiveHardcore)
        assertFalse(unsupportedCoreDecision.coreHardcoreSupported)
        assertTrue(unsupportedCoreDecision.coreUnsupportedReason.isNotBlank())
    }

    @Test
    fun coreOptionPolicy_blocksDisallowedCheatsUnderclocksPalRegionsBiosesAndLayerHidingAcrossCores() {
        val nes = RetroAchievementsConsoleMap.RC_CONSOLE_NINTENDO
        val md = RetroAchievementsConsoleMap.RC_CONSOLE_MEGA_DRIVE
        val arc = RetroAchievementsConsoleMap.RC_CONSOLE_ARCADE
        val psp = RetroAchievementsConsoleMap.RC_CONSOLE_PSP
        val dos = RetroAchievementsConsoleMap.RC_CONSOLE_MS_DOS
        val psx = RetroAchievementsConsoleMap.RC_CONSOLE_PLAYSTATION
        val dc = RetroAchievementsConsoleMap.RC_CONSOLE_DREAMCAST
        val nds = RetroAchievementsConsoleMap.RC_CONSOLE_NINTENDO_DS
        val snes = RetroAchievementsConsoleMap.RC_CONSOLE_SUPER_NINTENDO
        val c64 = RetroAchievementsConsoleMap.RC_CONSOLE_COMMODORE_64

        // FCEUmm Game Genie & PAL/Dendy
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("FCEUmm", nes, "fceumm_game_genie", "enabled"))
        assertTrue(HardcorePolicy.isCoreOptionAllowedMirror("FCEUmm", nes, "fceumm_game_genie", "disabled"))
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("FCEUmm", nes, "fceumm_region", "PAL"))
        assertTrue(HardcorePolicy.isCoreOptionAllowedMirror("FCEUmm", nes, "fceumm_region", "NTSC"))

        // Genesis Plus GX lock-on & PAL
        assertFalse(
            HardcorePolicy.isCoreOptionAllowedMirror("Genesis Plus GX", md, "genesis_plus_gx_lock_on", "game genie"),
        )
        assertFalse(
            HardcorePolicy.isCoreOptionAllowedMirror(
                "Genesis Plus GX",
                md,
                "genesis_plus_gx_lock_on",
                "action replay (pro)",
            ),
        )
        assertTrue(
            HardcorePolicy.isCoreOptionAllowedMirror("Genesis Plus GX", md, "genesis_plus_gx_lock_on", "disabled"),
        )

        // FinalBurn Neo cheats, UNIBIOS, diagnostic service mode, <100% CPU underclock
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("FinalBurn Neo", arc, "fbneo-cheat-0-mvsc", "1 - Enabled"))
        assertTrue(HardcorePolicy.isCoreOptionAllowedMirror("FinalBurn Neo", arc, "fbneo-cheat-0-mvsc", "0 - Disabled"))
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("FinalBurn Neo", arc, "fbneo-neogeo-mode", "UNIBIOS"))
        assertFalse(
            HardcorePolicy.isCoreOptionAllowedMirror(
                "FinalBurn Neo",
                arc,
                "fbneo-dipswitch-mslug-BIOS",
                "Universe BIOS ver. 4.0",
            ),
        )
        assertFalse(
            HardcorePolicy.isCoreOptionAllowedMirror("FinalBurn Neo", arc, "fbneo-diagnostic-input", "Hold Start"),
        )
        assertTrue(HardcorePolicy.isCoreOptionAllowedMirror("FinalBurn Neo", arc, "fbneo-diagnostic-input", "None"))
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("FinalBurn Neo", arc, "fbneo-cpu-speed-adjust", "50%"))
        assertTrue(HardcorePolicy.isCoreOptionAllowedMirror("FinalBurn Neo", arc, "fbneo-cpu-speed-adjust", "100%"))

        // PPSSPP internal cheats & DOSBox-pure strict mode
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("PPSSPP", psp, "ppsspp_cheats", "enabled"))
        assertTrue(HardcorePolicy.isCoreOptionAllowedMirror("PPSSPP", psp, "ppsspp_cheats", "disabled"))
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("DOSBox-pure", dos, "dosbox_pure_strict_mode", "false"))
        assertTrue(HardcorePolicy.isCoreOptionAllowedMirror("DOSBox-pure", dos, "dosbox_pure_strict_mode", "true"))

        // PCSX-ReARMed & Flycast CPU underclocks
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("PCSX-ReARMed", psx, "pcsx_rearmed_psxclock", "40"))
        assertTrue(HardcorePolicy.isCoreOptionAllowedMirror("PCSX-ReARMed", psx, "pcsx_rearmed_psxclock", "57"))
        assertTrue(HardcorePolicy.isCoreOptionAllowedMirror("PCSX-ReARMed", psx, "pcsx_rearmed_psxclock", "auto"))
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("Flycast", dc, "reicast_sh4clock", "150"))
        assertTrue(HardcorePolicy.isCoreOptionAllowedMirror("Flycast", dc, "reicast_sh4clock", "200"))

        // melonDS DSi mode on DS sets, Snes9x layer/clip/transp hiding, VICE x64 autostart
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("melonDS", nds, "melonds_console_mode", "dsi"))
        assertTrue(HardcorePolicy.isCoreOptionAllowedMirror("melonDS", nds, "melonds_console_mode", "ds"))
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("Snes9x", snes, "snes9x_layer_1", "disabled"))
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("Snes9x", snes, "snes9x_gfx_clip", "disabled"))
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("Snes9x", snes, "snes9x_gfx_transp", "disabled"))
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("VICE x64", c64, "vice_autostart", "disabled"))
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("VICE x64", c64, "vice_reset", "soft"))
        assertTrue(HardcorePolicy.isCoreOptionAllowedMirror("VICE x64", c64, "vice_reset", "autostart"))
    }

    @Test
    fun sessionHolderAndBridge_mapHardcoreIndicatorsLeaderboardsRichPresenceAndUserAgent() {
        val resolvedUa =
            AchievementsBridge.resolveUserAgent(
                fullOrClauseUserAgent = "Arcadune/1.0.9 (Android 14) mGBA/0.10.3 rcheevos/12.5",
                fallbackAppVersion = "1.0.9",
            )
        assertEquals("Arcadune/1.0.9 (Android 14) mGBA/0.10.3 rcheevos/12.5", resolvedUa)

        // Presentation-only leaderboardPopupsEnabled = false MUST NOT suppress leaderboard tracking in state!
        RetroAchievementsSessionHolder.reset(
            GameAchievementsUiState(
                hardcorePreference = true,
                leaderboardPopupsEnabled = false,
            ),
        )

        val snapshot =
            AchievementsSnapshot(
                sessionStatus = AchievementsSnapshot.STATUS_LOADED,
                statusMessage = "",
                gameId = 1001,
                gameTitle = "Metroid Fusion",
                gameBadgeUrl = "https://media.retroachievements.org/Images/012345.png",
                gameHash = "abcdef0123456789abcdef0123456789",
                richPresence = "Sector 1 (SRX) • Energy 299 • Missiles 40",
                userAgent = resolvedUa,
                totalCount = 2,
                unlockedCount = 1,
                totalPoints = 35,
                unlockedPoints = 10,
                hardcoreActive = true,
                achievements =
                    arrayOf(
                        AchievementDef(
                            id = 501,
                            title = "First Boss",
                            description = "Defeat Arachnus",
                            badgeUrl = "https://media.retroachievements.org/Badge/501.png",
                            badgeLockedUrl = "https://media.retroachievements.org/Badge/501_lock.png",
                            points = 10,
                            state = 0,
                            category = AchievementDef.CATEGORY_CORE,
                            bucket = 0,
                            unlockedMask = AchievementDef.UNLOCKED_HARDCORE or AchievementDef.UNLOCKED_SOFTCORE,
                            unlockTimeSeconds = 1_700_000_000L,
                            measuredProgress = "",
                            measuredPercent = 100f,
                            rarity = 65.0f,
                            rarityHardcore = 42.5f,
                            type = AchievementDef.TYPE_PROGRESSION,
                        ),
                        AchievementDef(
                            id = 502,
                            title = "Speedrunner",
                            description = "Clear under 2 hours",
                            badgeUrl = "https://media.retroachievements.org/Badge/502.png",
                            badgeLockedUrl = "https://media.retroachievements.org/Badge/502_lock.png",
                            points = 25,
                            state = 1,
                            category = AchievementDef.CATEGORY_CORE,
                            bucket = AchievementDef.BUCKET_ACTIVE_CHALLENGE,
                            // Softcore-only unlock must show as locked while Hardcore is active!
                            unlockedMask = AchievementDef.UNLOCKED_SOFTCORE,
                            unlockTimeSeconds = 0L,
                            measuredProgress = "1/3",
                            measuredPercent = 33.3f,
                            rarity = 12.0f,
                            rarityHardcore = 6.5f,
                            type = AchievementDef.TYPE_WIN,
                        ),
                    ),
                leaderboards =
                    arrayOf(
                        LeaderboardDef(
                            id = 9001,
                            title = "Any% IGT",
                            description = "Fastest clear time",
                            trackerValue = "01:12:44",
                            state = LeaderboardDef.STATE_TRACKING,
                            format = LeaderboardDef.FORMAT_TIME,
                            lowerIsBetter = true,
                        ),
                    ),
                leaderboardTrackers =
                    arrayOf(
                        LeaderboardTracker(
                            id = 9001,
                            display = "01:12:44",
                        ),
                    ),
            )

        RetroAchievementsSessionHolder.applySnapshot(snapshot)
        val uiState = RetroAchievementsSessionHolder.state.value

        assertEquals(AchievementSessionStatus.LOADED, uiState.status)
        assertTrue(uiState.hardcoreActive)
        assertFalse(uiState.leaderboardPopupsEnabled)
        assertEquals("Sector 1 (SRX) • Energy 299 • Missiles 40", uiState.richPresence)
        assertEquals(resolvedUa, uiState.userAgent)

        // Achievement 501 unlocked in Hardcore; Achievement 502 unlocked only in Softcore so locked in Hardcore!
        assertTrue(uiState.achievements[0].isUnlocked)
        assertTrue(uiState.achievements[0].isUnlockedHardcore)
        assertFalse(uiState.achievements[1].isUnlocked)
        assertTrue(uiState.achievements[1].isActiveChallenge)

        // Leaderboard state & live tracker remain active even when leaderboardPopupsEnabled == false!
        assertEquals(1, uiState.leaderboards.size)
        assertTrue(uiState.leaderboards[0].isAttempting)
        assertEquals("01:12:44", uiState.leaderboards[0].trackerValue)
        assertEquals(1, uiState.activeLeaderboardTrackers.size)
        assertEquals("01:12:44", uiState.activeLeaderboardTrackers[0].display)
    }
}
