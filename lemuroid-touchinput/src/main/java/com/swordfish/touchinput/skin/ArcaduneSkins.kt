package com.swordfish.touchinput.skin

import android.view.KeyEvent
import androidx.compose.ui.graphics.Color
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_REWIND
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_SWAP_SCREENS
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_FAST_FORWARD
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_QUICK_LOAD
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_QUICK_SAVE
import com.swordfish.touchinput.radial.settings.TouchControllerID

/** A rectangle in the skin's own units (see [SkinLayout]). */
data class SkinRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val centerX get() = x + width / 2
    val centerY get() = y + height / 2
}

enum class ButtonShape { ROUND, PILL, SHOULDER }

/**
 * Small pictures drawn on buttons whose name says less than a picture: the DS microphone and lid,
 * the 3DS swap, and the quick buttons (fast forward, quick save, quick load).
 */
enum class SkinIcon { MIC, LID, SWAP, FAST_FORWARD, QUICK_SAVE, QUICK_LOAD }

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

    /** [source] is which direction it sends: the d-pad, or a stick moved in 8 (C buttons: 4) steps. */
    data class Dpad(
        override val frame: SkinRect,
        override val reach: Float = 14f,
        val source: Int = ComposeTouchLayouts.MOTION_SOURCE_DPAD,
        val look: DpadLook = DpadLook.CROSS,
    ) : SkinControl

    /** An analog stick. [pressKey] is sent when the stick is pressed in (L3 / R3), if the console has it. */
    data class Stick(
        override val frame: SkinRect,
        val source: Int,
        val pressKey: Int? = null,
        override val reach: Float = 8f,
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
    ) : SkinControl

    data class Menu(override val frame: SkinRect, override val reach: Float = 10f) : SkinControl

    data class Rewind(override val frame: SkinRect, override val reach: Float = 10f) : SkinControl
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
)

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
                landscape.copy(style = if (landscape.screenArea != null) SkinStyle.CONSOLE else SkinStyle.OVERLAY, palette = palette),
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

    /** Not a real key: where [N64]'s C buttons find their colour. */
    const val C_BUTTONS_KEY = -64
}

/**
 * Arcadune's own controller skins, laid out the way Delta lays out its controllers: d-pad on the
 * left, face buttons on the right, menu / select / start in a row at the bottom with their names
 * under them. The artwork is Arcadune's own (no Delta graphics or branding).
 */
