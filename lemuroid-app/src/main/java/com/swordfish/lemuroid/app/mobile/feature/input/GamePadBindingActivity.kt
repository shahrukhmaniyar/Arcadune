package com.swordfish.lemuroid.app.mobile.feature.input

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneDialog
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton
import com.swordfish.lemuroid.app.shared.input.InputBindingUpdater
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.lib.android.RetrogradeActivity
import timber.log.Timber
import javax.inject.Inject

class GamePadBindingActivity : RetrogradeActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    private lateinit var inputBindingUpdater: InputBindingUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inputBindingUpdater = InputBindingUpdater(inputDeviceManager, intent)

        setContent {
            AppTheme {
                val focusRequester = remember { FocusRequester() }

                ArcaduneDialog(
                    modifier =
                        Modifier
                            .focusRequester(focusRequester)
                            .focusable()
                            .onKeyEvent { handleKeyEvent(it.nativeKeyEvent) }
                            .onGloballyPositioned { focusRequester.requestFocus() },
                    onDismissRequest = { finish() },
                    icon = Icons.Filled.SportsEsports,
                    title = { Text(text = inputBindingUpdater.getTitle(applicationContext)) },
                    dismissButton = {
                        ArcadunePillButton(
                            label = stringResource(id = R.string.cancel),
                            onClick = { finish() },
                        )
                    },
                ) {
                    Text(
                        text = inputBindingUpdater.getMessage(applicationContext),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArcaduneColors.OnSurfaceDim,
                    )
                }
            }
        }
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        Timber.i("Received key event: $event")
        val result = inputBindingUpdater.handleKeyEvent(event)

        if (event.action == KeyEvent.ACTION_UP && result) {
            finish()
        }

        return result
    }

    @dagger.Module
    abstract class Module
}
