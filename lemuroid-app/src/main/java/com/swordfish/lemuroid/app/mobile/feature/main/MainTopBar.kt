package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork

/** The top bar of the screens other than the library (Settings and its pages). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    currentRoute: MainRoute,
    navController: NavHostController,
    onHelpPressed: () -> Unit,
    mainUIState: MainViewModel.UiState,
) {
    val context = LocalContext.current
    val barColor = BottomAppBarDefaults.containerColor

    Column {
        TopAppBar(
            title = { Text(text = stringResource(currentRoute.titleId)) },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = barColor,
                    containerColor = barColor,
                ),
            navigationIcon = {
                AnimatedVisibility(visible = currentRoute.parent != null, enter = fadeIn(), exit = fadeOut()) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(id = R.string.back))
                    }
                }
            },
            actions = {
                Row {
                    IconButton(onClick = onHelpPressed) {
                        Icon(Icons.Outlined.Info, stringResource(R.string.mobile_settings_help))
                    }
                    if (mainUIState.saveSyncEnabled) {
                        IconButton(
                            onClick = { SaveSyncWork.enqueueManualWork(context.applicationContext) },
                            enabled = !mainUIState.operationInProgress,
                        ) {
                            Icon(Icons.Outlined.CloudSync, stringResource(R.string.save_sync))
                        }
                    }
                }
            },
        )

        AnimatedVisibility(mainUIState.operationInProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
