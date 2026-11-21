package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconPill
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork

/** The top bar rendered inside each Settings / utility screen so it slides naturally with its page. */
@Composable
fun MainTopBar(
    route: MainRoute,
    navController: NavHostController,
    onHelpPressed: () -> Unit,
    mainUIState: MainViewModel.UiState,
) {
    val context = LocalContext.current

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            ArcaduneColors.Purple.copy(alpha = 0.08f),
                            ArcaduneColors.Background,
                        ),
                    ),
                ),
    ) {
        val horizontalSafe =
            WindowInsets.displayCutout
                .union(WindowInsets.navigationBars)
                .only(WindowInsetsSides.Horizontal)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .windowInsetsPadding(horizontalSafe)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (route.parent != null) {
                ArcaduneIconPill(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(id = R.string.back),
                    onClick = { navController.popBackStackIfResumed() },
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Text(
                text = stringResource(route.titleId),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = ArcaduneColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mainUIState.saveSyncEnabled) {
                    ArcaduneIconPill(
                        icon = Icons.Outlined.CloudSync,
                        contentDescription = stringResource(R.string.save_sync),
                        enabled = !mainUIState.operationInProgress,
                        onClick = { SaveSyncWork.enqueueManualWork(context.applicationContext) },
                    )
                }
                ArcaduneIconPill(
                    icon = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.mobile_settings_help),
                    onClick = onHelpPressed,
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.06f)),
        )

        AnimatedVisibility(mainUIState.operationInProgress) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = ArcaduneColors.Purple,
                trackColor = ArcaduneColors.SurfaceHigh,
            )
        }
    }
}
