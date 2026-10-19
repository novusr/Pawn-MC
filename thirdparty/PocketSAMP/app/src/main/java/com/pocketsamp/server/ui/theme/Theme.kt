package com.pocketsamp.server.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pocketsamp.server.PocketSampLocales

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    secondary = AccentOrange,
    background = BgDark,
    surface = SurfaceDark,
    onPrimary = BgDark,
    onSecondary = BgDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = AccentCyanDarkText,
    secondary = AccentOrangeDarkText,
    background = BgLight,
    surface = SurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight
)

/** True when the embedded UI should paint the dark semi-violet look. */
@Composable
fun isPocketSampDarkTheme(): Boolean = when (PocketSampLocales.theme) {
    PocketSampLocales.Theme.DARK -> true
    PocketSampLocales.Theme.LIGHT -> false
    PocketSampLocales.Theme.SYSTEM -> isSystemInDarkTheme()
}

/**
 * Semantic palette for the current theme. Screens read [SampThemeColors] instead
 * of raw constants so the whole embedded UI adapts to PawnMC's theme setting;
 * the dark look remains the semi-violet ramp, identical to the main interface.
 */
object SampThemeColors {
    val bg: Color @Composable get() = if (isPocketSampDarkTheme()) BgDark else BgLight
    val surface: Color @Composable get() = if (isPocketSampDarkTheme()) SurfaceDark else SurfaceLight
    val card: Color @Composable get() = if (isPocketSampDarkTheme()) SurfaceCard else SurfaceCardLight
    val cardBorder: Color @Composable get() = if (isPocketSampDarkTheme()) SurfaceCardBorder else SurfaceCardBorderLight
    val textPrimary: Color @Composable get() = if (isPocketSampDarkTheme()) TextPrimary else TextPrimaryLight
    val textSecondary: Color @Composable get() = if (isPocketSampDarkTheme()) TextSecondary else TextSecondaryLight
    val textMuted: Color @Composable get() = if (isPocketSampDarkTheme()) TextMuted else TextMutedLight
    val accent: Color @Composable get() = if (isPocketSampDarkTheme()) AccentCyan else AccentCyanDarkText
    val accentOn: Color @Composable get() = if (isPocketSampDarkTheme()) BgDark else Color.White
    val warn: Color @Composable get() = if (isPocketSampDarkTheme()) AccentOrange else AccentOrangeDarkText
    val success: Color @Composable get() = if (isPocketSampDarkTheme()) StatusGreen else StatusGreenDarkText
    val danger: Color @Composable get() = if (isPocketSampDarkTheme()) StatusRed else StatusRedDarkText
    val terminal: Color @Composable get() = TerminalBg
}

@Composable
fun PocketSAMPTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isPocketSampDarkTheme()) DarkColorScheme else LightColorScheme,
        content = content
    )
}
