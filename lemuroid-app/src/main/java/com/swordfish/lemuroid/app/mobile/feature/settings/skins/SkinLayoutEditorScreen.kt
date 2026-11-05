package com.swordfish.lemuroid.app.mobile.feature.settings.skins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.game.SkinCustomizationStore
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.touchinput.radial.settings.TouchControllerID
import com.swordfish.touchinput.skin.ArcaduneShellPreview
import com.swordfish.touchinput.skin.ArcaduneSkins
import com.swordfish.touchinput.skin.SkinCustomization

/** The route of [id]'s layout editor (see [SkinLayoutEditorScreen]). */
fun skinLayoutRoute(id: TouchControllerID) = "settings/skins/layout/${id.name}"

/**
 * Arranges a console's controller outside a game, like the in-game Edit controls: its skin
 * (Console or Minimal), and for the upright and the sideways layout each, every button dragged
 * where the player wants it, one size for all of them and their opacity. Laid out on this phone's
 * own screen shape, so it looks exactly as in a game, and saved under the same key the game reads.
 */
@Composable
fun SkinLayoutEditorScreen(
    modifier: Modifier = Modifier,
    controller: String,
    onDone: () -> Unit,
) {
    val id = TouchControllerID.values().firstOrNull { it.name == controller } ?: return
    val context = LocalContext.current
    val store = remember { SkinCustomizationStore(context) }
    var variant by remember { mutableStateOf(store.variant(id.name)) }
    var landscape by rememberSaveable { mutableStateOf(false) }
    val skin = remember(variant) { ArcaduneSkins.forController(id, variant) } ?: return
    val layout = if (landscape) skin.landscape else skin.portrait
    val key = SkinCustomizationStore.keyOf(id.name, landscape, layout)
    val customization = remember(key) { mutableStateOf(store.load(key)) }
    val art = rememberSkinArt(id, variant, landscape)
    // Kept in memory while dragging (saving every move makes dragging stutter), saved when the
    // other layout is picked or the screen is left.
    DisposableEffect(key) { onDispose { store.save(key, customization.value) } }

    // Several moves can arrive between two frames: each builds on the newest value.
    fun move(
        index: Int,
        delta: Offset,
    ) {
        val latest = customization.value
        val current = latest.offsets[index] ?: Offset.Zero
        customization.value = latest.copy(offsets = latest.offsets + (index to current + delta))
    }

    // This phone, held upright or sideways.
    val configuration = LocalConfiguration.current
    val short = minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp
    val long = maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp
    val name = Controllers.firstOrNull { it.first == id }?.second ?: id.name

    // A wide, short screen (a phone held sideways, a tablet) has no height to spare: there the
    // preview takes all the room, with its buttons all in view to drag, and the settings and the
    // Reset and Done buttons form one compact row under it. A tall screen keeps them stacked.
    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val compact = maxWidth > maxHeight
        val sizeSlider: @Composable (Modifier) -> Unit = { slider ->
            Column(slider) {
                Text(
                    text = stringResource(R.string.skin_editor_size, (customization.value.size * 100).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = customization.value.size,
                    valueRange = SkinCustomization.MIN_SIZE..SkinCustomization.MAX_SIZE,
                    onValueChange = { customization.value = customization.value.copy(size = it) },
                )
            }
        }
        val opacitySlider: @Composable (Modifier) -> Unit = { slider ->
            Column(slider) {
                Text(
                    text = stringResource(R.string.skin_editor_opacity, (customization.value.opacity * 100).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = customization.value.opacity,
                    valueRange = SkinCustomization.MIN_OPACITY..1f,
                    onValueChange = { customization.value = customization.value.copy(opacity = it) },
                )
            }
        }
        val buttons: @Composable () -> Unit = {
            TextButton(onClick = { customization.value = SkinCustomization() }) {
                Text(stringResource(R.string.skin_editor_reset))
            }
            Button(onClick = onDone) { Text(stringResource(R.string.skin_editor_done)) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
            if (compact) {
                // The name, the layout and the skin on one line.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    PillSwitch(
                        options = listOf(false to R.string.skin_layout_upright, true to R.string.skin_layout_sideways),
                        selected = landscape,
                        onChange = { landscape = it },
                    )
                    VariantSwitch(
                        variant = variant,
                        onChange = {
                            variant = it
                            store.setVariant(id.name, it)
                        },
                    )
                }
            } else {
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
                PillSwitch(
                    options = listOf(false to R.string.skin_layout_upright, true to R.string.skin_layout_sideways),
                    selected = landscape,
                    onChange = { landscape = it },
                )
            }

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                ArcaduneShellPreview(
                    modifier = Modifier.fillMaxSize(),
                    id = id,
                    skin = skin,
                    isLandscape = landscape,
                    phoneWidth = if (landscape) long else short,
                    phoneHeight = if (landscape) short else long,
                    customization = customization.value,
                    onMove = ::move,
                    art = art,
                )
            }

            if (compact) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    sizeSlider(Modifier.weight(1f))
                    opacitySlider(Modifier.weight(1f))
                    buttons()
                }
            } else {
                Text(
                    text = stringResource(R.string.skin_editor_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = ArcaduneColors.OnSurfaceDim,
                )
                sizeSlider(Modifier)
                opacitySlider(Modifier)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { buttons() }
            }
        }
    }
}
