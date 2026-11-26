# Arcadune — RetroAchievements Hardcore Compliance Review Package

**Emulator:** Arcadune (`io.github.shahrukhmaniyar.arcadune`)  
**Current Version:** `1.1.5` (`versionCode = 267`)  
**Pinned `rcheevos` SDK:** `12.5.0` (`RCHEEVOS_VERSION_STRING = "12.5"`, `libretrodroid/src/main/cpp/rcheevos/src/rc_version.h`)  
**Supported Platforms:** Android 6.0+ (`minSdk = 23`, `targetSdk = 35`) — Mobile (Phone/Tablet) and Android TV (Leanback), `arm64-v8a` & `armeabi-v7a`  
**Repositories:**
- Frontend + `libretrodroid` + `rcheevos`: https://github.com/shahrukhmaniyar/Arcadune
- Libretro Core Build Sources (all 52 cores): https://github.com/shahrukhmaniyar/ArcaduneCores
**Application License:** GNU General Public License v3.0 (`COPYING`)  
**Privacy Policy:** `PRIVACY.md` (`https://github.com/shahrukhmaniyar/Arcadune/blob/master/PRIVACY.md`)  
**Public Hardcore Gate Status:** **LOCKED (`BuildConfig.RA_HARDCORE_APPROVED = false` by default)** until official `RAdmin` approval and `User-Agent` registration.

---

## 1. Executive Summary & Architecture Overview

Arcadune implements RetroAchievements Hardcore Mode end-to-end across its native C++ emulation boundary (`libretrodroid` + `rcheevos 12.5.0`), shared security/account layer (`retrograde-app-shared`), and multi-process Android frontend (`lemuroid-app` across Mobile Compose and Android TV Leanback):

1. **Separated Preference vs. Effective Session State:** `RetroAchievementsPreferences.hardcoreEnabled` stores the user's saved preference, while `GameAchievementsUiState.hardcoreActive` + native `Achievements::isHardcoreEnforced()` track the mode actually running in the active `:game` process.
2. **Pre-`retro_init()` Hardcore Option Enforcement:** `GLRetroView` applies `AchievementsConfig` and Hardcore enforcement to `LibretroDroid` *before* `LibretroDroid::create` calls `retro_init()` or `retro_load_game()`, ensuring `Environment::getVariable` blocks disallowed startup/boot core options from frame 0.
3. **Visible Mode Indicators & One-Action Access:** Both `HARDCORE` and `CASUAL` badges are displayed at game launch (`GameLoadedCapsule` on Mobile and Android TV) and continuously inside the in-game pause menu (`GameMenuHomeScreen`, `GameMenuAchievementsScreen`, and Android TV `TVGameMenuFragment`), with switching from Casual to Hardcore available in one tap/click.
4. **Confirmed Clean Pristine Restart on Mid-Game Enable:** Enabling Hardcore during a running game requires explicit confirmation warning that unsaved progress will be lost while ordinary in-game saves (`.srm` / memory cards) are preserved. Confirming bumps a monotonic `TransitionFence` epoch, suspends `rc_client` evaluation, cancels queued state loads and cheat writes, clears active cheats/rewind/latched inputs, unloads and `dlclose`s the Libretro core, reloads a fresh core instance from unpatched `pristineRomBytes_` / original ROM path with Hardcore core-option filtering, restores ordinary SRAM only (skipping all save-state autoloads), refreshes `rc_libretro_memory_init`, calls `rc_client_reset()`, and enables Hardcore evaluation **only after** clean boot succeeds.
5. **Dynamic Memory Map Re-Initialization:** `Environment` tracks a generation counter across `RETRO_ENVIRONMENT_SET_MEMORY_MAPS` calls, and `Achievements` automatically refreshes `rc_libretro_memory_init` on post-boot frame 1, across `reset()` / `unserializeState()`, and whenever a core updates its memory descriptors dynamically, while checking `rc_libretro_memory_init`'s boolean return value (`hasValidMemoryRegion_`) so 0-byte cores can never run Hardcore silently.
6. **Single Authoritative Boundary Policy (`HardcorePolicy` + C++ `coreLock`):** Save-state loading, cheats, rewind, `< 1x` slowdown, frame advance, debuggers/trainers, disallowed core options, and external file mutations (`GameProcessLock`) are blocked at execution time in both Kotlin and C++.
7. **Explicit Quick Resume vs. Normal Launch:** Normal game launches keep Hardcore active and skip `quickSaveData` autoload (preserving ordinary `saveRAMData`). Choosing explicit **Resume** (`onGameQuickResume`) switches the effective session and `AchievementsConfig` to **Casual** *before* initializing `GLRetroView` or restoring the snapshot, and displays a clear Casual Mode HUD notice.
8. **Windows `RAIntegration.dll` Applicability:** Arcadune ships exclusively for Android (`arm64-v8a` / `armeabi-v7a`); no Windows build exists, so Windows `RAIntegration.dll` developer tooling is not applicable (`N/A`).

---

## 2. Official Requirements Compliance Checklist

