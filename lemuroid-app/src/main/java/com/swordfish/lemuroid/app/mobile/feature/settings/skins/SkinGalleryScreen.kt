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
import com.swordfish.touchinput.skin.SkinVariant
import androidx.navigation.NavController
import androidx.compose.material3.OutlinedButton

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

/**
 * The controllers of every console, like Delta's Controller Skins: each with its two skins (the
 * Console one or Minimal, the same switch as in a game's Edit controls) and a way into its layout
 * editor. As many columns as fit: one on a phone, more in landscape and on tablets.
 */
@Composable
fun SkinGalleryScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
) {
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
            SkinCard(id, name, store, onEdit = { navController.navigate(skinLayoutRoute(id)) })
        }
    }
}

@Composable
private fun SkinCard(
    id: TouchControllerID,
    name: String,
    store: SkinCustomizationStore,
    onEdit: () -> Unit,
) {
    var variant by remember { mutableStateOf(store.variant(id.name)) }
    val skin = remember(variant) { ArcaduneSkins.forController(id, variant) } ?: return
    // The player's own layout (moved buttons, size, opacity), as the game shows it.
    val upright = store.load(SkinCustomizationStore.keyOf(id.name, false, skin.portrait))
    val sideways = store.load(SkinCustomizationStore.keyOf(id.name, true, skin.landscape))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            VariantSwitch(
                variant = variant,
                onChange = {
                    variant = it
                    store.setVariant(id.name, it)
                },
            )
        }
        // Held upright and sideways, as in a game: the phone shapes side by side, the same height.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 12.dp
            val height = (maxWidth - gap) / (360f / 780f + 780f / 360f)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                val uprightSize = Modifier.size(height * 360f / 780f, height)
                val sidewaysSize = Modifier.size(height * 780f / 360f, height)
                ArcaduneShellPreview(uprightSize, id, skin, isLandscape = false, customization = upright)
                ArcaduneShellPreview(sidewaysSize, id, skin, isLandscape = true, customization = sideways)
            }
        }
        OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.skin_layout_edit)) }
    }
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
    Row(
        modifier =
            Modifier
                .background(ArcaduneColors.SurfaceHigh, RoundedCornerShape(50))
                .padding(3.dp),
    ) {
        options.forEach { (option, label) ->
            val isSelected = option == selected
            Box(
                modifier =
                    Modifier
                        .background(
                            if (isSelected) ArcaduneColors.Purple else ArcaduneColors.SurfaceHigh,
                            RoundedCornerShape(50),
                        )
                        .clickable { onChange(option) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) ArcaduneColors.OnAccent else ArcaduneColors.OnSurfaceDim,
                )
            }
        }
    }
}
