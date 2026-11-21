package com.swordfish.lemuroid.app.tv.settings

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.GameSystem

class CoresSelectionPreferences {
    fun addCoresSelectionPreferences(preferenceScreen: PreferenceScreen) {
        val context = preferenceScreen.context
        val deviceAbis = Build.SUPPORTED_ABIS.toSet()

        GameSystem.all()
            .forEach { system ->
                val compatibleConfigs =
                    system.systemCoreConfigs.filter { cfg ->
                        val required = cfg.supportedOnlyArchitectures
                        required == null || required.intersect(deviceAbis).isNotEmpty()
                    }
                if (compatibleConfigs.size > 1) {
                    preferenceScreen.addPreference(createPreference(context, system, compatibleConfigs))
                }
            }
    }

    private fun createPreference(
        context: Context,
        system: GameSystem,
        compatibleConfigs: List<com.swordfish.lemuroid.lib.library.SystemCoreConfig>,
    ): Preference {
        val preference = ListPreference(context)
        preference.key = CoresSelection.computeSystemPreferenceKey(system.id)
        preference.title = context.getString(system.titleResId)
        preference.isIconSpaceReserved = false
        preference.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        preference.setDefaultValue(compatibleConfigs.first().coreID.coreName)
        preference.isEnabled = compatibleConfigs.size > 1

        preference.entries =
            compatibleConfigs
                .map { it.coreID.coreDisplayName }
                .toTypedArray()

        preference.entryValues =
            compatibleConfigs
                .map { it.coreID.coreName }
                .toTypedArray()

        val appContext = context.applicationContext
        preference.setOnPreferenceChangeListener { _, _ ->
            // Post onto main looper so CoreUpdateWork reads SharedPreferences AFTER ListPreference commits the new value.
            Handler(Looper.getMainLooper()).post {
                LibraryIndexScheduler.scheduleCoreUpdate(appContext)
            }
            true
        }

        return preference
    }
}
