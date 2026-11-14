package com.swordfish.lemuroid.app.mobile.feature.gamemenu.states

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.shared.gamemenu.GameMenuHelper
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat

@OptIn(ExperimentalCoroutinesApi::class)
class GameMenuStatesViewModel(
    private val application: Application,
    private val gameMenuRequest: GameMenuActivity.GameMenuRequest,
    private val statesManager: StatesManager,
    private val disableMissingEntries: Boolean,
    private val statesPreviewManager: StatesPreviewManager,
) : ViewModel() {
    class Factory(
        private val application: Application,
        private val gameMenuRequest: GameMenuActivity.GameMenuRequest,
        private val statesManager: StatesManager,
        private val disableMissingEntries: Boolean,
        private val statesPreviewManager: StatesPreviewManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameMenuStatesViewModel(
                application,
                gameMenuRequest,
                statesManager,
                disableMissingEntries,
                statesPreviewManager,
            ) as T
        }
    }

    data class StateEntry(
        val title: String,
        val description: String,
        val hasState: Boolean,
        val enabled: Boolean,
        val preview: Bitmap?,
    )

    data class State(val entries: List<StateEntry> = emptyList())

    private val refreshTrigger = MutableStateFlow(0)
    private val deleteMutex = Mutex()

    val uiStates =
        refreshTrigger
            .mapLatest {
                val slotsInfo = statesManager.getSavedSlotsInfo(gameMenuRequest.game, gameMenuRequest.coreConfig.coreID)
                val formatter = SimpleDateFormat.getDateTimeInstance()

                val entries =
                    slotsInfo.mapIndexed { index, slotInfo ->
                        val title =
                            application.applicationContext.getString(
                                R.string.game_menu_state,
                                (index + 1).toString(),
                            )
                        val description = GameMenuHelper.getSaveStateDescription(slotInfo, formatter)
                        val isEnabled = !disableMissingEntries || slotInfo.exists
                        val preview =
                            GameMenuHelper.getSaveStateBitmap(
                                application.applicationContext,
                                statesPreviewManager,
                                slotInfo,
                                gameMenuRequest.game,
                                gameMenuRequest.coreConfig.coreID,
                                index,
                            )

                        StateEntry(title, description, slotInfo.exists, isEnabled, preview)
                    }

                State(entries)
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, State())

    fun deleteState(index: Int) {
        if (index !in 0 until StatesManager.MAX_STATES) return
        viewModelScope.launch(Dispatchers.IO) {
            deleteMutex.withLock {
                statesManager.deleteSlotSave(gameMenuRequest.game, gameMenuRequest.coreConfig.coreID, index)
                statesPreviewManager.deletePreviewForSlot(
                    gameMenuRequest.game,
                    gameMenuRequest.coreConfig.coreID,
                    index,
                )
                refreshTrigger.value += 1
            }
        }
    }
}