object ArcaduneSkins {
    /** The skin of [id]: the Console skin, in its colours and shape, or the Minimal one in outlines. */
    fun forController(
        id: TouchControllerID,
        variant: SkinVariant = SkinVariant.CONSOLE,
    ): ArcaduneSkin? =
        skinOf(id)
            ?.let { skin ->
                val palette = skin.landscape.palette ?: skin.portrait.palette ?: SkinPalettes.NES
                shellLandscape(id, palette)?.let { skin.copy(landscape = it) } ?: skin
            }
            ?.let { if (variant == SkinVariant.MINIMAL) it.minimal() else it }

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
                twoButtons(first = "B", second = "A", start = "START", select = null).colouredAs(SkinPalettes.WONDERSWAN)
            TouchControllerID.WS_PORTRAIT -> wonderSwanVertical()
            TouchControllerID.ARCADE_4 -> arcade(sixButtons = false)
            TouchControllerID.ARCADE_6 -> arcade(sixButtons = true)
            else -> null
        }

    /** Keys the skin itself handles and never sends to the game. */
    val rewindKey = KEYCODE_REWIND

    private const val PW = 320f
    private const val PH = 274f
    private const val LW = 812f
    private const val LH = 375f

    private fun r(x: Number, y: Number, w: Number, h: Number = w) =
        SkinRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())

    private fun round(
        key: Int,
        label: String,
        frame: SkinRect,
        labelBelow: Boolean = false,
        icon: SkinIcon? = null,
        reach: Float = 6f,
    ) = SkinControl.Button(key, label, ButtonShape.ROUND, frame, reach = reach, labelBelow = labelBelow || icon != null, icon = icon)

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

    private fun shoulder(key: Int, label: String, frame: SkinRect) =
        SkinControl.Button(key, label, ButtonShape.SHOULDER, frame, reach = 4f)

    private const val A = KeyEvent.KEYCODE_BUTTON_A
    private const val B = KeyEvent.KEYCODE_BUTTON_B
    private const val X = KeyEvent.KEYCODE_BUTTON_X
    private const val Y = KeyEvent.KEYCODE_BUTTON_Y
    private const val L = KeyEvent.KEYCODE_BUTTON_L1
    private const val R = KeyEvent.KEYCODE_BUTTON_R1
    private const val SELECT = KeyEvent.KEYCODE_BUTTON_SELECT
    private const val START = KeyEvent.KEYCODE_BUTTON_START

    /**
     * The bottom row of the handheld skins: MENU on the left, SELECT and START in the middle,
     * REWIND on the right, all the same pill. Consoles the app cannot rewind ([rewind] false) have
     * no REWIND, and the others are spread evenly across the row.
     */
    private fun portraitBottomRow(
        select: String? = "SELECT",
        start: String? = "START",
        y: Int = 206,
        rewind: Boolean = true,
    ): List<SkinControl> {
        if (!rewind) {
            val keys = listOfNotNull(select?.let { SELECT to it }, start?.let { START to it })
            val slot = PW / (keys.size + 1)

            fun x(i: Int) = slot * i + (slot - 40f) / 2
            return listOf(SkinControl.Menu(r(x(0), y, 40, 17))) +
                keys.mapIndexed { i, (key, label) -> pill(key, label, r(x(i + 1), y, 40, 17)) }
        }
        return listOfNotNull(
            SkinControl.Menu(r(10, y, 40, 17)),
            select?.let { pill(SELECT, it, r(110, y, 40, 17)) },
            start?.let { pill(START, it, if (select == null) r(140, y, 40, 17) else r(170, y, 40, 17)) },
            SkinControl.Rewind(r(270, y, 40, 17)),
        )
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

    private fun portrait(controls: List<SkinControl>, style: SkinStyle = SkinStyle.MONO) =
        SkinLayout(PW, PH, style, controls)

    private fun landscape(
        controls: List<SkinControl>,
        palette: SkinPalette? = null,
        screenArea: SkinRect? = null,
    ) = SkinLayout(LW, LH, if (screenArea != null) SkinStyle.CONSOLE else SkinStyle.OVERLAY, controls, palette, screenArea)

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

    private val FRAMED_SCREEN = SkinRect(226f, 0f, 360f, 302f)

    /** Menu and rewind top left, select and start under the screen. */
    private fun framedHandheldBand(
        select: String? = "SELECT",
        start: String = "START",
    ) = listOfNotNull(
        SkinControl.Menu(r(28, 18, 66, 24)),
        SkinControl.Rewind(r(112, 18, 66, 24)),
        select?.let { pill(SELECT, it, r(330, 320, 66, 24)) },
        pill(START, start, if (select == null) r(373, 320, 66, 24) else r(416, 320, 66, 24)),
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
    ) = landscape(controls, palette, SHELL_SCREENS)

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
            TouchControllerID.PSP ->
                shell(
                    palette,
                    listOf(wingShoulderL, wingShoulderR, separateDpad(r(20, 70, 110)), SkinControl.Stick(r(48, 222, 56), LEFT_STICK)) +
                        psSymbols(737f, 158f, 46f) + landscapeShellBand(rewind = false),
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
            else -> null
        }

    /** A portrait panel in a console's colours. DS and 3DS use a shorter one, so both screens stay big. */
    private fun console(
        palette: SkinPalette,
        controls: List<SkinControl>,
        height: Float = PH,
    ) = SkinLayout(PW, height, SkinStyle.CONSOLE, controls, palette)

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
            portrait = console(SkinPalettes.PLAYSTATION, shoulders + portraitControls + portraitBottomRow(rewind = false)),
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
     * Nintendo DS: the white DS Lite. Upright, the panel is shorter so both screens stay big; the
     * lid sits along the bottom, between menu, start and select. Sideways, the screens sit across
     * the top, the quick buttons down the left side and the lid on the right one. No rewind: the app
     * cannot rewind the DS. No microphone button either: as in Delta, the phone's microphone simply
     * listens while the game runs (the DS core is set to "always" in GameSystem).
     *
     * The lid is L2 with Y, as melonDS DS reads it (L2 held turns Y into "close / open the lid").
     */
    private fun ds() =
        ArcaduneSkin(
            // Upright the way Delta's standard DS skin is laid out: the body behind everything,
            // both screens in one recessed panel with the L / R tabs right under the touch screen,
            // a big d-pad and big X / Y / A / B, and menu, start and select along the bottom (with
            // the lid between start and select).
            portrait =
                console(
                    SkinPalettes.DS,
                    listOf(
                        shoulder(L, "L", r(22, 0, 102, 24)),
                        shoulder(R, "R", r(196, 0, 102, 24)),
                        SkinControl.Dpad(r(8, 46, 130), reach = 10f),
                    ) + diamondAt(246f, 114f, 48f, 45f, X to "X", Y to "Y", A to "A", B to "B") +
                        listOf(
                            SkinControl.Menu(r(8, 192, 40, 17)),
                            pill(START, "START", r(96, 192, 40, 17)),
                            dsLid(r(192, 188, 24)),
                            pill(SELECT, "SELECT", r(272, 192, 40, 17)),
                        ),
                    height = 236f,
                ).copy(uprightScreenSide = 0.075f, screenPanelBelow = 24f),
            // Sideways, as Delta's standard DS skin: both screens side by side across the top in
            // one panel, L / R tabs right under them near the middle, the d-pad and X / Y / A / B
            // low in the corners, menu, start and select along the bottom. The quick buttons
            // (fast forward, save, load) go down the left side, the lid on the right one.
            landscape =
                landscape(
                    listOf(
                        round(KEYCODE_FAST_FORWARD, "FAST", r(16, 16, 40), icon = SkinIcon.FAST_FORWARD, reach = 4f),
                        round(KEYCODE_QUICK_SAVE, "SAVE", r(16, 64, 40), icon = SkinIcon.QUICK_SAVE, reach = 4f),
                        round(KEYCODE_QUICK_LOAD, "LOAD", r(16, 112, 40), icon = SkinIcon.QUICK_LOAD, reach = 4f),
                        dsLid(r(756, 16, 40)),
                        SkinControl.Dpad(r(26, 246, 126), reach = 8f),
                        shoulder(L, "L", r(250, 236, 142, 32)),
                        shoulder(R, "R", r(420, 236, 142, 32)),
                        SkinControl.Menu(r(254, 328, 56, 22), reach = 6f),
                        pill(START, "START", r(378, 328, 56, 22), reach = 6f),
                        pill(SELECT, "SELECT", r(502, 328, 56, 22), reach = 6f),
                    ) + diamondAt(728f, 310f, 44f, 42f, X to "X", Y to "Y", A to "A", B to "B"),
                    SkinPalettes.DS,
                    DS_SIDEWAYS_SCREENS,
                ).copy(screenPanelBelow = 36f),
        )

    /** Closing / opening the DS lid: melonDS DS toggles it when Y is pressed with L2 held. */
    private fun dsLid(frame: SkinRect) = round(L2, "LID", frame, icon = SkinIcon.LID, reach = 4f).copy(alsoKey = Y)

    /**
     * Nintendo 3DS (New 3DS): the Circle Pad over the d-pad, the coloured buttons, the C-Stick (the
     * right stick), L / ZL / ZR / R, Select and Start, and Swap. Citra reads ZL / ZR as L2 / R2.
     * No Home button: Citra's Home (L3) only swaps the screens, which Swap already does (and Swap
     * also moves the shell's windows). Sideways: Circle Pad and d-pad on the left wing, C-Stick,
     * buttons and Swap on the right one.
     */
    private fun n3ds(): ArcaduneSkin {
        val y = 196
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
                        round(KEYCODE_SWAP_SCREENS, "SWAP", r(142, 62, 36), icon = SkinIcon.SWAP),
                    ) + portraitBottomRow(y = y, rewind = false) + nintendoFace(256f, 96f, 42f),
                    height = 232f,
                ),
            landscape =
                landscape(
                    listOf(
                        shoulder(L, "L", r(10, 8, 82, 36)),
                        shoulder(L2, "ZL", r(96, 8, 46, 36)),
                        shoulder(R2, "ZR", r(670, 8, 46, 36)),
                        shoulder(R, "R", r(720, 8, 82, 36)),
                        SkinControl.Stick(r(24, 62, 108), LEFT_STICK),
                        SkinControl.Dpad(r(40, 206, 76), reach = 8f),
                        SkinControl.Stick(r(676, 60, 30), RIGHT_STICK),
                        round(KEYCODE_SWAP_SCREENS, "SWAP", r(712, 272, 44), icon = SkinIcon.SWAP),
                    ) + nintendoFace(736f, 178f, 46f) + landscapeShellBand(rewind = false),
                    SkinPalettes.N3DS,
                    SHELL_SCREENS,
                ),
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
                        SkinControl.Menu(r(10, y, 40, 17)),
                        pill(L, "DIFF A", r(70, y, 40, 17)),
                        pill(L2, "DIFF B", r(120, y, 40, 17)),
                        pill(SELECT, "SELECT", r(170, y, 40, 17)),
                        pill(START, "RESET", r(220, y, 40, 17)),
                        SkinControl.Rewind(r(270, y, 40, 17)),
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
                        SkinControl.Menu(r(10, y, 40, 17)),
                        // Spaced so each name (under its pill) has room.
                        pill(L, "OPTION 1", r(78, y, 40, 17), reach = 4f),
                        pill(R, "OPTION 2", r(140, y, 40, 17), reach = 4f),
                        pill(START, "PAUSE", r(202, y, 40, 17), reach = 4f),
                        SkinControl.Rewind(r(270, y, 40, 17)),
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
                    ) + diamond(244f, 114f, 50f, Y to "X1", B to "X4", X to "X2", A to "X3") + portraitBottomRow(select = null),
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
            portrait = console(palette, listOf(ballTop(r(14, 46, 132), stick)) + portraitButtons + portraitBottomRow(select = "COIN", rewind = false)),
            landscape =
                landscape(
                    listOf(ballTop(r(30, 160, 180), stick)) + landscapeButtons + landscapeCommon(select = "COIN", rewind = false),
                    palette,
                ),
        )
    }

    /** NES: the classic pad. Positions follow the classic controller (and Delta's NES layout). */
    private fun nes() =
        ArcaduneSkin(
            portrait =
                portrait(
                    style = SkinStyle.NES_CLASSIC,
                    controls =
                        listOf(
                            SkinControl.Dpad(r(23, 57, 118)),
                            round(B, "B", r(171, 84, 55), labelBelow = true),
                            round(A, "A", r(242, 84, 55), labelBelow = true),
                            pill(SELECT, "SELECT", r(108, 203, 38, 13)),
                            pill(START, "START", r(174, 203, 38, 13)),
                            SkinControl.Menu(r(279, 44, 19, 19)),
                            SkinControl.Rewind(r(22, 207, 19, 19)),
                        ),
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

    private fun snes() =
        ArcaduneSkin(
            portrait =
                portrait(
                    listOf(
                        SkinControl.Dpad(r(8, 52, 128)),
                        round(Y, "Y", r(157, 96, 52)),
                        round(X, "X", r(206, 52, 52)),
                        round(B, "B", r(206, 140, 52)),
                        round(A, "A", r(255, 96, 52)),
                        shoulder(L, "L", r(0, 0, 100, 28)),
                        shoulder(R, "R", r(220, 0, 100, 28)),
                    ) + portraitBottomRow(),
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
                        SkinControl.Dpad(r(10, 58, 128)),
                        round(B, "B", r(178, 122, 60)),
                        round(A, "A", r(246, 70, 60)),
                        shoulder(L, "L", r(0, 0, 104, 28)),
                        shoulder(R, "R", r(216, 0, 104, 28)),
                    ) + portraitBottomRow(),
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
}

/**
 * A short fingerprint of the controls of this layout (kind, key and place of each). Saved button
 * moves are kept by control index, so they are stored under it: when an update changes a layout,
 * the old moves are left behind instead of being applied to the wrong buttons.
 */
fun SkinLayout.signature(): String {
    val text =
        controls.joinToString("|") { control ->
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
    return Integer.toHexString(text.hashCode())
}