Mapped against the [RetroAchievements Hardcore Compliance Requirements](https://docs.retroachievements.org/general/hardcore-compliance-requirements.html), the [`rcheevos` `rc_client` Integration Guide](https://github.com/RetroAchievements/rcheevos/wiki/rc_client-integration), and the [RetroAchievements FAQ](https://docs.retroachievements.org/general/faq.html).

### 2.1 Mode Switching, Indicators & Clean Reset Sequence

| Requirement | Status | Code & Configuration Evidence | Reproduction Steps | Test Evidence |
|---|---|---|---|---|
| **Visible Hardcore setting; separate saved preference from active session; "Casual Mode" terminology** | **PASS** | `RetroAchievementsPreferences.kt` (`hardcoreEnabled`, `isHardcoreApproved()`), `RetroAchievementsSettingsScreen.kt`, `RetroAchievementsModels.kt` (`hardcorePreference` vs `hardcoreActive`), `strings.xml` (`Casual` / `Casual Mode` throughout, zero `Softcore` UI strings) | Open **Settings > RetroAchievements** to toggle saved preference; inspect active session badge in **Game Menu** independently when Quick Resume or an unsupported core overrides the session to Casual Mode. | `RetroAchievementsHardcoreIntegrationTest`, `HardcoreComplianceRegressionTest` |
| **Effective mode shown at game launch and in-game menu (Mobile & TV)** | **PASS** | `RetroAchievementsOverlay.kt` (`GameLoadedCapsule` `HARDCORE` / `CASUAL` pill), `TVGameScreen.kt` (mounts `RetroAchievementsOverlay`), `GameMenuHomeScreen.kt`, `GameMenuAchievementsScreen.kt`, `TVGameMenuFragment.kt` (`pref_game_ra_mode`) | Launch any RA-supported title on phone or Android TV; observe the launch placard badge (`HARDCORE` or `CASUAL`) and open the Pause Menu to see the live badge. | `HardcoreComplianceRegressionTest.sessionHolderAndBridge_*` |
| **Hardcore one action away when Casual is active; confirmation required mid-game** | **PASS** | `GameMenuHomeScreen.kt` (`Enable Hardcore` pill + confirmation dialog), `GameMenuAchievementsScreen.kt`, `TVGameMenuFragment.kt` (`showEnableHardcoreConfirmDialog`) | Start a game in Casual, open Pause Menu, tap **Enable Hardcore**, verify warning dialog; tap **Cancel** and verify Casual gameplay, cheats, and state remain untouched. | `HardcoreComplianceRegressionTest.casualWithCheats_toHardcore_*` |
| **Confirming Hardcore suspends evaluation, clears cheats/rewind/inputs, performs full clean core restart, preserves SRAM, skips state autoload, and resumes Hardcore only after reset** | **PASS** | `BaseGameScreenViewModel.enableHardcoreWithCleanRestart()`, `HardcorePolicy.TransitionFence`, `GameViewModelRewind.clearAndDisable()`, `GameViewModelTouchControls.cancelPendingInputs()`, `LibretroDroid::setHardcoreMode(true)` -> `performCleanHardcoreRestartLocked()` (`libretrodroid.cpp`), `Achievements::beginHardcoreEnableLocked` / `completeHardcoreCleanResetLocked` (`achievements.cpp`) | Enable a cheat in Casual, confirm **Enable Hardcore**, observe full boot sequence from console BIOS/title screen with cheat cleared, SRAM intact, and `HARDCORE` badge active after reset. | `HardcoreComplianceRegressionTest.casualWithCheats_toHardcore_*` |
| **`RC_CLIENT_EVENT_RESET` handling, memory map refresh, failure abort, and immediate Casual exit** | **PASS** | `Achievements::eventHandlerCallback` (`RC_CLIENT_EVENT_RESET` performs immediate single `resetCallback_()` on emulation thread + `TYPE_RESET_REQUIRED` UI notification), `completeHardcoreCleanResetLocked` (`initMemoryRegions` -> `rc_client_reset`), `abortHardcoreEnableLocked` (`rc_client_set_hardcore_enabled(client_, 0)`), `BaseGameScreenViewModel.switchToCasualImmediate()` | Switch Hardcore -> Casual in-game (takes effect immediately without reset); toggle Hardcore back on (requires confirmation + full clean reset again). | `HardcoreComplianceRegressionTest.repeatedModeToggles_andResetFailure_*` |

### 2.2 Emulator Boundary Restrictions

| Requirement | Status | Code & Configuration Evidence | Reproduction Steps | Test Evidence |
|---|---|---|---|---|
| **Block every save-state load path in Hardcore while keeping save-state creation & ordinary SRAM saves working** | **PASS** | `HardcorePolicy.canLoadSaveState` / `canSaveState`, `GameViewModelSaves.kt`, `BaseGameScreenViewModel.kt`, `GameMenuHomeScreen.kt`, `TVGameMenuFragment.kt`, `LibretroDroid::unserializeState` (`libretrodroid.cpp` C++ guard under `coreLock`) | In Hardcore: save to Slot 1 or Quick Save (succeeds); attempt Load from Pause Menu, Quick Load skin button, gamepad shortcut, or TV menu (blocked with toast + native guard). Save in-game to battery SRAM, restart game, verify SRAM loads cleanly. | `HardcoreComplianceRegressionTest.boundaryPolicy_*` |
| **Normal Launch vs. Explicit Quick Resume** | **PASS** | `HardcorePolicy.resolveLaunchDecision`, `GameInteractor.onGameQuickResume`, `GameLauncher.kt` (`EXTRA_EXPLICIT_QUICK_RESUME`), `GameViewModelAchievements.buildNativeConfig`, `GameLoader.kt` | Tap a game card normally with Hardcore enabled -> boots cleanly in Hardcore without loading auto-save state. Long-press game -> tap **Resume** -> session switches to Casual *before* core init, restores snapshot, and shows Casual HUD notice. | `HardcoreComplianceRegressionTest.launchDecision_*` |
| **Block external save/state import, restore, core deletion, and cloud sync while `:game` is running** | **PASS** | `HardcorePolicy.canMutateSaveFilesExternally`, `GameProcessLock.isHeldByAnotherProcess`, `SaveSyncWork.kt` (`Result.retry()`), `SavesSettingsViewModel.kt` (`importGameSave`, `restoreSaves`), `CoresSelectionViewModel.kt` (`deleteCachedCore`, `clearUnusedCachedCores`) | While a game is running in `:game`, trigger background `SaveSyncWork` or attempt save import/ZIP restore/core delete -> deferred/blocked until game closes. | `HardcoreComplianceRegressionTest.boundaryPolicy_*` |
| **Block all cheats (database, custom, Game Genie/Action Replay, UNIBIOS, core options)** | **PASS** | `HardcorePolicy.canApplyCheats`, `GameViewModelCheats.kt`, `GameMenuCheatsScreen.kt` (locks Find/Add/Switch/Delete during Hardcore & transitions), `LibretroDroid::setCheat` (`libretrodroid.cpp`), `environment.cpp` (`fceumm_game_genie`, `genesis_plus_gx_lock_on`, `fbneo-cheat-*`, `fbneo-ips-*`, `fbneo-neogeo-mode=UNIBIOS`, `ppsspp_cheats`) | Open **Game Menu > Cheats** in Hardcore -> all controls including Delete are locked with a Hardcore compliance notice; native `setCheat` rejects any call while Hardcore is active. | `HardcoreComplianceRegressionTest.casualWithCheats_toHardcore_*` |
| **Block rewind, `< 1x` slowdown, frame advance, debuggers, scripting, and input playback; keep documented fast-forward** | **PASS** | `HardcorePolicy.canRewind` / `clampFrameSpeed`, `GameViewModelRewind.kt`, `BaseGameScreenViewModel.kt`, `GameMenuHelper.kt` (`maxFrameSpeed`), `LibretroDroid::setFrameSpeed` (`speed < 1` clamped to `1`). No frame-advance, memory editor, Lua/scripting, or TAS input playback exists in Arcadune. | Hold Rewind shortcut/button in Hardcore -> disabled; tap Fast-Forward -> cycles `2x`/`4x`/`6x`/`8x` normally. | `HardcoreComplianceRegressionTest.boundaryPolicy_*` |
| **Pause anti-abuse cooldown (`rc_client_can_pause`), obscured pause screen (`>= 75%`), and 1Hz `rc_client_idle`** | **PASS** | `Achievements::canPauseLocked` (`rc_client_can_pause`), `Achievements::idleLocked` (`rc_client_idle`), `BaseGameScreenViewModel.canOpenPauseMenu()` + `idleAchievementsWhilePaused()`, `material-themes.xml` & `leanback-themes.xml` (`backgroundDimAmount = 0.82`), `GameMenuHomeScreen.kt` (`0.85f` dark backdrop) | Open Pause Menu in Hardcore -> game frame is `>= 82%` darkened/blurred, `rc_client_idle` ticks every 1s, and rapid re-pausing within `rcheevos` cooldown shows frame-countdown toast instead of pausing. | `HardcoreComplianceRegressionTest.boundaryPolicy_*` |
| **Enforce core-option restrictions (`rc_libretro` + Arcadune supplement) across all compliant cores on Mobile, TV & native `GET_VARIABLE`** | **PASS** | `environment.cpp` (`resolveHardcoreSafeValue` with wildcard `*` matching + safe candidate fallback, `kArcaduneSupplementDisallowedSettings`, `kMandatoryHardcoreDefaults`), `HardcorePolicy.isCoreOptionValueAllowed` (1:1 mirror), `GameMenuCoreOptionsScreen.kt`, `CoreOptionsPreferenceHelper.kt`, `TVGameMenuFragment.kt` | Set `fbneo-cpu-speed-adjust` to `50%`, `swanstation_Console_Region` to `PAL`, or `beetle_saturn_cart` to `Action Replay ROM` in Casual; enable Hardcore -> native `Environment` forces `100%` / `Auto` and menu marks disallowed choices locked. | `HardcoreComplianceRegressionTest.coreOptionPolicy_*`, `AlternativeCoresArchitectureTest` |
| **Keep Rich Presence & Leaderboards active in Hardcore; popup toggle is presentation-only** | **PASS** | `achievements.cpp` (`RC_CLIENT_EVENT_LEADERBOARD_*`, `rc_client_get_rich_presence_message`), `GameViewModelAchievements.kt` (`leaderboardPopupsEnabled` gates only HUD banners/trackers), `GameMenuAchievementsScreen.kt` (`Leaderboards` filter tab) | Disable **Leaderboard Popups** in **Settings > RetroAchievements**, trigger a leaderboard in Hardcore -> HUD popup is hidden, but live tracking, submission, and **Game Menu > Achievements > Leaderboards** remain active. | `HardcoreComplianceRegressionTest.sessionHolderAndBridge_*` |
| **Disable Hardcore on non-compliant / 0-RAM / unvetted cores with explicit reason & switch guidance** | **PASS** | `RetroAchievementsConsoleMap.hardcoreUnsupportedReason` (`MAME2003PLUS`, `CITRA`, `AZAHAR`, `YABASANSHIRO`, `A5200`, `ATARI800`, `BSNES`, `RACE`, `ARMGBA`, `GPSP`, `BLASTEM`, `PARALLEL_N64`), `HardcorePolicy.resolveLaunchDecision`, `GameMenuAchievementsScreen.kt` | Launch a title on `YabaSanshiro`, `bsnes`, `RACE`, `armGBA`, `gpSP`, `BlastEm`, or `ParaLLEl N64` -> runs in Casual Mode and displays the exact core limitation + recommended compliant core (e.g., `Beetle Saturn`, `Snes9x`, `mGBA`). | `RetroAchievementsHardcoreIntegrationTest`, `AlternativeCoresArchitectureTest` |

### 2.3 Full RetroAchievements Integration, Security & Identity

| Requirement | Status | Code & Configuration Evidence | Reproduction Steps | Test Evidence |
|---|---|---|---|---|
| **Game identification, disc/archive hashing, FDS & PCE-CD promotion, multi-disc `changeDisk`, and clear container diagnostics** | **PASS** | `achievements.cpp` (`beginLoadGame`, VFS callbacks `rc_hash_init_custom_filereader`, `.fds` -> Console `81`, `.cue`/`.m3u` -> PCE-CD Console `76`, `.cue`/`.bin`/`.gdi`/`.iso`/`.m3u` disc hashing, Arcade ZIP stem hashing, `changeMediaLocked` -> `rc_client_begin_identify_and_change_media`, and explicit `hashFailureReason_` when user loads unsupported compressed `.chd`/`.cso`/`.cdi`/`.mds`/PS1 `.pbp` containers) | Launch cartridge, `.fds` disk, `.cue`/`.bin` or `.m3u` multi-disc PSX/Saturn/Sega CD/PCE-CD game, `.gdi` Dreamcast disc, or `.zip` FBNeo ROM; swap discs via **Game Menu > Change Disk** and verify `changeMediaLocked` re-validates media. | Verified in `achievements.cpp` + `environment.cpp` NDK build |
| **Keystore AES-256-GCM token storage, legacy migration & credential redaction** | **PASS** | `RetroAchievementsTokenStore.kt` (`AndroidKeyStore` `AES/GCM/NoPadding` 256-bit key `arcadune_ra_token_key_v1`, `ra_token_enc_v1`, automatic migration & deletion of legacy `ra_token`), `RetroAchievementsUserAgent.redactSecrets`, `AchievementsBridge.redactLogText`, `achievements.cpp` (`redactSecrets`) | Sign in -> inspect `SharedPreferences` XML (`ra_token_enc_v1` ciphertext only, zero plaintext `ra_token` or password). Sign out -> both keys deleted. | `RetroAchievementsHardcoreIntegrationTest.tokenStore_*` |
| **Durable cross-process offline unlock & leaderboard caching with multi-account isolation & active-core `User-Agent` preservation** | **PASS** | `RetroAchievementsOfflineQueue.kt` (`@SerialName` schema stability, cross-process `FileLock`, persisted per-item `userAgent`, `awardachievement` & `submitlbentry` replay with `o=` elapsed seconds and `rc_api_runtime.c` MD5 `v=` signature, captive-portal/non-JSON retry resilience, `"User already has"` idempotency, strict `username` isolation) | Disconnect network, trigger unlock/leaderboard -> queued without storing token; reconnect -> replays with exact active-core `User-Agent` and valid `v=` signature. | `RetroAchievementsHardcoreIntegrationTest.offlineQueue_*` |
| **Unique outbound `User-Agent` across every RA HTTP call (including Coil badge fetches)** | **PASS** | `RetroAchievementsUserAgent.kt`, `AchievementsBridge.kt`, `RetroAchievementsAuthClient.kt`, `RetroAchievementsOfflineQueue.kt`, `LemuroidApplication.kt` (`newImageLoader` OkHttp interceptor for `retroachievements.org`), `achievements.cpp` (`Arcadune/<ver> (Android <rel>) [<core>/<ver> ]rcheevos/12.5`) | Capture headers on login, session start, patch fetch, badge image fetch, ping, award, leaderboard submit, and offline replay -> every request carries `Arcadune/<version>`. | `RetroAchievementsHardcoreIntegrationTest.outboundUserAgent_*` |
| **Configurable staging server host (`-PraServerHost`) for safe testing** | **PASS** | `lemuroid-app/build.gradle.kts` (`-PraServerHost=...` -> `BuildConfig.RA_SERVER_HOST`), `GameViewModelAchievements.kt`, `RetroAchievementsAuthClient.kt`, `RetroAchievementsOfflineQueue.kt`, `achievements.cpp` | Build with `-PraHardcoreApproved=true -PraServerHost=stage.retroachievements.org` (or loopback mock server) -> routes 100% of auth, runtime, and offline queue requests away from production. | Verified in `lemuroid-app/build.gradle.kts` + mock server tests |

---

## 3. Explicit Audit of Automatic Rejection Criteria

Every automatic rejection criterion in the [RetroAchievements Hardcore Compliance Requirements](https://docs.retroachievements.org/general/hardcore-compliance-requirements.html) has been audited directly:

1. **Spoofing another emulator's `User-Agent` (e.g., `RetroArch`, `RALibRetro`, `DuckStation`):** **PASS (Never Spoofed).** Arcadune has never sent another emulator's `User-Agent` in any commit or release (`v1.0.0`–`v1.0.9`). All requests identify exclusively as `Arcadune/<version>`.
2. **Shipping Hardcore enabled publicly before official approval:** **PASS (Gated Off).** `BuildConfig.RA_HARDCORE_APPROVED` defaults to `false` in `lemuroid-app/build.gradle.kts`, and `RetroAchievementsPreferences.HARDCORE_SUPPORT_APPROVED = false`. Standard release builds (`assembleFreeBundleRelease` / `assembleFreeDynamicRelease`) cannot activate Hardcore until `RAdmin` grants approval.
3. **Enabling Hardcore mid-game without a full reset / clean boot:** **PASS.** Mid-game activation requires user confirmation and executes `LibretroDroid::performCleanHardcoreRestartLocked()` (`dlclose` + fresh `dlopen` + unpatched ROM boot) before `rc_client` resumes Hardcore evaluation.
4. **Allowing save-state loads, rewind, frame advance, or slowdown in Hardcore:** **PASS.** Blocked at both `HardcorePolicy` (UI/ViewModel/Shortcuts/TV) and C++ `LibretroDroid` (`unserializeState`, `setFrameSpeed`).
5. **Allowing gameplay cheats or disallowed core options in Hardcore:** **PASS.** Blocked at `GameViewModelCheats`, `LibretroDroid::setCheat`, `environment.cpp` (`RETRO_ENVIRONMENT_GET_VARIABLE` from `retro_init()` onward), and `GameMenuCoreOptionsScreen` / `TVGameMenuFragment`.
6. **Core license violations or unauthorized commercial use of non-commercial cores:** **PASS.** Arcadune is 100% free, non-commercial GPLv3 open-source software with zero paid tiers, IAP, ads, subscriptions, or donations, and all 52 cores' sources are published at `https://github.com/shahrukhmaniyar/ArcaduneCores` and listed in-app in `AboutDialog.kt` (see Section 5).
7. **Missing or inaccurate Privacy Policy / undisclosed telemetry:** **PASS.** Zero analytics or telemetry SDKs exist; `PRIVACY.md` is published on `master` (`https://github.com/shahrukhmaniyar/Arcadune/blob/master/PRIVACY.md`) and linked directly from `README.md`, **Settings > RetroAchievements**, and **Settings > Info / About** (see Section 6).
8. **Storing RetroAchievements passwords or exposing credentials in logs:** **PASS.** Passwords are never persisted; Connect tokens are encrypted via Android Keystore `AES-256-GCM`; `redactSecrets` scrubs `t=`, `p=`, and `"Token"` across C++ and Kotlin logs.

---

## 4. Six-Month Public Release Eligibility & Lineage Disclosure

- **Upstream Parent Project Lineage (Qualifying Parent > 6 Years Public):**
  - Original ancestor **Retrograde** (`https://github.com/retrograde/retrograde-android`): initial commit `d3e670886ea68d59d586edca2f6c81e1a690c063` on **2017-09-04**.
  - Qualifying parent **Lemuroid** by Filippo Scognamiglio (`https://github.com/Swordfish90/Lemuroid`) + **LibretroDroid** (`https://github.com/Swordfish90/LibretroDroid`): publicly released since **June 2019** (`b22026cc8d840a8bd7a6c0f69e0131bc63c3e7d1`, `2019-06-17`), with Arcadune forked from upstream `master` commit `3625bc121d5d79fd4b80ce930fb0f3236c930e8` (`2026-10-16`).
- **Arcadune Public Release History (`https://github.com/shahrukhmaniyar/Arcadune/releases`):**
  - First public Arcadune release **`v1.0.0`** (`92520d8f2c77addd348d8a63e20a533e85f773fb`): **2026-11-02**.
  - Subsequent public releases: `v1.0.1` (`2026-11-02`) through **`v1.0.9`** (`0a5eee2770566b51d50447a9a876b57a77ca5e2a`, **2026-11-14**, first Casual-only RetroAchievements release).
- **Transparent Eligibility Flag for `RAdmin`:**
  - While Arcadune's underlying engine (**Lemuroid** / **LibretroDroid**) has been publicly distributed and battle-tested for **over 7 years**, Arcadune's standalone rebranded fork (`v1.0.0`, `2026-11-02`) is **under 6 months old** as an independent distribution. We explicitly disclose both dates so `RAdmin` can determine whether Lemuroid's parent release history satisfies the 6-month maturity rule immediately or whether Arcadune should complete a 6-month standalone window from `2026-11-02` (until **2027-05-02**) before Hardcore is flipped on.

---

## 5. Shipped & On-Demand Emulation Cores (All 52 Cores), Licenses & Hardcore Matrix

### 5.1 Commercialization, Core Hosting & License Compliance

- **Commercial Status:** **100% Free & Non-Commercial Open-Source Software (GPLv3).** Zero paid editions, IAP, ads, subscriptions, sponsor gates, or donation prompts exist anywhere in Arcadune.
- **All 6 Non-Commercial Cores Accounted For (`Snes9x`, `Genesis Plus GX`, `FBNeo`, `MAME 2003-Plus`, `PicoDrive`, `fMSX`):** Because Arcadune is distributed strictly free of charge with zero monetization, bundling/downloading these 6 non-commercial cores complies fully with their upstream non-commercial licenses.
- **Core Provenance & Source Availability (GPLv3 §5–6):** All 52 core `.so` binaries (`arm64-v8a` and `armeabi-v7a`, with `Citra` & `Azahar` 64-bit `arm64-v8a`) are built from full public source at [`shahrukhmaniyar/ArcaduneCores`](https://github.com/shahrukhmaniyar/ArcaduneCores) and hosted on Arcadune's GitHub Releases (`cores-2`). The Full APK (`freeBundle`) packages the **29 default cores** and downloads any of the **23 optional alternative cores** only when selected by the user; the Lite APK (`freeDynamic`) downloads selected cores on demand. All 52 cores, their licenses, and clickable upstream links are displayed directly inside the app under **Settings > Info / About > Emulator Cores & Licenses** (`AboutDialog.kt` + `CoreID.kt`).

### 5.2 Complete 52-Core Compliance & License Table

| # | `CoreID` | Distribution | `SystemID`(s) | Upstream Repository | License | Hardcore Status & Enforcement Notes |
|---|---|---|---|---|---|---|
| 1 | `STELLA` | Built-in Default | `ATARI2600` | https://github.com/stella-emu/stella | GPLv2 | **ENABLED** — Console `25`, 128B RAM mapped |
| 2 | `FCEUMM` | Built-in Default | `NES` (`.nes`, `.fds`) | https://github.com/libretro/libretro-fceumm | GPLv2 | **ENABLED** — Consoles `7` / `81`; `fceumm_game_genie` & PAL/Dendy blocked |
| 3 | `SNES9X` | Built-in Default | `SNES` | https://github.com/snes9xgit/snes9x | Snes9x Non-Commercial | **ENABLED** — Console `3`; PAL & `snes9x_layer_*`/`gfx_*` hiding blocked |
| 4 | `GENESIS_PLUS_GX` | Built-in Default | `SMS`, `GG`, `GENESIS`, `SEGACD` | https://github.com/ekeeke/Genesis-Plus-GX | Non-Commercial | **ENABLED** — Consoles `11, 15, 1, 9`; `lock_on` cheats & PAL blocked |
| 5 | `GAMBATTE` | Built-in Default | `GB`, `GBC` | https://github.com/libretro/gambatte-libretro | GPLv2 | **ENABLED** — Consoles `4, 6` |
| 6 | `MGBA` | Built-in Default | `GBA` | https://github.com/mgba-emu/mgba | MPL-2.0 | **ENABLED** — Console `5` |
| 7 | `MUPEN64_PLUS_NEXT` | Built-in Default | `N64` | https://github.com/libretro/mupen64plus-libretro-nx | GPLv2 | **ENABLED** — Console `2` (`maxFrameSpeed = 6`) |
| 8 | `PCSX_REARMED` | Built-in Default | `PSX` | https://github.com/libretro/pcsx_rearmed | GPLv2 | **ENABLED** — Console `12`; PAL & `<55` clock underclock blocked |
| 9 | `PPSSPP` | Built-in Default | `PSP` | https://github.com/hrydgard/ppsspp | GPLv2+ | **ENABLED** — Console `41`; `ppsspp_cheats` forced `disabled` |
| 10 | `FBNEO` | Built-in Default | `FBNEO` | https://github.com/finalburnneo/FBNeo | FBNeo Non-Commercial | **ENABLED** — Console `27`; `<100%` CPU speed, `UNIBIOS`, `fbneo-cheat-*`, `fbneo-ips-*`, `fbneo-diagnostic-input=None` enforced |
| 11 | `MAME2003PLUS` | Built-in Default | `MAME2003PLUS` | https://github.com/libretro/mame2003-plus-libretro | MAME Non-Commercial | **DISABLED** — Exposes 0 bytes RAM & embeds MAME OSD cheat/DIP menu |
| 12 | `MELONDS` | Built-in Default | `NDS` | https://github.com/JesseTG/melonds-ds | GPLv3 | **ENABLED** — Console `18`; `melonds_console_mode = dsi` blocked |
| 13 | `HANDY` | Built-in Default | `LYNX` | https://github.com/libretro/libretro-handy | Zlib | **ENABLED** — Console `13`; `60Hz` enforced |
| 14 | `MEDNAFEN_PCE_FAST` | Built-in Default | `PC_ENGINE` | https://github.com/libretro/beetle-pce-fast-libretro | GPLv2 | **ENABLED** — Consoles `8` / `76` (PCE-CD); `pce_fast_disable_softreset = enabled` enforced |
| 15 | `PROSYSTEM` | Built-in Default | `ATARI7800` | https://github.com/libretro/prosystem-libretro | GPLv2 | **ENABLED** — Console `51` |
| 16 | `MEDNAFEN_NGP` | Built-in Default | `NGP`, `NGC` | https://github.com/libretro/beetle-ngp-libretro | GPLv2 | **ENABLED** — Console `14` |
| 17 | `MEDNAFEN_WSWAN` | Built-in Default | `WS`, `WSC` | https://github.com/libretro/beetle-wswan-libretro | GPLv2 | **ENABLED** — Console `53` |
| 18 | `CITRA` | Built-in Default | `NINTENDO_3DS` | https://github.com/libretro/citra | GPLv2+ | **DISABLED** — Zero RAM exposed & no 3DS console memory map in `rcheevos 12.5` |
| 19 | `DOSBOX_PURE` | Built-in Default | `DOS` | https://github.com/schellingb/dosbox-pure | GPLv2 | **ENABLED** — Console `26`; `dosbox_pure_strict_mode = true` enforced |
| 20 | `FLYCAST` | Built-in Default | `DREAMCAST` | https://github.com/flyinghead/flycast | GPLv2 | **ENABLED** — Console `40`; `reicast_sh4clock < 200` blocked |
| 21 | `OPERA` | Built-in Default | `THREEDO` | https://github.com/libretro/opera-libretro | LGPL / GPLv2+ | **ENABLED** — Console `43`; `< 1.0x` CPU underclock blocked (`1.0x+` allowed per `rcheevos`) |
| 22 | `YABASANSHIRO` | Built-in Default | `SATURN` | https://github.com/libretro/yabause | GPLv2 | **DISABLED** — Binary exposes 0 bytes RAM (recommend switching to `Beetle Saturn`) |
| 23 | `PICODRIVE` | Built-in Default | `SEGA32X`, `SMS`, `GENESIS`, `SEGACD` | https://github.com/irixxxx/picodrive | MAME Non-Commercial / BSD | **ENABLED** — Consoles `10, 11, 1, 9`; `Europe` & `Japan PAL` 50Hz regions blocked |
| 24 | `A5200` | Built-in Default | `ATARI5200` | https://github.com/libretro/a5200 | GPLv2 | **DISABLED** — Exposes 0 bytes RAM & `rcheevos 12.5` lacks Atari 5200 file hashing |
| 25 | `ATARI800` | Built-in Default | `ATARI800` | https://github.com/libretro/libretro-atari800 | GPLv2 | **DISABLED** — Internal F1 trainer/state OSD menu & no RA console ID |
| 26 | `GEARCOLECO` | Built-in Default | `COLECOVISION` | https://github.com/drhelius/Gearcoleco | MIT | **ENABLED** — Console `44` |
| 27 | `VICE_X64` | Built-in Default | `C64` | https://github.com/libretro/vice-libretro | GPLv2 | **ENABLED** — Console `30`; `vice_autostart=enabled` & `vice_reset=autostart` enforced |
| 28 | `APPLEWIN` | Built-in Default | `APPLEII` | https://github.com/audetto/AppleWin | GPLv2 | **ENABLED** — Console `38` |
| 29 | `FMSX` | Built-in Default | `MSX` | https://github.com/libretro/fmsx-libretro | fMSX Non-Commercial | **ENABLED** — Console `29` (`fmsx_video_mode = NTSC` enforced) |
| 30 | `ARMGBA` | On-Demand Alt | `GBA`, `GB`, `GBC` | https://github.com/shahrukhmaniyar/ArcaduneCores | GPLv3 | **DISABLED** — Custom fork not yet vetted on RA's official core allowlist (Casual Mode supported; recommend `mGBA` / `Gambatte` for Hardcore) |
| 31 | `GPSP` | On-Demand Alt | `GBA` | https://github.com/davidgfnet/gpsp | GPLv2 | **DISABLED** — Dynarec lacks `rcheevos` Hardcore core-option vetting (Casual Mode supported; recommend `mGBA` / `VBA-M` / `Beetle GBA`) |
| 32 | `VBAM` | On-Demand Alt | `GBA`, `GB`, `GBC` | https://github.com/visualboyadvance-m/visualboyadvance-m | GPLv2 | **ENABLED** — Consoles `5, 4, 6`; `vbam_layer_*` hiding & turbo blocked |
| 33 | `MEDNAFEN_GBA` | On-Demand Alt | `GBA` | https://github.com/libretro/beetle-gba-libretro | GPLv2 | **ENABLED** — Console `5` (requires `gba_bios.bin`) |
| 34 | `SAMEBOY` | On-Demand Alt | `GB`, `GBC` | https://github.com/LIJI32/SameBoy | MIT | **ENABLED** — Consoles `4, 6` |
| 35 | `GEARBOY` | On-Demand Alt | `GB`, `GBC` | https://github.com/drhelius/Gearboy | GPLv3 | **ENABLED** — Consoles `4, 6` |
| 36 | `MESEN` | On-Demand Alt | `NES` (`.nes`, `.fds`) | https://github.com/SourMesen/Mesen2 | GPLv3 | **ENABLED** — Consoles `7` / `81`; PAL/Dendy & `<100%` speed blocked |
| 37 | `NESTOPIA` | On-Demand Alt | `NES` (`.nes`, `.fds`) | https://github.com/0ldsk00l/nestopia | GPLv2 | **ENABLED** — Consoles `7` / `81`; `nestopia_favored_system` PAL/Dendy blocked |
| 38 | `QUICKNES` | On-Demand Alt | `NES` | https://github.com/libretro/QuickNES_Core | LGPL-2.1+ | **ENABLED** — Console `7` |
| 39 | `MEDNAFEN_SUPAFAUST` | On-Demand Alt | `SNES` | https://github.com/libretro/supafaust | GPLv2+ | **ENABLED** — Console `3`; `supafaust_region` PAL blocked |
| 40 | `BSNES` | On-Demand Alt | `SNES` | https://github.com/bsnes-emu/bsnes | GPLv3 | **DISABLED** — Android `.so` has stub `retro_get_memory_data` and no `SET_MEMORY_MAPS` (0 bytes RAM; recommend `Snes9x` or `Beetle Supafaust`) |
| 41 | `GEARSYSTEM` | On-Demand Alt | `SMS`, `GG` | https://github.com/drhelius/Gearsystem | GPLv3 | **ENABLED** — Consoles `11, 15`; `gearsystem_timing = PAL` & PAL regions blocked |
| 42 | `BLASTEM` | On-Demand Alt | `GENESIS` | https://github.com/libretro/blastem | GPLv3 | **DISABLED** — Unvetted for RA Hardcore (Casual Mode supported; recommend `Genesis Plus GX` or `PicoDrive`) |
| 43 | `SWANSTATION` | On-Demand Alt | `PSX` | https://github.com/libretro/swanstation | GPLv3 | **ENABLED** — Console `12`; PAL region, `<100%` CPU clock, & `Enable8MBRAM` blocked |
| 44 | `MEDNAFEN_PSX` | On-Demand Alt | `PSX` | https://github.com/libretro/beetle-psx-libretro | GPLv2 | **ENABLED** — Console `12`; `<100%` CPU freq & `skip_bios` blocked |
| 45 | `PARALLEL_N64` | On-Demand Alt | `N64` | https://github.com/libretro/parallel-n64 | GPLv2+ | **DISABLED** — Unvetted plugin/HLE matrix for RA Hardcore (Casual Mode supported; recommend `Mupen64Plus-Next`) |
| 46 | `DESMUME` | On-Demand Alt | `NDS` | https://github.com/TASEmulators/desmume | GPLv2 | **ENABLED** — Console `18` |
| 47 | `AZAHAR` | On-Demand Alt | `NINTENDO_3DS` | https://github.com/azahar-emu/azahar | GPLv2+ | **DISABLED** — No 3DS console memory map in `rcheevos 12.5` |
| 48 | `MEDNAFEN_SATURN` | On-Demand Alt | `SATURN` | https://github.com/libretro/beetle-saturn-libretro | GPLv2 | **ENABLED** — Console `39`; PAL regions & Action Replay cart blocked |
| 49 | `MEDNAFEN_PCE` | On-Demand Alt | `PC_ENGINE` | https://github.com/libretro/beetle-pce-libretro | GPLv2 | **ENABLED** — Consoles `8` / `76` (PCE-CD); `pce_disable_softreset = enabled` enforced |
| 50 | `GEARLYNX` | On-Demand Alt | `LYNX` | https://github.com/drhelius/Gearlynx | GPLv3 | **ENABLED** — Console `13` |
| 51 | `RACE` | On-Demand Alt | `NGP`, `NGC` | https://github.com/libretro/RACE | GPLv2 | **DISABLED** — Maps `0x0000..0x3FFF` instead of `rcheevos`'s `0x4000` NGP base (0 mapped bytes; recommend `Mednafen NGP`) |
| 52 | `STELLA2014` | On-Demand Alt | `ATARI2600` | https://github.com/libretro/stella2014-libretro | GPLv2 | **ENABLED** — Console `25` |

---

## 6. Privacy Policy, Data Retention & Telemetry Verification

Published at [`PRIVACY.md`](../PRIVACY.md) (`https://github.com/shahrukhmaniyar/Arcadune/blob/master/PRIVACY.md`) and linked in `README.md`, `AboutDialog.kt`, and `RetroAchievementsSettingsScreen.kt`:

- **First-Party Servers & Telemetry:** **None.** Arcadune operates zero backend servers and embeds zero analytics, crash-reporting, or advertising SDKs.
- **Local Credential & Data Storage:** Passwords are used once over HTTPS (`r=login2`) and never stored. Connect tokens are encrypted at rest with Android Keystore `AES-256-GCM` (`ra_token_enc_v1`). Signing out immediately deletes all token keys; clearing storage or uninstalling deletes all local app data.
- **Third-Party Endpoints Disclosed:**
  1. `https://retroachievements.org/dorequest.php` & `https://media.retroachievements.org` (opt-in RetroAchievements sign-in, hashes, Rich Presence, unlocks, leaderboards, badges).
  2. `https://api.github.com`, `https://github.com`, `https://objects.githubusercontent.com`, `https://raw.githubusercontent.com` (update checks, on-demand core `.so` downloads from `cores-2`, `libretro-thumbnails` box art, `libretro-database` cheats).
  3. `https://www.google.com` + user-selected image URL (only when the user explicitly opens **Change cover > Search online** in `CoverSearchActivity`).
  4. `https://www.googleapis.com` (`play` build flavor only, if the user explicitly signs in to Google Drive `appDataFolder` Save Sync).

---

## 7. Reviewer Verification Guide (`RAdmin`)

1. **Build & Install the Reviewer APK (with Hardcore Unlocked):**
   ```powershell
   # Optional: add -PraServerHost=stage.retroachievements.org to avoid production unlocks
   .\gradlew.bat :lemuroid-app:assembleFreeBundleDebug -PraHardcoreApproved=true
   adb install -r lemuroid-app\build\outputs\apk\freeBundle\debug\lemuroid-app-free-bundle-debug.apk
   ```
2. **Sign In & Enable Hardcore Preference:**
   - Open **Settings > RetroAchievements**, sign in, and toggle **Hardcore Mode** ON.
   - Verify the **Privacy Policy** link (`blob/master/PRIVACY.md`) and **Leaderboard Popups** toggle are visible, and open **Settings > Info (i)** to verify the 52-core FOSS license & upstream directory.
3. **Verify Normal Launch vs. Explicit Quick Resume:**
   - Tap a supported game (e.g., SNES `Snes9x`, GBA `mGBA`, or Saturn `Beetle Saturn`) from the Library: verify the launch capsule displays the gold **`HARDCORE`** badge and boots cleanly from the console startup screen (preserving `.srm` battery saves while skipping auto-save snapshots).
   - Exit to the Library, long-press the game card, and choose **Resume**: verify the session switches to **`CASUAL`** *before* restoring the snapshot and displays `"Casual Mode Active • Quick Resume restored your saved state in Casual Mode"`.
4. **Verify Mid-Game Casual -> Hardcore Clean Restart & Cheat Clearing:**
   - While in Casual Mode, open **Game Menu > Cheats**, enable a cheat, and advance past the title screen.
   - Open **Game Menu**, tap **Enable Hardcore**, first tap **Cancel** (confirming gameplay and cheats continue uninterrupted), then tap **Enable Hardcore -> Restart in Hardcore**.
   - Confirm the game performs a full clean boot from the beginning, cheats are cleared and locked (including Delete), Rewind and Load State are disabled, Save State still saves slots, and Fast-Forward (`2x`–`8x`) remains available.

---

## 8. Remaining External Steps Before Public Enablement

1. **Push `master` Docs Before Submitting:** Ensure `PRIVACY.md`, `README.md`, and `docs/HARDCORE_COMPLIANCE_REVIEW.md` are pushed to `origin/master` on GitHub so `https://github.com/shahrukhmaniyar/Arcadune/blob/master/PRIVACY.md` resolves with HTTP 200 for reviewers.
2. **Official `RAdmin` Approval & `User-Agent` Registration:** Message [`RAdmin`](https://retroachievements.org/messages/create?to=RAdmin) on RetroAchievements.org to register `Arcadune/<version>` (`Arcadune/1.0.9`, `Arcadune/1.1.0+`) for Hardcore sessions; keep `BuildConfig.RA_HARDCORE_APPROVED = false` in public releases until `RAdmin` confirms allowlisting.
3. **Six-Month Fork Maturity Ruling (`RAdmin`):** Confirm with `RAdmin` whether parent Lemuroid's 2019–2026 public release history satisfies the 6-month maturity rule immediately or whether Hardcore activation should wait until May 2, 2027 (`6 months` after Arcadune `v1.0.0` on `2026-11-02`).
