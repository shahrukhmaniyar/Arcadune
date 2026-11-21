package com.swordfish.lemuroid.app.utils.android.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.storage.base.SettingValueState
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.SettingsSlider
import com.alorma.compose.settings.ui.SettingsSwitch
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneGlassCard
import kotlin.math.roundToInt

@Composable
fun LemuroidSettingsPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val horizontalSafe =
        WindowInsets.displayCutout
            .union(WindowInsets.navigationBars)
            .only(WindowInsetsSides.Horizontal)
    // On a wide screen (landscape, tablets) the cards keep a readable width, centred.
    Box(
        modifier = modifier.fillMaxWidth().windowInsetsPadding(horizontalSafe).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
fun LemuroidLazySettingsPage(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val horizontalSafe =
        WindowInsets.displayCutout
            .union(WindowInsets.navigationBars)
            .only(WindowInsetsSides.Horizontal)
    Box(
        modifier = modifier.fillMaxSize().windowInsetsPadding(horizontalSafe),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
fun LemuroidSettingsSwitch(
    enabled: Boolean = true,
    state: SettingValueState<Boolean>,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable () -> Unit,
    subtitle: @Composable (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    SettingsSwitch(
        enabled = enabled,
        state = state.value,
        icon = icon,
        title = title,
        subtitle = subtitle,
        onCheckedChange = {
            state.value = it
            onCheckedChange(it)
        },
        colors = lemuroidSettingsColor(enabled),
    )
}

@Composable
fun LemuroidSettingsMenuLink(
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    SettingsMenuLink(
        enabled = enabled,
        icon = icon,
        title = title,
        subtitle = subtitle,
        action = action,
        onClick = onClick,
        colors = lemuroidSettingsColor(enabled),
    )
}

@Composable
fun LemuroidSettingsGroup(
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = Color.Transparent) {
        Column(
            modifier = modifier.fillMaxWidth(),
        ) {
            if (title != null) {
                SettingsGroupTitleSmall(title)
            }
            content()
        }
    }
}

@Composable
fun LemuroidCardSettingsGroup(
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    ) {
        ArcaduneGlassCard(
            cornerRadius = 22.dp,
            containerColor = ArcaduneColors.Surface,
            borderColor = ArcaduneColors.GlassEdge,
            clip = false,
        ) {
            if (title != null) {
                SettingsGroupTitleSmall(title)
            }
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
fun LemuroidSettingsSlider(
    modifier: Modifier = Modifier,
    state: SettingValueState<Int>,
    steps: Int,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    icon: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit = { },
) {
    SettingsSlider(
        modifier = modifier,
        steps = steps,
        value = state.value.toFloat(),
        onValueChange = { state.value = it.roundToInt() },
        valueRange = valueRange,
        icon = icon,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        colors = lemuroidSettingsColor(enabled),
    )
}

@Composable
private fun SettingsGroupTitleSmall(title: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val titleStyle =
            MaterialTheme.typography.labelLarge.copy(
                color = ArcaduneColors.Purple,
                fontWeight = FontWeight.SemiBold,
            )
        ProvideTextStyle(value = titleStyle) { title() }
    }
}

@Composable
private fun lemuroidSettingsColor(enabled: Boolean): ListItemColors {
    val defaultColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    if (enabled) {
        return ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = ArcaduneColors.OnSurface,
            supportingColor = ArcaduneColors.OnSurfaceDim,
            leadingIconColor = ArcaduneColors.OnSurface,
            trailingIconColor = ArcaduneColors.OnSurfaceDim,
        )
    }

    return ListItemDefaults.colors(
        containerColor = Color.Transparent,
        headlineColor = defaultColors.disabledHeadlineColor,
        leadingIconColor = defaultColors.disabledLeadingIconColor,
        trailingIconColor = defaultColors.disabledTrailingIconColor,
        supportingColor = defaultColors.supportingTextColor.copy(alpha = 0.35f),
    )
}
