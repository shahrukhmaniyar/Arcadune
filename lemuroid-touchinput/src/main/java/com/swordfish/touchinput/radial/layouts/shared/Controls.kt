package com.swordfish.touchinput.radial.layouts.shared

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.controls.LemuroidControlAnalog
import com.swordfish.touchinput.radial.controls.LemuroidControlButton
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonSelect(position: Int = 0) {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(120f - 30f * position),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
        icon = R.drawable.button_select,
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonL1() {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(90f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_L1),
        label = "L1",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonL2() {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(120f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
        label = "L2",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonR1() {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(90f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_R1),
        label = "R1",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonR2() {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(60f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_R2),
        label = "R2",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonL() {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(120f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_L1),
        label = "L",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonR() {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(60f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_R1),
        label = "R",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonStart(position: Int = 0) {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(60f + 30f * position),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_START),
        icon = R.drawable.button_start,
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonMenu(settings: TouchControllerSettingsManager.Settings) {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(-60f + 2f * settings.rotation * TouchControllerSettingsManager.MAX_ROTATION),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_MODE),
        icon = R.drawable.button_menu,
    )
}

/** True when the running game can be rewound: the spot opposite the menu button then holds a rewind button. */
val LocalRewindAvailable = staticCompositionLocalOf { false }

/** Key the rewind button sends. The app handles it itself; it never reaches the emulator. */
const val KEYCODE_REWIND = KeyEvent.KEYCODE_MEDIA_REWIND

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonMenuPlaceholder(settings: TouchControllerSettingsManager.Settings) {
    val position =
        Modifier.radialPosition(
            -120f - 2f * settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
        )
    if (LocalRewindAvailable.current) {
        LemuroidControlButton(
            modifier = position,
            id = Id.Key(KEYCODE_REWIND),
            icon = R.drawable.button_rewind,
        )
    } else {
        Box(modifier = position)
    }
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryAnalogLeft() {
    LemuroidControlAnalog(
        modifier =
            Modifier
                .radialPosition(-80f)
                .radialScale(2.0f),
        id = Id.ContinuousDirection(ComposeTouchLayouts.MOTION_SOURCE_LEFT_STICK),
        analogPressId = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBL),
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryAnalogRight() {
    LemuroidControlAnalog(
        modifier =
            Modifier
                .radialPosition(+80f - 180f)
                .radialScale(2.0f),
        id = Id.ContinuousDirection(ComposeTouchLayouts.MOTION_SOURCE_RIGHT_STICK),
        analogPressId = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBR),
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonCoin() {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(120f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
        icon = R.drawable.button_coin,
    )
}

object ComposeTouchLayouts {
    const val MOTION_SOURCE_DPAD = 0
    const val MOTION_SOURCE_LEFT_STICK = 1
    const val MOTION_SOURCE_RIGHT_STICK = 2
    const val MOTION_SOURCE_DPAD_AND_LEFT_STICK = 3
    const val MOTION_SOURCE_RIGHT_DPAD = 4
}
