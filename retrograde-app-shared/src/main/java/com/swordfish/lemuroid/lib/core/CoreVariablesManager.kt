package com.swordfish.lemuroid.lib.core

import android.content.SharedPreferences
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.InvalidParameterException

class CoreVariablesManager(private val sharedPreferences: Lazy<SharedPreferences>) {
    /**
     * The options for a core: its defaults, then the player's choices for the system, then (when
     * [game] is given and has its own settings switched on) the player's choices for that game.
     */
    suspend fun getOptionsForCore(
        systemID: SystemID,
        systemCoreConfig: SystemCoreConfig,
        game: Game? = null,
    ): List<CoreVariable> {
        val defaultMap = convertCoreVariablesToMap(systemCoreConfig.defaultSettings)
        val coreVariables = retrieveCustomCoreVariables(systemCoreConfig, computeSharedPreferencesPrefix(systemID.dbname))
        val gameVariables =
            if (game != null && hasGameSettings(game)) {
                retrieveCustomCoreVariables(systemCoreConfig, computeGamePrefix(game.systemId, game.fileName))
            } else {
                emptyList()
            }
        val coreVariablesMap =
            defaultMap + convertCoreVariablesToMap(coreVariables) + convertCoreVariablesToMap(gameVariables)
        return convertMapToCoreVariables(coreVariablesMap)
    }

    private suspend fun hasGameSettings(game: Game): Boolean =
        withContext(Dispatchers.IO) {
            sharedPreferences.get().getBoolean(computeGameSettingsEnabledKey(game.systemId, game.fileName), false)
        }

    private fun convertMapToCoreVariables(variablesMap: Map<String, String>): List<CoreVariable> {
        return variablesMap.entries.map { CoreVariable(it.key, it.value) }
    }

    private fun convertCoreVariablesToMap(coreVariables: List<CoreVariable>): Map<String, String> {
        return coreVariables.associate { it.key to it.value }
    }

    private suspend fun retrieveCustomCoreVariables(
        systemCoreConfig: SystemCoreConfig,
        prefix: String,
    ): List<CoreVariable> =
        withContext(Dispatchers.IO) {
            val exposedKeys = systemCoreConfig.exposedSettings
            val exposedAdvancedKeys = systemCoreConfig.exposedAdvancedSettings

            val requestedKeys =
                (exposedKeys + exposedAdvancedKeys).map { it.key }
                    .map { "$prefix$it" }

            sharedPreferences.get().all.filter { it.key in requestedKeys }
                .map { (key, value) ->
                    val result =
                        when (value!!) {
                            is Boolean -> if (value as Boolean) "enabled" else "disabled"
                            is String -> value as String
                            else -> throw InvalidParameterException("Invalid setting in SharedPreferences")
                        }
                    CoreVariable(key.removePrefix(prefix), result)
                }
        }

    companion object {
        private const val RETRO_OPTION_PREFIX = "cv"

        fun computeSharedPreferenceKey(
            retroVariableName: String,
            systemID: String,
        ): String {
            return "${computeSharedPreferencesPrefix(systemID)}$retroVariableName"
        }

        fun computeOriginalKey(
            sharedPreferencesKey: String,
            systemID: String,
        ): String {
            return sharedPreferencesKey.replace(computeSharedPreferencesPrefix(systemID), "")
        }

        private fun computeSharedPreferencesPrefix(systemID: String): String {
            return "${RETRO_OPTION_PREFIX}_${systemID}_"
        }

        // Settings for a single game live next to the system's, under a prefix made of the game's file.
        private const val GAME_OPTION_PREFIX = "gs"

        private fun computeGamePrefix(
            systemID: String,
            fileName: String,
        ): String = "${GAME_OPTION_PREFIX}_${systemID}_${fileName}_"

        /** Key of a core option that applies to one game only. */
        fun computeGameSharedPreferenceKey(
            retroVariableName: String,
            systemID: String,
            fileName: String,
        ): String = "${computeGamePrefix(systemID, fileName)}$retroVariableName"

        /** True (Boolean) when the game uses its own settings instead of the system's. */
        fun computeGameSettingsEnabledKey(
            systemID: String,
            fileName: String,
        ): String = "${computeGamePrefix(systemID, fileName)}#enabled"

        /** The screen filter chosen for one game (String, one of the screen filter values). */
        fun computeGameScreenFilterKey(
            systemID: String,
            fileName: String,
        ): String = "${computeGamePrefix(systemID, fileName)}#screen_filter"
    }
}
