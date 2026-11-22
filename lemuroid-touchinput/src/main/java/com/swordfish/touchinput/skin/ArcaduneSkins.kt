package com.swordfish.touchinput.skin

import android.view.KeyEvent
import androidx.compose.ui.graphics.Color
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_EXIT
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_FAST_FORWARD
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_QUICK_LOAD
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_QUICK_SAVE
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_REWIND
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_SWAP_SCREENS
import com.swordfish.touchinput.radial.settings.TouchControllerID

/** A rectangle in the skin's own units (see [SkinLayout]). */
data class SkinRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val centerX get() = x + width / 2
    val centerY get() = y + height / 2
}

enum class ButtonShape { ROUND, PILL, SHOULDER }

/**
 * Small pictures drawn on buttons whose name says less than a picture: the DS microphone,
 * the 3DS swap, and the quick buttons (fast forward, quick save, quick load).
 */
enum class SkinIcon { MIC, LID, SWAP, FAST_FORWARD, QUICK_SAVE, QUICK_LOAD, EXIT }

/** How a skin is painted. Both are Arcadune's own artwork. */
enum class SkinStyle {
    /** A white console body with black buttons, shaped like a handheld. */
    MONO,

    /** A grey frame around a black pad, with red buttons on white squares, like the classic NES pad. */
    NES_CLASSIC,

    /** Light outlines over the game, for landscape. */
    OVERLAY,

    /** Painted in the colours of the console's own controller (see [SkinPalette]). */
    CONSOLE,

    /** White outlines: on a dark grey body upright, over the game sideways (the Minimal skin). */
    OUTLINE,
}

/**
 * The two skins every console offers, like Delta's: [CONSOLE] in the console's own colours and
 * shape, [MINIMAL] in plain outlines. Both put every control in the same place, so the player's
 * moved buttons and sizes apply to either.
 */
enum class SkinVariant { CONSOLE, MINIMAL }

/**
 * The colours of a console's controller. [buttons] gives some buttons their own colour (fill, then
 * label), like the coloured A / B / X / Y of the Super Famicom or the PlayStation symbols.
 */
data class SkinPalette(
    val body: Color,
    val bodyShade: Color,
    val dpad: Color,
    val dpadMark: Color,
    val button: Color,
    val buttonLabel: Color,
    val shoulder: Color,
    val shoulderLabel: Color,
    val small: Color,
    val caption: Color,
    val stickBase: Color = dpad,
    val stickKnob: Color = button,
    val buttons: Map<Int, Pair<Color, Color>> = emptyMap(),
) {
    fun fillOf(key: Int): Color = buttons[key]?.first ?: button

    fun labelOf(key: Int): Color = buttons[key]?.second ?: buttonLabel
}

/** How a d-pad looks: one cross, four separate arrows (PlayStation, PSP) or four round C buttons (N64). */
enum class DpadLook { CROSS, SEPARATE, C_BUTTONS, BALL_TOP }

sealed interface SkinControl {
    val frame: SkinRect

    /** Extra touch area around the control, in skin units, so near misses still count. */
    val reach: Float

    /** True for optional utility buttons (Fast Forward, Exit) that start off until enabled in Edit Controls. */
    val defaultHidden: Boolean

    /** [source] is which direction it sends: the d-pad, or a stick moved in 8 (C buttons: 4) steps. */
    data class Dpad(
        override val frame: SkinRect,
        override val reach: Float = 14f,
        val source: Int = ComposeTouchLayouts.MOTION_SOURCE_DPAD,
        val look: DpadLook = DpadLook.CROSS,
        override val defaultHidden: Boolean = false,
    ) : SkinControl

    /** An analog stick. [pressKey] is sent when the stick is pressed in (L3 / R3), if the console has it. */
    data class Stick(
        override val frame: SkinRect,
        val source: Int,
        val pressKey: Int? = null,
        override val reach: Float = 8f,
        override val defaultHidden: Boolean = false,
    ) : SkinControl

    data class Button(
        val keyCode: Int,
        val label: String,
        val shape: ButtonShape,
        override val frame: SkinRect,
        override val reach: Float = 6f,
        /** Small buttons (select, start) and NES face buttons carry their name under them. */
        val labelBelow: Boolean = false,
        /** Drawn on the button instead of its name (which then goes under it). */
        val icon: SkinIcon? = null,
        /** A second key pressed with [keyCode] (the AB button). */
        val alsoKey: Int? = null,
        override val defaultHidden: Boolean = false,
        /** How big the name under the button is, 1 normally; the extra buttons shrink it when a skin is crowded. */
        val captionScale: Float = 1f,
    ) : SkinControl

    data class Menu(
        override val frame: SkinRect,
        override val reach: Float = 10f,
        override val defaultHidden: Boolean = false,
    ) : SkinControl

    data class Rewind(
        override val frame: SkinRect,
        override val reach: Float = 10f,
        override val defaultHidden: Boolean = false,
    ) : SkinControl
}

/**
 * Where everything sits, in a coordinate space of [width] x [height] units. In portrait the
 * layout is the controller panel under the game, scaled to the screen width. In landscape it
 * covers the whole screen over the game: controls stay pinned to the side (or the middle) they
 * are drawn on, so they keep their place on any screen shape.
 */
data class SkinLayout(
    val width: Float,
    val height: Float,
    val style: SkinStyle,
    val controls: List<SkinControl>,
    /** The console's colours: the whole controller in [SkinStyle.CONSOLE], tinted buttons in [SkinStyle.OVERLAY]. */
    val palette: SkinPalette? = null,
    /**
     * Landscape skins with a console shell: where the game's screens go, between the controls.
     * The shell's body fills the rest of the screen. Null for skins whose controls sit over the game.
     */
    val screenArea: SkinRect? = null,
    /**
     * Upright: set, the console's body covers the whole screen and the screens sit in it, this
     * share of the width in from each side (the DS, the way Delta lays it out). Not set, the body
     * is only behind the controller and the screen sits in black above it.
     */
    val uprightScreenSide: Float? = null,
    /**
     * Set, the screens sit in one recessed panel with thin outlines (no thick bezel, no hinge),
     * the way Delta frames its DS; the panel reaches this far (in layout units) below the screens,
     * around the L / R tabs that sit right under them.
     */
    val screenPanelBelow: Float? = null,
    /** How the layout starts, tuned by hand (the screens, the buttons, the background): what Reset goes back to. */
    val defaultCustomization: SkinCustomization = SkinCustomization(),
    /** Set, the menu button has no name under it (the small buttons carry a picture or their name on them). */
    val captionless: Boolean = false,
    /** Set, the four extra buttons are where the layout put them, not placed by [placeExtras]. */
    val fixedExtras: Boolean = false,
) {
    internal val memoizedSignature: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val text =
            controls
                .filterNot { it.defaultHidden }
                .joinToString("|") { control ->
                    val kind =
                        when (control) {
                            is SkinControl.Button -> "b${control.keyCode}"
                            is SkinControl.Dpad -> "d${control.source}"
                            is SkinControl.Stick -> "s${control.source}"
                            is SkinControl.Menu -> "m"
                            is SkinControl.Rewind -> "r"
                        }
                    "$kind@${control.frame.x},${control.frame.y},${control.frame.width},${control.frame.height}"
                }
        Integer.toHexString(text.hashCode())
    }
}

data class ArcaduneSkin(val portrait: SkinLayout, val landscape: SkinLayout) {
    /** The same controller in plain outlines (the Minimal skin), for players who prefer it to the console's look. */
    fun minimal(): ArcaduneSkin =
        ArcaduneSkin(
            portrait = portrait.copy(style = SkinStyle.OUTLINE, palette = SkinPalettes.OUTLINE),
            landscape = landscape.copy(style = SkinStyle.OUTLINE, palette = SkinPalettes.OUTLINE),
        )

    /** The same layout painted in [palette]: the portrait panel in its colours, tinted buttons in landscape. */
    fun colouredAs(palette: SkinPalette): ArcaduneSkin =
        ArcaduneSkin(
            portrait = portrait.copy(style = SkinStyle.CONSOLE, palette = palette),
            landscape =
                landscape.copy(
                    style = if (landscape.screenArea != null) SkinStyle.CONSOLE else SkinStyle.OVERLAY,
                    palette = palette,
                ),
        )
}

/** Each console's colours. All Arcadune's own picks, after the look of the real controllers. */
object SkinPalettes {
    private val White = Color(0xFFFFFFFF)

    /** The Minimal skin: white outlines on dark grey. */
    val OUTLINE =
        SkinPalette(
            body = Color(0xFF2C2C30),
            bodyShade = Color(0xFF232326),
            dpad = Color(0xFF2C2C30),
            dpadMark = Color(0xFFF2F2F4),
            button = Color(0xFF2C2C30),
            buttonLabel = Color(0xFFF2F2F4),
            shoulder = Color(0xFF2C2C30),
            shoulderLabel = Color(0xFFF2F2F4),
            small = Color(0xFF2C2C30),
            caption = Color(0xFFD6D6DA),
        )

    /** The black-and-white look of [SkinStyle.MONO], for the controls only the coloured skins have. */
    val MONO =
        SkinPalette(
            body = Color(0xFFF0F0F2),
            bodyShade = Color(0xFFD3D3D7),
            dpad = Color(0xFF1E1E21),
            dpadMark = Color(0xFF4A4A4F),
            button = Color(0xFF1E1E21),
            buttonLabel = Color(0xFF8A8A90),
            shoulder = Color(0xFF2A2A2E),
            shoulderLabel = Color(0xFFBDBDC3),
            small = Color(0xFF2A2A2E),
            caption = Color(0xFF151517),
            stickBase = Color(0xFF2A2A2E),
            stickKnob = Color(0xFF1A1A1D),
        )

    private val PsSymbols =
        mapOf(
            KeyEvent.KEYCODE_BUTTON_X to (Color(0xFF3E3E44) to Color(0xFF46C487)),
            KeyEvent.KEYCODE_BUTTON_A to (Color(0xFF3E3E44) to Color(0xFFEF5A5F)),
            KeyEvent.KEYCODE_BUTTON_B to (Color(0xFF3E3E44) to Color(0xFF6F9FF0)),
            KeyEvent.KEYCODE_BUTTON_Y to (Color(0xFF3E3E44) to Color(0xFFE48AC0)),
        )

    val PLAYSTATION =
        SkinPalette(
            body = Color(0xFFD4D4D8),
            bodyShade = Color(0xFFB9B9BE),
            dpad = Color(0xFF4A4A50),
            dpadMark = Color(0xFF2E2E33),
            button = Color(0xFF3E3E44),
            buttonLabel = White,
            shoulder = Color(0xFFA6A6AB),
            shoulderLabel = Color(0xFF2E2E33),
            small = Color(0xFF55555C),
            caption = Color(0xFF3A3A40),
            stickBase = Color(0xFF3A3A40),
            stickKnob = Color(0xFF55555C),
            buttons = PsSymbols,
        )

    val PSP =
        SkinPalette(
            body = Color(0xFF26262A),
            bodyShade = Color(0xFF141416),
            dpad = Color(0xFF8D8D93),
            dpadMark = Color(0xFF3A3A3F),
            button = Color(0xFF2C2C31),
            buttonLabel = White,
            shoulder = Color(0xFF3A3A3F),
            shoulderLabel = Color(0xFFC9C9CF),
            small = Color(0xFF5A5A60),
            caption = Color(0xFFB4B4BA),
            stickBase = Color(0xFF3C3C42),
            stickKnob = Color(0xFF66666D),
            buttons =
                mapOf(
                    KeyEvent.KEYCODE_BUTTON_X to (Color(0xFF2C2C31) to Color(0xFF9FD8BE)),
                    KeyEvent.KEYCODE_BUTTON_A to (Color(0xFF2C2C31) to Color(0xFFF2A1A4)),
                    KeyEvent.KEYCODE_BUTTON_B to (Color(0xFF2C2C31) to Color(0xFFA9C4F2)),
                    KeyEvent.KEYCODE_BUTTON_Y to (Color(0xFF2C2C31) to Color(0xFFECB7D6)),
                ),
        )

    val N64 =
        SkinPalette(
            body = Color(0xFF9A9BA3),
            bodyShade = Color(0xFF808189),
            dpad = Color(0xFF3D3E44),
            dpadMark = Color(0xFF6A6B72),
            button = Color(0xFF4D4E55),
            buttonLabel = White,
            shoulder = Color(0xFF6E6F77),
            shoulderLabel = White,
            small = Color(0xFF55565D),
            caption = Color(0xFF2E2F35),
            stickBase = Color(0xFF6E6F77),
            stickKnob = Color(0xFF55565D),
            buttons =
                mapOf(
                    // A, B, Start (A and B are sent as B / Y, like the other N64 controls).
                    KeyEvent.KEYCODE_BUTTON_B to (Color(0xFF2558CB) to White),
                    KeyEvent.KEYCODE_BUTTON_Y to (Color(0xFF2C9B49) to White),
                    KeyEvent.KEYCODE_BUTTON_START to (Color(0xFFC8322F) to White),
                    // The C buttons.
                    C_BUTTONS_KEY to (Color(0xFFE9C31F) to Color(0xFF5A4600)),
                ),
        )

    val DS =
        SkinPalette(
            body = Color(0xFFF2F2F4),
            bodyShade = Color(0xFFDADADF),
            dpad = Color(0xFF2C2C31),
            dpadMark = Color(0xFF55555C),
            button = Color(0xFFCFD0D6),
            buttonLabel = Color(0xFF2C2C31),
            shoulder = Color(0xFFC8C8CE),
            shoulderLabel = Color(0xFF3A3A40),
            small = Color(0xFF2C2C31),
            caption = Color(0xFF4D4D55),
        )

    val N3DS =
        SkinPalette(
            body = Color(0xFF2B2D34),
            bodyShade = Color(0xFF1C1E23),
            dpad = Color(0xFF3B3E46),
            dpadMark = Color(0xFF6A6D76),
            button = Color(0xFF3B3E46),
            buttonLabel = White,
            shoulder = Color(0xFF3B3E46),
            shoulderLabel = Color(0xFFCFD2DA),
            small = Color(0xFF4A4D56),
            caption = Color(0xFFAEB1BA),
            stickBase = Color(0xFF3B3E46),
            stickKnob = Color(0xFF5A5D66),
            // The New 3DS's coloured buttons.
            buttons =
                mapOf(
                    KeyEvent.KEYCODE_BUTTON_X to (Color(0xFF2F5CC4) to White),
                    KeyEvent.KEYCODE_BUTTON_Y to (Color(0xFF2F9B50) to White),
                    KeyEvent.KEYCODE_BUTTON_A to (Color(0xFFD43B3B) to White),
                    KeyEvent.KEYCODE_BUTTON_B to (Color(0xFFE8B82C) to Color(0xFF4A3500)),
                ),
        )

    /** The NES pad sideways: grey body, black d-pad, red A and B. */
    val NES =
        SkinPalette(
            body = Color(0xFFDADADC),
            bodyShade = Color(0xFFC4C4C7),
            dpad = Color(0xFF1A1A1C),
            dpadMark = Color(0xFF4A4A4F),
            button = Color(0xFFD71920),
            buttonLabel = White,
            shoulder = Color(0xFF3A3A3D),
            shoulderLabel = Color(0xFFEDEDEF),
            small = Color(0xFF2A2A2C),
            caption = Color(0xFFC3161C),
        )

    /** Super Famicom / European SNES: grey body, red A, yellow B, blue X, green Y. */
    val SNES =
        SkinPalette(
            body = Color(0xFFD6D3DB),
            bodyShade = Color(0xFFBDBAC4),
            dpad = Color(0xFF3B3A40),
            dpadMark = Color(0xFF66656C),
            button = Color(0xFF8E8B96),
            buttonLabel = White,
            shoulder = Color(0xFFAEABB5),
            shoulderLabel = Color(0xFF3B3A40),
            small = Color(0xFF5A5860),
            caption = Color(0xFF4A4752),
            buttons =
                mapOf(
                    KeyEvent.KEYCODE_BUTTON_A to (Color(0xFFD43B3B) to White),
                    KeyEvent.KEYCODE_BUTTON_B to (Color(0xFFE8B82C) to Color(0xFF4A3500)),
                    KeyEvent.KEYCODE_BUTTON_X to (Color(0xFF2F5CC4) to White),
                    KeyEvent.KEYCODE_BUTTON_Y to (Color(0xFF2F9B50) to White),
                ),
        )

    /** The original Game Boy: warm grey, dark d-pad, magenta A and B, navy writing. */
    val GB =
        SkinPalette(
            body = Color(0xFFD2D0CA),
            bodyShade = Color(0xFFBAB8B1),
            dpad = Color(0xFF2B2B30),
            dpadMark = Color(0xFF55555C),
            button = Color(0xFF9C2457),
            buttonLabel = Color(0xFFF2D6E2),
            shoulder = Color(0xFF8C8A85),
            shoulderLabel = Color(0xFF2C2C30),
            small = Color(0xFF7D7B84),
            caption = Color(0xFF2E3F8E),
        )

    /** Game Boy Advance: indigo body, grey A and B. */
    val GBA =
        SkinPalette(
            body = Color(0xFF5544A0),
            bodyShade = Color(0xFF3F3184),
            dpad = Color(0xFF1F1F25),
            dpadMark = Color(0xFF4A4A52),
            button = Color(0xFFBAB7C6),
            buttonLabel = Color(0xFF2E2650),
            shoulder = Color(0xFF3A2E72),
            shoulderLabel = Color(0xFFD9D4F0),
            small = Color(0xFF2E2650),
            caption = Color(0xFFD9D4F0),
        )

    /** Mega Drive / Genesis: black pad, red letters. */
    val GENESIS =
        SkinPalette(
            body = Color(0xFF2A2A2E),
            bodyShade = Color(0xFF161618),
            dpad = Color(0xFF3A3A40),
            dpadMark = Color(0xFF66666D),
            button = Color(0xFF34343A),
            buttonLabel = Color(0xFFE85050),
            shoulder = Color(0xFF3A3A40),
            shoulderLabel = Color(0xFFBBBBC2),
            small = Color(0xFF4A4A50),
            caption = Color(0xFFBBBBC2),
        )

    /** Master System: black pad, white numbers. */
    val SMS = GENESIS.copy(buttonLabel = White)

    /** Game Gear: dark grey body, deep blue buttons. */
    val GG =
        GENESIS.copy(
            body = Color(0xFF34353C),
            bodyShade = Color(0xFF24252A),
            button = Color(0xFF2E3D96),
            buttonLabel = White,
            small = Color(0xFF2E3D96),
        )

