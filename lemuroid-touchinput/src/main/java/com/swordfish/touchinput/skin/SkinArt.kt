package com.swordfish.touchinput.skin

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Designed artwork for a skin, like Delta's: a picture for the body and one for each control,
 * made by the skin studio (`tools/skin-studio`) or by a designer, and shipped in the app's assets
 * under `skin-art/<controller>/<portrait|landscape>/`. A control without a picture is drawn by
 * code, as are the skins without artwork (Minimal, the see-through sideways controls).
 *
 * The pictures are parts rather than one picture of the whole controller (as a Delta skin is) so
 * the player can still move and resize every control.
 */
class SkinArt(
    /** Drawn over the body colour the app paints: grain, sheen, wordmark, grille. Transparent elsewhere. */
    val body: ImageBitmap?,
    private val parts: Map<String, ImageBitmap>,
    /** The plastic's grain, a small tile repeated over the body (shared by every skin). */
    val grain: ImageBitmap? = null,
) {
    /** The picture of [control] (pressed, when there is one), or null when it is drawn by code. */
    fun part(
        control: SkinControl,
        pressed: Boolean,
    ): ImageBitmap? {
        val key = artKey(control)
        return (if (pressed) parts["${key}_pressed"] else null) ?: parts[key]
    }

    fun has(control: SkinControl): Boolean = parts.containsKey(artKey(control))

    companion object {
        /** The folder of [controller]'s artwork for one orientation, in the assets. */
        fun folder(
            controller: String,
            landscape: Boolean,
        ) = "skin-art/$controller/${if (landscape) "landscape" else "portrait"}"

        /** The file name (without extension) of the body's picture. */
        const val BODY = "body"

        /** The file name (without extension) of the shared grain tile, in `skin-art/`. */
        const val GRAIN = "grain"
    }
}

/**
 * How far (in layout units) a control's picture reaches beyond its touch box (its frame plus its
 * reach) on every side, for its shadow.
 */
const val ART_BLEED = 10f

/** The name of [control]'s picture: what kind of control it is, its key and its name. */
fun artKey(control: SkinControl): String =
    when (control) {
        is SkinControl.Button -> {
            val name = control.label.filter { it.isLetterOrDigit() }.lowercase()
            "button_${control.keyCode}_$name"
        }
        is SkinControl.Dpad -> "dpad_${control.source}_${control.look.name.lowercase()}"
        is SkinControl.Stick -> "stick_${control.source}"
        is SkinControl.Menu -> "menu"
        is SkinControl.Rewind -> "rewind"
    }

private val PictureSymbols = setOf("△", "○", "✕", "□")

/**
 * True when the picture of [control] carries its name, moulded into the plastic (the app then
 * does not write it): buttons named with letters or numbers, on them rather than under them.
 * Pictures, PlayStation symbols and names under small buttons are still drawn by the app.
 */
fun artCarriesLabel(control: SkinControl): Boolean =
    control is SkinControl.Button &&
        control.icon == null &&
        control.label.isNotBlank() &&
        control.label !in PictureSymbols &&
        (!control.labelBelow || control.shape == ButtonShape.SHOULDER)
