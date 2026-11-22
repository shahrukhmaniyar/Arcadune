package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneGlassCard
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton
import com.swordfish.lemuroid.app.shared.update.AppUpdater
import com.swordfish.lemuroid.app.shared.update.UpdateNotice
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.common.displayDetailsSettingsScreen

/** The home screen's notices only (permissions, no games yet, ...), for the library screen. */
@Composable
fun HomeNotices(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext

    ComposableLifecycle { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) viewModel.updatePermissions(applicationContext)
    }

    val permissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) context.displayDetailsSettingsScreen()
        }

    // At most once a day; shows the update notice below when a new version is out.
    LaunchedEffect(Unit) { AppUpdater.checkAutomatically(applicationContext) }

    val state = viewModel.getViewStates().collectAsState(HomeViewModel.UIState()).value
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UpdateNotice()
        AnimatedVisibility(state.showRomsAccessLostCard) {
            HomeNotification(
                titleId = R.string.home_roms_access_lost_title,
                messageId = R.string.home_roms_access_lost_message,
                actionId = R.string.home_roms_access_lost_action,
                onAction = { viewModel.changeLocalStorageFolder(context) },
                enabled = !state.indexInProgress,
            )
        }
        AnimatedVisibility(state.showNoNotificationPermissionCard) {
            HomeNotification(
                titleId = R.string.home_notification_title,
                messageId = R.string.home_notification_message,
                actionId = R.string.home_notification_action,
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }
        AnimatedVisibility(state.showNoGamesCard) {
            HomeNotification(
                titleId = R.string.home_empty_title,
                messageId = R.string.home_empty_message,
                actionId = R.string.home_empty_action,
                onAction = { viewModel.changeLocalStorageFolder(context) },
                enabled = !state.indexInProgress,
            )
        }
        AnimatedVisibility(state.showNoMicrophonePermissionCard) {
            HomeNotification(
                titleId = R.string.home_microphone_title,
                messageId = R.string.home_microphone_message,
                actionId = R.string.home_microphone_action,
                onAction = { permissionsLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            )
        }
    }
}

@Composable
private fun HomeNotification(
    titleId: Int,
    messageId: Int,
    actionId: Int,
    enabled: Boolean = true,
    onAction: () -> Unit = { },
) {
    ArcaduneGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(titleId),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ArcaduneColors.OnSurface,
            )
            Text(
                text = stringResource(messageId),
                style = MaterialTheme.typography.bodyMedium,
                color = ArcaduneColors.OnSurfaceDim,
            )
            ArcadunePillButton(
                modifier = Modifier.align(Alignment.End),
                label = stringResource(id = actionId),
                highlighted = true,
                enabled = enabled,
                onClick = onAction,
            )
        }
    }
}
