package com.swordfish.lemuroid.app.mobile.feature.game

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.touchinput.skin.ArcaduneSkin
import com.swordfish.touchinput.skin.ArcaduneSkinEditor
import com.swordfish.touchinput.skin.SkinCustomization
import com.swordfish.touchinput.skin.SkinVariant
import com.swordfish.touchinput.skin.signature

/** Saves the player's skin changes, one set per controller and orientation. */
class SkinCustomizationStore(context: Context) {
    private val preferences = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)

    fun load(key: String): SkinCustomization = SkinCustomization.decode(preferences.getString(prefKey(key), null))

    fun save(
        key: String,
        customization: SkinCustomization,
    ) = preferences.edit { putString(prefKey(key), customization.encode()) }

    /**
     * Which of its two skins a controller uses ([controller] is its name). Players who had chosen
     * black and white before there were two skins get the Minimal one.
     */
    fun variant(controller: String): SkinVariant =
        preferences.getString(variantKey(controller), null)
            ?.let { name -> SkinVariant.values().firstOrNull { it.name == name } }
            ?: if (preferences.getBoolean(monoKey(controller), false)) SkinVariant.MINIMAL else SkinVariant.CONSOLE

    fun setVariant(
        controller: String,
        variant: SkinVariant,
    ) = preferences.edit {
        putString(variantKey(controller), variant.name)
        remove(monoKey(controller))
    }

    private fun prefKey(key: String) = "arcadune_skin_$key"

    private fun variantKey(controller: String) = "arcadune_skin_variant_$controller"

    private fun monoKey(controller: String) = "arcadune_skin_mono_$controller"

    companion object {
        /**
         * Where the changes to [layout] (the [landscape] or upright layout of [controller]) are
         * kept: one set per controller, orientation and version of the layout, so a redesigned
         * layout never gets moves made for the old one. The game and Settings share it.
         */
        fun keyOf(
            controller: String,
            landscape: Boolean,
            layout: com.swordfish.touchinput.skin.SkinLayout,
        ) = "${controller}_${if (landscape) "landscape" else "portrait"}_${layout.signature()}"
    }
}

/**
 * Edits the skin on top of the paused game: drag any button to move it, one slider for the size
 * of every button, Reset and Done.
 */
@Composable
fun SkinEditorOverlay(
    isLandscape: Boolean,
    skin: ArcaduneSkin,
    rewindAvailable: Boolean,
    customizationState: MutableState<SkinCustomization>,
    /** Where the game shows the upright controller panel (in pixels); the editor shows it there too. */
    panel: androidx.compose.ui.geometry.Rect?,
    /** Which of its two skins the controller uses (the Console one, or Minimal). */
    variant: SkinVariant,
    onVariantChange: (SkinVariant) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    // Back keeps the changes, like Done (it used to leave the editor and lose them).
    androidx.activity.compose.BackHandler(onBack = onDone)
    val layout = if (isLandscape) skin.landscape else skin.portrait
    val customization = customizationState.value

    // Several moves can arrive between two frames, so each one builds on the newest value, not on
    // the one this screen was drawn with (that lost all but the last move and the button barely moved).
    fun move(
        index: Int,
        delta: Offset,
    ) {
        val latest = customizationState.value
        val current = latest.offsets[index] ?: Offset.Zero
        customizationState.value = latest.copy(offsets = latest.offsets + (index to current + delta))
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                // Takes every touch, so nothing reaches the game while editing.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        if (isLandscape) {
            ArcaduneSkinEditor(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(4.dp),
                layout = layout,
                pinToEdges = true,
                rewindAvailable = rewindAvailable,
                customization = customization,
                onMove = ::move,
            )
        } else {
            // Exactly over the controller of the game (the editor is drawn on top of it).
            val editorModifier =
                if (panel != null) {
                    with(androidx.compose.ui.platform.LocalDensity.current) {
                        Modifier
                            .absoluteOffset { androidx.compose.ui.unit.IntOffset(panel.left.toInt(), panel.top.toInt()) }
                            .size(panel.width.toDp(), panel.height.toDp())
                    }
                } else {
                    Modifier.align(Alignment.BottomCenter).widthIn(max = 520.dp).fillMaxWidth().aspectRatio(layout.width / layout.height)
                }
            ArcaduneSkinEditor(
                modifier = editorModifier,
                layout = layout,
                pinToEdges = false,
                rewindAvailable = rewindAvailable,
                customization = customization,
                onMove = ::move,
            )
        }

        Column(
            modifier =
                Modifier
                    .align(if (isLandscape) Alignment.Center else Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .widthIn(max = 360.dp)
                    .background(Color(0xF01C1A27), RoundedCornerShape(20.dp))
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.skin_editor_title),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Text(stringResource(R.string.skin_editor_hint), color = Color(0xFFA7A1B8), fontSize = 13.sp)
            Text(
                stringResource(R.string.skin_editor_size, (customization.size * 100).toInt()),
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Slider(
                value = customization.size,
                valueRange = SkinCustomization.MIN_SIZE..SkinCustomization.MAX_SIZE,
                onValueChange = { customizationState.value = customizationState.value.copy(size = it) },
            )
            Text(
                stringResource(R.string.skin_editor_opacity, (customization.opacity * 100).toInt()),
                color = Color.White,
                fontSize = 13.sp,
            )
            Slider(
                value = customization.opacity,
                valueRange = SkinCustomization.MIN_OPACITY..1f,
                onValueChange = { customizationState.value = customizationState.value.copy(opacity = it) },
            )
            run {
                // Two halves of one pill: the Console skin, or the Minimal one.
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(Color(0xFF2A2738), RoundedCornerShape(50))
                            .padding(3.dp),
                ) {
                    val options =
                        listOf(
                            SkinVariant.CONSOLE to R.string.skin_variant_console,
                            SkinVariant.MINIMAL to R.string.skin_variant_minimal,
                        )
                    options.forEach { (option, label) ->
                        val selected = option == variant
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .background(if (selected) Color(0xFF4A4560) else Color.Transparent, RoundedCornerShape(50))
                                    .clickable { onVariantChange(option) }
                                    .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(label),
                                color = if (selected) Color.White else Color(0xFFA7A1B8),
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.skin_editor_reset)) }
                TextButton(onClick = onDone) { Text(stringResource(R.string.skin_editor_done)) }
            }
        }
    }
}
