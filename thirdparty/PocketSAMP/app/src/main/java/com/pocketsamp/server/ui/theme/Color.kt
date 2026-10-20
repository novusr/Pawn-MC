package com.pocketsamp.server.ui.theme

import androidx.compose.ui.graphics.Color

// Semi violet/blue-purple dark ramp: every surface keeps a faint violet cast so the
// theme reads as a purple-blue night, matching the PawnMC main interface dark theme.
// A light ramp mirrors PawnMC's light theme (white surfaces, same violet family).
// The accent stays cyan because the SA-MP status chrome is built around it.
val BgDark = Color(0xFF120D1F)
val SurfaceDark = Color(0xFF1A1429)
val SurfaceCard = Color(0xFF241B38)
val SurfaceCardBorder = Color(0xFF332748)

// Light ramp stays white with subtle neutral/violet elevation.
val BgLight = Color(0xFFFFFBFF)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceCardLight = Color(0xFFF8F6FA)
val SurfaceCardBorderLight = Color(0xFFE6E0EA)

val AccentCyan = Color(0xFF00E5FF)
val AccentCyanDarkText = Color(0xFF006A8B) // readable cyan tint on light surfaces
val AccentOrange = Color(0xFFFF9800)
val AccentOrangeDarkText = Color(0xFF9A5B00)
val StatusGreen = Color(0xFF00E676)
val StatusGreenDarkText = Color(0xFF006E2B)
val StatusRed = Color(0xFFFF5252)
val StatusRedDarkText = Color(0xFFB3261E)

val TextPrimary = Color(0xFFEFEBFA)
val TextSecondary = Color(0xFFA79FC0)
val TextMuted = Color(0xFF756C90)

val TextPrimaryLight = Color(0xFF1D1A26)
val TextSecondaryLight = Color(0xFF57506B)
val TextMutedLight = Color(0xFF7C7590)

// Terminal/log canvas: near-black in both themes (a console is expected to be dark
// and this keeps the log colour coding readable).
val TerminalBg = Color(0xFF07080B)
