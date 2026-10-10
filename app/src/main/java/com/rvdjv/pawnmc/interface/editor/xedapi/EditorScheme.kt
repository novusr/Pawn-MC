
package com.rvdjv.pawnmc.`interface`.editor.xedapi

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse

/**
 * Colour scheme construction for the code canvas.
 *
 * Split out of `EditorScreen.kt`: everything here is a pure function of the app theme and
 * the optional user-picked canvas colour, with no Compose state and no widget access, so
 * it can be changed — and reasoned about — without the screen. Two decisions live here:
 *
 *  - the editor is tinted from the *canvas* rather than from the app theme, so a user who
 *    pins a dark canvas while the app is in light mode still gets light-on-dark text;
 *  - the shipped Darcula/Eclipse token colours are replaced by a palette whose hues are
 *    separated by kind, because the stock ones sit within a couple of shades of the plain
 *    text colour and read as one grey wall.
 */
internal class EditorSchemeHolder {
    var current: EditorColorScheme? = null
}

/**
 * Builds the sora-editor scheme for the current theme.
 *
 * Darcula/Eclipse ship with their own hard-coded backgrounds, which used to make the
 * code area look like an unrelated white block inside the light theme. We keep the
 * syntax highlighting of the chosen scheme, but re-tint the surface family (code
 * background, current line, line-number panel, dividers, scrollbars) from the app
 * color scheme so the editor sits on the same elevation step as the surrounding UI.
 *
 * [customBackground] lets the user override the canvas with any `#RRGGBB` colour.
 * When it is set, the whole surface family is re-derived from that single colour so
 * the panel stays internally consistent (gutter, current line and dividers) instead
 * of clashing with a colour picked for the canvas alone.
 *
 * Every decision is driven by the canvas that will actually be painted rather than
 * by the app theme, so a user who pins a dark canvas while the app is in light mode
 * still gets a light-on-dark editor and vice versa.
 */
internal fun buildEditorScheme(
    colorScheme: ColorScheme,
    customBackground: String? = null
): EditorColorScheme {
    val customArgb = customBackground?.let { parseHexColor(it) }

    // Re-derive the gutter and the accents from whichever canvas is in play, so
    // a custom colour produces a coherent editor instead of a mismatched one.
    val background: Int
    val lineNumberBackground: Int
    val outline: Int
    val faint: Int
    if (customArgb != null) {
        val onCustom = readableForegroundOn(customArgb)
        background = customArgb
        lineNumberBackground = blend(customArgb, onCustom, 0.06f)
        outline = blend(customArgb, onCustom, 0.22f)
        faint = blend(customArgb, onCustom, 0.10f)
    } else {
        background = colorScheme.surfaceContainerLowest.toArgb()
        lineNumberBackground = colorScheme.surfaceContainerLow.toArgb()
        outline = colorScheme.outlineVariant.toArgb()
        faint = colorScheme.onSurface.copy(alpha = 0.10f).toArgb()
    }

    // Pick the base scheme from the canvas rather than the app theme, so slots
    // the palette below does not explicitly set (selection, cursors, search
    // highlights) also follow the canvas.
    val darkCanvas = isDarkCanvas(background)
    val base: EditorColorScheme = if (darkCanvas) SchemeDarcula() else SchemeEclipse()

    applyVividSyntaxPalette(base, darkCanvas)

    base.setColor(EditorColorScheme.WHOLE_BACKGROUND, background)
    base.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, lineNumberBackground)
    base.setColor(EditorColorScheme.LINE_NUMBER_PANEL, lineNumberBackground)
    base.setColor(EditorColorScheme.LINE_DIVIDER, outline)
    base.setColor(EditorColorScheme.BLOCK_LINE, outline)
    base.setColor(EditorColorScheme.CURRENT_LINE, faint)
    base.setColor(EditorColorScheme.SCROLL_BAR_TRACK, lineNumberBackground)
    base.setColor(EditorColorScheme.SCROLL_BAR_THUMB, outline)
    return base
}

/**
 * High-contrast, high-chroma syntax palette.
 *
 * Darcula and Eclipse ship muted token colours that are hard to tell apart at
 * a glance: `IDENTIFIER_NAME`, `TYPE` and `OPERATOR` in particular all sit
 * within a couple of shades of the plain text colour, so the editor reads as a
 * wall of grey. This palette replaces every token slot the Pawn analyzer uses
 * with a clearly separated hue, while keeping each hue on the correct side of
 * the background so contrast holds in both light and dark mode.
 *
 * The assignment is stable per token kind, so a given line never changes colour
 * between re-analyses (the analyzer runs asynchronously on every keystroke).
 */
