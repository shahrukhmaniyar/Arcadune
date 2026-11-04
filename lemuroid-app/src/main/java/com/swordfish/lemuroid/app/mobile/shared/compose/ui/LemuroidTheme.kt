package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

private val LightColorScheme =
    lightColorScheme(
        primary = md_theme_light_primary,
        onPrimary = md_theme_light_onPrimary,
        primaryContainer = md_theme_light_primaryContainer,
        onPrimaryContainer = md_theme_light_onPrimaryContainer,
        secondary = md_theme_light_secondary,
        onSecondary = md_theme_light_onSecondary,
        secondaryContainer = md_theme_light_secondaryContainer,
        onSecondaryContainer = md_theme_light_onSecondaryContainer,
        tertiary = md_theme_light_tertiary,
        onTertiary = md_theme_light_onTertiary,
        tertiaryContainer = md_theme_light_tertiaryContainer,
        onTertiaryContainer = md_theme_light_onTertiaryContainer,
        error = md_theme_light_error,
        errorContainer = md_theme_light_errorContainer,
        onError = md_theme_light_onError,
        onErrorContainer = md_theme_light_onErrorContainer,
        background = md_theme_light_background,
        onBackground = md_theme_light_onBackground,
        surface = md_theme_light_surface,
        onSurface = md_theme_light_onSurface,
        surfaceVariant = md_theme_light_surfaceVariant,
        onSurfaceVariant = md_theme_light_onSurfaceVariant,
        outline = md_theme_light_outline,
        inverseOnSurface = md_theme_light_inverseOnSurface,
        inverseSurface = md_theme_light_inverseSurface,
        inversePrimary = md_theme_light_inversePrimary,
        surfaceTint = md_theme_light_surfaceTint,
        outlineVariant = md_theme_light_outlineVariant,
        scrim = md_theme_light_scrim,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = md_theme_dark_primary,
        onPrimary = md_theme_dark_onPrimary,
        primaryContainer = md_theme_dark_primaryContainer,
        onPrimaryContainer = md_theme_dark_onPrimaryContainer,
        secondary = md_theme_dark_secondary,
        onSecondary = md_theme_dark_onSecondary,
        secondaryContainer = md_theme_dark_secondaryContainer,
        onSecondaryContainer = md_theme_dark_onSecondaryContainer,
        tertiary = md_theme_dark_tertiary,
        onTertiary = md_theme_dark_onTertiary,
        tertiaryContainer = md_theme_dark_tertiaryContainer,
        onTertiaryContainer = md_theme_dark_onTertiaryContainer,
        error = md_theme_dark_error,
        errorContainer = md_theme_dark_errorContainer,
        onError = md_theme_dark_onError,
        onErrorContainer = md_theme_dark_onErrorContainer,
        background = md_theme_dark_background,
        onBackground = md_theme_dark_onBackground,
        surface = md_theme_dark_surface,
        onSurface = md_theme_dark_onSurface,
        surfaceVariant = md_theme_dark_surfaceVariant,
        onSurfaceVariant = md_theme_dark_onSurfaceVariant,
        outline = md_theme_dark_outline,
        inverseOnSurface = md_theme_dark_inverseOnSurface,
        inverseSurface = md_theme_dark_inverseSurface,
        inversePrimary = md_theme_dark_inversePrimary,
        surfaceTint = md_theme_dark_surfaceTint,
        outlineVariant = md_theme_dark_outlineVariant,
        scrim = md_theme_dark_scrim,
    )

/**
 * The accent colours the player can pick. All are soft pastels: on a dark background strong,
 * saturated colours seem to glow and vibrate, which tires the eyes (Material's dark theme
 * guidance), so each one is a light, desaturated tone that stays easy to read.
 */