    /** PC Engine: the white pad with black buttons. */
    val PCE =
        SkinPalette(
            body = Color(0xFFEBEBED),
            bodyShade = Color(0xFFD2D2D6),
            dpad = Color(0xFF2B2B30),
            dpadMark = Color(0xFF55555C),
            button = Color(0xFF2B2B30),
            buttonLabel = Color(0xFFEBEBED),
            shoulder = Color(0xFFC8C8CE),
            shoulderLabel = Color(0xFF3A3A40),
            small = Color(0xFF3A3A40),
            caption = Color(0xFF3A3A40),
        )

    /** Atari joysticks: black base, ball-top stick, red fire buttons. */
    val ATARI =
        SkinPalette(
            body = Color(0xFF2A2A2D),
            bodyShade = Color(0xFF17171A),
            dpad = Color(0xFF111113),
            dpadMark = Color(0xFF3A3A3F),
            button = Color(0xFFD2232A),
            buttonLabel = White,
            shoulder = Color(0xFF3A3A40),
            shoulderLabel = Color(0xFFBBBBC2),
            small = Color(0xFF8A6A3A),
            caption = Color(0xFFC9B38A),
            stickBase = Color(0xFF111113),
            stickKnob = Color(0xFF1E1E21),
        )

    /** Atari Lynx: dark grey with grey buttons. */
    val LYNX =
        GENESIS.copy(
            body = Color(0xFF3A3A3F),
            bodyShade = Color(0xFF26262A),
            button = Color(0xFF55555C),
            buttonLabel = White,
        )

    /** Neo Geo Pocket Color: silver body, dark stick and buttons. */
    val NGP =
        SkinPalette(
            body = Color(0xFFCDD1DA),
            bodyShade = Color(0xFFB2B7C2),
            dpad = Color(0xFF2B2B30),
            dpadMark = Color(0xFF55555C),
            button = Color(0xFF2B2B30),
            buttonLabel = White,
            shoulder = Color(0xFFB2B7C2),
            shoulderLabel = Color(0xFF2B2B30),
            small = Color(0xFF3A3A40),
            caption = Color(0xFF2B2B30),
        )

    /** WonderSwan: pearl white with dark buttons. */
    val WONDERSWAN = NGP.copy(body = Color(0xFFEDEBE4), bodyShade = Color(0xFFD7D4CB))

    private val ArcadePanel =
        SkinPalette(
            body = Color(0xFF232327),
            bodyShade = Color(0xFF121214),
            dpad = Color(0xFF0E0E10),
            dpadMark = Color(0xFF3A3A3F),
            button = Color(0xFFD2232A),
            buttonLabel = White,
            shoulder = Color(0xFF3A3A40),
            shoulderLabel = Color(0xFFBBBBC2),
            small = Color(0xFF4A4A50),
            caption = Color(0xFFBBBBC2),
            stickBase = Color(0xFF0E0E10),
            stickKnob = Color(0xFFD2232A),
        )

    private val ArcadeRed = Color(0xFFD2232A) to White
    private val ArcadeYellow = Color(0xFFF2C21B) to Color(0xFF4A3A00)
    private val ArcadeGreen = Color(0xFF26A04A) to White
    private val ArcadeBlue = Color(0xFF2F5CC4) to White

    /** Arcade with four buttons, coloured like the Neo Geo's A, B, C, D. */
    val ARCADE_4 =
        ArcadePanel.copy(
            buttons =
                mapOf(
                    KeyEvent.KEYCODE_BUTTON_B to ArcadeRed,
                    KeyEvent.KEYCODE_BUTTON_A to ArcadeYellow,
                    KeyEvent.KEYCODE_BUTTON_Y to ArcadeGreen,
                    KeyEvent.KEYCODE_BUTTON_X to ArcadeBlue,
                ),
        )

    /** Arcade with six buttons: punches on top, kicks below. */
    val ARCADE_6 =
        ArcadePanel.copy(
            buttons =
                mapOf(
                    KeyEvent.KEYCODE_BUTTON_Y to ArcadeRed,
                    KeyEvent.KEYCODE_BUTTON_X to ArcadeYellow,
                    KeyEvent.KEYCODE_BUTTON_L1 to ArcadeGreen,
                    KeyEvent.KEYCODE_BUTTON_B to ArcadeBlue,
                    KeyEvent.KEYCODE_BUTTON_A to (Color(0xFFE8E8EC) to Color(0xFF2A2A2E)),
                    KeyEvent.KEYCODE_BUTTON_R1 to (Color(0xFF7A3FB8) to White),
                ),
        )

    /** Sega Dreamcast: light warm grey shell, dark triggers/d-pad, and iconic coloured A/B/X/Y + coral Start. */
    val DREAMCAST =
        SkinPalette(
            body = Color(0xFFDFDFE4),
            bodyShade = Color(0xFFC6C6CE),
            dpad = Color(0xFF44454D),
            dpadMark = Color(0xFF292A30),
            button = Color(0xFF44454D),
            buttonLabel = White,
            shoulder = Color(0xFFB4B5BE),
            shoulderLabel = Color(0xFF2E2F36),
            small = Color(0xFFD9582C),
            caption = Color(0xFF3A3B43),
            stickBase = Color(0xFFB7B8C1),
            stickKnob = Color(0xFFD6D7DF),
            buttons =
                mapOf(
                    KeyEvent.KEYCODE_BUTTON_X to (Color(0xFF2B9B56) to White),
                    KeyEvent.KEYCODE_BUTTON_Y to (Color(0xFFE5AE24) to Color(0xFF3E2B00)),
                    KeyEvent.KEYCODE_BUTTON_A to (Color(0xFF286EC7) to White),
                    KeyEvent.KEYCODE_BUTTON_B to (Color(0xFFDC4437) to White),
                ),
        )

    /** Sega Saturn: cool slate charcoal pad with coloured A/B/C row and dark X/Y/Z arc. */
    val SATURN =
        SkinPalette(
            body = Color(0xFF32343D),
            bodyShade = Color(0xFF202228),
            dpad = Color(0xFF22242B),
            dpadMark = Color(0xFF565966),
            button = Color(0xFF4D505D),
            buttonLabel = White,
            shoulder = Color(0xFF444752),
            shoulderLabel = Color(0xFFD6D8E2),
            small = Color(0xFF4A4D5A),
            caption = Color(0xFFC4C7D2),
            stickBase = Color(0xFF22242B),
            stickKnob = Color(0xFF4D505D),
            buttons =
                mapOf(
                    KeyEvent.KEYCODE_BUTTON_B to (Color(0xFF2E9B58) to White),
                    KeyEvent.KEYCODE_BUTTON_A to (Color(0xFFE2B028) to Color(0xFF3E2C00)),
                    KeyEvent.KEYCODE_BUTTON_L1 to (Color(0xFF3268C8) to White),
                ),
        )

    /** Panasonic 3DO: warm charcoal FZ-1 pad with angled A/B/C buttons and crimson control pills. */
    val THREEDO =
        SkinPalette(
            body = Color(0xFF343339),
            bodyShade = Color(0xFF222126),
            dpad = Color(0xFF222126),
            dpadMark = Color(0xFF57555F),
            button = Color(0xFF4B4954),
            buttonLabel = White,
            shoulder = Color(0xFF45434D),
            shoulderLabel = Color(0xFFD4D2DC),
            small = Color(0xFF7B2E3B),
            caption = Color(0xFFC8C5D0),
        )

    /** Atari 5200 SuperSystem: matte charcoal housing, brushed silver accents, red-orange fire buttons. */
    val ATARI5200 =
        ATARI.copy(
            body = Color(0xFF28292E),
            bodyShade = Color(0xFF17181B),
            small = Color(0xFF575963),
            caption = Color(0xFFC6C8D2),
            stickBase = Color(0xFF141518),
            stickKnob = Color(0xFF2D2F36),
        )

    /** Atari 8-bit (800XL): espresso brown & brushed cream-silver console function keys. */
    val ATARI800 =
        ATARI.copy(
            body = Color(0xFF352D28),
            bodyShade = Color(0xFF221C19),
            button = Color(0xFFD44727),
            shoulder = Color(0xFFC8BFA8),
            shoulderLabel = Color(0xFF2B2420),
            small = Color(0xFF8C7A65),
            caption = Color(0xFFE4DAC8),
            buttons =
                mapOf(
                    KeyEvent.KEYCODE_BUTTON_Y to (Color(0xFF594D44) to Color(0xFFF2ECE1)),
                ),
        )

    /** ColecoVision: matte charcoal shell, warm beige trim, dual-tone fire buttons & numeric keypad. */
    val COLECOVISION =
        SkinPalette(
            body = Color(0xFF27272B),
            bodyShade = Color(0xFF161619),
            dpad = Color(0xFF151518),
            dpadMark = Color(0xFF45454D),
            button = Color(0xFF4A4A52),
            buttonLabel = Color(0xFFF2EFE9),
            shoulder = Color(0xFF4A4A52),
            shoulderLabel = Color(0xFFF2EFE9),
            small = Color(0xFF5C5850),
            caption = Color(0xFFDFD8C8),
            stickBase = Color(0xFF151518),
            stickKnob = Color(0xFF2D2D33),
            buttons =
                mapOf(
                    KeyEvent.KEYCODE_BUTTON_B to (Color(0xFFD94E28) to White),
                    KeyEvent.KEYCODE_BUTTON_A to (Color(0xFFD94E28) to White),
                ),
        )

    /** Commodore 64: iconic "Breadbin" taupe-brown case, dark keycaps, and Competition Pro red fire. */
    val C64 =
        SkinPalette(
            body = Color(0xFF8C8272),
            bodyShade = Color(0xFF736A5B),
            dpad = Color(0xFF2C2723),
            dpadMark = Color(0xFF574E47),
            button = Color(0xFFC92A2A),
            buttonLabel = White,
            shoulder = Color(0xFF453D36),
            shoulderLabel = Color(0xFFF0EBE1),
            small = Color(0xFF453D36),
            caption = Color(0xFF2B2520),
            stickBase = Color(0xFF24201D),
            stickKnob = Color(0xFFC92A2A),
            buttons =
                mapOf(
                    KeyEvent.KEYCODE_BUTTON_X to (Color(0xFF453D36) to Color(0xFFF0EBE1)),
                ),
        )

    /** Apple II: classic platinum-beige housing with warm Apple orange & charcoal joystick buttons. */
    val APPLEII =
        SkinPalette(
            body = Color(0xFFD8D2C2),
            bodyShade = Color(0xFFBFB8A6),
            dpad = Color(0xFF302E2B),
            dpadMark = Color(0xFF5C5852),
            button = Color(0xFF3A3733),
            buttonLabel = Color(0xFFF4EFE4),
            shoulder = Color(0xFF9E9788),
            shoulderLabel = Color(0xFF2B2926),
            small = Color(0xFF4A4640),
            caption = Color(0xFF3A3631),
            stickBase = Color(0xFF2B2926),
            stickKnob = Color(0xFF3D3A36),
            buttons =
                mapOf(
                    KeyEvent.KEYCODE_BUTTON_A to (Color(0xFFD96B27) to White),
                ),
        )

    /** Microsoft MSX: dark navy-anthracite MSX2 deck with royal blue A, crimson B, and slate spacebar. */
    val MSX =
        SkinPalette(
            body = Color(0xFF2B303C),
            bodyShade = Color(0xFF1C2029),
            dpad = Color(0xFF1E222B),
            dpadMark = Color(0xFF4A5162),
            button = Color(0xFF2F66C8),
            buttonLabel = White,
            shoulder = Color(0xFF3E4556),
            shoulderLabel = Color(0xFFDCE2F0),
            small = Color(0xFF434B5C),
            caption = Color(0xFFC8D0E0),
            buttons =
                mapOf(
                    KeyEvent.KEYCODE_BUTTON_A to (Color(0xFF2F66C8) to White),
                    KeyEvent.KEYCODE_BUTTON_B to (Color(0xFFD4433B) to White),
                    KeyEvent.KEYCODE_BUTTON_Y to (Color(0xFF434B5C) to White),
                ),
        )

    /** Not a real key: where [N64]'s C buttons find their colour. */
    const val C_BUTTONS_KEY = -64
}

/**
 * Arcadune's own controller skins, laid out the way Delta lays out its controllers: d-pad on the
 * left, face buttons on the right, menu / select / start in a row at the bottom with their names
 * under them. The artwork is Arcadune's own (no Delta graphics or branding).
 */
object ArcaduneSkins {
    private val consoleCache = java.util.concurrent.ConcurrentHashMap<TouchControllerID, ArcaduneSkin>()

    private val skinCache =
        java.util.concurrent.ConcurrentHashMap<Pair<TouchControllerID, SkinVariant>, ArcaduneSkin>()

    /** The skin of [id]: the Console skin, in its colours and shape, or the Minimal one in outlines. */
    fun forController(
        id: TouchControllerID,
        variant: SkinVariant = SkinVariant.CONSOLE,
    ): ArcaduneSkin? {
        if (id == TouchControllerID.DOS) return null
        val cacheKey = id to variant
        skinCache[cacheKey]?.let { return it }
        // Both looks share one layout, worked out once (placing the extra buttons takes a moment).
        val base =
            consoleCache[id] ?: skinOf(id)
                ?.let { skin ->
                    val palette = skin.landscape.palette ?: skin.portrait.palette ?: SkinPalettes.NES
                    shellLandscape(id, palette)?.let { skin.copy(landscape = it) } ?: skin
                }
                ?.withExtrasClearOfScreens(id)
                ?.handTuned(id)
                ?.also { consoleCache.putIfAbsent(id, it) }
        val built = base?.let { if (variant == SkinVariant.MINIMAL) it.minimal() else it }
        if (built != null) {
            skinCache.putIfAbsent(cacheKey, built)
        }
        return built
    }

    private fun skinOf(id: TouchControllerID): ArcaduneSkin? =
        when (id) {
            TouchControllerID.PSX -> playStation(dualShock = false)
            TouchControllerID.PSX_DUALSHOCK -> playStation(dualShock = true)
            TouchControllerID.PSP -> psp()
            TouchControllerID.N64 -> n64()
            TouchControllerID.MELONDS -> ds()
            TouchControllerID.NINTENDO_3DS -> n3ds()
            TouchControllerID.NES -> nes()
            TouchControllerID.SNES -> snes().colouredAs(SkinPalettes.SNES)
            TouchControllerID.GB -> gb().colouredAs(SkinPalettes.GB)
            TouchControllerID.GBA -> gba().colouredAs(SkinPalettes.GBA)
            TouchControllerID.GENESIS_3 -> genesis3().colouredAs(SkinPalettes.GENESIS)
            TouchControllerID.GENESIS_6 -> genesis6().colouredAs(SkinPalettes.GENESIS)
            TouchControllerID.SMS ->
                twoButtons(first = "1", second = "2", start = "START", select = null).colouredAs(SkinPalettes.SMS)
            TouchControllerID.GG ->
                twoButtons(first = "1", second = "2", start = "START", select = null).colouredAs(SkinPalettes.GG)
            TouchControllerID.PCE ->
                twoButtons(first = "I", second = "II", start = "RUN", select = "SELECT").colouredAs(SkinPalettes.PCE)
            TouchControllerID.ATARI2600 -> atari2600()
            TouchControllerID.ATARI7800 -> atari7800()
            TouchControllerID.LYNX -> lynx()
            TouchControllerID.NGP ->
                twoButtons(first = "A", second = "B", start = "OPTION", select = null).colouredAs(SkinPalettes.NGP)
            TouchControllerID.WS_LANDSCAPE ->
                twoButtons(
                    first = "B",
                    second = "A",
                    start = "START",
                    select = null,
                ).colouredAs(SkinPalettes.WONDERSWAN)
            TouchControllerID.WS_PORTRAIT -> wonderSwanVertical()
            TouchControllerID.ARCADE_4 -> arcade(sixButtons = false)
            TouchControllerID.ARCADE_6 -> arcade(sixButtons = true)
            TouchControllerID.DREAMCAST -> dreamcast()
            TouchControllerID.SATURN -> saturn(threeDPad = false)
            TouchControllerID.SATURN_3D -> saturn(threeDPad = true)
            TouchControllerID.THREEDO -> threedo()
            TouchControllerID.ATARI5200 -> atari5200()
            TouchControllerID.ATARI800 -> atari800()
            TouchControllerID.COLECOVISION -> colecoVision()
            TouchControllerID.C64 -> c64()
            TouchControllerID.APPLEII -> appleII()
            TouchControllerID.MSX -> msx()
            else -> null
        }

    /** Keys the skin itself handles and never sends to the game. */
    val rewindKey = KEYCODE_REWIND

    private const val PW = 320f
    private const val PH = 274f
    private const val LW = 812f
    private const val LH = 375f

