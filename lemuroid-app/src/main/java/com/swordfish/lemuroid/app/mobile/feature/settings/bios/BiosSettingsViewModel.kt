package com.swordfish.lemuroid.app.mobile.feature.settings.bios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.lib.bios.BiosManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class BiosSettingsViewModel(private val biosManager: BiosManager) : ViewModel() {
    class Factory
        @Inject
        constructor(private val biosManager: BiosManager) : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BiosSettingsViewModel(biosManager) as T
            }
        }

    private val _uiState =
        MutableStateFlow(biosManager.cachedBiosInfo ?: biosManager.getBiosInfo())
    val uiState: StateFlow<BiosManager.BiosInfo> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            delay(260)
            _uiState.value = biosManager.getBiosInfoAsync()
        }
    }
}
