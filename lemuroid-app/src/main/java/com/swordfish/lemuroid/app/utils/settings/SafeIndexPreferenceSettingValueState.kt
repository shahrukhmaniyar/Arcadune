package com.swordfish.lemuroid.app.utils.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.alorma.compose.settings.storage.base.SettingValueState
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

@Composable
fun rememberSafePreferenceIndexSettingState(
    key: String,
    values: List<String>,
    defaultValue: String,
    preferences: SharedPreferences =
        SharedPreferencesHelper.getSharedPreferences(
            LocalContext.current.applicationContext,
        ),
): SafeIndexPreferenceSettingValueState {
    return remember(key, values, preferences) {
        SafeIndexPreferenceSettingValueState(preferences, key, values, defaultValue)
    }
}

class SafeIndexPreferenceSettingValueState(
    private val preferences: SharedPreferences,
    val key: String,
    private val values: List<String>,
    private val defaultValue: String,
) : SettingValueState<Int> {
    private var _value by mutableStateOf(preferences.safeGetString(key, defaultValue))

    private val fallbackIndex: Int
        get() = values.indexOf(defaultValue).coerceAtLeast(0)

    override var value: Int
        set(index) {
            if (values.isEmpty()) return
            val safeIndex = index.coerceIn(0, values.lastIndex)
            _value = values[safeIndex]
            preferences.edit { putString(key, _value) }
        }
        get() {
            val idx = values.indexOf(_value)
            return if (idx >= 0) idx else fallbackIndex
        }

    override fun reset() {
        value = fallbackIndex
    }
}