enum class ArcaduneAccent(val color: Color, val onColor: Color, val container: Color, val titleRes: Int) {
    VIOLET(Color(0xFFBB9CFF), Color(0xFF1E0F3D), Color(0xFF3B2A63), com.swordfish.lemuroid.R.string.theme_color_violet),
    TEAL(Color(0xFF5FD4C4), Color(0xFF00201C), Color(0xFF1C4A44), com.swordfish.lemuroid.R.string.theme_color_teal),
    BLUE(Color(0xFF8AB4F8), Color(0xFF0B1D3A), Color(0xFF243D66), com.swordfish.lemuroid.R.string.theme_color_blue),
    MINT(Color(0xFF81D9A3), Color(0xFF052313), Color(0xFF1F4A30), com.swordfish.lemuroid.R.string.theme_color_mint),
    AMBER(Color(0xFFF6C177), Color(0xFF2B1A00), Color(0xFF5A4012), com.swordfish.lemuroid.R.string.theme_color_amber),
    ;

    companion object {
        private const val PREF_KEY = "arcadune_accent"

        /** The chosen accent. Reading it inside a composable redraws that part when it changes. */
        val current = mutableStateOf(VIOLET)

        // Read again by every screen that opens: the game runs in its own process, which would
        // otherwise keep the colour it saw when it started.
        fun load(context: Context): ArcaduneAccent {
            val name = SharedPreferencesHelper.getSharedPreferences(context).getString(PREF_KEY, null)
            current.value = entries.firstOrNull { it.name == name } ?: VIOLET
            return current.value
        }

        fun choose(
            context: Context,
            accent: ArcaduneAccent,
        ) {
            current.value = accent
            SharedPreferencesHelper.getSharedPreferences(context).edit().putString(PREF_KEY, accent.name).apply()
        }
    }
}

/** Arcadune's own look: always dark, with the accent the player chose. */
object ArcaduneColors {
    val Background = Color(0xFF0B0A12)
    val Surface = Color(0xFF15131F)
    val SurfaceHigh = Color(0xFF1F1C2D)

    /** The accent colour (named Purple from when it was the only one). */
    val Purple: Color get() = ArcaduneAccent.current.value.color
    val OnAccent: Color get() = ArcaduneAccent.current.value.onColor
    val Pink = Color(0xFFFF4FB7)
    val Orange = Color(0xFFFF9A3C)
    val OnSurface = Color(0xFFF2EEFA)
    val OnSurfaceDim = Color(0xFFA7A1B8)

    /** Fill and edge of the floating "glass" bars. */
    val Glass = Color(0xE01C1A27)
    val GlassEdge = Color(0x33FFFFFF)
}

private fun arcaduneColorScheme(accent: ArcaduneAccent) =
    darkColorScheme(
        primary = accent.color,
        onPrimary = accent.onColor,
        primaryContainer = accent.container,
        onPrimaryContainer = Color(0xFFF2EEFA),
        secondary = accent.color,
        onSecondary = accent.onColor,
        secondaryContainer = accent.container,
        onSecondaryContainer = Color(0xFFF2EEFA),
        tertiary = accent.color,
        onTertiary = accent.onColor,
        background = ArcaduneColors.Background,
        onBackground = ArcaduneColors.OnSurface,
        surface = ArcaduneColors.Background,
        onSurface = ArcaduneColors.OnSurface,
        surfaceVariant = ArcaduneColors.SurfaceHigh,
        onSurfaceVariant = ArcaduneColors.OnSurfaceDim,
        surfaceContainerLowest = ArcaduneColors.Background,
        surfaceContainerLow = ArcaduneColors.Surface,
        surfaceContainer = ArcaduneColors.Surface,
        surfaceContainerHigh = ArcaduneColors.SurfaceHigh,
        surfaceContainerHighest = Color(0xFF292538),
        outline = Color(0xFF4A4559),
        outlineVariant = Color(0xFF2E2A3B),
    )

@Composable
fun AppTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    remember { ArcaduneAccent.load(context.applicationContext) }
    val accent = ArcaduneAccent.current.value
    val scheme = remember(accent) { arcaduneColorScheme(accent) }
    MaterialTheme(colorScheme = scheme) {
        content()
    }
}
