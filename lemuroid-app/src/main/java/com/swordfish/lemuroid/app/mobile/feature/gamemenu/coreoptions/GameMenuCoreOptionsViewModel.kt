package com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class GameMenuCoreOptionsViewModel(val inputDeviceManager: InputDeviceManager) : ViewModel() {
    class Factory(val inputDeviceManager: InputDeviceManager) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameMenuCoreOptionsViewModel(inputDeviceManager) as T
        }
    }

    val connectedGamePads =
        inputDeviceManager.getEnabledInputsObservable()
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
