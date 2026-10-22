package com.pocketsamp.server.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
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
    // Every screen pins its text with literal `sp` values (12.sp, 14.sp, 20.sp ...).
    // Those sp units follow the *font scale*, and the dashboards were designed for
    // 1.0x: at Android's 1.3x/1.5x accessibility settings the same layout overflowed,
    // labels clipped and the bottom tab rail pushed content off screen. The embedded
    // runtime therefore caps the scale it accepts instead of stretching every row.
    val density = LocalDensity.current
    val effectiveScale = density.fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)

    CompositionLocalProvider(
        LocalDensity provides Density(density.density, effectiveScale)
    ) {
        MaterialTheme(
            colorScheme = if (isPocketSampDarkTheme()) DarkColorScheme else LightColorScheme,
            typography = pocketSampTypography(effectiveScale),
            content = content
        )
    }
}

/**
 * Smallest text each type role is allowed to render at, in `sp`.
 *
 * Below this the labels stop being readable regardless of scale, so the typography
 * floors them instead of letting a multiplied `labelSmall` collapse to a few pixels.
 */
private const val MIN_LABEL_SM = 10f
private const val MIN_BODY_SM = 11f
private const val MIN_BODY_MD = 12f
private const val MIN_TITLE_SM = 13f
private const val MIN_TITLE_MD = 15f

/** Font scale accepted by the embedded UI: shrunk, never stretched past a small bump. */
const val MIN_FONT_SCALE = 0.85f
const val MAX_FONT_SCALE = 1.15f

/**
 * Typography whose line heights are tied to the scaled sizes.
 *
 * Material's defaults keep a fixed absolute line height, so a scaled font would draw
 * on top of the next line in the dashboards that lay rows out tightly. Building every
 * role from the scaled size keeps the row height proportional, which is what removes
 * the clipping without touching the layouts.
 */
@Composable
fun pocketSampTypography(scale: Float = LocalDensity.current.fontScale): Typography {
    fun role(size: Float, floor: Float): TextUnit =
        (size * scale).coerceAtLeast(floor).sp
    fun height(size: Float, floor: Float, ratio: Float = 1.3f): TextUnit =
        ((size * scale).coerceAtLeast(floor) * ratio).sp

    val base = Typography()
    return base.copy(
        labelSmall = base.labelSmall.copy(fontSize = role(11f, MIN_LABEL_SM), lineHeight = height(11f, MIN_LABEL_SM)),
        labelMedium = base.labelMedium.copy(fontSize = role(12f, MIN_BODY_SM), lineHeight = height(12f, MIN_BODY_SM)),
        labelLarge = base.labelLarge.copy(fontSize = role(14f, MIN_BODY_MD), lineHeight = height(14f, MIN_BODY_MD)),
        bodySmall = base.bodySmall.copy(fontSize = role(12f, MIN_BODY_MD), lineHeight = height(12f, MIN_BODY_MD)),
        bodyMedium = base.bodyMedium.copy(fontSize = role(14f, MIN_BODY_MD), lineHeight = height(14f, MIN_BODY_MD)),
        bodyLarge = base.bodyLarge.copy(fontSize = role(16f, MIN_TITLE_SM), lineHeight = height(16f, MIN_TITLE_SM)),
        titleSmall = base.titleSmall.copy(fontSize = role(14f, MIN_TITLE_SM), lineHeight = height(14f, MIN_TITLE_SM)),
        titleMedium = base.titleMedium.copy(fontSize = role(16f, MIN_TITLE_MD), lineHeight = height(16f, MIN_TITLE_MD)),
        titleLarge = base.titleLarge.copy(fontSize = role(22f, 18f), lineHeight = height(22f, 18f))
    )
}
