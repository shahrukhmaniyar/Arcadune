package com.swordfish.lemuroid.app.shared.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelRetroGameView
import kotlinx.coroutines.delay

/**
 * The game screen. With [showScreenWhileLoading] the screen (the console skin) is on screen from
 * the first frame and a small indicator shows over the game's own area only if loading takes a
 * moment; without it (Android TV) the indicator fills the screen until the game is ready.
 */
@Composable
fun BaseGameScreen(
    viewModel: BaseGameScreenViewModel,
    showScreenWhileLoading: Boolean = false,
    gameScreen: @Composable (BaseGameScreenViewModel) -> Unit,
) {
    val gameState =
        viewModel.getGameState()
            .collectAsState(GameViewModelRetroGameView.GameState.Uninitialized)
            .value

    val isGameReady =
        gameState is GameViewModelRetroGameView.GameState.Loaded ||
            gameState is GameViewModelRetroGameView.GameState.Ready

    if (showScreenWhileLoading) {
        Box(modifier = Modifier.fillMaxSize()) {
            gameScreen(viewModel)
        }
    } else if (isGameReady) {
        gameScreen(viewModel)
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(gameState)
        }
    }
}

/** A small spinner over the top of the screen (where the game appears), only once loading takes a moment. */
@Composable
private fun LoadingIndicatorOverGame(gameState: GameViewModelRetroGameView.GameState) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SHOW_AFTER_MS)
        visible = true
    }
    if (!visible) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Box(
            modifier =
                if (landscape) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth().fillMaxHeight(UPRIGHT_GAME_AREA)
                },
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(gameState)
        }
    }
}

@Composable
private fun LoadingIndicator(gameState: GameViewModelRetroGameView.GameState) {
    Column(
        modifier = Modifier.wrapContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()

        val message = if (gameState is GameViewModelRetroGameView.GameState.Loading) gameState.message else null
        AnimatedVisibility(message != null) {
            Text(text = message!!, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

private const val SHOW_AFTER_MS = 350L
private const val UPRIGHT_GAME_AREA = 0.4f
