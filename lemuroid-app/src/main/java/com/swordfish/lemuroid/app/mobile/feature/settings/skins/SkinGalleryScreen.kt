package com.swordfish.lemuroid.app.mobile.feature.settings.skins

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.game.SkinCustomizationStore
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneGlassCard
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.arcadunePressable
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.touchinput.radial.settings.TouchControllerID
import com.swordfish.touchinput.skin.ArcaduneShellPreview
import com.swordfish.touchinput.skin.ArcaduneSkins
import com.swordfish.touchinput.skin.SkinArt
import com.swordfish.touchinput.skin.SkinArtLoader
import com.swordfish.touchinput.skin.SkinVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every console's controller, by the name players know it by (console names are not translated). */
internal val Controllers =
    listOf(
        TouchControllerID.NES to "NES",
        TouchControllerID.SNES to "Super Nintendo",
        TouchControllerID.N64 to "Nintendo 64",
        TouchControllerID.GB to "Game Boy and Game Boy Color",
        TouchControllerID.GBA to "Game Boy Advance",
        TouchControllerID.MELONDS to "Nintendo DS",
        TouchControllerID.NINTENDO_3DS to "Nintendo 3DS",
        TouchControllerID.PSX to "PlayStation",
        TouchControllerID.PSX_DUALSHOCK to "PlayStation (DualShock)",
        TouchControllerID.PSP to "PSP",
        TouchControllerID.GENESIS_3 to "Mega Drive / Genesis",
        TouchControllerID.GENESIS_6 to "Mega Drive / Genesis (6 buttons)",
        TouchControllerID.SMS to "Master System",
        TouchControllerID.GG to "Game Gear",
        TouchControllerID.PCE to "PC Engine",
        TouchControllerID.NGP to "Neo Geo Pocket",
        TouchControllerID.WS_LANDSCAPE to "WonderSwan",
        TouchControllerID.WS_PORTRAIT to "WonderSwan (held upright)",
        TouchControllerID.LYNX to "Atari Lynx",
        TouchControllerID.ATARI2600 to "Atari 2600",
        TouchControllerID.ATARI7800 to "Atari 7800",
        TouchControllerID.ARCADE_4 to "Arcade (4 buttons)",
        TouchControllerID.ARCADE_6 to "Arcade (6 buttons)",
    )

private enum class ControllerFamily(val labelRes: Int) {
    ALL(R.string.skin_gallery_filter_all),
    CUSTOMIZED(R.string.skin_gallery_filter_customized),
    NINTENDO(R.string.skin_gallery_filter_nintendo),
    PLAYSTATION(R.string.skin_gallery_filter_playstation),
    SEGA(R.string.skin_gallery_filter_sega),
    MORE(R.string.skin_gallery_filter_more),
}

private fun TouchControllerID.family(): ControllerFamily =
    when (this) {
        TouchControllerID.NES,
        TouchControllerID.SNES,
        TouchControllerID.N64,
        TouchControllerID.GB,
        TouchControllerID.GBA,
        TouchControllerID.MELONDS,
        TouchControllerID.NINTENDO_3DS,
        -> ControllerFamily.NINTENDO
        TouchControllerID.PSX,
        TouchControllerID.PSX_DUALSHOCK,
        TouchControllerID.PSP,
        -> ControllerFamily.PLAYSTATION
        TouchControllerID.GENESIS_3,
        TouchControllerID.GENESIS_6,
        TouchControllerID.SMS,
        TouchControllerID.GG,
        -> ControllerFamily.SEGA
        else -> ControllerFamily.MORE
    }

private fun isControllerCustomized(
    id: TouchControllerID,
    store: SkinCustomizationStore,
): Boolean {
    val variant = store.variant(id.name)
    val skin = ArcaduneSkins.forController(id, variant) ?: return false
    val upright = store.load(SkinCustomizationStore.keyOf(id.name, false, skin.portrait))
    val sideways = store.load(SkinCustomizationStore.keyOf(id.name, true, skin.landscape))
    return upright.isCustomized || sideways.isCustomized
}

/**
 * The controllers of every console, with family filter chips, instant Console / Minimal style
 * switching, and interactive Upright & Sideways previews that launch the 1:1 full-screen studio.
 */
