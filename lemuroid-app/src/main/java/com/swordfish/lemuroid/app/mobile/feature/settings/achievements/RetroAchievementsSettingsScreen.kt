package com.swordfish.lemuroid.app.mobile.feature.settings.achievements

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneDialog
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneGlassCard
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconBadge
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconPill
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneRowDivider
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidLazySettingsPage
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsAccount
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsPreferences

internal val RaGoldColor = Color(0xFFF6C453)
private val RaErrorColor = Color(0xFFFF6B6B)
private const val PRIVACY_POLICY_URL = "https://github.com/shahrukhmaniyar/Arcadune/blob/master/PRIVACY.md"

@Composable
fun RetroAchievementsSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: RetroAchievementsSettingsViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshAccountIfNeeded()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LemuroidLazySettingsPage(modifier = modifier.fillMaxSize()) {
        if (!uiState.settings.isSignedIn) {
            item(key = "signed_out") {
                SignedOutContent(
                    uiState = uiState,
                    onUsernameChanged = viewModel::onUsernameChanged,
                    onPasswordChanged = viewModel::onPasswordChanged,
                    onSignIn = viewModel::signIn,
                )
            }
        } else {
            val account = uiState.settings.account
            if (account != null) {
                signedInItems(
                    account = account,
                    uiState = uiState,
                    onRefresh = { viewModel.refreshAccount(silent = false) },
                    onSyncOfflineQueue = viewModel::syncPendingOfflineQueue,
                    onRequestSignOut = { confirmSignOut = true },
                    onSetEnabled = viewModel::setEnabled,
                    onSetHardcore = viewModel::setHardcoreEnabled,
                    onSetEncore = viewModel::setEncoreEnabled,
                    onSetUnofficial = viewModel::setUnofficialEnabled,
                    onSetNotifications = viewModel::setNotificationsEnabled,
                    onSetSoundEffects = viewModel::setSoundEffectsEnabled,
                    onSetProgress = viewModel::setProgressIndicatorsEnabled,
                    onSetChallenges = viewModel::setChallengeIndicatorsEnabled,
                    onSetLeaderboardPopups = viewModel::setLeaderboardPopupsEnabled,
                )
            }
        }
    }

    if (confirmSignOut) {
        ArcaduneDialog(
            onDismissRequest = { confirmSignOut = false },
            icon = Icons.AutoMirrored.Filled.Logout,
            title = { Text(text = stringResource(R.string.ra_sign_out_confirm_title)) },
            dismissButton = {
                ArcadunePillButton(
                    label = stringResource(R.string.cancel),
                    onClick = { confirmSignOut = false },
                )
            },
            confirmButton = {
                ArcadunePillButton(
                    label = stringResource(R.string.ra_sign_out_button),
                    highlighted = true,
                    onClick = {
                        confirmSignOut = false
                        viewModel.signOut()
                    },
                )
            },
        ) {
            Text(
                text = stringResource(R.string.ra_sign_out_confirm_message),
                style = MaterialTheme.typography.bodyMedium,
                color = ArcaduneColors.OnSurfaceDim,
            )
        }
    }
}