private fun applyVividSyntaxPalette(scheme: EditorColorScheme, darkCanvas: Boolean) {
    val p = if (darkCanvas) DarkPalette else LightPalette

    scheme.setColor(EditorColorScheme.KEYWORD, p.keyword)
    scheme.setColor(EditorColorScheme.IDENTIFIER_NAME, p.type)
    scheme.setColor(EditorColorScheme.LITERAL, p.literal)
    scheme.setColor(EditorColorScheme.FUNCTION_NAME, p.function)
    scheme.setColor(EditorColorScheme.OPERATOR, p.operator)
    scheme.setColor(EditorColorScheme.ANNOTATION, p.annotation)
    scheme.setColor(EditorColorScheme.COMMENT, p.comment)
    scheme.setColor(EditorColorScheme.LINE_NUMBER, p.lineNumber)
    // Plain identifiers (variables, labels) inherit the scheme's text colour, so
    // it has to be re-pointed at the canvas as well. Without this a custom dark
    // canvas under a light app theme would render every variable dark-on-dark.
    scheme.setColor(
        EditorColorScheme.TEXT_NORMAL,
        if (darkCanvas) 0xFFF2F5FA.toInt() else 0xFF111418.toInt()
    )
}

/**
 * Token colours for the dark canvas.
 *
 * Hues are chosen so adjacent kinds never share a family: keywords are amber,
 * types are cyan, literals are orange, functions are blue, operators are
 * magenta, annotations are violet and comments are neutral grey.
 */
private data class SyntaxPalette(
    val keyword: Int,
    val type: Int,
    val literal: Int,
    val function: Int,
    val operator: Int,
    val annotation: Int,
    val comment: Int,
    val lineNumber: Int
)

private val DarkPalette = SyntaxPalette(
    keyword = 0xFFFFD866.toInt(),
    type = 0xFF6FE3F5.toInt(),
    literal = 0xFFFFB07C.toInt(),
    function = 0xFF8AB4F8.toInt(),
    operator = 0xFFE6A8FF.toInt(),
    annotation = 0xFF7EE081.toInt(),
    comment = 0xFF9CA3AF.toInt(),
    lineNumber = 0xFF7E8CA3.toInt()
)

/**
 * Token colours for the light canvas.
 *
 * The same hue assignment as [DarkPalette] but darkened so the contrast ratio
 * against a white canvas stays readable.
 */
private val LightPalette = SyntaxPalette(
    keyword = 0xFF9A3412.toInt(),
    type = 0xFF005E70.toInt(),
    literal = 0xFFB42318.toInt(),
    function = 0xFF1D4ED8.toInt(),
    operator = 0xFF6B21C8.toInt(),
    annotation = 0xFF16753B.toInt(),
    comment = 0xFF616161.toInt(),
    lineNumber = 0xFF64748B.toInt()
)

/**
 * Parses a `#RRGGBB` string into an ARGB int, returning `null` for anything else.
 *
 * The stored preference is already validated by
 * [com.rvdjv.pawnmc.data.config.CompilerConfig.normalizeEditorBackgroundColor];
 * this is the second gate that keeps a malformed value from reaching the
 * editor scheme, and it never throws.
 */
private fun parseHexColor(hex: String): Int? {
    val digits = hex.trim().removePrefix("#")
    if (digits.length != 6) return null
    val value = digits.toIntOrNull(16) ?: return null
    return 0xFF000000.toInt() or value
}

/**
 * Picks black or white — whichever contrasts more with [background].
 *
 * Uses the ITU-R BT.601 luma approximation, which is the same weighting the
 * Material colour system uses for `onColor` decisions, so a custom background
 * the user picked for a dark project still gets light text.
 */
private fun readableForegroundOn(background: Int): Int {
    val r = (background shr 16) and 0xFF
    val g = (background shr 8) and 0xFF
    val b = background and 0xFF
    val luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    return if (luma > 0.5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
}

/**
 * True when an opaque ARGB canvas is dark enough to need the light-on-dark
 * token colours.
 *
 * The same BT.601 luma threshold as [readableForegroundOn], so the token
 * palette and the automatically chosen foreground can never disagree.
 */
private fun isDarkCanvas(argb: Int): Boolean = readableForegroundOn(argb) == 0xFFFFFFFF.toInt()

/** Linearly mixes [amount] of [tint] into [base]; `amount` is clamped to 0..1. */
private fun blend(base: Int, tint: Int, amount: Float): Int {
    val t = amount.coerceIn(0f, 1f)
    fun mix(shift: Int): Int {
        val b = (base shr shift) and 0xFF
        val c = (tint shr shift) and 0xFF
        return (b + (c - b) * t).toInt().coerceIn(0, 255)
    }
    return 0xFF000000.toInt() or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}
