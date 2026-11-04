package com.swordfish.lemuroid.app.mobile.feature.settings.skins

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.game.SkinCustomizationStore
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.touchinput.radial.settings.TouchControllerID
import com.swordfish.touchinput.skin.ArcaduneShellPreview
import androidx.compose.foundation.layout.size
import com.swordfish.touchinput.skin.ArcaduneSkins

/** Every console's controller, by the name players know it by (console names are not translated). */
private val Controllers =
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

/**
 * The controllers of every console, each with its colours / black and white switch, the same one
 * as in a game's Edit controls. As many columns as fit: one on a phone, more in landscape and on tablets.
 */
@Composable
fun SkinGalleryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { SkinCustomizationStore(context) }
    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(minSize = 300.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(Controllers, key = { it.first.name }) { (id, name) ->
            SkinCard(id, name, store)
        }
    }
}

@Composable
private fun SkinCard(
    id: TouchControllerID,
    name: String,
    store: SkinCustomizationStore,
) {
    var monochrome by remember { mutableStateOf(store.isMonochrome(id.name)) }
    val skin = remember(monochrome) { ArcaduneSkins.forController(id, monochrome) } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            ColourSwitch(
                monochrome = monochrome,
                onChange = {
                    monochrome = it
                    store.setMonochrome(id.name, it)
                },
            )
        }
        // Held upright and sideways, as in a game: the phone shapes side by side, the same height.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 12.dp
            val height = (maxWidth - gap) / (360f / 780f + 780f / 360f)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                ArcaduneShellPreview(Modifier.size(height * 360f / 780f, height), id, skin, isLandscape = false)
                ArcaduneShellPreview(Modifier.size(height * 780f / 360f, height), id, skin, isLandscape = true)
            }
        }
    }
}

/** Two halves of one small pill: the console's colours, or black and white. */
@Composable
private fun ColourSwitch(
    monochrome: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .background(ArcaduneColors.SurfaceHigh, RoundedCornerShape(50))
                .padding(3.dp),
    ) {
        listOf(false to R.string.skin_gallery_colours, true to R.string.skin_gallery_mono).forEach { (mono, label) ->
            val selected = mono == monochrome
            Box(
                modifier =
                    Modifier
                        .background(if (selected) ArcaduneColors.Purple else ArcaduneColors.SurfaceHigh, RoundedCornerShape(50))
                        .clickable { onChange(mono) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) ArcaduneColors.OnAccent else ArcaduneColors.OnSurfaceDim,
                )
            }
        }
    }
}
