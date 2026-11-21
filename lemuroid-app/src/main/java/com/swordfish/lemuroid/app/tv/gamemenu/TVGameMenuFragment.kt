package com.swordfish.lemuroid.app.tv.gamemenu

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.lifecycle.Lifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.achievements.AchievementSessionStatus
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsSessionHolder
import com.swordfish.lemuroid.app.shared.coreoptions.CoreOptionsPreferenceHelper
import com.swordfish.lemuroid.app.shared.coreoptions.LemuroidCoreOption
import com.swordfish.lemuroid.app.shared.gamemenu.GameMenuHelper
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.coroutines.safeCollect
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsPreferences
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager

class TVGameMenuFragment(
    private val statesManager: StatesManager,
    private val statesPreviewManager: StatesPreviewManager,
    private val inputDeviceManager: InputDeviceManager,
    private val game: Game,
    private val systemCoreConfig: SystemCoreConfig,
    private val coreOptions: Array<LemuroidCoreOption>,
    private val advancedCoreOptions: Array<LemuroidCoreOption>,
    private val numDisks: Int,
    private val currentDisk: Int,
    private val audioEnabled: Boolean,
    private val frameSpeed: Int,
    private val fastForwardSupported: Boolean,
    private val maxFrameSpeed: Int = 8,
) : LeanbackPreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        preferenceManager.preferenceDataStore =
            SharedPreferencesHelper.getSharedPreferencesDataStore(requireContext())
        setPreferencesFromResource(R.xml.tv_game_settings, rootKey)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        GameMenuHelper.setupAudioOption(preferenceScreen, audioEnabled)
        GameMenuHelper.setupFastForwardOption(
            activity,
            preferenceScreen,
            frameSpeed,
            fastForwardSupported,
            maxFrameSpeed,
        )
        GameMenuHelper.setupSaveOption(preferenceScreen, systemCoreConfig)
        setupRetroAchievementsModeOption()

        if (numDisks > 1) {
            GameMenuHelper.setupChangeDiskOption(activity, preferenceScreen, currentDisk, numDisks)
        }

        launchOnState(Lifecycle.State.CREATED) {
            initializeLoadAndSave()
        }

        launchOnState(Lifecycle.State.CREATED) {
            initializeControllers()
        }
    }

    private suspend fun initializeControllers() {
        inputDeviceManager.getGamePadsObservable()
            .safeCollect { setupCoreOptions(it.size) }
    }

    private fun setupCoreOptions(connectedGamePads: Int) {
        val coreOptionsScreen =
            findPreference<PreferenceScreen>(GameMenuHelper.SECTION_CORE_OPTIONS)
                ?: return

        coreOptionsScreen.removeAll()

        CoreOptionsPreferenceHelper.addPreferences(
            coreOptionsScreen,
            game.systemId,
            coreOptions.toList(),
            advancedCoreOptions.toList(),
            systemCoreConfig.coreID,
        )

        CoreOptionsPreferenceHelper.addControllers(
            coreOptionsScreen,
            game.systemId,
            systemCoreConfig.coreID,
            connectedGamePads,
            systemCoreConfig.controllerConfigs,
        )
    }

    private suspend fun initializeLoadAndSave() {
        val saveScreen = findPreference<PreferenceScreen>(GameMenuHelper.SECTION_SAVE_GAME)
        val loadScreen = findPreference<PreferenceScreen>(GameMenuHelper.SECTION_LOAD_GAME)
        val hardcoreActive = RetroAchievementsSessionHolder.state.value.hardcoreActive

        saveScreen?.isEnabled = systemCoreConfig.statesSupported
        loadScreen?.isEnabled = systemCoreConfig.statesSupported && !hardcoreActive
        if (hardcoreActive) {
            loadScreen?.summary = getString(R.string.game_menu_hardcore_badge)
        }

        val slotsInfo = statesManager.getSavedSlotsInfo(game, systemCoreConfig.coreID)

        slotsInfo.forEachIndexed { index, saveInfo ->
            val bitmap =
                GameMenuHelper.getSaveStateBitmap(
                    requireContext(),
                    statesPreviewManager,
                    saveInfo,
                    game,
                    systemCoreConfig.coreID,
                    index,
                )

            if (saveScreen != null) {
                GameMenuHelper.addSavePreference(saveScreen, index, saveInfo, bitmap)
            }

            if (loadScreen != null) {
                GameMenuHelper.addLoadPreference(loadScreen, index, saveInfo, bitmap)
            }
        }
    }

    private fun setupRetroAchievementsModeOption() {
        val pref = findPreference<Preference>("pref_game_ra_mode") ?: return
        val achState = RetroAchievementsSessionHolder.state.value
        val active =
            achState.status != AchievementSessionStatus.DISABLED &&
                achState.status != AchievementSessionStatus.NOT_SIGNED_IN &&
                achState.status != AchievementSessionStatus.UNSUPPORTED_SYSTEM
        pref.isVisible = active
        if (!active) return

        val approved = RetroAchievementsPreferences.isHardcoreApproved()
        if (achState.hardcoreActive) {
            pref.title = getString(R.string.ra_mode_hardcore_label)
            pref.summary = getString(R.string.ra_mode_switch_casual_action)
            pref.setOnPreferenceClickListener {
                val resultIntent =
                    Intent().apply {
                        putExtra(GameMenuContract.RESULT_SET_HARDCORE, false)
                    }
                activity?.setResult(android.app.Activity.RESULT_OK, resultIntent)
                activity?.finish()
                true
            }
        } else {
            pref.title = getString(R.string.ra_mode_casual_label)
            pref.summary =
                when {
                    !approved -> getString(R.string.ra_menu_hardcore_fallback_note)
                    !achState.coreHardcoreSupported -> achState.coreUnsupportedReason
                    else -> getString(R.string.ra_mode_enable_hardcore_action)
                }
            pref.setOnPreferenceClickListener {
                if (!approved) {
                    Toast.makeText(requireContext(), R.string.ra_hardcore_registration_note, Toast.LENGTH_LONG).show()
                } else if (!achState.coreHardcoreSupported) {
                    Toast.makeText(requireContext(), achState.coreUnsupportedReason, Toast.LENGTH_LONG).show()
                } else {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.ra_hardcore_confirm_enable_title)
                        .setMessage(R.string.ra_hardcore_confirm_enable_message)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ra_hardcore_confirm_enable_confirm) { _, _ ->
                            val resultIntent =
                                Intent().apply {
                                    putExtra(GameMenuContract.RESULT_SET_HARDCORE, true)
                                }
                            activity?.setResult(android.app.Activity.RESULT_OK, resultIntent)
                            activity?.finish()
                        }
                        .show()
                }
                true
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (GameMenuHelper.onPreferenceTreeClicked(activity, preference)) {
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    @dagger.Module
    class Module
}
