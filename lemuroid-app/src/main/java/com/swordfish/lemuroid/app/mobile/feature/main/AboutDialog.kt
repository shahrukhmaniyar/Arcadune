package com.swordfish.lemuroid.app.mobile.feature.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.lib.library.GameSystem

private const val REPOSITORY_URL = "https://github.com/shahrukhmaniyar/Arcadune"

/** About Arcadune (the info button of Settings): the consoles it plays, how to add games, credits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.82f

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().heightIn(max = maxHeight),
            shape = RoundedCornerShape(28.dp),
            color = ArcaduneColors.Surface,
        ) {
            Column {
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Header()

                    Section(title = stringResource(R.string.about_consoles_title)) {
                        Text(
                            text = stringResource(R.string.about_consoles_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ArcaduneColors.OnSurfaceDim,
                        )
                        Spacer(Modifier.size(4.dp))
                        ConsoleList()
                    }

                    Section(title = stringResource(R.string.about_start_title)) {
                        Step(number = 1, text = stringResource(R.string.about_start_step_folder))
                        Step(number = 2, text = stringResource(R.string.about_start_step_scan))
                        Step(number = 3, text = stringResource(R.string.about_start_step_missing))
                        FlowRow(
                            modifier = Modifier.padding(start = 34.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GameSystem.all().forEach { FolderChip(it.id.dbname) }
                        }
                    }

                    Section(title = stringResource(R.string.about_credits_title)) {
                        Text(
                            text = stringResource(R.string.about_credits_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ArcaduneColors.OnSurfaceDim,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL)))
                        },
                    ) {
                        Text(stringResource(R.string.about_github))
                    }
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.about_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF0C0524)),
            contentAlignment = Alignment.Center,
        ) {
            // The launcher icon's own artwork, cropped like the round icon.
            Image(
                painter = painterResource(R.drawable.lemuroid_launcher_foreground),
                contentDescription = null,
                modifier =
                    Modifier.size(56.dp).graphicsLayer {
                        scaleX = 1.5f
                        scaleY = 1.5f
                    },
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(R.string.lemuroid_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = ArcaduneColors.OnSurfaceDim,
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = ArcaduneColors.Purple,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

/** Every console, with the emulator that plays it, on rounded rows like a settings card. */
@Composable
private fun ConsoleList() {
    val context = LocalContext.current
    val consoles =
        remember {
            GameSystem.all()
                .map { context.getString(it.titleResId) to it.systemCoreConfigs.first().coreID.coreDisplayName }
                .sortedBy { it.first.lowercase() }
        }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ArcaduneColors.SurfaceHigh),
    ) {
        consoles.forEachIndexed { index, (console, emulator) ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(ArcaduneColors.Surface))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = console,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = emulator,
                    style = MaterialTheme.typography.bodySmall,
                    color = ArcaduneColors.OnSurfaceDim,
                )
            }
        }
    }
}

@Composable
private fun Step(
    number: Int,
    text: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(ArcaduneColors.Purple),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = ArcaduneColors.OnAccent,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = ArcaduneColors.OnSurfaceDim,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun FolderChip(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = ArcaduneColors.OnSurface,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ArcaduneColors.SurfaceHigh)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