    private fun r(
        x: Number,
        y: Number,
        w: Number,
        h: Number = w,
    ) = SkinRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())

    private fun round(
        key: Int,
        label: String,
        frame: SkinRect,
        labelBelow: Boolean = false,
        icon: SkinIcon? = null,
        reach: Float = 6f,
    ) = SkinControl.Button(
        key,
        label,
        ButtonShape.ROUND,
        frame,
        reach = reach,
        labelBelow = labelBelow || icon != null,
        icon = icon,
    )

    /** A small pill; [reach] is smaller where pills sit close together, so a press never lands on the next one. */
    private fun pill(
        key: Int,
        label: String,
        frame: SkinRect,
        reach: Float = 10f,
        icon: SkinIcon? = null,
    ) = SkinControl.Button(key, label, ButtonShape.PILL, frame, reach = reach, labelBelow = true, icon = icon)

    /**
     * Four round buttons in a diamond around ([cx], [cy]), far enough apart that a press on one
     * never reaches the next (their boxes only meet at the corners, so two can still be pressed together).
     */
    private fun diamond(
        cx: Float,
        cy: Float,
        size: Float,
        top: Pair<Int, String>,
        left: Pair<Int, String>,
        right: Pair<Int, String>,
        bottom: Pair<Int, String>,
    ): List<SkinControl> {
        val away = size * 0.8f + 8f
        val half = size / 2
        return listOf(
            round(top.first, top.second, r(cx - half, cy - away - half, size)),
            round(left.first, left.second, r(cx - away - half, cy - half, size)),
            round(right.first, right.second, r(cx + away - half, cy - half, size)),
            round(bottom.first, bottom.second, r(cx - half, cy + away - half, size)),
        )
    }

    /** Four round buttons in a diamond around ([cx], [cy]), [away] from it: [top], [left], [right], [bottom]. */
    private fun diamondAt(
        cx: Float,
        cy: Float,
        size: Float,
        away: Float,
        top: Pair<Int, String>,
        left: Pair<Int, String>,
        right: Pair<Int, String>,
        bottom: Pair<Int, String>,
    ): List<SkinControl> {
        val half = size / 2
        return listOf(
            round(top.first, top.second, r(cx - half, cy - away - half, size)),
            round(left.first, left.second, r(cx - away - half, cy - half, size)),
            round(right.first, right.second, r(cx + away - half, cy - half, size)),
            round(bottom.first, bottom.second, r(cx - half, cy + away - half, size)),
        )
    }

    private fun psSymbols(
        cx: Float,
        cy: Float,
        size: Float,
    ) = diamond(cx, cy, size, X to TRIANGLE, Y to SQUARE, A to CIRCLE, B to CROSS)

    private fun nintendoFace(
        cx: Float,
        cy: Float,
        size: Float,
    ) = diamond(cx, cy, size, X to "X", Y to "Y", A to "A", B to "B")

    private fun shoulder(
        key: Int,
        label: String,
        frame: SkinRect,
    ) = SkinControl.Button(key, label, ButtonShape.SHOULDER, frame, reach = 4f)

    private const val A = KeyEvent.KEYCODE_BUTTON_A
    private const val B = KeyEvent.KEYCODE_BUTTON_B
    private const val X = KeyEvent.KEYCODE_BUTTON_X
    private const val Y = KeyEvent.KEYCODE_BUTTON_Y
    private const val L = KeyEvent.KEYCODE_BUTTON_L1
    private const val R = KeyEvent.KEYCODE_BUTTON_R1
    private const val SELECT = KeyEvent.KEYCODE_BUTTON_SELECT
    private const val START = KeyEvent.KEYCODE_BUTTON_START

    /**
     * The left edge of each of [count] equal buttons in a row, with the same gutter at both ends
     * and between them: the space left over is split into count + 1 equal parts.
     */
    private fun gutterXs(
        count: Int,
        width: Float = 40f,
        total: Float = PW,
    ): List<Float> {
        val gutter = (total - count * width) / (count + 1)
        return List(count) { gutter + it * (width + gutter) }
    }

    /**
     * The bottom row of the handheld skins: MENU, SELECT, START and REWIND, all the same pill, with
     * the same gutter at both ends and between them. Consoles the app cannot rewind ([rewind] false)
     * have no REWIND, and the others are spread evenly across the row.
     */
    private fun portraitBottomRow(
        select: String? = "SELECT",
        start: String? = "START",
        y: Int = 206,
        rewind: Boolean = true,
    ): List<SkinControl> {
        val keys = listOfNotNull(select?.let { SELECT to it }, start?.let { START to it })
        // Exit is always on the row, its last button.
        val xs = gutterXs(2 + keys.size + if (rewind) 1 else 0)
        return listOf<SkinControl>(SkinControl.Menu(r(xs[0], y, 40, 17), reach = 6f)) +
            keys.mapIndexed { i, (key, label) -> pill(key, label, r(xs[i + 1], y, 40, 17)) } +
            listOfNotNull(if (rewind) SkinControl.Rewind(r(xs[xs.size - 2], y, 40, 17)) else null) +
            optionalButton(KEYCODE_EXIT, "EXIT", SkinIcon.EXIT, r(xs.last(), y, 40, 17), 6f, hidden = false)
    }

    /**
     * The middle of the landscape skins: select / start at the bottom, rewind / menu at the top
     * (the menu alone, centred, on consoles the app cannot rewind).
     */
    private fun landscapeCommon(
        select: String? = "SELECT",
        start: String? = "START",
        rewind: Boolean = true,
    ) = listOfNotNull(
        select?.let { pill(SELECT, it, r(330, 318, 66, 24)) },
        start?.let { pill(START, it, if (select == null) r(373, 318, 66, 24) else r(416, 318, 66, 24)) },
        if (rewind) SkinControl.Rewind(r(330, 18, 66, 24)) else null,
        SkinControl.Menu(if (rewind) r(416, 18, 66, 24) else r(373, 18, 66, 24)),
    )

    /** A pill button that is not on the skin until the player adds it from the editor's hidden tray. */
    private fun optionalButton(
        keyCode: Int,
        label: String,
        icon: SkinIcon,
        frame: SkinRect,
        reach: Float,
        captionScale: Float = 1f,
        captioned: Boolean = true,
        hidden: Boolean = true,
    ) = SkinControl.Button(
        keyCode = keyCode,
        label = label,
        shape = ButtonShape.PILL,
        frame = frame,
        reach = reach,
        labelBelow = captioned,
        icon = icon,
        defaultHidden = hidden,
        captionScale = captionScale,
    )

    /**
     * Where the four extra buttons go on a layout, as frames in the order save, fast-forward, exit,
     * load. The rule: put them where the layout has the most room, as far from every other control
     * (its touch area, the name under it) and from the edges as they can be, so they sit in the
     * middle of an empty area instead of squeezed into a corner of it. Three shapes are tried: a row
     * of four (save, load, fast-forward, exit), a 2 x 2 block (save and load over fast-forward and
     * exit), and two pairs (save and load, fast-forward and exit), each pair side by side or one
     * over the other, placed apart (one in each wing of a console, one each side of the game). The
     * shape with the most room wins, a tidy row or a matching pair of pairs breaking near-ties. If
     * the skin is too crowded, the buttons (and the names under them) shrink a step and it is tried
     * again. Sideways, a full-screen skin keeps the game in the middle, so the buttons go beside it
     * ([overlayGame]); and it pins its left, middle and right controls to different places, so they
     * drift up to [zoneShift] units toward each other on a narrow screen: a button never sits within
     * that of a control of another zone, and a row, a block or a pair stays inside one zone.
     * Returns the frames, the touch margin used and how much the buttons were shrunk.
     */
    private fun placeExtras(
        controls: List<SkinControl>,
        width: Float,
        height: Float,
        slot: SkinRect,
        reach: Float,
        extraBlockers: List<SkinRect> = emptyList(),
        zoneShift: Float = 0f,
        topInset: Float = 2f,
        overlayGame: Boolean = false,
    ): Triple<List<SkinRect>, Float, Float> {
        fun zoneOf(centerX: Float) =
            when {
                zoneShift <= 0f -> 0
                centerX < width * 0.37f -> 0
                centerX > width * 0.63f -> 2
                else -> 1
            }

        // How far apart two boxes are (negative when they overlap).
        fun gap(
            a: SkinRect,
            b: SkinRect,
        ) = maxOf(b.x - (a.x + a.width), a.x - (b.x + b.width), b.y - (a.y + a.height), a.y - (b.y + b.height))

        val visible = controls.filterNot { it.defaultHidden }
        val margin = 2f
        val upright = width < 500f
        val tries = listOf(1f to reach, 0.88f to reach, 0.88f to reach * 0.5f, 0.76f to reach * 0.5f, 0.64f to 1f)
        var overall: Triple<List<SkinRect>, Float, Float>? = null
        var overallScore = -Float.MAX_VALUE
        for ((attempt, tryValues) in tries.withIndex()) {
            val (factor, touchReach) = tryValues
            val lastTry = attempt == tries.lastIndex
            val sw = slot.width * factor
            val sh = slot.height * factor
            // The name under a button: a 4-unit gap and a line of 8.5-unit text, shrunk with the button.
            val caption = 18f * factor
            // Sideways a coarser grid is quicker, but the last, smallest try looks at every other
            // unit: on a crowded skin the gaps left are barely wider than a button.
            val step = if (upright || lastTry) 2f else 4f

            // What each visible control takes: its touch area, plus (upright, where names are written
            // under controls) the name under it. Sideways, the game in the middle of a full-screen skin.
            class Taken(val rect: SkinRect, val zone: Int, val drifts: Boolean)

            val taken =
                visible.map { control ->
                    val f = control.frame
                    val named =
                        upright && (
                            control is SkinControl.Menu || control is SkinControl.Rewind ||
                                (control as? SkinControl.Button)?.labelBelow == true
                        )
                    val under = if (named) maxOf(0f, 18f - control.reach) else 0f
                    Taken(
                        SkinRect(
                            f.x - control.reach,
                            f.y - control.reach,
                            f.width + 2 * control.reach,
                            f.height + 2 * control.reach + under,
                        ),
                        zoneOf(f.centerX),
                        true,
                    )
                } + extraBlockers.map { Taken(it, zoneOf(it.centerX), false) } +
                    listOfNotNull(
                        // A 4:3 game, full height, centred: what the buttons should not cover.
                        if (overlayGame) {
                            Taken(SkinRect(width / 2 - height * 2 / 3, 0f, height * 4 / 3, height), 1, false)
                        } else {
                            null
                        },
                    )

            // One button's cell: its touch area and the name under it.
            fun cell(
                x: Float,
                y: Float,
            ) = SkinRect(x - touchReach, y - touchReach, sw + 2 * touchReach, sh + caption + 2 * touchReach)

            // Room around one button at (x, y): its distance to the nearest control or edge (negative: no room).
            fun room(
                x: Float,
                y: Float,
            ): Float {
                val outside =
                    x < margin || x + sw > width - margin || y < topInset || y + sh + caption > height - margin
                if (outside) return -1f
                val c = cell(x, y)
                val zone = zoneOf(x + sw / 2)
                var best =
                    minOf(x - margin, width - margin - (x + sw), y - topInset, height - margin - (y + sh + caption))
                for (t in taken) {
                    val r =
                        if (t.drifts && zoneShift > 0f && t.zone != zone) {
                            SkinRect(t.rect.x - zoneShift, t.rect.y, t.rect.width + 2 * zoneShift, t.rect.height)
                        } else {
                            t.rect
                        }
                    val g = gap(c, r)
                    if (g < best) best = g
                    if (best < 0f) return best
                }
                return best
            }

            val nx = ((width - sw) / step).toInt() + 1
            val ny = ((height - sh) / step).toInt() + 1
            val grid = Array(nx) { i -> FloatArray(ny) { j -> room(i * step, j * step) } }

            fun roomAt(
                x: Float,
                y: Float,
            ): Float {
                val i = Math.round(x / step)
                val j = Math.round(y / step)
                return if (i in 0 until nx && j in 0 until ny) grid[i][j] else -1f
            }

            // Spacing between two buttons of a group: their touch areas never meet, the names fit.
            val across = (Math.ceil(((sw + 2 * touchReach + 6f) / step).toDouble()) * step).toFloat()
            val down = (Math.ceil(((sh + caption + 2 * touchReach + 2f) / step).toDouble()) * step).toFloat()

            // The least room among [n] buttons at (gx, gy), or -1 when one has none or they are not
            // all in the same zone. Arrays, not lists: this runs a few hundred thousand times.
            fun groupRoom(
                gx: FloatArray,
                gy: FloatArray,
                n: Int,
            ): Float {
                var least = Float.MAX_VALUE
                val zone = zoneOf(gx[0] + sw / 2)
                for (k in 0 until n) {
                    val r = roomAt(gx[k], gy[k])
                    if (r < 0f) return -1f
                    if (k > 0 && zoneOf(gx[k] + sw / 2) != zone) return -1f
                    if (r < least) least = r
                }
                return least
            }

            // The rows of small buttons: menu, rewind and the pills (select, start and the like).
            val smallBands =
                visible.filter {
                    it is SkinControl.Menu ||
                        it is SkinControl.Rewind ||
                        (it is SkinControl.Button && it.shape == ButtonShape.PILL)
                }
                    .map { it.frame.y to it.frame.y + it.frame.height }
            var best: List<SkinRect>? = null
            var bestScore = -1f

            // Scores a group of four (cells in the order save, load, fast-forward, exit) and keeps the best.
            fun offer(
                gx: FloatArray,
                gy: FloatArray,
                room: Float,
                bonus: Float,
            ) {
                var minX = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var top = Float.MAX_VALUE
                var bottom = -Float.MAX_VALUE
                var level = 0
                for (k in 0 until 4) {
                    minX = minOf(minX, gx[k])
                    maxX = maxOf(maxX, gx[k])
                    top = minOf(top, gy[k])
                    bottom = maxOf(bottom, gy[k] + sh)
                    if (smallBands.any { (t, b) -> gy[k] < b && gy[k] + sh > t }) level++
                }
                // A little credit for sitting on the middle line upright.
                val cx = (minX + maxX + sw) / 2
                val centred = if (upright) maxOf(0f, 6f - kotlin.math.abs(cx - width / 2) * 0.05f) else 0f
                // Some buttons level with a row of small buttons (menu, select, start) and the others
                // not, or the group around such a row without joining it, looks like a row that spilled
                // over: all four join the row, or none comes near it.
                val straddles = level == 0 && smallBands.any { (t, b) -> top < b && bottom > t }
                val spilled = if (level in 1..3 || straddles) 30f else 0f
                // Upright, the top of the panel first: the extras sit above the controller, away from the thumbs.
                val high = if (upright) (1f - top / height) * 16f else 0f
                val score = minOf(room, 20f) + bonus + centred + high - spilled
                if (score > bestScore) {
                    bestScore = score
                    best = listOf(0, 2, 3, 1).map { SkinRect(gx[it], gy[it], sw, sh) }
                }
            }

            val gx = FloatArray(4)
            val gy = FloatArray(4)
            // Rows with equal gutters: in every empty stretch of a line the four buttons spread out, so
            // the gap at each end is the same as the gaps between them (not tucked against one side).
            for (j in 0 until ny) {
                var i = 0
                while (i < nx) {
                    if (grid[i][j] < 0f) {
                        i++
                        continue
                    }
                    var e = i
                    while (e + 1 < nx && grid[e + 1][j] >= 0f) e++
                    // A stretch that runs to the panel's edge counts from the edge itself, not from its 2-unit margin.
                    val from = if (i * step <= margin + step) 0f else i * step
                    val to = if (e * step + sw >= width - margin - step) width else e * step + sw
                    val gutter = (to - from - 4 * sw) / 5f
                    if (gutter >= across - sw) {
                        for (k in 0 until 4) {
                            gx[k] = from + gutter + k * (sw + gutter)
                            gy[k] = j * step
                        }
                        groupRoom(gx, gy, 4).let { if (it >= 0f) offer(gx, gy, it, if (upright) 14f else 6f) }
                    } else {
                        // Too tight for the gutters to match the gaps: at least the same space at both ends.
                        val packed = 4 * sw + 3 * (across - sw)
                        if (to - from >= packed) {
                            val start = from + (to - from - packed) / 2
                            for (k in 0 until 4) {
                                gx[k] = start + k * across
                                gy[k] = j * step
                            }
                            groupRoom(gx, gy, 4).let { if (it >= 0f) offer(gx, gy, it, if (upright) 9f else 4f) }
                        }
                    }
                    i = e + 1
                }
            }
            for (j in 0 until ny) {
                val y = j * step
                for (i in 0 until nx) {
                    val x = i * step
                    if (grid[i][j] < 0f) continue
                    // A row: save, load, fast-forward, exit.
                    for (k in 0 until 4) {
                        gx[k] = x + k * across
                        gy[k] = y
                    }
                    groupRoom(gx, gy, 4).let { if (it >= 0f) offer(gx, gy, it, if (upright) 10f else 2f) }
                    // A block: save and load over fast-forward and exit.
                    gx[0] = x
                    gx[1] = x + across
                    gx[2] = x
                    gx[3] = x + across
                    gy[0] = y
                    gy[1] = y
                    gy[2] = y + down
                    gy[3] = y + down
                    groupRoom(gx, gy, 4).let { if (it >= 0f) offer(gx, gy, it, if (upright) 2f else 0f) }
                }
            }

            // Two pairs, apart: first the best places for one pair (side by side, or sideways also
            // one over the other, for the narrow wings), then the best two of them together.
            class PairSpot(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val room: Float) {
                val left = x0 - touchReach
                val top = y0 - touchReach
                val right = maxOf(x0, x1) + sw + touchReach
                val bottom = maxOf(y0, y1) + sh + caption + touchReach
                val centerX = (left + right) / 2
                val centerY = (top + bottom) / 2
                val width = right - left
            }

            val spots = ArrayList<PairSpot>()
            val px = FloatArray(2)
            val py = FloatArray(2)
            for (j in 0 until ny) {
                val y = j * step
                for (i in 0 until nx) {
                    val x = i * step
                    if (grid[i][j] < 0f) continue
                    px[0] = x
                    py[0] = y
                    px[1] = x + across
                    py[1] = y
                    groupRoom(px, py, 2).let { if (it >= 0f) spots += PairSpot(x, y, x + across, y, it) }
                    // One over the other only sideways, in the narrow wings; upright that would straddle a row of buttons.
                    if (!upright) {
                        px[1] = x
                        py[1] = y + down
                        groupRoom(px, py, 2).let { if (it >= 0f) spots += PairSpot(x, y, x, y + down, it) }
                    }
                }
            }
            // The roomiest places, but at most a dozen from each patch of the skin: otherwise one big
            // empty area fills the whole shortlist and a second, tighter one (where the other pair
            // has to go) is never tried.
            val perPatch = HashMap<Long, Int>()
            val ranked =
                spots.sortedByDescending { it.room }.filter { spot ->
                    val patch = (spot.centerX / 60f).toLong() * 1000L + (spot.centerY / 60f).toLong()
                    val n = perPatch.getOrDefault(patch, 0)
                    perPatch[patch] = n + 1
                    n < 12
                }.take(160)
            for (i in ranked.indices) {
                val a = ranked[i]
                for (j in i + 1 until ranked.size) {
                    val b = ranked[j]
                    val apart = maxOf(b.left - a.right, a.left - b.right, b.top - a.bottom, a.top - b.bottom)
                    if (apart < 4f) continue
                    val za = zoneOf(a.centerX)
                    val zb = zoneOf(b.centerX)
                    val sameRows = a.top < b.bottom && b.top < a.bottom
                    val sideGap = maxOf(b.left - a.right, a.left - b.right)
                    if (za != zb && sameRows && zoneShift > 0f && sideGap < 2 * zoneShift) continue
                    // A matching pair of pairs: mirrored across the middle, at the same height.
                    val level = kotlin.math.abs(a.centerY - b.centerY) < 6f
                    val mirrored =
                        level && kotlin.math.abs((a.centerX + b.centerX) / 2 - width / 2) < 12f && a.width == b.width
                    // Two pairs at different heights and not mirrored look scattered: only when nothing tidier fits.
                    if (!mirrored && !level && !lastTry) continue
                    val (l, r) = if (a.centerX <= b.centerX) a to b else b to a
                    gx[0] = l.x0
                    gy[0] = l.y0
                    gx[1] = l.x1
                    gy[1] = l.y1
                    gx[2] = r.x0
                    gy[2] = r.y0
                    gx[3] = r.x1
                    gy[3] = r.y1
                    offer(gx, gy, minOf(a.room, b.room), if (mirrored) (if (upright) 1f else 8f) else -3f)
                }
            }
            // The first four sizes compete (a smaller button pays a little), so a tidy row at a slightly
            // smaller size can beat a block at full size; the smallest is only for crowded skins.
            best?.let {
                val score = bestScore - (1f - factor) * 30f
                if (overall == null || score > overallScore) {
                    overall = Triple(it, touchReach, factor)
                    overallScore = score
                }
            }
            if (overall != null && attempt >= 3) return overall!!
        }
        overall?.let { return it }
        // The skin has no room at all: the top row, as a plain strip (a test would flag it).
        return List(4) {
            SkinRect(
                width / 2 - (4 * slot.width + 3 * slot.width * 0.2f) / 2 + it * slot.width * 1.2f,
                margin,
                slot.width,
                slot.height,
            )
        }
            .let { Triple(it, reach, 1f) }
    }

    private fun autoExtras(
        controls: List<SkinControl>,
        width: Float,
        height: Float,
        slot: SkinRect,
        reach: Float,
        extraBlockers: List<SkinRect> = emptyList(),
        zoneShift: Float = 0f,
        topInset: Float = 2f,
        overlayGame: Boolean = false,
    ): List<SkinControl> {
        val (frames, used, scale) =
            placeExtras(controls, width, height, slot, reach, extraBlockers, zoneShift, topInset, overlayGame)
        return extraButtons(frames, scale, used)
    }

    private fun extraButtons(
        frames: List<SkinRect>,
        captionScale: Float = 1f,
        reach: Float,
    ): List<SkinControl> =
        // The order is the one players' saved layouts were made with: fast-forward, exit, save, load.
        listOf(
            optionalButton(KEYCODE_FAST_FORWARD, "FAST", SkinIcon.FAST_FORWARD, frames[1], reach, captionScale),
            optionalButton(KEYCODE_EXIT, "EXIT", SkinIcon.EXIT, frames[2], reach, captionScale, hidden = false),
            optionalButton(KEYCODE_QUICK_SAVE, "SAVE", SkinIcon.QUICK_SAVE, frames[0], reach, captionScale),
            optionalButton(KEYCODE_QUICK_LOAD, "LOAD", SkinIcon.QUICK_LOAD, frames[3], reach, captionScale),
        )

    /**
     * The four extra buttons of an upright skin. When the bottom row already carries Exit (it is
     * always there, as the last button of the row), only the other three are placed.
     */
    private fun portraitExtras(
        controls: List<SkinControl>,
        height: Float,
    ): List<SkinControl> {
        val extras = autoExtras(controls, PW, height, r(0, 0, 40, 17), 4f)
        val rowHasExit = controls.any { it is SkinControl.Button && it.keyCode == KEYCODE_EXIT }
        return if (rowHasExit) extras.filterNot { it is SkinControl.Button && it.keyCode == KEYCODE_EXIT } else extras
    }

    private fun portrait(
        controls: List<SkinControl>,
        style: SkinStyle = SkinStyle.MONO,
        optionalControls: List<SkinControl>? = null,
    ) = SkinLayout(
        PW,
        PH,
        style,
        controls + (optionalControls ?: portraitExtras(controls, PH)),
    )

    private fun landscape(
        controls: List<SkinControl>,
        palette: SkinPalette? = null,
        screenArea: SkinRect? = null,
        optionalControls: List<SkinControl>? = null,
    ) = SkinLayout(
        LW,
        LH,
        if (screenArea != null) SkinStyle.CONSOLE else SkinStyle.OVERLAY,
        controls +
            (
                optionalControls
                    ?: autoExtras(
                        controls,
                        LW,
                        LH,
                        r(0, 0, 58, 24),
                        3f,
                        listOfNotNull(screenArea),
                        // A full-screen skin pins its left, middle and right controls apart.
                        zoneShift = if (screenArea == null) 106f else 0f,
                        overlayGame = screenArea == null,
                    )
            ),
        palette,
        screenArea,
    )

    /**
     * A layout that starts the way a player tuned it: [encoded] is a saved customization (the
     * moved and resized buttons, the screens' size and place, the background height). [removed]
     * lists controls the layout has dropped since, so the later ones are re-numbered. The four
     * extra buttons are never part of it: they are always placed by [placeExtras].
     */
    private fun SkinLayout.withDefaults(
        encoded: String,
        removed: Set<Int> = emptySet(),
    ): SkinLayout {
        val saved = SkinCustomization.decode(encoded)
        val visible = controls.count { !it.defaultHidden } + removed.size

        fun renumbered(index: Int): Int? =
            if (index in removed || index >= visible) {
                null
            } else {
                index -
                    removed.count {
                        it < index
                    }
            }

        val tuned =
            saved.copy(
                offsets = saved.offsets.mapNotNull { (i, o) -> renumbered(i)?.let { it to o } }.toMap(),
                scales = saved.scales.mapNotNull { (i, s) -> renumbered(i)?.let { it to s } }.toMap(),
                hidden = saved.hidden.mapNotNull { renumbered(it) }.toSet(),
                shown = emptySet(),
            )

        // Sideways, a control is pinned to the left edge, the middle or the right edge by where it
        // was drawn, and a move is kept from there. One the player dragged to another side would
        // drift on a wider or narrower phone (the 3DS SELECT went under the screen): it gets the
        // place it was dragged to as its own, so it is pinned to that side.
        fun sideOf(frame: SkinRect) =
            when {
                frame.centerX < width * 0.37f -> 0
                frame.centerX > width * 0.63f -> 2
                else -> 1
            }
        val crossed =
            if (width > 500f) {
                controls.indices.filter { i ->
                    val c = controls[i]
                    !c.defaultHidden && i in tuned.offsets && sideOf(tuned.frameOf(i, c.frame)) != sideOf(c.frame)
                }.toSet()
            } else {
                emptySet()
            }
        // Where the player saw it, on the phone the layout was tuned on (891 x 411 dp, less the 4 dp
        // margin), turned back into layout units pinned to its new side.
        val geometry = SkinGeometry(this, pinToEdges = true, width = 883f, height = 403f)

        fun seenFrame(i: Int): SkinRect {
            val p = geometry.placedFrame(i, controls[i].frame, tuned)
            val s = geometry.scale
            val x =
                when {
                    p.center.x < 883f * 0.37f -> p.left / s
                    p.center.x > 883f * 0.63f -> width - (883f - p.left) / s
                    else -> width / 2 + (p.left - 883f / 2) / s
                }
            return SkinRect(x, height / 2 + (p.top - 403f / 2) / s, p.width / s, p.height / s)
        }
        val baked =
            copy(controls = controls.mapIndexed { i, c -> if (i in crossed) c.withFrame(seenFrame(i)) else c })
        val rest = tuned.copy(offsets = tuned.offsets - crossed, scales = tuned.scales - crossed)
        return baked.copy(defaultCustomization = rest).withExtrasPlaced(listOfNotNull(screenArea))
    }

    /**
     * When a layout's defaults move the screens (the DS and 3DS: the small screen onto a wing,
     * say), the extras are placed again clear of where the screens really end up, worked out the
     * way the game screen places them on a few common phone sizes and turned back into layout units.
     */
    private fun ArcaduneSkin.withExtrasClearOfScreens(id: TouchControllerID): ArcaduneSkin {
        fun screensIn(isLandscape: Boolean): List<SkinRect> {
            val layout = if (isLandscape) landscape else portrait
            val spec = ScreenSpecs.forController(id, emptyMap(), isLandscape)
            // The frame drawn round each screen, and a little air.
            val bezel = (if (layout.screenPanelBelow != null) SCREEN_OUTLINE_DP else SCREEN_BEZEL_DP) + 2f
            // The 19.5:9 to 20:9 phones the DS and 3DS layouts were tuned on (and their upright sizes):
            // on other shapes the screens grow or shrink against the buttons, so no one place suits all.
            val sizes =
                if (isLandscape) {
                    listOf(891f to 411f, 800f to 360f, 915f to 412f)
                } else {
                    listOf(411f to 891f, 360f to 800f, 412f to 915f)
                }
            return sizes.flatMap { (w, h) ->
                val shell = !isLandscape || layout.screenArea != null
                val placement =
                    placeShell(
                        this,
                        isLandscape,
                        shell,
                        spec,
                        w,
                        h,
                        androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
                        1f,
                        layout.defaultCustomization,
                    )
                // A phone so wide and short that the player's screens run off it has no layout that
                // keeps them clear: it is left out rather than letting it squeeze out every other phone.
                val offPanel =
                    placement.windows.any { it.top < -1f || it.bottom > h + 1f || it.left < -1f || it.right > w + 1f }
                if (offPanel) {
                    emptyList()
                } else if (isLandscape) {
                    // The controls fill the screen less a 4-unit margin, pinned to its left, middle or right.
                    val sw = w - 8f
                    val sh = h - 8f
                    val s = SkinGeometry(layout, pinToEdges = true, width = sw, height = sh).scale
                    placement.windows.map { it.inflate(bezel) }.map { win ->
                        val left = win.left - 4f
                        val top = win.top - 4f
                        val cx = left + win.width / 2
                        val x =
                            when {
                                cx < sw * 0.37f -> left / s
                                cx > sw * 0.63f -> layout.width - (sw - left) / s
                                else -> layout.width / 2 + (left - sw / 2) / s
                            }
                        SkinRect(x, layout.height / 2 + (top - sh / 2) / s, win.width / s, win.height / s)
                    }
                } else {
                    val panel = placement.panel
                    val unit = panel.width / layout.width
                    placement.windows.map { it.inflate(bezel) }.map { win ->
                        SkinRect(
                            (win.left - panel.left) / unit,
                            (win.top - panel.top) / unit,
                            win.width / unit,
                            win.height / unit,
                        )
                    }
                }
            }
        }

        fun SkinLayout.clear(isLandscape: Boolean): SkinLayout =
            if (fixedExtras || !defaultCustomization.hasScreenChanges || controls.none { it.isExtraButton() }) {
                this
            } else {
                withExtrasPlaced(screensIn(isLandscape))
            }
        return copy(portrait = portrait.clear(false), landscape = landscape.clear(true))
    }

    /**
     * The four extras placed again around the controls where [SkinLayout.defaultCustomization]
     * leaves them (moved and resized), not where the layout first drew them, so Reset never puts one
     * on another button; and clear of [blockers] (the screens, in layout units).
     */
    internal fun SkinLayout.withExtrasPlaced(blockers: List<SkinRect>): SkinLayout {
        if (fixedExtras) return this
        val tuned = defaultCustomization
        val moved =
            controls.mapIndexed {
                    i,
                    c,
                ->
                if (c.isExtraButton()) null else c.withFrame(tuned.frameOf(i, c.frame))
            }.filterNotNull()
        val landscape = width > 500f
        val extras =
            autoExtras(
                moved,
                width,
                height,
                if (landscape) r(0, 0, 58, 24) else r(0, 0, 40, 17),
                if (landscape) 3f else 4f,
                blockers,
                zoneShift = if (landscape && screenArea == null) 106f else 0f,
                // Upright, the player's screens may have been moved down onto the top of the panel: start a little lower.
                topInset = if (landscape) 2f else 12f,
                overlayGame = landscape && screenArea == null,
            )
        var next = 0
        return copy(controls = controls.map { if (it.isExtraButton()) extras[next++] else it })
    }

    /**
     * Tuned layouts make some buttons big (the DS face buttons at 160%): their touch margins then
     * reach the next button. Every control's margin is cut to half the gap to its nearest neighbour,
     * so a press near one button is never taken by another.
     */
    internal fun SkinLayout.withSettledReach(): SkinLayout {
        val c = defaultCustomization.copy(shown = controls.indices.filter { controls[it].defaultHidden }.toSet())
        val frames = controls.mapIndexed { i, ctl -> c.frameOf(i, ctl.frame) }
        return copy(
            controls =
                controls.mapIndexed { i, ctl ->
                    var r = ctl.reach
                    for (j in controls.indices) {
                        if (j == i) continue
                        val a = frames[i]
                        val b = frames[j]
                        val gap =
                            maxOf(
                                b.x - (a.x + a.width),
                                a.x - (b.x + b.width),
                                b.y - (a.y + a.height),
                                a.y - (b.y + b.height),
                            )
                        r = minOf(r, maxOf(0f, gap / 2f))
                    }
                    ctl.withReach(r)
                },
        )
    }

    /**
     * The layouts the player arranged by hand on a phone (2026-11-21), one per console and
     * orientation, as saved customizations in the order of each skin's controls. They are baked
     * into the skins as their starting place, then tidied (see [handTuned]).
     */
    private val HAND_TUNED: Map<String, String> =
        HandTunedLayouts.ALL

    /** A box the tidying moves about, in layout units. */
    private class Box(var x: Float, var y: Float, var w: Float, var h: Float) {
        val cx get() = x + w / 2
        val cy get() = y + h / 2
        val right get() = x + w
    }

    private fun SkinControl.isSmallPill(): Boolean =
        (
            this is SkinControl.Menu ||
                this is SkinControl.Rewind ||
                (this is SkinControl.Button && shape == ButtonShape.PILL)
        ) &&
            frame.width <= 75f && frame.height <= 30f &&
            !(this is SkinControl.Button && label in setOf("SPACE", "RETURN", "STOP"))

    /**
     * Bakes the layout the player arranged ([encoded]) as the skin's own, then tidies what a human
     * hand leaves loose: small buttons standing in a line share one height, one size (the biggest
     * in the line) and the same gap between them (and, across the panel, the same gutter at both
     * ends); a stack shares one centre line. Their names go inside the button or give way to the
     * picture, so the row reads as one.
     */
    private fun SkinLayout.handTuned(encoded: String?): SkinLayout {
        val c = SkinCustomization.decode(encoded ?: "1.0")
        val isLandscape = width > 500f
        val geo = if (isLandscape) SkinGeometry(this, pinToEdges = true, width = 883f, height = 403f) else null
        val cornerCluster = screenArea == null || (screenArea?.x ?: 0f) >= 195f

        // Where the player put the control, in layout units (sideways: as seen on the 883 x 403 dp phone, pinned to its side).
        fun seen(i: Int): SkinRect {
            val g = geo!!
            val p = g.placedFrame(i, controls[i].frame, c)
            val s = g.scale
            val x =
                when {
                    p.center.x < 883f * 0.37f -> p.left / s
                    p.center.x > 883f * 0.63f -> width - (883f - p.left) / s
                    else -> width / 2 + (p.left - 883f / 2) / s
                }
            val fy = p.center.y / 403f
            val y =
                when {
                    cornerCluster && fy < 0.18f -> p.top / s
                    cornerCluster -> height - (403f - p.top) / s
                    fy < 0.16f -> p.top / s
                    fy > 0.86f -> height - (403f - p.top) / s
                    else -> height / 2 + (p.top - 403f / 2) / s
                }
            return SkinRect(x, y, p.width / s, p.height / s)
        }

        val boxes =
            controls.mapIndexed { i, ctl ->
                val moved = i in c.offsets || i in c.scales
                val f =
                    if (!moved) {
                        ctl.frame
                    } else if (isLandscape) {
                        seen(i)
                    } else {
                        c.frameOf(i, ctl.frame)
                    }
                Box(f.x, f.y, f.width, f.height)
            }.toMutableList()
        // A diamond of four round buttons keeps its shape: top over bottom, left level with right, all four the same distance out.
        val rounds =
            controls.indices.filter {
                val k = controls[it]
                k is SkinControl.Button && k.shape == ButtonShape.ROUND && !k.isExtraButton() && k.icon == null
            }
        val fourOfOneSize =
            rounds.size == 4 &&
                rounds.all { kotlin.math.abs(boxes[it].w - boxes[rounds[0]].w) < 0.1f * boxes[rounds[0]].w }
        if (fourOfOneSize) {
            val byY = rounds.sortedBy { boxes[it].cy }
            val top = boxes[byY[0]]
            val bottom = boxes[byY[3]]
            val sides = byY.subList(1, 3).sortedBy { boxes[it].cx }
            val left = boxes[sides[0]]
            val right = boxes[sides[1]]
            val w = rounds.maxOf { boxes[it].w }
            if (kotlin.math.abs(top.cx - bottom.cx) < 0.4f * w && kotlin.math.abs(left.cy - right.cy) < 0.4f * w) {
                val cx = (top.cx + bottom.cx + left.cx + right.cx) / 4
                val cy = (top.cy + bottom.cy + left.cy + right.cy) / 4
                val away = ((bottom.cy - top.cy) / 2 + (right.cx - left.cx) / 2) / 2
                for (b in listOf(top, bottom, left, right)) {
                    b.w = w
                    b.h = w
                }
                top.x = cx - w / 2
                top.y = cy - away - w / 2
                bottom.x = cx - w / 2
                bottom.y = cy + away - w / 2
                left.x = cx - away - w / 2
                left.y = cy - w / 2
                right.x = cx + away - w / 2
                right.y = cy - w / 2
            }
        }
        val small = controls.indices.filter { controls[it].isSmallPill() }
        val inRow = HashSet<Int>()
        val original = boxes.map { Box(it.x, it.y, it.w, it.h) }

        // Rows: small buttons level with each other, then split where they stand far apart.
        val byY = small.sortedBy { boxes[it].cy }
        val rows = mutableListOf<MutableList<Int>>()
        for (i in byY) {
            val row = rows.lastOrNull()
            val rowY = row?.map { boxes[it].cy }?.average()?.toFloat() ?: 0f
            if (row != null && kotlin.math.abs(boxes[i].cy - rowY) <= maxOf(3f, 0.45f * boxes[i].h)) {
                row += i
            } else {
                rows += mutableListOf(i)
            }
        }
        for (row in rows) {
            val sorted = row.sortedBy { boxes[it].x }
            val runs = mutableListOf<MutableList<Int>>()
            for (i in sorted) {
                val last = runs.lastOrNull()
                val maxW = (last?.maxOfOrNull { boxes[it].w } ?: 0f).coerceAtLeast(boxes[i].w)
                if (last != null && boxes[i].x - boxes[last.last()].right <= 1.6f * maxW) {
                    last += i
                } else {
                    runs += mutableListOf(i)
                }
            }
            for (run in runs) {
                if (run.size < 2) continue
                inRow += run
                val cy = run.map { boxes[it].cy }.average().toFloat()
                val w = run.maxOf { boxes[it].w }
                val h = run.maxOf { boxes[it].h }
                for (i in run) {
                    val b = boxes[i]
                    val cx = b.cx
                    b.w = w
                    b.h = h
                    b.x = cx - w / 2
                    b.y = cy - h / 2
                }
                if (run.size >= 3) {
                    val left = boxes[run.first()].x
                    val right = boxes[run.last()].right
                    val wide = (right - left) > 0.6f * width
                    val balanced = kotlin.math.abs(left - (width - right)) < 0.12f * width
                    val even = wide && balanced
                    val gutter =
                        if (even) {
                            (width - run.size * w) / (run.size + 1)
                        } else {
                            (right - left - run.size * w) / (run.size - 1)
                        }
                    val start = if (even) gutter else left
                    run.forEachIndexed { k, i -> boxes[i].x = start + k * (w + gutter) }
                } else if (!isLandscape) {
                    // A pair near the middle of the panel sits exactly on its middle line.
                    val middle = (boxes[run.first()].x + boxes[run.last()].right) / 2
                    if (kotlin.math.abs(middle - width / 2) in 0.5f..14f) {
                        run.forEach { boxes[it].x += width / 2 - middle }
                    }
                }
            }
        }

        // Stacks: the rest, one above the other, share a centre line and (three or more) equal gaps.
        val loose = small.filter { it !in inRow }.sortedBy { boxes[it].cx }
        val columns = mutableListOf<MutableList<Int>>()
        for (i in loose) {
            val col = columns.lastOrNull()
            val colX = col?.map { boxes[it].cx }?.average()?.toFloat() ?: 0f
            if (col != null && kotlin.math.abs(boxes[i].cx - colX) <= 0.4f * boxes[i].w) {
                col += i
            } else {
                columns += mutableListOf(i)
            }
        }
        for (col in columns) {
            if (col.size < 2) continue
            val cx = col.map { boxes[it].cx }.average().toFloat()
            val w = col.maxOf { boxes[it].w }
            val h = col.maxOf { boxes[it].h }
            val sorted = col.sortedBy { boxes[it].y }
            for (i in sorted) {
                val b = boxes[i]
                val cy = b.cy
                b.w = w
                b.h = h
                b.x = cx - w / 2
                b.y = cy - h / 2
            }
            val gaps = (1 until sorted.size).map { boxes[sorted[it]].y - (boxes[sorted[it - 1]].y + h) }
            if (sorted.size >= 3 && gaps.max() <= 1.5f * maxOf(gaps.min(), 1f)) {
                val top = boxes[sorted.first()].y
                val bottom = boxes[sorted.last()].y + h
                val gap = (bottom - top - sorted.size * h) / (sorted.size - 1)
                sorted.forEachIndexed { k, i -> boxes[i].y = top + k * (h + gap) }
            }
        }

        fun Box.hits(o: Box) = x + 0.5f < o.right && o.x + 0.5f < right && y + 0.5f < o.y + o.h && o.y + 0.5f < y + h
        // Last, make it legal: inside the panel, off the screens and the big controls, clear of each other.
        val wall = if (isLandscape && screenArea != null) screenArea else null

        fun clamp(b: Box) {
            b.x = b.x.coerceIn(2f, maxOf(2f, width - 2f - b.w))
            b.y = b.y.coerceIn(2f, maxOf(2f, height - 2f - b.h))
        }
        for (pass in 0 until 3) {
            for (i in small) {
                val b = boxes[i]
                clamp(b)
                if (wall != null) {
                    val gap = 9f
                    val onScreen =
                        b.x < wall.x + wall.width + gap && wall.x - gap < b.right &&
                            b.y < wall.y + wall.height && wall.y < b.y + b.h
                    if (onScreen) {
                        b.x = if (b.cx < wall.x + wall.width / 2) wall.x - gap - b.w else wall.x + wall.width + gap
                        clamp(b)
                    }
                }
                for (j in controls.indices) {
                    if (j == i || j in small) continue
                    val o = boxes[j]
                    if (b.hits(o)) {
                        b.x = if (b.cx < o.cx) o.x - b.w - 1f else o.right + 1f
                        clamp(b)
                    }
                }
            }
            for (a in small) for (c2 in small) {
                if (a < c2 && boxes[a].hits(boxes[c2])) {
                    val first = if (boxes[a].x <= boxes[c2].x) boxes[a] else boxes[c2]
                    val second = if (first === boxes[a]) boxes[c2] else boxes[a]
                    if (kotlin.math.abs(first.cy - second.cy) < first.h) {
                        second.x = first.right + 4f
                    } else {
                        second.y = first.y + first.h + 4f
                    }
                    clamp(second)
                }
            }
        }

        // One size for every small button of the skin (the biggest system button), wherever it fits.
        val system =
            small.filter {
                val k = controls[it]
                k is SkinControl.Menu || (k is SkinControl.Button && (k.keyCode == SELECT || k.keyCode == START))
            }
        if (system.isNotEmpty()) {
            val tw = system.maxOf { boxes[it].w }
            val th = system.maxOf { boxes[it].h }
            for (i in small) {
                val b = boxes[i]
                if (kotlin.math.abs(b.w - tw) < 0.5f && kotlin.math.abs(b.h - th) < 0.5f) continue
                val grown = Box(b.cx - tw / 2, b.cy - th / 2, tw, th)
                val inside =
                    grown.x >= 2f && grown.y >= 2f && grown.right <= width - 2f && grown.y + grown.h <= height - 2f
                val offWall =
                    wall == null ||
                        !(
                            grown.x < wall.x + wall.width + 9f && wall.x - 9f < grown.right &&
                                grown.y < wall.y + wall.height && wall.y < grown.y + grown.h
                        )
                if (inside && offWall && controls.indices.none { it != i && boxes[it].hits(grown) }) {
                    b.x = grown.x
                    b.y = grown.y
                    b.w = tw
                    b.h = th
                }
            }
        }
        // A small button the tidying pushed onto another control goes back to where the player left it.
        for (i in small) {
            val newlyOverlaps =
                controls.indices.any {
                    it != i && it !in small && boxes[it].hits(boxes[i]) && !original[it].hits(original[i])
                }
            if (newlyOverlaps) {
                boxes[i] = original[i]
            }
        }
        val nes = style == SkinStyle.NES_CLASSIC
        val baked =
            controls.mapIndexed { i, ctl ->
                val b = boxes[i]
                var out = ctl.withFrame(SkinRect(b.x, b.y, b.w, b.h))
                val shortNamePill =
                    out is SkinControl.Button &&
                        (
                            out.isExtraButton() ||
                                (out.shape == ButtonShape.PILL && out.icon == null && out.label.length <= 8)
                        )
                if (!nes && shortNamePill && out is SkinControl.Button) {
                    out = out.copy(labelBelow = false)
                }
                out
            }
        return copy(
            controls = baked,
            defaultCustomization =
                c.copy(
                    offsets = emptyMap(),
                    scales = emptyMap(),
                    shown = emptySet(),
                    hidden = emptySet(),
                ),
            captionless = !nes,
            fixedExtras = true,
        ).withSettledReach()
    }

    /** Per-console touches after the general tidying (what the player asked for by name). */
    private fun SkinLayout.adjusted(
        id: TouchControllerID,
        portrait: Boolean,
    ): SkinLayout {
        if (!portrait) return this

        fun idx(match: (SkinControl) -> Boolean) = controls.indexOfFirst(match)

        fun label(
            c: SkinControl,
            vararg names: String,
        ) = c is SkinControl.Button && c.label in names
        val frames = controls.map { it.frame }.toMutableList()
        when (id) {
            // The top four: save, load, fast-forward (optional), rewind (always there) last.
            TouchControllerID.NES -> {
                val rew = idx { it is SkinControl.Rewind }
                val fast = idx { it is SkinControl.Button && it.keyCode == KEYCODE_FAST_FORWARD }
                if (rew >= 0 && fast >= 0) {
                    val a = frames[rew]
                    frames[rew] = SkinRect(frames[fast].x, a.y, a.width, a.height)
                    frames[fast] = SkinRect(a.x, frames[fast].y, frames[fast].width, frames[fast].height)
                }
            }
            // Save and load close together.
            TouchControllerID.PSP -> {
                val save = idx { label(it, "SAVE") }
                val load = idx { label(it, "LOAD") }
                if (save >= 0 && load >= 0) {
                    val s = frames[save]
                    frames[load] = SkinRect(s.x, s.y + s.height + 8f, frames[load].width, frames[load].height)
                }
            }
            // Save, load and fast-forward as one row of three up under the shoulders, level and even, the d-pad and the buttons moved down to make room.
            TouchControllerID.PSX -> {
                val ids = listOf("SAVE", "LOAD", "FAST").map { n -> idx { label(it, n) } }
                if (ids.all { it >= 0 }) {
                    val shoulders =
                        frames.indices.filter { (controls[it] as? SkinControl.Button)?.shape == ButtonShape.SHOULDER }
                    val shoulderBottom = shoulders.maxOf { frames[it].y + frames[it].height }
                    val shift = 14f
                    frames.indices.filter {
                        controls[it] is SkinControl.Dpad ||
                            (
                                (controls[it] as? SkinControl.Button)?.shape == ButtonShape.ROUND &&
                                    !controls[it].isExtraButton()
                            )
                    }.forEach {
                        frames[it] = SkinRect(frames[it].x, frames[it].y + shift, frames[it].width, frames[it].height)
                    }
                    val w = 40f
                    val h = 17f
                    val gutter = (width - 3 * w) / 4
                    ids.forEachIndexed {
                            k,
                            i,
                        ->
                        frames[i] = SkinRect(gutter + k * (w + gutter), shoulderBottom + 6f, w, h)
                    }
                }
            }
            else -> return this
        }
        return copy(controls = controls.mapIndexed { i, c -> c.withFrame(frames[i]) }).withSettledReach()
    }

    private fun ArcaduneSkin.handTuned(id: TouchControllerID): ArcaduneSkin {
        return copy(
            portrait =
                (
                    if (portrait.fixedExtras) {
                        portrait
                    } else {
                        portrait.handTuned(
                            HAND_TUNED["${id}_portrait"],
                        )
                    }
                ).adjusted(id, portrait = true),
            landscape =
                if (landscape.fixedExtras) {
                    landscape
                } else {
                    landscape.handTuned(
                        HAND_TUNED["${id}_landscape"],
                    )
                },
        )
    }

    private fun SkinControl.withReach(reach: Float): SkinControl =
        when (this) {
            is SkinControl.Dpad -> copy(reach = reach)
            is SkinControl.Stick -> copy(reach = reach)
            is SkinControl.Button -> copy(reach = reach)
            is SkinControl.Menu -> copy(reach = reach)
            is SkinControl.Rewind -> copy(reach = reach)
        }

    private fun SkinControl.withFrame(frame: SkinRect): SkinControl =
        when (this) {
            is SkinControl.Dpad -> copy(frame = frame)
            is SkinControl.Stick -> copy(frame = frame)
            is SkinControl.Button -> copy(frame = frame)
            is SkinControl.Menu -> copy(frame = frame)
            is SkinControl.Rewind -> copy(frame = frame)
        }

    /** The DS sideways: both screens side by side across the top, the controls under them. */
    private val DS_SIDEWAYS_SCREENS = SkinRect(70f, 0f, 672f, 232f)

    /** Where the screens go in a landscape shell: the full height between the two 150-unit wings. */
    private val SHELL_SCREENS = SkinRect(150f, 6f, 512f, 363f)

    /**
     * The small buttons of a landscape shell, at the bottom of the wings so the screens can take the
     * whole height: menu and select on the left, start and rewind on the right.
     */
    private fun landscapeShellBand(
        select: String? = "SELECT",
        start: String = "START",
        rewind: Boolean = true,
    ): List<SkinControl> {
        val y = 334
        return listOfNotNull(
            SkinControl.Menu(r(14, y, 58, 26), reach = 3f),
            select?.let { pill(SELECT, it, r(78, y, 58, 26), reach = 3f) },
            pill(START, start, if (rewind) r(676, y, 58, 26) else r(708, y, 58, 26), reach = 3f),
            if (rewind) SkinControl.Rewind(r(740, y, 58, 26), reach = 3f) else null,
        )
    }

    /**
     * Sideways, full screen (the way Delta solves it): the game takes the whole height, centred, and
     * the controls float over the black sides and the game's edges, where the thumbs rest when the
     * phone is held sideways: a big d-pad low in the left corner, the face buttons low in the right
     * one, shoulders in the top corners, menu / rewind top centre, select / start bottom centre.
     */
    private fun fullScreen(
        palette: SkinPalette,
        controls: List<SkinControl>,
    ) = landscape(controls, palette)

    /**
     * Sideways, for the small, nearly square handheld screens (Game Boy, Game Gear, Neo Geo Pocket),
     * the way Delta frames its Game Boy Color: the console's body from top to bottom, the screen in
     * the top middle (as big as the corners allow), the d-pad and buttons low in the corners and
     * select / start under the screen.
     */
    private fun framedHandheld(
        palette: SkinPalette,
        controls: List<SkinControl>,
    ) = landscape(controls, palette, FRAMED_SCREEN)

    /**
     * Sideways for the wide pictures (3DS, DS, PSP): the screens between two slim 120-unit wings,
     * the full height, so the main screen is as big as the width allows (a wide picture's height
     * is set by the width it gets).
     */
    private val SLIM_WING_SCREENS = SkinRect(106f, 4f, 600f, 367f)

    /** The small buttons of the slim wings, at their bottom: menu and select left, start right. */
    private fun slimWingBand(select: String? = "SELECT") =
        listOfNotNull(
            SkinControl.Menu(r(6, 336, 46, 26), reach = 3f),
            select?.let { pill(SELECT, it, r(56, 336, 46, 26), reach = 3f) },
            pill(START, "START", r(738, 336, 60, 26), reach = 3f),
        )

    /** How far in from each side the DS screens sit upright: room for the L and R tabs beside them. */
    private const val DS_UPRIGHT_SIDE = 0.08f

    // The whole height: nothing goes under the screen, so it is as big as the phone allows.
    private val FRAMED_SCREEN = SkinRect(200f, 4f, 412f, 367f)

    /** Menu and rewind top left; select and start on the right, above the face buttons. */
    private fun framedHandheldBand(
        select: String? = "SELECT",
        start: String = "START",
    ) = listOfNotNull(
        SkinControl.Menu(r(28, 18, 66, 24)),
        SkinControl.Rewind(r(112, 18, 66, 24)),
        select?.let { pill(SELECT, it, r(646, 140, 64, 26)) },
        pill(START, start, if (select == null) r(682, 140, 64, 26) else r(718, 140, 64, 26)),
    )

    // About 40% of the height, low in the left corner, where the left thumb rests.
    private val cornerDpad get() = SkinControl.Dpad(r(28, 200, 150))
    private val cornerShoulderL get() = shoulder(L, "L", r(16, 14, 110, 40))
    private val cornerShoulderR get() = shoulder(R, "R", r(686, 14, 110, 40))

    /** Two big face buttons low in the right corner: [first] lower and left (B), [second] higher and right (A). */
    private fun cornerTwoButtons(
        first: Pair<Int, String>,
        second: Pair<Int, String>,
    ) = listOf(round(first.first, first.second, r(632, 280, 74)), round(second.first, second.second, r(712, 220, 74)))

    // Sideways shells: the controls on two 150-unit wings and in the band under the screens, so
    // nothing sits on the game. Shared pieces first, then each console.
    private val wingShoulderL get() = shoulder(L, "L", r(10, 8, 128, 36))
    private val wingShoulderR get() = shoulder(R, "R", r(674, 8, 128, 36))

    private fun shell(
        palette: SkinPalette,
        controls: List<SkinControl>,
        optionalControls: List<SkinControl>? = null,
    ) = landscape(controls, palette, SHELL_SCREENS, optionalControls)

    /** The sideways shell of [id], or null when it has none of its own (DS, 3DS: built with their skin; DOS). */
    private fun shellLandscape(
        id: TouchControllerID,
        palette: SkinPalette,
    ): SkinLayout? =
        when (id) {
            TouchControllerID.NES ->
                fullScreen(
                    palette,
                    listOf(cornerDpad) + cornerTwoButtons(B to "B", A to "A") + landscapeCommon(),
                )
            // The small, nearly square handheld screens: framed in the console's body (Delta's GBC way).
            TouchControllerID.GB ->
                framedHandheld(
                    palette,
                    listOf(cornerDpad) + cornerTwoButtons(B to "B", A to "A") + framedHandheldBand(),
                )
            TouchControllerID.GG ->
                framedHandheld(
                    palette,
                    listOf(cornerDpad) + cornerTwoButtons(B to "1", A to "2") + framedHandheldBand(select = null),
                )
            TouchControllerID.NGP ->
                framedHandheld(
                    palette,
                    listOf(cornerDpad) +
                        cornerTwoButtons(B to "A", A to "B") +
                        framedHandheldBand(select = null, start = "OPTION"),
                )
            TouchControllerID.GBA ->
                fullScreen(
                    palette,
                    listOf(cornerShoulderL, cornerShoulderR, cornerDpad) +
                        cornerTwoButtons(B to "B", A to "A") +
                        // A and B with one tap, under A (as on Delta's GBA skin).
                        SkinControl.Button(A, "AB", ButtonShape.PILL, r(716, 304, 66, 38), alsoKey = B) +
                        landscapeCommon(),
                )
            TouchControllerID.SNES ->
                fullScreen(
                    palette,
                    listOf(cornerShoulderL, cornerShoulderR, cornerDpad) +
                        nintendoFace(700f, 268f, 54f) +
                        landscapeCommon(),
                )
            TouchControllerID.SMS ->
                fullScreen(
                    palette,
                    listOf(cornerDpad) + cornerTwoButtons(B to "1", A to "2") + landscapeCommon(select = null),
                )
            TouchControllerID.PCE ->
                fullScreen(
                    palette,
                    listOf(cornerDpad) + cornerTwoButtons(B to "I", A to "II") + landscapeCommon(start = "RUN"),
                )
            TouchControllerID.WS_LANDSCAPE ->
                fullScreen(
                    palette,
                    listOf(cornerDpad) + cornerTwoButtons(B to "B", A to "A") + landscapeCommon(select = null),
                )
            TouchControllerID.WS_PORTRAIT ->
                fullScreen(
                    palette,
                    listOf(cornerDpad) +
                        diamond(700f, 268f, 54f, Y to "X1", B to "X4", X to "X2", A to "X3") +
                        landscapeCommon(select = null),
                )
            // Genesis keys follow the existing mapping: A = Y, B = B, C = A (6-button: X = L1, Y = X, Z = R1).
            TouchControllerID.GENESIS_3 ->
                fullScreen(
                    palette,
                    listOf(
                        cornerDpad,
                        round(Y, "A", r(592, 288, 60)),
                        round(B, "B", r(660, 254, 60)),
                        round(A, "C", r(728, 220, 60)),
                    ) + landscapeCommon(select = null),
                )
            TouchControllerID.GENESIS_6 ->
                fullScreen(
                    palette,
                    listOf(
                        cornerDpad,
                        round(L, "X", r(614, 214, 56), reach = 3f),
                        round(X, "Y", r(674, 214, 56), reach = 3f),
                        round(R, "Z", r(734, 214, 56), reach = 3f),
                        round(Y, "A", r(614, 280, 56), reach = 3f),
                        round(B, "B", r(674, 280, 56), reach = 3f),
                        round(A, "C", r(734, 280, 56), reach = 3f),
                    ) + landscapeCommon(select = null),
                )
            TouchControllerID.PSX, TouchControllerID.PSX_DUALSHOCK -> {
                val shoulders =
                    listOf(
                        shoulder(L, "L1", r(10, 8, 64, 36)),
                        shoulder(L2, "L2", r(78, 8, 60, 36)),
                        shoulder(R2, "R2", r(674, 8, 60, 36)),
                        shoulder(R, "R1", r(738, 8, 64, 36)),
                    )
                val pad =
                    if (id == TouchControllerID.PSX_DUALSHOCK) {
                        listOf(
                            separateDpad(r(20, 70, 110)),
                            SkinControl.Stick(r(42, 216, 70), LEFT_STICK, L3),
                            SkinControl.Stick(r(700, 238, 70), RIGHT_STICK, R3),
                        ) + psSymbols(737f, 150f, 44f)
                    } else {
                        listOf(separateDpad(r(14, 96, 124))) + psSymbols(737f, 166f, 48f)
                    }
                shell(palette, shoulders + pad + landscapeShellBand(rewind = false))
            }
            // PSP: its wide picture gets the slim wings (like the 3DS), so it is as big as it can be.
            TouchControllerID.PSP ->
                landscape(
                    listOf(
                        shoulder(L, "L", r(4, 6, 98, 32)),
                        shoulder(R, "R", r(710, 6, 98, 32)),
                        separateDpad(r(6, 64, 94)),
                        SkinControl.Stick(r(30, 220, 60), LEFT_STICK),
                    ) + psSymbols(760f, 172f, 32f) + slimWingBand(),
                    palette,
                    SLIM_WING_SCREENS,
                )
            // N64: the stick is the main control, the small d-pad and Z under it; the C buttons above B and A.
            TouchControllerID.N64 ->
                shell(
                    palette,
                    listOf(
                        wingShoulderL,
                        wingShoulderR,
                        SkinControl.Stick(r(14, 66, 124), LEFT_STICK),
                        SkinControl.Dpad(r(24, 222, 64), reach = 6f),
                        round(L2, "Z", r(96, 232, 46)),
                        SkinControl.Dpad(r(694, 54, 100), reach = 6f, source = RIGHT_STICK, look = DpadLook.C_BUTTONS),
                        round(Y, "B", r(668, 178, 52)),
                        round(B, "A", r(732, 204, 62)),
                    ) + landscapeShellBand(select = null, rewind = false),
                )
            TouchControllerID.ATARI2600 ->
                shell(
                    palette,
                    listOf(
                        ballTop(r(14, 80, 124)),
                        pill(L, "DIFF A", r(20, 252, 52, 24), reach = 3f),
                        pill(L2, "DIFF B", r(80, 252, 52, 24), reach = 3f),
                        round(B, "", r(686, 120, 100)),
                    ) + landscapeShellBand(start = "RESET"),
                )
            TouchControllerID.ATARI7800 ->
                fullScreen(
                    palette,
                    listOf(ballTop(r(28, 200, 150))) + cornerTwoButtons(B to "1", A to "2") + landscapeCommon(),
                )
            TouchControllerID.LYNX ->
                fullScreen(
                    palette,
                    listOf(
                        cornerDpad,
                        pill(L, "OPTION 1", r(28, 18, 66, 24)),
                        pill(R, "OPTION 2", r(112, 18, 66, 24)),
                    ) + cornerTwoButtons(B to "B", A to "A") + landscapeCommon(select = null, start = "PAUSE"),
                )
            TouchControllerID.ARCADE_4 ->
                shell(
                    palette,
                    listOf(
                        ballTop(r(14, 90, 124), ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK),
                        round(Y, "C", r(676, 104, 54)),
                        round(X, "D", r(742, 96, 54)),
                        round(B, "A", r(676, 170, 54)),
                        round(A, "B", r(742, 162, 54)),
                    ) + landscapeShellBand(select = "COIN", rewind = false),
                )
            TouchControllerID.ARCADE_6 ->
                shell(
                    palette,
                    listOf(
                        ballTop(r(14, 90, 124), ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK),
                        round(Y, "", r(666, 110, 42), reach = 3f),
                        round(X, "", r(712, 104, 42), reach = 3f),
                        round(L, "", r(758, 104, 42), reach = 3f),
                        round(B, "", r(666, 166, 42), reach = 3f),
                        round(A, "", r(712, 160, 42), reach = 3f),
                        round(R, "", r(758, 160, 42), reach = 3f),
                    ) + landscapeShellBand(select = "COIN", rewind = false),
                )
            TouchControllerID.DREAMCAST ->
                shell(
                    palette,
                    listOf(
                        shoulder(L2, "L", r(10, 8, 128, 36)),
                        shoulder(R2, "R", r(674, 8, 128, 36)),
                        SkinControl.Stick(r(18, 60, 114), LEFT_STICK),
                        SkinControl.Dpad(r(36, 196, 78), reach = 6f),
                    ) + diamond(737f, 166f, 46f, X to "Y", Y to "X", A to "B", B to "A") +
                        landscapeShellBand(select = null, rewind = false),
                )
            TouchControllerID.SATURN, TouchControllerID.SATURN_3D -> {
                val leftControls =
                    if (id == TouchControllerID.SATURN_3D) {
                        listOf(
                            SkinControl.Stick(r(18, 60, 114), LEFT_STICK),
                            SkinControl.Dpad(r(36, 196, 78), reach = 6f),
                        )
                    } else {
                        listOf(SkinControl.Dpad(r(14, 96, 122), reach = 10f))
                    }
                shell(
                    palette,
                    listOf(
                        shoulder(L2, "L", r(10, 8, 128, 36)),
                        shoulder(R2, "R", r(674, 8, 128, 36)),
                    ) + leftControls +
                        listOf(
                            round(Y, "X", r(666, 112, 40), reach = 2.5f),
                            round(X, "Y", r(714, 102, 40), reach = 2.5f),
                            round(R, "Z", r(762, 92, 40), reach = 2.5f),
                            round(B, "A", r(665, 170, 42), reach = 2.5f),
                            round(A, "B", r(713, 160, 42), reach = 2.5f),
                            round(L, "C", r(761, 150, 42), reach = 2.5f),
                        ) + landscapeShellBand(select = null, rewind = false),
                )
            }
            TouchControllerID.THREEDO ->
                fullScreen(
                    palette,
                    listOf(
                        cornerShoulderL,
                        cornerShoulderR,
                        cornerDpad,
                        round(Y, "A", r(608, 244, 58)),
                        round(B, "B", r(674, 216, 58)),
                        round(A, "C", r(740, 188, 58)),
                    ) + landscapeCommon(select = "SELECT/X", start = "START/P", rewind = false),
                )
            TouchControllerID.ATARI5200 ->
                shell(
                    palette,
                    listOf(
                        SkinControl.Stick(r(16, 76, 118), LEFT_STICK),
                        pill(L, "KEYPAD", r(36, 232, 76, 26), reach = 4f),
                        round(B, "2", r(674, 168, 58)),
                        round(A, "1", r(738, 104, 58)),
                    ) + landscapeShellBand(select = "PAUSE", start = "START", rewind = true),
                )
            TouchControllerID.COLECOVISION ->
                shell(
                    palette,
                    listOf(
                        ballTop(r(14, 88, 122), ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK),
                        round(B, "L", r(674, 84, 56)),
                        round(A, "R", r(742, 84, 56)),
                        round(Y, "1", r(686, 168, 42), reach = 3f),
                        round(X, "2", r(742, 168, 42), reach = 3f),
                        round(L, "3", r(686, 220, 42), reach = 3f),
                        round(R, "4", r(742, 220, 42), reach = 3f),
                    ) + landscapeShellBand(select = "#", start = "*", rewind = true),
                )
            TouchControllerID.ATARI800 ->
                shell(
                    palette,
                    listOf(
                        ballTop(r(14, 72, 122), ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK),
                        pill(L3, "KEYB", r(14, 232, 58, 24), reach = 3f),
                        pill(L, "OPTION", r(78, 232, 58, 24), reach = 3f),
                        round(B, "1", r(674, 134, 58)),
                        round(A, "2", r(738, 76, 58)),
                        SkinControl.Button(Y, "SPACE", ButtonShape.PILL, r(686, 220, 104, 28), reach = 4f),
                    ) + landscapeShellBand(select = "SELECT", start = "START", rewind = false),
                )
            TouchControllerID.C64 ->
                shell(
                    palette,
                    listOf(
                        ballTop(r(14, 72, 122), ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK),
                        pill(L2, "STOP", r(14, 232, 58, 24), reach = 3f),
                        pill(R2, "RETURN", r(78, 232, 58, 24), reach = 3f),
                        round(B, "FIRE", r(692, 92, 90)),
                        SkinControl.Button(X, "SPACE", ButtonShape.PILL, r(684, 212, 106, 28), reach = 4f),
                    ) + landscapeShellBand(select = "KEYB", start = "START", rewind = false),
                )
            TouchControllerID.APPLEII ->
                shell(
                    palette,
                    listOf(
                        SkinControl.Stick(r(16, 86, 118), ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK),
                        round(B, "1", r(674, 168, 58)),
                        round(A, "0", r(738, 104, 58)),
                    ) + landscapeShellBand(select = "SELECT", start = "START", rewind = false),
                )
            TouchControllerID.MSX ->
                fullScreen(
                    palette,
                    listOf(cornerDpad) +
                        cornerTwoButtons(B to "B", A to "A") +
                        SkinControl.Button(Y, "SPACE", ButtonShape.PILL, r(716, 304, 68, 34), reach = 4f) +
                        landscapeCommon(select = "F2", start = "F1", rewind = true),
                )
            else -> null
        }

    /** A portrait panel in a console's colours. DS and 3DS use a shorter one, so both screens stay big. */
    private fun console(
        palette: SkinPalette,
        controls: List<SkinControl>,
        height: Float = PH,
        optionalControls: List<SkinControl>? = null,
    ) = SkinLayout(
        PW,
        height,
        SkinStyle.CONSOLE,
        controls + (optionalControls ?: portraitExtras(controls, height)),
        palette,
    )

    private const val L2 = KeyEvent.KEYCODE_BUTTON_L2
    private const val R2 = KeyEvent.KEYCODE_BUTTON_R2
    private const val L3 = KeyEvent.KEYCODE_BUTTON_THUMBL
    private const val R3 = KeyEvent.KEYCODE_BUTTON_THUMBR
    private const val LEFT_STICK = ComposeTouchLayouts.MOTION_SOURCE_LEFT_STICK
    private const val RIGHT_STICK = ComposeTouchLayouts.MOTION_SOURCE_RIGHT_STICK

    private fun separateDpad(frame: SkinRect) = SkinControl.Dpad(frame, look = DpadLook.SEPARATE)

    // PlayStation symbols: triangle = X, circle = A, cross = B, square = Y, like the other PlayStation controls.
    private const val TRIANGLE = "△"
    private const val CIRCLE = "○"
    private const val CROSS = "✕"
    private const val SQUARE = "□"

    /** PlayStation: the grey pad with its four symbols, two shoulders on each side, and (DualShock) two sticks. */
    private fun playStation(dualShock: Boolean): ArcaduneSkin {
        val shoulders =
            listOf(
                shoulder(L, "L1", r(0, 0, 76, 26)),
                shoulder(L2, "L2", r(80, 0, 58, 26)),
                shoulder(R2, "R2", r(182, 0, 58, 26)),
                shoulder(R, "R1", r(244, 0, 76, 26)),
            )
        val portraitControls =
            if (dualShock) {
                listOf(
                    separateDpad(r(12, 40, 96)),
                    SkinControl.Stick(r(96, 148, 50), LEFT_STICK, L3),
                    SkinControl.Stick(r(174, 148, 50), RIGHT_STICK, R3),
                ) + psSymbols(252f, 96f, 44f)
            } else {
                listOf(separateDpad(r(14, 50, 120))) + psSymbols(244f, 114f, 50f)
            }
        val landscapeSticks =
            if (dualShock) {
                listOf(
                    SkinControl.Stick(r(212, 258, 92), LEFT_STICK, L3),
                    SkinControl.Stick(r(508, 258, 92), RIGHT_STICK, R3),
                )
            } else {
                emptyList()
            }
        return ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.PLAYSTATION,
                    shoulders + portraitControls + portraitBottomRow(rewind = false),
                ),
            landscape =
                landscape(
                    listOf(
                        separateDpad(r(30, 150, 150)),
                        round(X, TRIANGLE, r(660, 128, 62)),
                        round(Y, SQUARE, r(598, 190, 62)),
                        round(A, CIRCLE, r(722, 190, 62)),
                        round(B, CROSS, r(660, 252, 62)),
                        shoulder(L, "L1", r(16, 16, 100, 40)),
                        shoulder(L2, "L2", r(122, 16, 76, 40)),
                        shoulder(R2, "R2", r(614, 16, 76, 40)),
                        shoulder(R, "R1", r(696, 16, 100, 40)),
                    ) + landscapeSticks + landscapeCommon(rewind = false),
                    SkinPalettes.PLAYSTATION,
                ),
        )
    }

    /** PSP: the black handheld, silver d-pad, analog nub under it, long L and R on top. */
    private fun psp() =
        ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.PSP,
                    listOf(
                        shoulder(L, "L", r(0, 0, 112, 26)),
                        shoulder(R, "R", r(208, 0, 112, 26)),
                        separateDpad(r(16, 42, 100)),
                        SkinControl.Stick(r(46, 158, 40), LEFT_STICK),
                    ) + psSymbols(250f, 100f, 46f) + portraitBottomRow(rewind = false),
                ),
            landscape =
                landscape(
                    listOf(
                        separateDpad(r(30, 120, 150)),
                        SkinControl.Stick(r(70, 282, 76), LEFT_STICK),
                        round(X, TRIANGLE, r(660, 128, 62)),
                        round(Y, SQUARE, r(598, 190, 62)),
                        round(A, CIRCLE, r(722, 190, 62)),
                        round(B, CROSS, r(660, 252, 62)),
                        shoulder(L, "L", r(16, 16, 120, 40)),
                        shoulder(R, "R", r(676, 16, 120, 40)),
                    ) + landscapeCommon(rewind = false),
                    SkinPalettes.PSP,
                ),
        )

    /**
     * N64: the grey pad with the stick as the main control, a small d-pad above it, blue A, green
     * B, the four yellow C buttons (the right stick moved in 4 steps) and the red Start.
     * Keys follow the other N64 controls: A = B, B = Y, Z = L2.
     */
    private fun n64() =
        ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.N64,
                    listOf(
                        shoulder(L, "L", r(0, 0, 80, 26)),
                        shoulder(R, "R", r(240, 0, 80, 26)),
                        // The small d-pad and the C buttons reach less far: they sit close to other controls.
                        SkinControl.Dpad(r(12, 40, 52), reach = 6f),
                        SkinControl.Stick(r(24, 100, 96), LEFT_STICK),
                        round(L2, "Z", r(136, 140, 40)),
                        round(START, "START", r(146, 42, 30), labelBelow = true),
                        round(Y, "B", r(180, 96, 44)),
                        round(B, "A", r(222, 142, 54)),
                        SkinControl.Dpad(r(236, 36, 80), reach = 6f, source = RIGHT_STICK, look = DpadLook.C_BUTTONS),
                    ) + portraitBottomRow(select = null, start = null, rewind = false),
                ),
            landscape =
                landscape(
                    listOf(
                        SkinControl.Stick(r(32, 166, 156), LEFT_STICK),
                        SkinControl.Dpad(r(214, 262, 86)),
                        round(L2, "Z", r(216, 170, 62)),
                        round(Y, "B", r(588, 198, 64)),
                        round(B, "A", r(650, 262, 76)),
                        SkinControl.Dpad(r(672, 96, 120), source = RIGHT_STICK, look = DpadLook.C_BUTTONS),
                        shoulder(L, "L", r(16, 16, 104, 40)),
                        shoulder(R, "R", r(692, 16, 104, 40)),
                    ) + landscapeCommon(select = null, rewind = false),
                    SkinPalettes.N64,
                ),
        )

    /**
     * Nintendo DS: the white DS Lite. Upright, the panel is shorter so both screens stay big; menu, start
     * and select sit along the bottom. Sideways, the screens sit across
     * the top, L on the left wing and Swap on the right. No rewind: the app
     * cannot rewind the DS. No microphone button either: as in Delta, the phone's microphone simply
     * listens while the game runs (the DS core is set to "always" in GameSystem).
     */
    private fun ds(): ArcaduneSkin {
        val row4 = gutterXs(4)
        val row3 = gutterXs(3)
        return ArcaduneSkin(
            // Upright the way Delta's standard DS skin is laid out: the body behind everything,
            // both screens in one recessed panel with L and R as tabs either side of the touch screen,
            // a big d-pad and big X / Y / A / B,
            // and menu, start and select along the bottom, start in the middle.
            portrait =
                console(
                    SkinPalettes.DS,
                    listOf(
                        // L and R as upright tabs either side of the touch screen, like Delta's DS:
                        // above the controller (negative y), in the margins the screens leave, low
                        // enough to sit beside the touch screen on every phone shape.
                        shoulder(L, "L", r(3, -74, 19, 62)).copy(reach = 2f),
                        shoulder(R, "R", r(298, -74, 19, 62)).copy(reach = 2f),
                        SkinControl.Dpad(r(8, 20, 130), reach = 10f),
                    ) + diamondAt(246f, 88f, 48f, 45f, X to "X", Y to "Y", A to "A", B to "B") +
                        listOf(
                            // The small buttons all one size, a picture or their name on them: a row
                            // of menu, select, start and exit, and save, load and fast-forward under it.
                            SkinControl.Menu(r(row4[0], 168, 40, 17), reach = 3f),
                            flatPill(SELECT, "SELECT", r(row4[1], 168, 40, 17), 3f),
                            flatPill(START, "START", r(row4[2], 168, 40, 17), 3f),
                        ),
                    height = 214f,
                    optionalControls =
                        fixedExtraButtons(
                            fast = r(row3[2], 192, 40, 17),
                            exit = r(row4[3], 168, 40, 17),
                            save = r(row3[0], 192, 40, 17),
                            load = r(row3[1], 192, 40, 17),
                            reach = 3f,
                        ),
                ).copy(
                    uprightScreenSide = DS_UPRIGHT_SIDE,
                    screenPanelBelow = 6f,
                    captionless = true,
                    fixedExtras = true,
                )
                    .withDefaults(DS_PORTRAIT_TUNED).withSettledReach(),
            // Sideways like the 3DS: slim wings, a big main screen with the other one small at its
            // lower right (Swap trades them), L and the d-pad on the left wing, R, Swap,
            // and X / Y / A / B on the right one.
            landscape =
                landscape(
                    listOf(
                        shoulder(L, "L", r(4, 6, 98, 32)),
                        shoulder(R, "R", r(710, 6, 98, 32)),
                        SkinControl.Dpad(r(5, 160, 96), reach = 6f),
                        round(KEYCODE_SWAP_SCREENS, "SWAP", r(714, 54, 36), icon = SkinIcon.SWAP, reach = 2f),
                    ) + diamondAt(760f, 214f, 32f, 33f, X to "X", Y to "Y", A to "A", B to "B") +
                        listOf(
                            // Menu and exit together on the left, select and start on the right.
                            SkinControl.Menu(r(10, 346, 50, 22), reach = 3f),
                            flatPill(SELECT, "SELECT", r(696, 346, 50, 22), 3f),
                            flatPill(START, "START", r(752, 346, 50, 22), 3f),
                        ),
                    SkinPalettes.DS,
                    SLIM_WING_SCREENS,
                    optionalControls =
                        fixedExtraButtons(
                            fast = r(8, 84, 50, 22),
                            exit = r(66, 346, 50, 22),
                            save = r(8, 52, 50, 22),
                            load = r(66, 52, 50, 22),
                            reach = 3f,
                        ),
                ).copy(captionless = true, fixedExtras = true).withDefaults(DS_LANDSCAPE_TUNED).withSettledReach(),
        )
    }

    // The DS and 3DS layouts as the maker tuned them on a phone (screens, buttons, background):
    // where a new player starts. Everything stays editable; Reset comes back here.
    private const val DS_PORTRAIT_TUNED =
        "1.0;ss=1.15;so=-1.13028065E-7:0.02758789;ss1=0.9026235;so1=0.0:0.020141602;" +
            "0:5.1562505:2.9669868E-6;1:-6.4331393:2.9669868E-6;s0=1.3;s1=1.3"
    private const val DS_LANDSCAPE_TUNED =
        "1.0;ss=1.1421564;so=0.0592041:0.0;ss1=0.71829206;so1=0.095214844:-0.42615378;" +
            "3:-67.27749:-26.906977;2:9.388709:-14.879306;5:-62.27748:51.502384;7:-52.120354:69.70162;" +
            "4:-52.120354:37.322453;6:-36.842728:51.502384;1:-10.268363:1.8190217E-6;" +
            "s3=1.15;s2=1.2;s7=1.6;s6=1.6;s5=1.6;s4=1.6"

    // The 3DS as tuned on 2026-11-20: screens, sticks, shoulders and face buttons. The small buttons
    // (menu, select, start, and the four extras) are not in it: they are placed in the layout itself,
    // all one size, with no names under them.
    private const val N3DS_PORTRAIT_TUNED =
        "1.0;so=0.0:0.0513916;ss1=0.80572915;so1=0.0:0.019042969;bg=0.041943215;" +
            "0:0.0:-22.712059;1:0.0:-22.712059;2:-3.5999994:-22.712059;3:0.0:-22.712059;" +
            "4:1.0000051:-12.484267;7:-1.1867947E-5:63.60938;14:0.0:11.823644;11:0.0:8.115735;" +
            "12:0.0:10.065325;13:0.0:10.065325;6:-44.99998:46.1303;5:-5.048803:-6.57635;" +
            "s5=1.05;s4=1.05"
    private const val N3DS_LANDSCAPE_TUNED =
        "1.0;ss=1.1180903;so=0.060337633:-0.045776367;so1=0.12060547:-0.38513184;" +
            "7:-13.457617:-207.43091;4:5.9999995:15.110787;11:-13.457617:112.825035;" +
            "9:-11.625557:104.74486;8:-13.457617:97.833664;10:-10.5999775:104.74486;" +
            "1:14.91135:1.8190217E-6;0:7.390605:0.0;" +
            "s4=1.3226775;s10=1.3;s11=1.3;s9=1.3;s8=1.3;s7=1.15"

    /** A small pill with its name on it (not under it), the size of the buttons next to it. */
    private fun flatPill(
        key: Int,
        label: String,
        frame: SkinRect,
        reach: Float,
    ) = SkinControl.Button(key, label, ButtonShape.PILL, frame, reach = reach)

    /** The four extra buttons at the places given, picture only (no names), in the order fast-forward, exit, save, load. */
    private fun fixedExtraButtons(
        fast: SkinRect,
        exit: SkinRect,
        save: SkinRect,
        load: SkinRect,
        reach: Float,
    ) = listOf(
        optionalButton(KEYCODE_FAST_FORWARD, "FAST", SkinIcon.FAST_FORWARD, fast, reach, captioned = false),
        optionalButton(KEYCODE_EXIT, "EXIT", SkinIcon.EXIT, exit, reach, captioned = false, hidden = false),
        optionalButton(KEYCODE_QUICK_SAVE, "SAVE", SkinIcon.QUICK_SAVE, save, reach, captioned = false),
        optionalButton(KEYCODE_QUICK_LOAD, "LOAD", SkinIcon.QUICK_LOAD, load, reach, captioned = false),
    )

    /**
     * Nintendo 3DS (New 3DS): the Circle Pad over the d-pad, the coloured buttons, the C-Stick (the
     * right stick), L / ZL / ZR / R, Select and Start, and Swap. Citra reads ZL / ZR as L2 / R2.
     * No Home button: Citra's Home (L3) only swaps the screens, which Swap already does (and Swap
     * also moves the shell's windows). Sideways: Circle Pad and d-pad on the left wing, C-Stick,
     * buttons and Swap on the right one.
     */
    private fun n3ds(): ArcaduneSkin {
        val row4 = gutterXs(4)
        // Every small button is one size, picture or name on it and nothing under it.
        val pw = 40
        val ph = 17
        val lw = 50
        val lh = 22
        return ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.N3DS,
                    listOf(
                        shoulder(L, "L", r(0, 0, 74, 24)),
                        shoulder(L2, "ZL", r(78, 0, 48, 24)),
                        shoulder(R2, "ZR", r(194, 0, 48, 24)),
                        shoulder(R, "R", r(246, 0, 74, 24)),
                        SkinControl.Stick(r(24, 32, 86), LEFT_STICK),
                        // Small, under the Circle Pad and above the menu: it reaches less far.
                        SkinControl.Dpad(r(36, 124, 64), reach = 6f),
                        SkinControl.Stick(r(190, 32, 30), RIGHT_STICK),
                        round(KEYCODE_SWAP_SCREENS, "SWAP", r(145, 65, 30), icon = SkinIcon.SWAP),
                        // One row along the bottom: menu, select, start, exit.
                        SkinControl.Menu(r(row4[0], 196, pw, ph), reach = 6f),
                        flatPill(SELECT, "SELECT", r(row4[1], 196, pw, ph), 6f),
                        flatPill(START, "START", r(row4[2], 196, pw, ph), 6f),
                    ) + nintendoFace(256f, 96f, 42f),
                    height = 232f,
                    // Save over load between the shoulders; exit ends the bottom row.
                    optionalControls =
                        fixedExtraButtons(
                            fast = r(190, 8.7f, pw, ph),
                            exit = r(row4[3], 196, pw, ph),
                            save = r(140, 8.7f, pw, ph),
                            load = r(140, 40.7f, pw, ph),
                            reach = 4f,
                        ),
                ).copy(captionless = true, fixedExtras = true).withDefaults(N3DS_PORTRAIT_TUNED).withSettledReach(),
            // Landscape: slim wings, so the big main screen takes as much of the width as it can (the
            // 3DS picture is wide: its height is set by the width it gets).
            landscape =
                landscape(
                    listOf(
                        shoulder(L, "L", r(4, 6, 50, 32)),
                        shoulder(L2, "ZL", r(58, 6, 44, 32)),
                        shoulder(R2, "ZR", r(710, 6, 44, 32)),
                        shoulder(R, "R", r(758, 6, 50, 32)),
                        SkinControl.Stick(r(10, 64, 88), LEFT_STICK),
                        SkinControl.Dpad(r(12.12f, 230.59f, 95.76f), reach = 6f),
                        SkinControl.Stick(r(81.45f, 195.37f, 26.93f), RIGHT_STICK, reach = 1f),
                        round(KEYCODE_SWAP_SCREENS, "SWAP", r(743, 262, 34), icon = SkinIcon.SWAP),
                    ) + nintendoFace(760f, 163f, 32f) +
                        listOf(
                            // Menu and exit together on the left, select and start on the right.
                            SkinControl.Menu(r(10, 346, lw, lh), reach = 3f),
                            flatPill(SELECT, "SELECT", r(696, 346, lw, lh), 3f),
                            flatPill(START, "START", r(752, 346, lw, lh), 3f),
                        ),
                    SkinPalettes.N3DS,
                    SLIM_WING_SCREENS,
                    optionalControls =
                        fixedExtraButtons(
                            fast = r(470, 346, lw, lh),
                            exit = r(66, 346, lw, lh),
                            save = r(350, 346, lw, lh),
                            load = r(412, 346, lw, lh),
                            reach = 3f,
                        ),
                ).copy(captionless = true, fixedExtras = true).withDefaults(N3DS_LANDSCAPE_TUNED).withSettledReach(),
        )
    }

    /** A joystick with a ball on top, as on Atari sticks and arcade cabinets. */
    private fun ballTop(
        frame: SkinRect,
        source: Int = ComposeTouchLayouts.MOTION_SOURCE_DPAD,
    ) = SkinControl.Dpad(frame, reach = 10f, source = source, look = DpadLook.BALL_TOP)

    /** Atari 2600: the joystick and its red fire button, with the console switches along the bottom. */
    private fun atari2600(): ArcaduneSkin {
        val y = 206
        return ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.ATARI,
                    listOf(
                        ballTop(r(18, 40, 140)),
                        round(B, "", r(200, 66, 92)),
                        SkinControl.Menu(r(11.4f, y, 40, 17)),
                        pill(L, "DIFF A", r(62.9f, y, 40, 17)),
                        pill(L2, "DIFF B", r(114.3f, y, 40, 17)),
                        pill(SELECT, "SELECT", r(165.7f, y, 40, 17)),
                        pill(START, "RESET", r(217.1f, y, 40, 17)),
                        SkinControl.Rewind(r(268.6f, y, 40, 17)),
                    ),
                ),
            landscape =
                landscape(
                    listOf(
                        ballTop(r(30, 160, 180)),
                        round(B, "", r(650, 196, 116)),
                        pill(L, "DIFF A", r(284, 318, 52, 24), reach = 3f),
                        pill(L2, "DIFF B", r(343, 318, 52, 24), reach = 3f),
                        pill(SELECT, "SELECT", r(402, 318, 52, 24), reach = 3f),
                        pill(START, "RESET", r(461, 318, 52, 24), reach = 3f),
                        SkinControl.Rewind(r(330, 18, 66, 24)),
                        SkinControl.Menu(r(416, 18, 66, 24)),
                    ),
                    SkinPalettes.ATARI,
                ),
        )
    }

    /** Atari 7800: the ProLine stick with its two red buttons (1 = B, 2 = A). */
    private fun atari7800() =
        ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.ATARI,
                    listOf(
                        ballTop(r(18, 44, 136)),
                        round(B, "1", r(176, 121, 64)),
                        round(A, "2", r(241, 62, 64)),
                    ) + portraitBottomRow(),
                ),
            landscape =
                landscape(
                    listOf(
                        ballTop(r(30, 170, 170)),
                        round(A, "2", r(706, 214, 76)),
                        round(B, "1", r(624, 272, 76)),
                    ) + landscapeCommon(),
                    SkinPalettes.ATARI,
                ),
        )

    /** Atari Lynx: d-pad, A and B, Option 1 and 2, and Pause. */
    private fun lynx(): ArcaduneSkin {
        val y = 206
        return ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.LYNX,
                    listOf(
                        SkinControl.Dpad(r(8, 56, 128)),
                        round(B, "B", r(176, 121, 64)),
                        round(A, "A", r(241, 62, 64)),
                        SkinControl.Menu(r(20, y, 40, 17)),
                        // Spaced so each name (under its pill) has room.
                        pill(L, "OPTION 1", r(80, y, 40, 17), reach = 4f),
                        pill(R, "OPTION 2", r(140, y, 40, 17), reach = 4f),
                        pill(START, "PAUSE", r(200, y, 40, 17), reach = 4f),
                        SkinControl.Rewind(r(260, y, 40, 17)),
                    ),
                ),
            landscape =
                landscape(
                    listOf(
                        SkinControl.Dpad(r(30, 200, 150)),
                        round(A, "A", r(706, 214, 76)),
                        round(B, "B", r(624, 272, 76)),
                        pill(L, "OPTION 1", r(300, 318, 66, 24)),
                        pill(R, "OPTION 2", r(373, 318, 66, 24)),
                        pill(START, "PAUSE", r(446, 318, 66, 24)),
                        SkinControl.Rewind(r(330, 18, 66, 24)),
                        SkinControl.Menu(r(416, 18, 66, 24)),
                    ),
                    SkinPalettes.LYNX,
                ),
        )
    }

    /** WonderSwan held upright: the X pad (X1 to X4, sent as Y, X, A, B) where the face buttons are. */
    private fun wonderSwanVertical() =
        ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.WONDERSWAN,
                    listOf(
                        SkinControl.Dpad(r(8, 56, 128)),
                    ) +
                        diamond(244f, 114f, 50f, Y to "X1", B to "X4", X to "X2", A to "X3") +
                        portraitBottomRow(select = null),
                ),
            landscape =
                landscape(
                    listOf(
                        SkinControl.Dpad(r(30, 196, 156)),
                        round(Y, "X1", r(660, 182, 56)),
                        round(X, "X2", r(712, 236, 56)),
                        round(A, "X3", r(660, 290, 56)),
                        round(B, "X4", r(608, 236, 56)),
                    ) + landscapeCommon(select = null),
                    SkinPalettes.WONDERSWAN,
                ),
        )

    /**
     * Arcade: a ball-top stick and coloured arcade buttons, Coin and Start. Four buttons sit in two
     * rows like the Neo Geo's A B / C D; six buttons are punches on top and kicks below.
     */
    private fun arcade(sixButtons: Boolean): ArcaduneSkin {
        val stick = ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK
        val palette = if (sixButtons) SkinPalettes.ARCADE_6 else SkinPalettes.ARCADE_4
        val portraitButtons =
            if (sixButtons) {
                listOf(
                    round(Y, "", r(158, 66, 48)),
                    round(X, "", r(212, 56, 48)),
                    round(L, "", r(266, 56, 48)),
                    round(B, "", r(158, 126, 48)),
                    round(A, "", r(212, 116, 48)),
                    round(R, "", r(266, 116, 48)),
                )
            } else {
                listOf(
                    round(Y, "C", r(176, 68, 56)),
                    round(X, "D", r(242, 58, 56)),
                    round(B, "A", r(176, 134, 56)),
                    round(A, "B", r(242, 124, 56)),
                )
            }
        val landscapeButtons =
            if (sixButtons) {
                listOf(
                    round(Y, "", r(574, 170, 62)),
                    round(X, "", r(644, 156, 62)),
                    round(L, "", r(714, 156, 62)),
                    round(B, "", r(574, 246, 62)),
                    round(A, "", r(644, 232, 62)),
                    round(R, "", r(714, 232, 62)),
                )
            } else {
                listOf(
                    round(Y, "C", r(620, 160, 72)),
                    round(X, "D", r(704, 146, 72)),
                    round(B, "A", r(620, 246, 72)),
                    round(A, "B", r(704, 232, 72)),
                )
            }
        return ArcaduneSkin(
            portrait =
                console(
                    palette,
                    listOf(ballTop(r(14, 46, 132), stick)) + portraitButtons +
                        portraitBottomRow(select = "COIN", rewind = false),
                ),
            landscape =
                landscape(
                    listOf(ballTop(r(30, 160, 180), stick)) + landscapeButtons +
                        landscapeCommon(select = "COIN", rewind = false),
                    palette,
                ),
        )
    }

    /** NES: the classic pad. Positions follow the classic controller (and Delta's NES layout). */
    private fun nes(): ArcaduneSkin {
        // The small pills, all alike: the top row of the deck holds save, load, rewind and fast-forward
        // together; menu and exit sit at the bottom right; select and start stay on the grey bar.
        val top = 10
        val bottom = 203
        val deck =
            listOf(
                SkinControl.Dpad(r(23, 57, 118)),
                round(B, "B", r(171, 84, 55), labelBelow = true),
                round(A, "A", r(242, 84, 55), labelBelow = true),
                pill(SELECT, "SELECT", r(108, bottom, 38, 13)),
                pill(START, "START", r(174, bottom, 38, 13)),
                SkinControl.Menu(r(226, bottom, 34, 14)),
                SkinControl.Rewind(r(166, top, 38, 14)),
            )
        // In the order saved layouts know them: fast-forward, exit, save, load.
        val extras =
            listOf(
                optionalButton(KEYCODE_FAST_FORWARD, "FAST", SkinIcon.FAST_FORWARD, r(216, top, 38, 14), 4f),
                optionalButton(KEYCODE_EXIT, "EXIT", SkinIcon.EXIT, r(270, bottom, 34, 14), 4f, hidden = false),
                optionalButton(KEYCODE_QUICK_SAVE, "SAVE", SkinIcon.QUICK_SAVE, r(66, top, 38, 14), 4f),
                optionalButton(KEYCODE_QUICK_LOAD, "LOAD", SkinIcon.QUICK_LOAD, r(116, top, 38, 14), 4f),
            )
        return ArcaduneSkin(
            portrait =
                portrait(
                    style = SkinStyle.NES_CLASSIC,
                    controls = deck,
                    optionalControls = extras,
                ),
            landscape =
                landscape(
                    listOf(
                        SkinControl.Dpad(r(30, 200, 150)),
                        round(A, "A", r(706, 214, 76)),
                        round(B, "B", r(624, 272, 76)),
                    ) + landscapeCommon(),
                ),
        )
    }

    private fun snes() =
        // Upright: the controls sit a little lower than they used to, so the four extra buttons fit along
        // the top between the shoulder buttons.
        ArcaduneSkin(
            portrait =
                portrait(
                    listOf(
                        SkinControl.Dpad(r(8, 66, 128)),
                        round(Y, "Y", r(157, 110, 52)),
                        round(X, "X", r(206, 66, 52)),
                        round(B, "B", r(206, 154, 52)),
                        round(A, "A", r(255, 110, 52)),
                        shoulder(L, "L", r(0, 0, 72, 28)),
                        shoulder(R, "R", r(248, 0, 72, 28)),
                    ) + portraitBottomRow(y = 220),
                ),
            landscape =
                landscape(
                    listOf(
                        SkinControl.Dpad(r(30, 196, 156)),
                        round(X, "X", r(660, 182, 56)),
                        round(Y, "Y", r(608, 236, 56)),
                        round(A, "A", r(712, 236, 56)),
                        round(B, "B", r(660, 290, 56)),
                        shoulder(L, "L", r(16, 16, 104, 40)),
                        shoulder(R, "R", r(692, 16, 104, 40)),
                    ) + landscapeCommon(),
                ),
        )

    private fun gb() =
        ArcaduneSkin(
            portrait =
                portrait(
                    listOf(
                        SkinControl.Dpad(r(8, 56, 128)),
                        round(B, "B", r(176, 121, 64)),
                        round(A, "A", r(241, 62, 64)),
                    ) + portraitBottomRow(),
                ),
            landscape =
                landscape(
                    listOf(
                        SkinControl.Dpad(r(30, 200, 150)),
                        round(A, "A", r(706, 214, 76)),
                        round(B, "B", r(624, 272, 76)),
                    ) + landscapeCommon(),
                ),
        )

    private fun gba() =
        ArcaduneSkin(
            portrait =
                portrait(
                    listOf(
                        SkinControl.Dpad(r(10, 72, 128)),
                        round(B, "B", r(178, 136, 60)),
                        round(A, "A", r(246, 84, 60)),
                        shoulder(L, "L", r(0, 0, 72, 28)),
                        shoulder(R, "R", r(248, 0, 72, 28)),
                    ) + portraitBottomRow(y = 220),
                ),
            landscape =
                landscape(
                    listOf(
                        SkinControl.Dpad(r(30, 196, 156)),
                        round(A, "A", r(706, 220, 76)),
                        round(B, "B", r(624, 278, 76)),
                        shoulder(L, "L", r(16, 16, 104, 40)),
                        shoulder(R, "R", r(692, 16, 104, 40)),
                    ) + landscapeCommon(),
                ),
        )

    // Genesis keys follow the existing Arcadune mapping: A = Y, B = B, C = A (6-button: X = L1, Y = X, Z = R1).
    private fun genesis3() =
        ArcaduneSkin(
            portrait =
                portrait(
                    listOf(
                        SkinControl.Dpad(r(8, 56, 128)),
                        round(Y, "A", r(150, 118, 50)),
                        round(B, "B", r(206, 98, 50)),
                        round(A, "C", r(262, 78, 50)),
                    ) + portraitBottomRow(select = null),
                ),
            landscape =
                landscape(
                    listOf(
                        SkinControl.Dpad(r(30, 200, 150)),
                        round(Y, "A", r(584, 286, 64)),
                        round(B, "B", r(652, 258, 64)),
                        round(A, "C", r(720, 230, 64)),
                    ) + landscapeCommon(select = null),
                ),
        )

    private fun genesis6() =
        ArcaduneSkin(
            portrait =
                portrait(
                    listOf(
                        SkinControl.Dpad(r(6, 60, 124)),
                        round(L, "X", r(150, 64, 46)),
                        round(X, "Y", r(204, 50, 46)),
                        round(R, "Z", r(258, 36, 46)),
                        round(Y, "A", r(150, 124, 46)),
                        round(B, "B", r(204, 110, 46)),
                        round(A, "C", r(258, 96, 46)),
                    ) + portraitBottomRow(select = null),
                ),
            landscape =
                landscape(
                    listOf(
                        SkinControl.Dpad(r(30, 200, 150)),
                        round(L, "X", r(590, 210, 58)),
                        round(X, "Y", r(656, 192, 58)),
                        round(R, "Z", r(722, 174, 58)),
                        round(Y, "A", r(590, 276, 58)),
                        round(B, "B", r(656, 258, 58)),
                        round(A, "C", r(722, 240, 58)),
                    ) + landscapeCommon(select = null),
                ),
        )

    /** Two-button pads (Master System, Game Gear, PC Engine): [first] is B, [second] is A, like the existing pads. */
    private fun twoButtons(
        first: String,
        second: String,
        start: String,
        select: String?,
    ) = ArcaduneSkin(
        portrait =
            portrait(
                listOf(
                    SkinControl.Dpad(r(8, 56, 128)),
                    round(B, first, r(176, 121, 64)),
                    round(A, second, r(241, 62, 64)),
                ) + portraitBottomRow(select = select, start = start),
            ),
        landscape =
            landscape(
                listOf(
                    SkinControl.Dpad(r(30, 200, 150)),
                    round(A, second, r(706, 214, 76)),
                    round(B, first, r(624, 272, 76)),
                ) + landscapeCommon(select = select, start = start),
            ),
    )

    /** Sega Dreamcast: analog stick above d-pad, L / R triggers (L2 / R2), coloured A / B / X / Y, and Start. */
    private fun dreamcast() =
        ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.DREAMCAST,
                    listOf(
                        shoulder(L2, "L", r(0, 0, 96, 26)),
                        shoulder(R2, "R", r(224, 0, 96, 26)),
                        SkinControl.Stick(r(20, 38, 88), LEFT_STICK),
                        SkinControl.Dpad(r(36, 136, 62), reach = 6f),
                    ) + diamond(244f, 110f, 48f, X to "Y", Y to "X", A to "B", B to "A") +
                        portraitBottomRow(select = null, rewind = false),
                ),
            landscape =
                landscape(
                    listOf(
                        shoulder(L2, "L", r(16, 16, 104, 40)),
                        shoulder(R2, "R", r(692, 16, 104, 40)),
                        SkinControl.Stick(r(30, 156, 148), LEFT_STICK),
                        SkinControl.Dpad(r(204, 256, 88)),
                    ) + diamond(700f, 252f, 54f, X to "Y", Y to "X", A to "B", B to "A") +
                        landscapeCommon(select = null, rewind = false),
                    SkinPalettes.DREAMCAST,
                ),
        )

    /**
     * Sega Saturn: L / R shoulders (L2 / R2), two-tier 6-button arc (A = B, B = A, C = L1; X = Y,
     * Y = X, Z = R1), and optional 3D Control Pad analog stick.
     */
    private fun saturn(threeDPad: Boolean): ArcaduneSkin {
        val leftPortrait =
            if (threeDPad) {
                listOf(
                    SkinControl.Stick(r(20, 38, 88), LEFT_STICK),
                    SkinControl.Dpad(r(36, 136, 62), reach = 6f),
                )
            } else {
                listOf(SkinControl.Dpad(r(8, 60, 124)))
            }
        val leftLandscape =
            if (threeDPad) {
                listOf(
                    SkinControl.Stick(r(30, 156, 148), LEFT_STICK),
                    SkinControl.Dpad(r(204, 256, 88)),
                )
            } else {
                listOf(SkinControl.Dpad(r(30, 200, 150)))
            }
        return ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.SATURN,
                    listOf(
                        shoulder(L2, "L", r(0, 0, 96, 26)),
                        shoulder(R2, "R", r(224, 0, 96, 26)),
                    ) + leftPortrait +
                        listOf(
                            round(Y, "X", r(154, 66, 42), reach = 4f),
                            round(X, "Y", r(206, 52, 42), reach = 4f),
                            round(R, "Z", r(258, 38, 42), reach = 4f),
                            round(B, "A", r(152, 124, 48), reach = 4f),
                            round(A, "B", r(206, 110, 48), reach = 4f),
                            round(L, "C", r(260, 96, 48), reach = 4f),
                        ) + portraitBottomRow(select = null, rewind = false),
                ),
            landscape =
                landscape(
                    listOf(
                        shoulder(L2, "L", r(16, 16, 104, 40)),
                        shoulder(R2, "R", r(692, 16, 104, 40)),
                    ) + leftLandscape +
                        listOf(
                            round(Y, "X", r(590, 210, 56), reach = 3f),
                            round(X, "Y", r(656, 192, 56), reach = 3f),
                            round(R, "Z", r(722, 174, 56), reach = 3f),
                            round(B, "A", r(590, 276, 56), reach = 3f),
                            round(A, "B", r(656, 258, 56), reach = 3f),
                            round(L, "C", r(722, 240, 56), reach = 3f),
                        ) + landscapeCommon(select = null, rewind = false),
                    SkinPalettes.SATURN,
                ),
        )
    }

    /** Panasonic 3DO: L and R shoulders, d-pad, angled A / B / C buttons, and X (Stop) / P (Play). */
    private fun threedo() =
        ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.THREEDO,
                    listOf(
                        shoulder(L, "L", r(0, 0, 96, 26)),
                        shoulder(R, "R", r(224, 0, 96, 26)),
                        SkinControl.Dpad(r(8, 56, 128)),
                        round(Y, "A", r(150, 118, 50)),
                        round(B, "B", r(206, 98, 50)),
                        round(A, "C", r(262, 78, 50)),
                    ) + portraitBottomRow(select = "SELECT/X", start = "START/P", rewind = false),
                ),
            landscape =
                landscape(
                    listOf(
                        shoulder(L, "L", r(16, 16, 104, 40)),
                        shoulder(R, "R", r(692, 16, 104, 40)),
                        SkinControl.Dpad(r(30, 200, 150)),
                        round(Y, "A", r(608, 244, 58)),
                        round(B, "B", r(674, 216, 58)),
                        round(A, "C", r(740, 188, 58)),
                    ) + landscapeCommon(select = "SELECT/X", start = "START/P", rewind = false),
                    SkinPalettes.THREEDO,
                ),
        )

    /** Atari 5200: analog stick, Fire 1 (A) and Fire 2 (B), Keypad OSK toggle (L1), Pause and Start. */
    private fun atari5200(): ArcaduneSkin {
        val y = 206
        return ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.ATARI5200,
                    listOf(
                        SkinControl.Stick(r(18, 46, 128), LEFT_STICK),
                        round(B, "2", r(176, 121, 64)),
                        round(A, "1", r(241, 62, 64)),
                        SkinControl.Menu(r(20, y, 40, 17)),
                        pill(L, "KEYPAD", r(80, y, 40, 17), reach = 4f),
                        pill(SELECT, "PAUSE", r(140, y, 40, 17), reach = 4f),
                        pill(START, "START", r(200, y, 40, 17), reach = 4f),
                        SkinControl.Rewind(r(260, y, 40, 17)),
                    ),
                ),
            landscape =
                landscape(
                    listOf(
                        SkinControl.Stick(r(30, 170, 170), LEFT_STICK),
                        round(A, "1", r(706, 214, 76)),
                        round(B, "2", r(624, 272, 76)),
                        pill(L, "KEYPAD", r(300, 318, 66, 24)),
                        pill(SELECT, "PAUSE", r(373, 318, 66, 24)),
                        pill(START, "START", r(446, 318, 66, 24)),
                        SkinControl.Rewind(r(330, 18, 66, 24)),
                        SkinControl.Menu(r(416, 18, 66, 24)),
                    ),
                    SkinPalettes.ATARI5200,
                ),
        )
    }

    /** ColecoVision: ball-top joystick, Left / Right fire buttons, skill keypad 1-4, and # / *. */
    private fun colecoVision(): ArcaduneSkin {
        val stick = ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK
        return ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.COLECOVISION,
                    listOf(
                        ballTop(r(14, 48, 124), stick),
                        round(B, "L", r(176, 52, 56)),
                        round(A, "R", r(246, 52, 56)),
                        round(Y, "1", r(158, 134, 34), reach = 3f),
                        round(X, "2", r(198, 134, 34), reach = 3f),
                        round(L, "3", r(238, 134, 34), reach = 3f),
                        round(R, "4", r(278, 134, 34), reach = 3f),
                    ) + portraitBottomRow(select = "#", start = "*", rewind = true),
                ),
            landscape =
                landscape(
                    listOf(
                        ballTop(r(30, 170, 170), stick),
                        round(B, "L", r(624, 198, 68)),
                        round(A, "R", r(706, 198, 68)),
                        round(Y, "1", r(596, 290, 44), reach = 3f),
                        round(X, "2", r(646, 290, 44), reach = 3f),
                        round(L, "3", r(696, 290, 44), reach = 3f),
                        round(R, "4", r(746, 290, 44), reach = 3f),
                    ) + landscapeCommon(select = "#", start = "*", rewind = true),
                    SkinPalettes.COLECOVISION,
                ),
        )
    }

    /** Atari 8-bit (800XL): joystick, Fire 1 / 2, Spacebar, Virtual Keyboard, and Option / Select / Start. */
    private fun atari800(): ArcaduneSkin {
        val stick = ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK
        val y = 206
        return ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.ATARI800,
                    listOf(
                        ballTop(r(16, 46, 132), stick),
                        round(B, "1", r(172, 84, 58)),
                        round(A, "2", r(244, 48, 58)),
                        SkinControl.Button(Y, "SPACE", ButtonShape.PILL, r(216, 154, 86, 24), reach = 4f),
                        SkinControl.Menu(r(20, y, 40, 17)),
                        pill(L3, "KEYB", r(80, y, 40, 17), reach = 4f),
                        pill(L, "OPTION", r(140, y, 40, 17), reach = 4f),
                        pill(SELECT, "SELECT", r(200, y, 40, 17), reach = 4f),
                        pill(START, "START", r(260, y, 40, 17), reach = 4f),
                    ),
                ),
            landscape =
                landscape(
                    listOf(
                        ballTop(r(30, 170, 170), stick),
                        round(B, "1", r(632, 280, 74)),
                        round(A, "2", r(712, 220, 74)),
                        SkinControl.Button(Y, "SPACE", ButtonShape.PILL, r(716, 304, 68, 34), reach = 4f),
                        pill(L3, "KEYB", r(264, 318, 60, 24), reach = 3f),
                        pill(L, "OPTION", r(331, 318, 60, 24), reach = 3f),
                        pill(SELECT, "SELECT", r(398, 318, 60, 24), reach = 3f),
                        pill(START, "START", r(465, 318, 60, 24), reach = 3f),
                        SkinControl.Menu(r(373, 18, 66, 24)),
                    ),
                    SkinPalettes.ATARI800,
                ),
        )
    }

    /** Commodore 64: Competition Pro joystick + Fire, Spacebar, Virtual Keyboard, Run/Stop, Return, Start. */
    private fun c64(): ArcaduneSkin {
        val stick = ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK
        val y = 206
        return ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.C64,
                    listOf(
                        ballTop(r(16, 46, 132), stick),
                        round(B, "FIRE", r(216, 50, 78)),
                        SkinControl.Button(X, "SPACE", ButtonShape.PILL, r(206, 148, 96, 26), reach = 4f),
                        SkinControl.Menu(r(20, y, 40, 17)),
                        pill(SELECT, "KEYB", r(80, y, 40, 17), reach = 4f),
                        pill(L2, "STOP", r(140, y, 40, 17), reach = 4f),
                        pill(R2, "RETURN", r(200, y, 40, 17), reach = 4f),
                        pill(START, "START", r(260, y, 40, 17), reach = 4f),
                    ),
                ),
            landscape =
                landscape(
                    listOf(
                        ballTop(r(30, 170, 170), stick),
                        round(B, "FIRE", r(664, 198, 96)),
                        SkinControl.Button(X, "SPACE", ButtonShape.PILL, r(668, 306, 92, 34), reach = 4f),
                        pill(SELECT, "KEYB", r(264, 318, 60, 24), reach = 3f),
                        pill(L2, "STOP", r(331, 318, 60, 24), reach = 3f),
                        pill(R2, "RETURN", r(398, 318, 60, 24), reach = 3f),
                        pill(START, "START", r(465, 318, 60, 24), reach = 3f),
                        SkinControl.Menu(r(373, 18, 66, 24)),
                    ),
                    SkinPalettes.C64,
                ),
        )
    }

    /** Apple II: analog Apple joystick, Button 0 (Open-Apple = A) and Button 1 (Solid-Apple = B). */
    private fun appleII(): ArcaduneSkin {
        val stick = ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK
        return ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.APPLEII,
                    listOf(
                        SkinControl.Stick(r(18, 46, 128), stick),
                        round(B, "1", r(176, 121, 64)),
                        round(A, "0", r(241, 62, 64)),
                    ) + portraitBottomRow(select = "SELECT", start = "START", rewind = false),
                ),
            landscape =
                landscape(
                    listOf(
                        SkinControl.Stick(r(30, 170, 170), stick),
                        round(A, "0", r(706, 214, 76)),
                        round(B, "1", r(624, 272, 76)),
                    ) + landscapeCommon(select = "SELECT", start = "START", rewind = false),
                    SkinPalettes.APPLEII,
                ),
        )
    }

    /** Microsoft MSX: d-pad, Trigger A / B, Spacebar, and F1 / F2 function keys. */
    private fun msx() =
        ArcaduneSkin(
            portrait =
                console(
                    SkinPalettes.MSX,
                    listOf(
                        SkinControl.Dpad(r(8, 56, 128)),
                        round(B, "B", r(172, 86, 58)),
                        round(A, "A", r(242, 48, 58)),
                        SkinControl.Button(Y, "SPACE", ButtonShape.PILL, r(216, 154, 86, 24), reach = 4f),
                    ) + portraitBottomRow(select = "F2", start = "F1", rewind = true),
                ),
            landscape =
                landscape(
                    listOf(
                        SkinControl.Dpad(r(30, 200, 150)),
                        round(B, "B", r(632, 280, 74)),
                        round(A, "A", r(712, 220, 74)),
                        SkinControl.Button(Y, "SPACE", ButtonShape.PILL, r(716, 304, 68, 34), reach = 4f),
                    ) + landscapeCommon(select = "F2", start = "F1", rewind = true),
                    SkinPalettes.MSX,
                ),
        )
}

/**
 * A short fingerprint of the controls of this layout (kind, key and place of each). Saved button
 * moves are kept by control index, so they are stored under it: when an update changes a layout,
 * the old moves are left behind instead of being applied to the wrong buttons.
 */
fun SkinLayout.signature(): String = memoizedSignature

/**
 * How far (in skin units) the controls of this layout reach above its top edge: the DS's L and R
 * tabs sit beside the screens, above the controller panel. 0 for every other layout.
 */
fun SkinLayout.unitsAbove(): Float =
    -(controls.filterNot { it.defaultHidden }.minOfOrNull { it.frame.y - it.reach }?.coerceAtMost(0f) ?: 0f)

/** One of the four extra buttons: fast-forward, exit, quick save, quick load. */
internal fun SkinControl.isExtraButton(): Boolean =
    this is SkinControl.Button && icon != null &&
        keyCode in setOf(KEYCODE_FAST_FORWARD, KEYCODE_EXIT, KEYCODE_QUICK_SAVE, KEYCODE_QUICK_LOAD)
