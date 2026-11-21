package com.swordfish.lemuroid.app.shared.update

import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneGlassCard
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton
import com.swordfish.lemuroid.app.shared.update.AppUpdater.State
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle

/** The update notice of the library: shown only while there is an update to get. */
@Composable
fun UpdateNotice(modifier: Modifier = Modifier) {
    if (!AppUpdater.isSupported) return
    val state by AppUpdater.state.collectAsState()
    val visible =
        when (val current = state) {
            is State.Available, is State.Downloading, is State.ReadyToInstall, is State.Installing -> true
            is State.Failed -> current.release != null
            else -> false
        }
    if (!visible) return
    ArcaduneGlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
    ) {
        UpdateContent(state = state, showNotes = false, modifier = Modifier.padding(18.dp))
    }
}

/** The Updates part of Settings: the version installed, and checking, downloading, installing. */
@Composable
fun UpdateSettingsContent(modifier: Modifier = Modifier) {
    if (!AppUpdater.isSupported) return
    val state by AppUpdater.state.collectAsState()
    UpdateContent(state = state, showNotes = true, modifier = modifier.padding(horizontal = 18.dp, vertical = 12.dp))
}

@Composable
private fun UpdateContent(
    state: State,
    showNotes: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ComposableLifecycle { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) AppUpdater.onResume(context)
    }
    Column(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val title =
            when (state) {
                is State.Available -> stringResource(R.string.update_available, state.release.version)
                is State.Downloading -> stringResource(R.string.update_available, state.release.version)
                is State.ReadyToInstall -> stringResource(R.string.update_available, state.release.version)
                is State.Installing -> stringResource(R.string.update_available, state.release.version)
                is State.Failed -> state.release?.let { stringResource(R.string.update_available, it.version) }
                else -> null
            } ?: stringResource(R.string.update_current_version, AppUpdater.currentVersion)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ArcaduneColors.OnSurface,
        )

        val message =
            when (state) {
                State.Checking -> stringResource(R.string.update_checking)
                State.UpToDate -> stringResource(R.string.update_up_to_date)
                is State.Downloading -> stringResource(R.string.update_downloading, (state.progress * 100).toInt())
                is State.ReadyToInstall -> stringResource(R.string.update_ready)
                is State.Installing -> stringResource(R.string.update_installing)
                is State.Failed ->
                    when (state.reason) {
                        AppUpdater.Reason.NO_CONNECTION -> stringResource(R.string.update_failed_connection)
                        AppUpdater.Reason.DOWNLOAD_BROKEN -> stringResource(R.string.update_failed_broken)
                        AppUpdater.Reason.INSTALL_FAILED -> stringResource(R.string.update_failed_install)
                    }
                else -> null
            }
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = ArcaduneColors.OnSurfaceDim,
            )
        }

        if (showNotes && state is State.Available) {
            val notes = releaseNotes(state.release.notes)
            if (notes.isNotEmpty()) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = ArcaduneColors.OnSurfaceDim,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (state is State.Downloading) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
                color = ArcaduneColors.Purple,
                trackColor = ArcaduneColors.SurfaceHigh,
            )
        }
        if (state is State.ReadyToInstall) {
            Text(
                text = stringResource(R.string.update_install_hint),
                style = MaterialTheme.typography.bodySmall,
                color = ArcaduneColors.OnSurfaceDim,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                State.Idle, State.UpToDate ->
                    ArcadunePillButton(
                        label = stringResource(R.string.update_check),
                        onClick = { AppUpdater.check(context) },
                    )
                State.Checking, is State.Installing -> Unit
                is State.Available -> {
                    val size = Formatter.formatShortFileSize(context, state.release.apkSize)
                    ArcadunePillButton(
                        label = stringResource(R.string.update_download, size),
                        highlighted = true,
                        onClick = { AppUpdater.download(context, state.release) },
                    )
                }
                is State.Downloading ->
                    ArcadunePillButton(
                        label = stringResource(R.string.update_cancel),
                        onClick = { AppUpdater.cancelDownload() },
                    )
                is State.ReadyToInstall ->
                    ArcadunePillButton(
                        label = stringResource(R.string.update_install),
                        highlighted = true,
                        onClick = { AppUpdater.install(context, state) },
                    )
                is State.Failed ->
                    ArcadunePillButton(
                        label = stringResource(R.string.update_try_again),
                        onClick = {
                            val release = state.release
                            if (release != null) AppUpdater.download(context, release) else AppUpdater.check(context)
                        },
                    )
            }
        }
    }
}

/** GitHub release notes are Markdown: keeps the text, without headings marks and links. */
private fun releaseNotes(markdown: String): String =
    markdown
        .lines()
        .map { it.trim().removePrefix("#").removePrefix("#").removePrefix("#").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("[") && !it.startsWith("!") }
        .joinToString("\n") { it.replace("**", "").replace(Regex("^[-*] "), "• ") }
