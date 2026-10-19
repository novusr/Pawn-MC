package com.rvdjv.pawnmc.`interface`.theme

import androidx.compose.ui.graphics.Color

// Dark 
// The ramp keeps a violet-blue cast on every surface: the old values drifted to a
// flat near-black grey, so the scheme now reads as a purple-blue night while the
// elevation steps stay visible. Accents keep their hue but are slightly deepened so
// they do not glow against the tinted background.
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
val md_theme_dark_background = Color(0xFF110D1C)
val md_theme_dark_onBackground = Color(0xFFE4DDF7)
val md_theme_dark_surface = Color(0xFF151021)
val md_theme_dark_onSurface = Color(0xFFE4DDF7)
val md_theme_dark_surfaceVariant = Color(0xFF1C1630)
val md_theme_dark_onSurfaceVariant = Color(0xFF9E95BB)
val md_theme_dark_outline = Color(0xFF6E6880)
val md_theme_dark_outlineVariant = Color(0xFF292143)
// Explicit surface elevation roles so every area keeps a visible depth step,
// mirroring the tonal ramp used by the light scheme.
val md_theme_dark_surfaceDim = Color(0xFF110D1C)
val md_theme_dark_surfaceBright = Color(0xFF272038)
val md_theme_dark_surfaceContainerLowest = Color(0xFF0C0915)
val md_theme_dark_surfaceContainerLow = Color(0xFF151021)
val md_theme_dark_surfaceContainer = Color(0xFF1A142B)
val md_theme_dark_surfaceContainerHigh = Color(0xFF211A35)
val md_theme_dark_surfaceContainerHighest = Color(0xFF28203F)
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
// The light scheme was originally flat (background == surface, almost-white
// surfaceVariant). It then became a normal light elevation ramp; as of the current
// design it follows the dark scheme instead: the page background and every
// container/box surface (compiler output box, file-picker box, editor, and
// everything else the app configures) use the same dark semi-violet tonal ramp as
// the dark theme. Light mode is now effectively the dark semi-violet palette while
// remaining a distinct selectable theme, so choosing light vs. dark no longer
// changes the look of boxes — both themes render them in dark semi-violet.
val md_theme_light_background = Color(0xFF110D1C)
val md_theme_light_onBackground = Color(0xFFE4DDF7)
val md_theme_light_surface = Color(0xFF151021)
val md_theme_light_onSurface = Color(0xFFE4DDF7)
val md_theme_light_surfaceVariant = Color(0xFF1C1630)
val md_theme_light_onSurfaceVariant = Color(0xFF9E95BB)
val md_theme_light_outline = Color(0xFF7A7488)
val md_theme_light_outlineVariant = Color(0xFF292143)
val md_theme_light_surfaceDim = Color(0xFF110D1C)
val md_theme_light_surfaceBright = Color(0xFF272038)
val md_theme_light_surfaceContainerLowest = Color(0xFF0C0915)
val md_theme_light_surfaceContainerLow = Color(0xFF151021)
val md_theme_light_surfaceContainer = Color(0xFF1A142B)
val md_theme_light_surfaceContainerHigh = Color(0xFF211A35)
val md_theme_light_surfaceContainerHighest = Color(0xFF28203F)
val md_theme_light_surfaceTint = Color(0xFFC3ADFF)
val md_theme_light_scrim = Color(0xFF000000)
val md_theme_light_inverseSurface = Color(0xFFE4DDF7)
val md_theme_light_inverseOnSurface = Color(0xFF211C30)
val md_theme_light_inversePrimary = Color(0xFF5B3FA8)