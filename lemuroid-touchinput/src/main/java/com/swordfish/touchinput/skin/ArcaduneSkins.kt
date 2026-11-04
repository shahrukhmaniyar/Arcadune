package com.swordfish.touchinput.skin

import android.view.KeyEvent
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_REWIND
import com.swordfish.touchinput.radial.settings.TouchControllerID

/** A rectangle in the skin's own units (see [SkinLayout]). */
data class SkinRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val centerX get() = x + width / 2
    val centerY get() = y + height / 2
}

enum class ButtonShape { ROUND, PILL, SHOULDER }

/** How a skin is painted. Both are Arcadune's own artwork. */
enum class SkinStyle {
    /** A white console body with black buttons, shaped like a handheld. */
    MONO,

    /** A grey frame around a black pad, with red buttons on white squares, like the classic NES pad. */
    NES_CLASSIC,

    /** Light outlines over the game, for landscape. */
    OVERLAY,
}

sealed interface SkinControl {
    val frame: SkinRect

    /** Extra touch area around the control, in skin units, so near misses still count. */
    val reach: Float

    data class Dpad(override val frame: SkinRect, override val reach: Float = 14f) : SkinControl

    data class Button(
        val keyCode: Int,
        val label: String,
        val shape: ButtonShape,
        override val frame: SkinRect,
        override val reach: Float = 6f,
        /** Small buttons (select, start) and NES face buttons carry their name under them. */
        val labelBelow: Boolean = false,
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
)

data class ArcaduneSkin(val portrait: SkinLayout, val landscape: SkinLayout)

/**
 * Arcadune's own controller skins, laid out the way Delta lays out its controllers: d-pad on the
 * left, face buttons on the right, menu / select / start in a row at the bottom with their names
 * under them. The artwork is Arcadune's own (no Delta graphics or branding).
 */
object ArcaduneSkins {
    fun forController(id: TouchControllerID): ArcaduneSkin? =
        when (id) {
            TouchControllerID.NES -> nes()
            TouchControllerID.SNES -> snes()
            TouchControllerID.GB -> gb()
            TouchControllerID.GBA -> gba()
            TouchControllerID.GENESIS_3 -> genesis3()
            TouchControllerID.GENESIS_6 -> genesis6()
            TouchControllerID.SMS -> twoButtons(first = "1", second = "2", start = "START", select = null)
            TouchControllerID.GG -> twoButtons(first = "1", second = "2", start = "START", select = null)
            TouchControllerID.PCE -> twoButtons(first = "I", second = "II", start = "RUN", select = "SELECT")
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

    private fun round(key: Int, label: String, frame: SkinRect, labelBelow: Boolean = false) =
        SkinControl.Button(key, label, ButtonShape.ROUND, frame, labelBelow = labelBelow)

    private fun pill(key: Int, label: String, frame: SkinRect) =
        SkinControl.Button(key, label, ButtonShape.PILL, frame, reach = 10f, labelBelow = true)

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
     * REWIND on the right, all the same pill.
     */
    private fun portraitBottomRow(
        select: String? = "SELECT",
        start: String? = "START",
    ): List<SkinControl> {
        val y = 206
        return listOfNotNull(
            SkinControl.Menu(r(10, y, 40, 17)),
            select?.let { pill(SELECT, it, r(110, y, 40, 17)) },
            start?.let { pill(START, it, if (select == null) r(140, y, 40, 17) else r(170, y, 40, 17)) },
            SkinControl.Rewind(r(270, y, 40, 17)),
        )
    }

    /** The middle of the landscape skins: select / start at the bottom, rewind / menu at the top. */
    private fun landscapeCommon(
        select: String? = "SELECT",
        start: String? = "START",
    ) = listOfNotNull(
        select?.let { pill(SELECT, it, r(330, 318, 66, 24)) },
        start?.let { pill(START, it, if (select == null) r(373, 318, 66, 24) else r(416, 318, 66, 24)) },
        SkinControl.Rewind(r(330, 18, 66, 24)),
        SkinControl.Menu(r(416, 18, 66, 24)),
    )

    private fun portrait(controls: List<SkinControl>, style: SkinStyle = SkinStyle.MONO) =
        SkinLayout(PW, PH, style, controls)

    private fun landscape(controls: List<SkinControl>) = SkinLayout(LW, LH, SkinStyle.OVERLAY, controls)

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
