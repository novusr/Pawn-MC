package com.rvdjv.pawnmc.user_interface.editor.xedapi

import com.rvdjv.pawnmc.user_interface.editor.syntax.HIGHLIGHT_COMMENT_ID
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
 * it can be changed - and reasoned about - without the screen. Two decisions live here:
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
    // The gutter scrollbar is drawn by `GutterScrollbar`, so sora's built-in bars stay
    // transparent: two bars over each other would fight for the same edge.
    base.setColor(EditorColorScheme.SCROLL_BAR_TRACK, 0x00000000)
    base.setColor(EditorColorScheme.SCROLL_BAR_THUMB, 0x00000000)
    return base
}

/**
 * High-contrast, high-chroma syntax palette.
 *
 * Darcula and Eclipse ship muted token colours that are hard to tell apart at
 * a glance: `IDENTIFIER_NAME`, `TYPE` and `OPERATOR` in particular all sit
 * within a couple of shades of the plain text colour, so the editor reads as a
 * wall of grey. This palette replaces every token slot the Pawn analyzer uses
 * with a clearly separated hue. On a dark canvas the hues are light and on a
 * light canvas they are saturated mid-tones, so in both themes every token
 * kind is unmistakably coloured rather than a shade of the plain text ink.
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
    // The four-slash highlight comment (`////`) paints through this custom id (the same
    // offset the analyzer uses) with a bright yellow that stays readable on both canvases.
    scheme.setColor(HIGHLIGHT_COMMENT_ID, p.highlightComment)
    scheme.setColor(EditorColorScheme.LINE_NUMBER, p.lineNumber)

    // `BRACKET`, `STRING`, `NUMBER` and `COMMENT_KEYWORD` do not exist in the bundled sora
    // version. The analyzer therefore reaches its extra hues through the slots above:
    // `ANNOTATION` carries the dense orange for brackets, `LITERAL` serves strings and
    // numbers, and `KEYWORD` serves directives and comment keywords.
    // Highlight comments (`////`) paint through the custom HIGHLIGHT_COMMENT_ID slot
    // set above, so the explainer markers and any hand-written `////` line read as their
    // own kind - bright yellow on a dark canvas, deep amber on a light one - rather than
    // the muted comment grey.
    // Plain identifiers (variables, labels) inherit the scheme's text colour, so
    // it has to be re-pointed at the canvas as well. Without this a custom dark
    // canvas under a light app theme would render every variable dark-on-dark.
    scheme.setColor(
        EditorColorScheme.TEXT_NORMAL,
        if (darkCanvas) 0xFFF2F5FA.toInt() else 0xFF111418.toInt()
    )
}

/**
 * Token colours for one canvas.
 *
 * Every hue is warm and each kind keeps its own family - keywords amber, functions gold,
 * types peach, strings tangerine, brackets a dense orange - so no two kinds read as the
 * same colour while the page as a whole stays in the orange/yellow band.
 */
private data class SyntaxPalette(
    val keyword: Int,
    val type: Int,
    val literal: Int,
    val function: Int,
    val operator: Int,
    val annotation: Int,
    val comment: Int,
    val highlightComment: Int,
    val lineNumber: Int,
    val tag: Int
)

/**
 * Custom color id for the four-slash highlight comment.
 *
 * Offset 256, the id pool the [EditorColorScheme] documentation recommends for custom
 * languages; the analyzer emits [io.github.rosemoe.sora.lang.styling.TextStyle] against
 * the same id, so the slot is shared by exactly one producer and one consumer.
 */
internal const val HIGHLIGHT_COMMENT_ID = 256

/**
 * Warm dark palette.
 *
 * The blues and violets this scheme used to carry are gone: keywords are bright amber,
 * functions a warm gold, types a soft peach and strings a deep tangerine, while brackets -
 * `(`, `)`, `{`, `}` - get their own saturated orange so structural characters stand out
 * from both keywords and operators. Numbers lean to a warm rose and comments to a warm
 * grey-green, which keeps the text readable without introducing a cold hue again.
 */
private val DarkPalette = SyntaxPalette(
    keyword = 0xFFFFC94A.toInt(),
    type = 0xFFFFB07C.toInt(),
    literal = 0xFFFF8A5B.toInt(),
    function = 0xFFFFD27A.toInt(),
    operator = 0xFFFF7A00.toInt(),
    // Brackets are painted through this slot, so it carries the dense orange.
    annotation = 0xFFFF7A00.toInt(),
    comment = 0xFF8E9B87.toInt(),
    // Bright yellow for `////` highlight comments - clearly a comment, unmistakably
    // not the muted grey of a normal one.
    highlightComment = 0xFFFFE45E.toInt(),
    lineNumber = 0xFF9C8B72.toInt(),
    tag = 0xFFFFA94D.toInt()
)

/**
 * Token colours for the light canvas.
 *
 * The first version of this palette re-used the dark hues but "darkened for
 * readability", which pushed every colour into the same narrow band of dark,
 * desaturated ink (dark rust, dark teal, dark green, dark violet). On a white
 * canvas they all read as near-black, so the light editor looked unhighlighted
 * while the dark one looked colourful. This set keeps every hue clearly
 * separated but sits in the mid-tone, high-chroma band that light IDE themes
 * (One Light, GitHub Light) use: saturated enough to be unmistakably coloured
 * at a glance, yet still >= 3:1 contrast against a white canvas.
 */
private val LightPalette = SyntaxPalette(
    keyword = 0xFFB45309.toInt(),
    type = 0xFF9A3412.toInt(),
    literal = 0xFFB91C1C.toInt(),
    function = 0xFFA16207.toInt(),
    operator = 0xFFC2410C.toInt(),
    // Brackets (and `=` / `;` in a server.cfg) use this slot: the heaviest orange of the
    // palette, so structure reads differently from every word around it.
    annotation = 0xFFC2410C.toInt(),
    comment = 0xFF7A7166.toInt(),
    // Saturated yellow tuned for a white canvas: still bright, still >= 3:1 contrast.
    highlightComment = 0xFFB45309.toInt(),
    lineNumber = 0xFF8A7A66.toInt(),
    tag = 0xFFB45309.toInt()
)

/**
 * Parses a `#RRGGBB` string into an ARGB int, returning `null` for anything else.
 *
 * The stored preference is already validated by
 * [com.rvdjv.pawnmc.apps_data.data_config.ContentConfig.normalizeEditorBackgroundColor];
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
 * Picks black or white - whichever contrasts more with [background].
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
