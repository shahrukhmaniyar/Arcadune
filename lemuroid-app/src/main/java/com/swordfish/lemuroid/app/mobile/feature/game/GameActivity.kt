package com.swordfish.lemuroid.app.mobile.feature.game

import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import androidx.compose.runtime.Composable
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel

class GameActivity : BaseGameActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android holds finger moves back until the next frame is drawn, then hands them over in a
        // batch: a thumb sliding across the d-pad or onto the next button reached the game up to a
        // frame late. Games want every move as it happens.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_POINTER)
        }
    }

    @Composable
    override fun GameScreen(viewModel: BaseGameScreenViewModel) {
        MobileGameScreen(viewModel)
    }

    override fun getDialogClass() = GameMenuActivity::class.java
}