@Composable
private fun SignedOutContent(
    uiState: RetroAchievementsSettingsViewModel.UiState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSignIn: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ArcaduneGlassCard(
            cornerRadius = 24.dp,
            borderColor = RaGoldColor.copy(alpha = 0.35f),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        RaGoldColor.copy(alpha = 0.14f),
                                        ArcaduneColors.Purple.copy(alpha = 0.08f),
                                        Color.Transparent,
                                    ),
                            ),
                        )
                        .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ArcaduneIconBadge(
                        icon = Icons.Filled.EmojiEvents,
                        tint = RaGoldColor,
                        containerColor = RaGoldColor.copy(alpha = 0.18f),
                        borderColor = RaGoldColor.copy(alpha = 0.50f),
                        size = 48.dp,
                        iconSize = 26.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ra_login_hero_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = ArcaduneColors.OnSurface,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.ra_login_hero_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = ArcaduneColors.OnSurfaceDim,
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.usernameInput,
                    onValueChange = onUsernameChanged,
                    enabled = !uiState.isSigningIn,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    label = { Text(stringResource(R.string.ra_username_label)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = ArcaduneColors.OnSurfaceDim,
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .autofillField(AutofillType.Username, onFill = onUsernameChanged),
                )

                OutlinedTextField(
                    value = uiState.passwordInput,
                    onValueChange = onPasswordChanged,
                    enabled = !uiState.isSigningIn,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    label = { Text(stringResource(R.string.ra_password_label)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = ArcaduneColors.OnSurfaceDim,
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector =
                                    if (passwordVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                contentDescription =
                                    stringResource(
                                        if (passwordVisible) {
                                            R.string.ra_hide_password
                                        } else {
                                            R.string.ra_show_password
                                        },
                                    ),
                                tint = ArcaduneColors.OnSurfaceDim,
                            )
                        }
                    },
                    visualTransformation =
                        if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onSignIn()
                            },
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .autofillField(AutofillType.Password, onFill = onPasswordChanged),
                )

                AnimatedVisibility(visible = !uiState.errorMessage.isNullOrBlank()) {
                    ErrorNoticeBox(message = uiState.errorMessage.orEmpty())
                }

                val canSubmit =
                    !uiState.isSigningIn &&
                        uiState.usernameInput.isNotBlank() &&
                        uiState.passwordInput.isNotEmpty()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArcadunePillButton(
                            label =
                                stringResource(
                                    if (uiState.isSigningIn) {
                                        R.string.ra_signing_in_button
                                    } else {
                                        R.string.ra_sign_in_button
                                    },
                                ),
                            icon = if (uiState.isSigningIn) null else Icons.AutoMirrored.Filled.Login,
                            highlighted = true,
                            enabled = canSubmit,
                            height = 48.dp,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onSignIn()
                            },
                        )

                        if (uiState.isSigningIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = ArcaduneColors.Purple,
                            )
                        }
                    }

                    if (!uiState.isSigningIn) {
                        ArcadunePillButton(
                            label = stringResource(R.string.ra_create_account_button),
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            highlighted = false,
                            height = 44.dp,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                openExternalUrl(context, "https://retroachievements.org/createaccount.php")
                            },
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = ArcaduneColors.OnSurfaceDim,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.ra_security_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = ArcaduneColors.OnSurfaceDim,
                        modifier = Modifier.weight(1f),
                    )
                    ArcadunePillButton(
                        label = stringResource(R.string.ra_privacy_policy_button),
                        height = 32.dp,
                        onClick = { openExternalUrl(context, PRIVACY_POLICY_URL) },
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.signedInItems(
    account: RetroAchievementsAccount,
    uiState: RetroAchievementsSettingsViewModel.UiState,
    onRefresh: () -> Unit,
    onSyncOfflineQueue: () -> Unit,
    onRequestSignOut: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetHardcore: (Boolean) -> Unit,
    onSetEncore: (Boolean) -> Unit,
    onSetUnofficial: (Boolean) -> Unit,
    onSetNotifications: (Boolean) -> Unit,
    onSetSoundEffects: (Boolean) -> Unit,
    onSetProgress: (Boolean) -> Unit,
    onSetChallenges: (Boolean) -> Unit,
    onSetLeaderboardPopups: (Boolean) -> Unit,
) {
    val settings = uiState.settings

    item(key = "ra_account_card") {
        val context = LocalContext.current
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            ArcaduneGlassCard(
                cornerRadius = 24.dp,
                borderColor =
                    if (settings.isHardcoreActive) {
                        RaGoldColor.copy(alpha = 0.45f)
                    } else {
                        ArcaduneColors.GlassEdge
                    },
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            if (settings.isHardcoreActive) {
                                                RaGoldColor.copy(alpha = 0.14f)
                                            } else {
                                                ArcaduneColors.Purple.copy(alpha = 0.12f)
                                            },
                                            Color.Transparent,
                                        ),
                                ),
                            )
                            .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        val avatarShape = RoundedCornerShape(16.dp)
                        val avatarRequest =
                            remember(context, account.avatarUrl) {
                                ImageRequest.Builder(context)
                                    .data(account.avatarUrl)
                                    .crossfade(true)
                                    .build()
                            }
                        Box(
                            modifier =
                                Modifier
                                    .size(58.dp)
                                    .clip(avatarShape)
                                    .background(ArcaduneColors.SurfaceHigh)
                                    .border(
                                        1.dp,
                                        if (settings.isHardcoreActive) {
                                            RaGoldColor.copy(alpha = 0.55f)
                                        } else {
                                            ArcaduneColors.GlassEdge
                                        },
                                        avatarShape,
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EmojiEvents,
                                contentDescription = null,
                                tint = RaGoldColor.copy(alpha = 0.65f),
                                modifier = Modifier.size(26.dp),
                            )
                            AsyncImage(
                                model = avatarRequest,
                                contentDescription = account.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = account.displayName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ArcaduneColors.OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                ModeStatusPill(
                                    enabled = settings.enabled,
                                    hardcore = settings.isHardcoreActive,
                                )
                            }
                            if (!account.displayName.equals(account.username, ignoreCase = true)) {
                                Text(
                                    text = "@${account.username}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ArcaduneColors.OnSurfaceDim,
                                )
                            }
                        }

                        val refreshModifier =
                            if (uiState.isRefreshing) {
                                val infiniteTransition = rememberInfiniteTransition(label = "raRefreshSpin")
                                val refreshRotation by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec =
                                        infiniteRepeatable(
                                            animation = tween(900, easing = LinearEasing),
                                            repeatMode = RepeatMode.Restart,
                                        ),
                                    label = "raRefreshDeg",
                                )
                                Modifier.rotate(refreshRotation)
                            } else {
                                Modifier
                            }

                        ArcaduneIconPill(
                            icon = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.ra_profile_refresh),
                            enabled = !uiState.isRefreshing,
                            modifier = refreshModifier,
                            onClick = onRefresh,
                        )
                        ArcaduneIconPill(
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.ra_profile_open_web),
                            onClick = {
                                openExternalUrl(
                                    context,
                                    "https://retroachievements.org/user/${Uri.encode(account.username)}",
                                )
                            },
                        )
                    }

                    if (RetroAchievementsPreferences.isHardcoreApproved()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ScoreStatChip(
                                icon = Icons.Filled.EmojiEvents,
                                label = stringResource(R.string.ra_profile_hardcore_points, account.score),
                                accentColor = RaGoldColor,
                                modifier = Modifier.weight(1f),
                            )
                            ScoreStatChip(
                                icon = Icons.Filled.MilitaryTech,
                                label = stringResource(R.string.ra_profile_softcore_points, account.softcoreScore),
                                accentColor = ArcaduneColors.Purple,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        ScoreStatChip(
                            icon = Icons.Filled.EmojiEvents,
                            label = stringResource(R.string.ra_profile_total_points, account.totalPoints),
                            accentColor = RaGoldColor,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    AnimatedVisibility(visible = uiState.pendingOfflineCount > 0) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(RaGoldColor.copy(alpha = 0.12f))
                                    .border(1.dp, RaGoldColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.ra_offline_queue_pending_summary,
                                        uiState.pendingOfflineCount,
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = ArcaduneColors.OnSurface,
                                modifier = Modifier.weight(1f),
                            )
                            ArcadunePillButton(
                                label = stringResource(R.string.ra_offline_queue_sync_now),
                                enabled = !uiState.isSyncingOffline,
                                height = 32.dp,
                                onClick = onSyncOfflineQueue,
                            )
                        }
                    }

                    AnimatedVisibility(visible = !uiState.errorMessage.isNullOrBlank()) {
                        ErrorNoticeBox(message = uiState.errorMessage.orEmpty())
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        ArcadunePillButton(
                            label = stringResource(R.string.ra_sign_out_button),
                            icon = Icons.AutoMirrored.Filled.Logout,
                            height = 38.dp,
                            onClick = onRequestSignOut,
                        )
                    }
                }
            }
        }
    }

    item(key = "ra_gameplay_card") {
        LemuroidCardSettingsGroup(
            title = { Text(text = stringResource(R.string.ra_pref_category_gameplay)) },
        ) {
            RaSwitchRow(
                icon = Icons.Filled.EmojiEvents,
                iconTint = RaGoldColor,
                title = stringResource(R.string.ra_pref_enable_title),
                subtitle = stringResource(R.string.ra_pref_enable_subtitle),
                checked = settings.enabled,
                onCheckedChange = onSetEnabled,
            )
            ArcaduneRowDivider(startIndent = 68.dp)
            val hardcoreApproved = RetroAchievementsPreferences.isHardcoreApproved()
            RaSwitchRow(
                icon = Icons.Filled.Whatshot,
                iconTint =
                    if (hardcoreApproved && settings.hardcoreEnabled && settings.enabled) {
                        RaGoldColor
                    } else {
                        ArcaduneColors.OnSurface
                    },
                title = stringResource(R.string.ra_pref_hardcore_title),
                subtitle = stringResource(R.string.ra_pref_hardcore_subtitle),
                checked = settings.isHardcoreActive,
                enabled = settings.enabled && hardcoreApproved,
                onCheckedChange = onSetHardcore,
            )
            AnimatedVisibility(visible = settings.enabled && !hardcoreApproved) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 68.dp, end = 16.dp, bottom = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RaGoldColor.copy(alpha = 0.10f))
                            .border(1.dp, RaGoldColor.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = RaGoldColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.ra_hardcore_registration_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = ArcaduneColors.OnSurfaceDim,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            ArcaduneRowDivider(startIndent = 68.dp)
            RaSwitchRow(
                icon = Icons.Filled.Replay,
                title = stringResource(R.string.ra_pref_encore_title),
                subtitle = stringResource(R.string.ra_pref_encore_subtitle),
                checked = settings.encoreEnabled,
                enabled = settings.enabled,
                onCheckedChange = onSetEncore,
            )
            ArcaduneRowDivider(startIndent = 68.dp)
            RaSwitchRow(
                icon = Icons.Filled.Science,
                title = stringResource(R.string.ra_pref_unofficial_title),
                subtitle = stringResource(R.string.ra_pref_unofficial_subtitle),
                checked = settings.unofficialEnabled,
                enabled = settings.enabled,
                onCheckedChange = onSetUnofficial,
            )
        }
    }

    item(key = "ra_overlay_card") {
        LemuroidCardSettingsGroup(
            title = { Text(text = stringResource(R.string.ra_pref_category_overlay)) },
        ) {
            RaSwitchRow(
                icon = Icons.Filled.NotificationsActive,
                title = stringResource(R.string.ra_pref_notifications_title),
                subtitle = stringResource(R.string.ra_pref_notifications_subtitle),
                checked = settings.notificationsEnabled,
                enabled = settings.enabled,
                onCheckedChange = onSetNotifications,
            )
            ArcaduneRowDivider(startIndent = 68.dp)
            RaSwitchRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = stringResource(R.string.ra_pref_sound_title),
                subtitle = stringResource(R.string.ra_pref_sound_subtitle),
                checked = settings.soundEffectsEnabled,
                enabled = settings.enabled,
                onCheckedChange = onSetSoundEffects,
            )
            ArcaduneRowDivider(startIndent = 68.dp)
            RaSwitchRow(
                icon = Icons.Filled.TrendingUp,
                title = stringResource(R.string.ra_pref_progress_title),
                subtitle = stringResource(R.string.ra_pref_progress_subtitle),
                checked = settings.progressIndicatorsEnabled,
                enabled = settings.enabled,
                onCheckedChange = onSetProgress,
            )
            ArcaduneRowDivider(startIndent = 68.dp)
            RaSwitchRow(
                icon = Icons.Filled.MilitaryTech,
                title = stringResource(R.string.ra_pref_challenges_title),
                subtitle = stringResource(R.string.ra_pref_challenges_subtitle),
                checked = settings.challengeIndicatorsEnabled,
                enabled = settings.enabled,
                onCheckedChange = onSetChallenges,
            )
            ArcaduneRowDivider(startIndent = 68.dp)
            RaSwitchRow(
                icon = Icons.Filled.Leaderboard,
                title = stringResource(R.string.ra_pref_leaderboard_popups_title),
                subtitle = stringResource(R.string.ra_pref_leaderboard_popups_subtitle),
                checked = settings.leaderboardPopupsEnabled,
                enabled = settings.enabled,
                onCheckedChange = onSetLeaderboardPopups,
            )
        }
    }

    item(key = "ra_hint") {
        val context = LocalContext.current
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.ra_changes_next_launch_hint),
                style = MaterialTheme.typography.bodySmall,
                color = ArcaduneColors.OnSurfaceDim,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ArcadunePillButton(
                    label = stringResource(R.string.ra_privacy_policy_button),
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    height = 34.dp,
                    onClick = { openExternalUrl(context, PRIVACY_POLICY_URL) },
                )
            }
        }
    }
}

