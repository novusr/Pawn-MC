package com.rvdjv.pawnmc.`interface`.theme

import androidx.compose.ui.graphics.Color

// Dark 
// The dark scheme used to sit around #13111C / #1C1829, which reads as a washed
// out semi-dark grey instead of a real dark theme. The ramp below starts from
// true near-black (#050409) and only steps up a few percent per elevation role,
// so the surface hierarchy stays visible while the whole scheme is genuinely
// dark. Accents keep their hue but are slightly deepened so they do not glow
// against the darker background.
val md_theme_dark_primary = Color(0xFFC3ADFF)
val md_theme_dark_onPrimary = Color(0xFF150F22)
val md_theme_dark_primaryContainer = Color(0xFF3A2C68)
val md_theme_dark_onPrimaryContainer = Color(0xFFE4DDF7)
val md_theme_dark_secondary = Color(0xFF8C86A3)
val md_theme_dark_onSecondary = Color(0xFF150F22)
val md_theme_dark_secondaryContainer = Color(0xFF201A2C)
val md_theme_dark_onSecondaryContainer = Color(0xFFE4DDF7)
val md_theme_dark_tertiary = Color(0xFFB7A6F5)
val md_theme_dark_onTertiary = Color(0xFF150F22)
val md_theme_dark_tertiaryContainer = Color(0xFF2C2340)
val md_theme_dark_onTertiaryContainer = Color(0xFFE4DDF7)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF050409)
val md_theme_dark_onBackground = Color(0xFFE4DDF7)
val md_theme_dark_surface = Color(0xFF08070E)
val md_theme_dark_onSurface = Color(0xFFE4DDF7)
val md_theme_dark_surfaceVariant = Color(0xFF12101B)
val md_theme_dark_onSurfaceVariant = Color(0xFF928CA5)
val md_theme_dark_outline = Color(0xFF6E6880)
val md_theme_dark_outlineVariant = Color(0xFF1F1B2C)
// Explicit surface elevation roles so every area keeps a visible depth step,
// mirroring the tonal ramp used by the light scheme.
val md_theme_dark_surfaceDim = Color(0xFF050409)
val md_theme_dark_surfaceBright = Color(0xFF22202F)
val md_theme_dark_surfaceContainerLowest = Color(0xFF020205)
val md_theme_dark_surfaceContainerLow = Color(0xFF08070E)
val md_theme_dark_surfaceContainer = Color(0xFF0D0B15)
val md_theme_dark_surfaceContainerHigh = Color(0xFF14111E)
val md_theme_dark_surfaceContainerHighest = Color(0xFF1B1727)
val md_theme_dark_surfaceTint = Color(0xFFC3ADFF)
val md_theme_dark_scrim = Color(0xFF000000)
val md_theme_dark_inverseSurface = Color(0xFFE4DDF7)
val md_theme_dark_inverseOnSurface = Color(0xFF211C30)
val md_theme_dark_inversePrimary = Color(0xFF5B3FA8)

val status_success = Color(0xFF4ADE80)
val status_success_container = Color(0xFF1E3A2C)
val status_error = Color(0xFFF87171)
val status_error_container = Color(0xFF3F1D24)
val status_idle = Color(0xFF8B8699)

//light
val md_theme_light_primary = Color(0xFF6D4DB8)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFE9DDFF)
val md_theme_light_onPrimaryContainer = Color(0xFF250F52)
val md_theme_light_secondary = Color(0xFF5F5A6B)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFE7E0F0)
val md_theme_light_onSecondaryContainer = Color(0xFF1C1829)
val md_theme_light_tertiary = Color(0xFF7C5DC7)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFEBE0FF)
val md_theme_light_onTertiaryContainer = Color(0xFF271A45)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onErrorContainer = Color(0xFF410002)
// The light scheme used to be flat (background == surface, almost-white surfaceVariant),
// which made cards, toolbars, tonal buttons and the editor blend into one bright mass.
// It now follows a real elevation ramp: the page is slightly tinted, surfaces are
// brighter, and containers step down in tone from Lowest to Highest.
val md_theme_light_background = Color(0xFFF3EDF7)
val md_theme_light_onBackground = Color(0xFF1C1829)
val md_theme_light_surface = Color(0xFFFBF8FF)
val md_theme_light_onSurface = Color(0xFF1C1829)
val md_theme_light_surfaceVariant = Color(0xFFE4DDEA)
val md_theme_light_onSurfaceVariant = Color(0xFF575163)
val md_theme_light_outline = Color(0xFF7A7488)
val md_theme_light_outlineVariant = Color(0xFFCDC6D6)
val md_theme_light_surfaceDim = Color(0xFFDCD5E1)
val md_theme_light_surfaceBright = Color(0xFFFBF8FF)
val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFFBF8FF)
val md_theme_light_surfaceContainer = Color(0xFFF6F1F9)
val md_theme_light_surfaceContainerHigh = Color(0xFFF1EBF4)
val md_theme_light_surfaceContainerHighest = Color(0xFFEAE3EF)
val md_theme_light_surfaceTint = Color(0xFF6D4DB8)
val md_theme_light_scrim = Color(0xFF000000)
val md_theme_light_inverseSurface = Color(0xFF322C3F)
val md_theme_light_inverseOnSurface = Color(0xFFF5EFF8)
val md_theme_light_inversePrimary = Color(0xFFCFBCFF)