@Composable
fun SkinGalleryScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
) {
    val context = LocalContext.current
    val store = remember { SkinCustomizationStore(context) }
    var refreshEpoch by remember { mutableIntStateOf(0) }
    var selectedFamily by rememberSaveable { mutableStateOf(ControllerFamily.ALL) }

    var seenInitialResume by remember { mutableStateOf(false) }
    ComposableLifecycle { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            if (seenInitialResume) {
                refreshEpoch++
            } else {
                seenInitialResume = true
            }
        }
    }

    val customizedSet =
        remember(refreshEpoch) {
            Controllers.map { it.first }.filter { isControllerCustomized(it, store) }.toSet()
        }
    LaunchedEffect(customizedSet, selectedFamily) {
        if (selectedFamily == ControllerFamily.CUSTOMIZED && customizedSet.isEmpty()) {
            selectedFamily = ControllerFamily.ALL
        }
    }

    val filteredControllers =
        remember(selectedFamily, customizedSet) {
            when (selectedFamily) {
                ControllerFamily.ALL -> Controllers
                ControllerFamily.CUSTOMIZED -> Controllers.filter { it.first in customizedSet }
                else -> Controllers.filter { it.first.family() == selectedFamily }
            }
        }

    val availableFamilies =
        remember(customizedSet) {
            buildList {
                add(ControllerFamily.ALL)
                if (customizedSet.isNotEmpty()) add(ControllerFamily.CUSTOMIZED)
                add(ControllerFamily.NINTENDO)
                add(ControllerFamily.PLAYSTATION)
                add(ControllerFamily.SEGA)
                add(ControllerFamily.MORE)
            }
        }

    Column(modifier = modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(availableFamilies, key = { it.name }) { family ->
                val selected = family == selectedFamily
                val chipShape = RoundedCornerShape(999.dp)
                Box(
                    modifier =
                        Modifier
                            .arcadunePressable(onClick = { selectedFamily = family })
                            .clip(chipShape)
                            .background(
                                if (selected) ArcaduneColors.Purple else ArcaduneColors.Surface,
                            )
                            .then(
                                if (selected) {
                                    Modifier
                                } else {
                                    Modifier.border(1.dp, ArcaduneColors.GlassEdge, chipShape)
                                },
                            )
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = stringResource(family.labelRes),
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                        color = if (selected) ArcaduneColors.OnAccent else ArcaduneColors.OnSurfaceDim,
                        maxLines = 1,
                    )
                }
            }
        }

        LazyVerticalGrid(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            columns = GridCells.Adaptive(minSize = 300.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(filteredControllers, key = { it.first.name }) { (id, name) ->
                SkinCard(
                    id = id,
                    name = name,
                    store = store,
                    refreshEpoch = refreshEpoch,
                    onEditOrientation = { landscape ->
                        navController.navigate(skinLayoutRoute(id, landscape = landscape)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SkinCard(
    id: TouchControllerID,
    name: String,
    store: SkinCustomizationStore,
    refreshEpoch: Int,
    onEditOrientation: (Boolean) -> Unit,
) {
    var variant by remember(id, refreshEpoch) { mutableStateOf(store.variant(id.name)) }
    val skin = remember(id, variant) { ArcaduneSkins.forController(id, variant) } ?: return
    val upright =
        remember(id, skin, refreshEpoch) {
            store.load(SkinCustomizationStore.keyOf(id.name, false, skin.portrait))
        }
    val sideways =
        remember(id, skin, refreshEpoch) {
            store.load(SkinCustomizationStore.keyOf(id.name, true, skin.landscape))
        }
    val uprightArt = rememberSkinArt(id, variant, landscape = false)
    val sidewaysArt = rememberSkinArt(id, variant, landscape = true)
    val anyCustomized = upright.isCustomized || sideways.isCustomized

    ArcaduneGlassCard(cornerRadius = 22.dp) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ArcaduneColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (anyCustomized) {
                        val badgeShape = RoundedCornerShape(999.dp)
                        Text(
                            text = stringResource(R.string.skin_gallery_customized_badge),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ArcaduneColors.Purple,
                            maxLines = 1,
                            modifier =
                                Modifier
                                    .clip(badgeShape)
                                    .background(ArcaduneColors.Purple.copy(alpha = 0.15f))
                                    .border(1.dp, ArcaduneColors.Purple.copy(alpha = 0.45f), badgeShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                VariantSwitch(
                    variant = variant,
                    onChange = {
                        variant = it
                        store.setVariant(id.name, it)
                    },
                )
            }

            // Tappable Upright & Sideways previews: tap either phone directly to edit that orientation 1:1.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 12.dp
                val height = (maxWidth - gap) / (360f / 780f + 780f / 360f)
                val previewCorner = RoundedCornerShape(13.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        val uprightBorder =
                            if (upright.isCustomized) {
                                ArcaduneColors.Purple.copy(alpha = 0.60f)
                            } else {
                                ArcaduneColors.GlassEdge
                            }
                        Box(
                            modifier =
                                Modifier
                                    .size(height * 360f / 780f, height)
                                    .arcadunePressable(
                                        onClick = { onEditOrientation(false) },
                                        scaleDown = 0.97f,
                                    )
                                    .clip(previewCorner)
                                    .border(1.dp, uprightBorder, previewCorner),
                        ) {
                            ArcaduneShellPreview(
                                modifier = Modifier.fillMaxSize(),
                                id = id,
                                skin = skin,
                                isLandscape = false,
                                customization = upright,
                                art = uprightArt,
                            )
                        }
                        OrientationPreviewCaption(
                            label = stringResource(R.string.skin_layout_upright),
                            customized = upright.isCustomized,
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        val sidewaysBorder =
                            if (sideways.isCustomized) {
                                ArcaduneColors.Purple.copy(alpha = 0.60f)
                            } else {
                                ArcaduneColors.GlassEdge
                            }
                        Box(
                            modifier =
                                Modifier
                                    .size(height * 780f / 360f, height)
                                    .arcadunePressable(
                                        onClick = { onEditOrientation(true) },
                                        scaleDown = 0.97f,
                                    )
                                    .clip(previewCorner)
                                    .border(1.dp, sidewaysBorder, previewCorner),
                        ) {
                            ArcaduneShellPreview(
                                modifier = Modifier.fillMaxSize(),
                                id = id,
                                skin = skin,
                                isLandscape = true,
                                customization = sideways,
                                art = sidewaysArt,
                            )
                        }
                        OrientationPreviewCaption(
                            label = stringResource(R.string.skin_layout_sideways),
                            customized = sideways.isCustomized,
                        )
                    }
                }
            }

            ArcadunePillButton(
                label = stringResource(R.string.skin_layout_edit),
                icon = Icons.Filled.Tune,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onEditOrientation(false) },
            )
        }
    }
}

@Composable
private fun OrientationPreviewCaption(
    label: String,
    customized: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (customized) {
            Box(
                modifier =
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(ArcaduneColors.Purple),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (customized) ArcaduneColors.OnSurface else ArcaduneColors.OnSurfaceDim,
        )
    }
}

/** The Console skin's designed pictures of [id] for one orientation (none for Minimal), read off the main thread. */
@Composable
internal fun rememberSkinArt(
    id: TouchControllerID,
    variant: SkinVariant,
    landscape: Boolean,
): SkinArt? {
    val context = LocalContext.current
    var art by
        remember(id, variant, landscape) {
            mutableStateOf(SkinArtLoader.cached(id.name, landscape).takeIf { variant == SkinVariant.CONSOLE })
        }
    LaunchedEffect(id, variant, landscape) {
        art =
            if (variant == SkinVariant.CONSOLE) {
                withContext(Dispatchers.IO) { SkinArtLoader.load(context, id.name, landscape) }
            } else {
                null
            }
    }
    return art
}

/** Two halves of one small pill: the Console skin, or the Minimal one. */
@Composable
internal fun VariantSwitch(
    variant: SkinVariant,
    onChange: (SkinVariant) -> Unit,
) = PillSwitch(
    options =
        listOf(
            SkinVariant.CONSOLE to R.string.skin_variant_console,
            SkinVariant.MINIMAL to R.string.skin_variant_minimal,
        ),
    selected = variant,
    onChange = onChange,
)

/** A small pill split into [options] (each with its label), [selected] highlighted. */
@Composable
internal fun <T> PillSwitch(
    options: List<Pair<T, Int>>,
    selected: T,
    onChange: (T) -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier =
            Modifier
                .clip(shape)
                .background(ArcaduneColors.SurfaceHigh)
                .border(1.dp, ArcaduneColors.GlassEdge, shape)
                .padding(3.dp),
    ) {
        options.forEach { (option, label) ->
            val isSelected = option == selected
            Box(
                modifier =
                    Modifier
                        .clip(shape)
                        .background(if (isSelected) ArcaduneColors.Purple else Color.Transparent)
                        .clickable { onChange(option) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(label),
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    color = if (isSelected) ArcaduneColors.OnAccent else ArcaduneColors.OnSurfaceDim,
                )
            }
        }
    }
}