@Composable
private fun ModeStatusPill(
    enabled: Boolean,
    hardcore: Boolean,
) {
    val (label, color) =
        when {
            !enabled -> stringResource(R.string.tilt_configuration_disabled) to ArcaduneColors.OnSurfaceDim
            !RetroAchievementsPreferences.isHardcoreApproved() ->
                stringResource(R.string.ra_status_active_badge) to ArcaduneColors.Purple
            hardcore -> stringResource(R.string.ra_hud_hardcore_badge) to RaGoldColor
            else -> stringResource(R.string.ra_hud_casual_badge) to ArcaduneColors.Purple
        }
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier =
            Modifier
                .clip(shape)
                .background(color.copy(alpha = 0.16f))
                .border(1.dp, color.copy(alpha = 0.48f), shape)
                .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun ScoreStatChip(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier =
            modifier
                .clip(shape)
                .background(ArcaduneColors.SurfaceHigh)
                .border(1.dp, accentColor.copy(alpha = 0.32f), shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = ArcaduneColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ErrorNoticeBox(message: String) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(RaErrorColor.copy(alpha = 0.14f))
                .border(1.dp, RaErrorColor.copy(alpha = 0.45f), shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = RaErrorColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = ArcaduneColors.OnSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RaSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    iconTint: Color = ArcaduneColors.OnSurface,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.45f)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ArcaduneIconBadge(
            icon = icon,
            tint = iconTint,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = ArcaduneColors.OnSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = ArcaduneColors.OnSurfaceDim,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.autofillField(
    type: AutofillType,
    onFill: (String) -> Unit,
): Modifier {
    val autofill = LocalAutofill.current
    val autofillNode =
        remember(type) {
            AutofillNode(
                onFill = onFill,
                autofillTypes = listOf(type),
            )
        }
    LocalAutofillTree.current += autofillNode

    return this
        .onGloballyPositioned { coordinates ->
            autofillNode.boundingBox = coordinates.boundsInWindow()
        }
        .onFocusChanged { focusState ->
            runCatching {
                if (focusState.isFocused) {
                    autofill?.requestAutofillForNode(autofillNode)
                } else {
                    autofill?.cancelAutofillForNode(autofillNode)
                }
            }
        }
}

private fun openExternalUrl(
    context: android.content.Context,
    url: String,
) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
