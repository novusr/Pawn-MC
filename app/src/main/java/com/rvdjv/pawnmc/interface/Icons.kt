package com.rvdjv.pawnmc.`interface`

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand drawn vector icons for PawnMC.
 *
 * Both glyphs are built as a single even-odd filled path: the outer silhouette
 * is a solid shape and every inner detail (label slot, save arrow, chevron
 * pair, text baseline) is a *hole* punched through it. Even-odd fill is what
 * makes that possible without a second colour, so each icon stays legible when
 * the `Icon` component applies a single tint, while still reading as a detailed
 * glyph rather than the flat Material boilerplate.
 *
 * The [CodeEdit] glyph replaces the generic `Icons.Filled.Code` that the Xed
 * entry point used to share with unrelated toolbar actions, so the button reads
 * as "edit this text file" instead of "some code feature".
 */
object _Icons {

    /**
     * Save icon: a storage device with a notched top-right corner, a label slot
     * across the top, and a downward save arrow cut into the body.
     *
     * Geometry is centred in the 24x24 viewport and kept within a 3..21 box so
     * the optical weight matches the Material icons it sits beside in the editor
     * top bar.
     */
    val Save: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.Save",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                // --- Outer device body, top-right corner notched. ---
                moveTo(4f, 3f)
                lineTo(15.5f, 3f)
                lineTo(21f, 8.5f)
                lineTo(21f, 21f)
                lineTo(4f, 21f)
                close()

                // --- Hole: label slot across the top of the body. ---
                moveTo(7f, 5.5f)
                lineTo(12.4f, 5.5f)
                lineTo(15f, 8.1f)
                lineTo(7f, 8.1f)
                close()

                // --- Hole: downward save arrow (shaft + head). ---
                moveTo(10.5f, 10.4f)
                lineTo(13.5f, 10.4f)
                lineTo(13.5f, 14.7f)
                lineTo(15.7f, 14.7f)
                lineTo(12f, 18.6f)
                lineTo(8.3f, 14.7f)
                lineTo(10.5f, 14.7f)
                close()
            }
        }.build()
    }

    /**
     * Code editing icon: a document sheet with a folded corner and a `</>`
     * chevron pair plus a text baseline punched out of it.
     *
     * The folded corner is cut away from the sheet silhouette itself rather than
     * drawn as a separate flap, which keeps the whole icon to a single path and
     * avoids a seam between two same-coloured shapes at 24dp.
     */
    val CodeEdit: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.CodeEdit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                // --- Sheet body, with the top-right corner folded away. ---
                moveTo(3.5f, 2.5f)
                lineTo(14.6f, 2.5f)
                lineTo(14.6f, 8.4f)
                lineTo(20.5f, 8.4f)
                lineTo(20.5f, 21.5f)
                lineTo(3.5f, 21.5f)
                close()

                // --- Hole: the fold, so the corner reads as turned down. ---
                moveTo(16.6f, 4.4f)
                lineTo(18.6f, 6.4f)
                lineTo(16.6f, 6.4f)
                close()

                // --- Hole: left chevron `<`. ---
                moveTo(9.6f, 11.6f)
                lineTo(7.0f, 14.0f)
                lineTo(9.6f, 16.4f)
                lineTo(11.0f, 15.0f)
                lineTo(9.9f, 14.0f)
                lineTo(11.0f, 13.0f)
                close()

                // --- Hole: right chevron `>`. ---
                moveTo(14.4f, 11.6f)
                lineTo(17.0f, 14.0f)
                lineTo(14.4f, 16.4f)
                lineTo(13.0f, 15.0f)
                lineTo(14.1f, 14.0f)
                lineTo(13.0f, 13.0f)
                close()

                // --- Hole: slash between the chevrons. ---
                moveTo(13.4f, 11.1f)
                lineTo(14.6f, 11.1f)
                lineTo(11.2f, 16.9f)
                lineTo(10.0f, 16.9f)
                close()

                // --- Hole: text baseline, so the sheet reads as a text document. ---
                moveTo(6.4f, 18.6f)
                lineTo(17.6f, 18.6f)
                lineTo(17.6f, 19.8f)
                lineTo(6.4f, 19.8f)
                close()
            }
        }.build()
    }

    /**
     * Workspace icon: an outlined editor frame with an inset sidebar, i.e. the
     * "project open in a panel" cue.
     *
     * The explorer glyphs below are deliberately drawn as *outlines* with one accent
     * block, the way the Visual Studio Code icon set reads at 16dp, instead of as
     * solid silhouettes. The PawnMC identity is kept in the shape of that block: the
     * workspace shows a short script line rather than a generic document corner, so
     * every workspace surface still looks like PawnMC rather than like a stock editor.
     */
    val Workspace: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.Workspace",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Outer frame, drawn as a stroked rounded rectangle.
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.0f, 5.6f)
                lineTo(20.0f, 5.6f)
                lineTo(20.0f, 18.4f)
                lineTo(4.0f, 18.4f)
                close()
            }
            // Sidebar split line, the "panel is docked" cue.
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
                moveTo(9.2f, 5.6f)
                lineTo(9.2f, 18.4f)
            }
            // Sidebar content: two stacked file lines.
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.4f, strokeLineCap = StrokeCap.Round) {
                moveTo(5.8f, 9.0f)
                lineTo(7.4f, 9.0f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.4f, strokeLineCap = StrokeCap.Round) {
                moveTo(5.8f, 12.0f)
                lineTo(7.4f, 12.0f)
            }
            // Content area: one solid accent block, kept short and left-aligned so the
            // glyph reads as code rather than as a paragraph of text.
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.6f, 9.2f)
                lineTo(18.0f, 9.2f)
                lineTo(18.0f, 10.6f)
                lineTo(11.6f, 10.6f)
                close()
                moveTo(11.6f, 12.4f)
                lineTo(16.2f, 12.4f)
                lineTo(16.2f, 13.8f)
                lineTo(11.6f, 13.8f)
                close()
            }
        }.build()
    }

    /**
     * Settings icon: a gear, used for the editor options menu.
     *
     * Drawn as an even-odd silhouette so the eight teeth read as part of one
     * shape and the hub is a punched hole. A gear is unmistakably "settings",
     * which the three-dot overflow glyph never was.
     */
    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                // --- Gear silhouette: eight teeth around a hub of radius 5.6,
                // tips at 9.4, generated from 45Â° steps so the teeth are even. ---
                moveTo(21.26f, 10.37f)
                lineTo(21.26f, 13.63f)
                lineTo(17.34f, 13.68f)
                lineTo(16.97f, 14.59f)
                lineTo(17.39f, 19.70f)
                lineTo(14.59f, 16.97f)
                lineTo(13.68f, 17.34f)
                lineTo(10.37f, 21.26f)
                lineTo(10.32f, 17.34f)
                lineTo(9.41f, 16.97f)
                lineTo(4.30f, 17.39f)
                lineTo(7.03f, 14.59f)
                lineTo(6.66f, 13.68f)
                lineTo(2.74f, 10.37f)
                lineTo(6.66f, 10.32f)
                lineTo(7.03f, 9.41f)
                lineTo(6.61f, 4.30f)
                lineTo(9.41f, 7.03f)
                lineTo(10.32f, 6.66f)
                lineTo(13.63f, 2.74f)
                lineTo(13.68f, 6.66f)
                lineTo(14.59f, 7.03f)
                lineTo(19.70f, 6.61f)
                lineTo(16.97f, 9.41f)
                lineTo(17.34f, 10.32f)
                close()

                // --- Hole: the hub of the gear. ---
                moveTo(12f, 9.4f)
                lineTo(14.6f, 9.4f)
                lineTo(14.6f, 12f)
                lineTo(9.4f, 12f)
                lineTo(9.4f, 9.4f)
                close()
            }
        }.build()
    }

    /**
     * Flower icon: six round petals around a punched-out centre.
     *
     * Kept for the self-test/decorative surfaces; the floating compile button of the
     * Xed editor uses [WifiSignal] instead.
     *
     * Each petal is a circle drawn with four cubic curves and every circle shares
     * one even-odd path, so the six petals read as a single silhouette while the
     * centre stays a hole.
     */
    val Flower: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.Flower",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                // --- Six petals, radius 3.2, orbiting the centre at 5.2. ---
                petal(cx = 17.2f, cy = 12f)
                petal(cx = 14.6f, cy = 16.5f)
                petal(cx = 9.4f, cy = 16.5f)
                petal(cx = 6.8f, cy = 12f)
                petal(cx = 9.4f, cy = 7.5f)
                petal(cx = 14.6f, cy = 7.5f)

                // --- Hole: the centre of the flower. ---
                circle(cx = 12f, cy = 12f, r = 2.3f)
            }
        }.build()
    }

    /**
     * Wi-Fi signal icon used by the floating compile button of the editor.
     *
     * Three nested arcs plus the dot at the bottom-left form the familiar signal
     * glyph. Each arc is a stroked ring segment, so the icon stays light in weight
     * next to the filled Material glyphs it replaces.
     */
    val WifiSignal: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.WifiSignal",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Stroked arcs, drawn from the left to the right so the fan opens towards
            // the top-right, exactly like the platform Wi-Fi glyph. Cubic curves are
            // used because PathBuilder has no quadratic helper.
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
                moveTo(8.6f, 12.4f)
                curveTo(10.1f, 10.9f, 13.9f, 10.9f, 15.4f, 12.4f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
                moveTo(5.9f, 9.5f)
                curveTo(8.6f, 6.8f, 15.4f, 6.8f, 18.1f, 9.5f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
                moveTo(3.2f, 6.6f)
                curveTo(7.4f, 2.4f, 16.6f, 2.4f, 20.8f, 6.6f)
            }
            // Solid dot: the emitter of the signal.
            path(fill = SolidColor(Color.Black)) {
                circle(cx = 8.7f, cy = 16.4f, r = 2.0f)
            }
        }.build()
    }

    /**
     * Closed-folder glyph for the workspace explorer.
     *
     * Redrawn as an outline with a filled tab, which is what makes the Visual Studio
     * Code explorer read as light and modern: at 18dp a solid silhouette turns into a
     * lump, while a 1.6dp stroke keeps the tab, the body and the fold separable.
     *
     * The PawnMC cue is the tab itself: it is drawn as a short slanted wedge, the same
     * angle as the fuel of the app's `{}` files, so a folder in this explorer cannot be
     * mistaken for the generic Material folder.
     */
    val FolderRect: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.FolderRect",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Filled tab, the identity wedge.
            path(fill = SolidColor(Color.Black)) {
                moveTo(3.4f, 5.6f)
                lineTo(9.2f, 5.6f)
                lineTo(10.9f, 7.6f)
                lineTo(3.4f, 7.6f)
                close()
            }
            // Outlined body.
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3.4f, 7.6f)
                lineTo(20.6f, 7.6f)
                lineTo(20.6f, 18.6f)
                lineTo(3.4f, 18.6f)
                close()
            }
        }.build()
    }

    /**
     * Open-folder glyph, used while a directory in the workspace explorer is expanded.
     *
     * The front plate is skewed left and the outline is left open at the top of that
     * plate, so the "flap is lifted" reading survives without any fill: the same trick
     * the platform folder glyphs use, kept as line art to match [FolderRect].
     */
    val FolderRectOpen: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.FolderRectOpen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Filled tab of the back plate.
            path(fill = SolidColor(Color.Black)) {
                moveTo(3.4f, 5.6f)
                lineTo(9.2f, 5.6f)
                lineTo(10.9f, 7.6f)
                lineTo(3.4f, 7.6f)
                close()
            }
            // Back plate outline, truncated where the front plate overlaps it.
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3.4f, 7.6f)
                lineTo(20.6f, 7.6f)
                lineTo(20.6f, 10.2f)
            }
            // Front plate, skewed, open at the top-right.
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6.6f, 10.2f)
                lineTo(20.6f, 10.2f)
                lineTo(20.6f, 18.6f)
                lineTo(3.4f, 18.6f)
                lineTo(6.6f, 10.2f)
                close()
            }
        }.build()
    }

    /**
     * Document glyph of a Pawn source file.
     *
     * A plain sheet outline with the `{}` pair the editor already uses for Pawn files,
     * so the explorer, the tab strip and the completion list all speak the same visual
     * language. The fold is drawn as a filled triangle instead of being punched out,
     * which is what keeps the shape legible once the outline is only 1.5dp wide.
     */
    val FileRect: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.FileRect",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5.4f, 3.6f)
                lineTo(14.2f, 3.6f)
                lineTo(18.6f, 8.0f)
                lineTo(18.6f, 20.4f)
                lineTo(5.4f, 20.4f)
                close()
            }
            // Solid fold, so the corner is unmistakable at tab-strip size.
            path(fill = SolidColor(Color.Black)) {
                moveTo(14.2f, 3.6f)
                lineTo(18.6f, 8.0f)
                lineTo(14.2f, 8.0f)
                close()
            }
            // Braces: the Pawn signature.
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10.2f, 11.2f)
                lineTo(8.9f, 13.6f)
                lineTo(10.2f, 16.0f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(13.8f, 11.2f)
                lineTo(15.1f, 13.6f)
                lineTo(13.8f, 16.0f)
            }
        }.build()
    }

    /**
     * Include-file glyph: [FileRect] with a `+` in the body instead of the braces.
     *
     * `#include` documents are the one file kind the explorer has to tell apart at a
     * glance, and a single extra stroke does that without reading the name.
     */
    val IncludeRect: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.IncludeRect",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5.4f, 3.6f)
                lineTo(14.2f, 3.6f)
                lineTo(18.6f, 8.0f)
                lineTo(18.6f, 20.4f)
                lineTo(5.4f, 20.4f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(14.2f, 3.6f)
                lineTo(18.6f, 8.0f)
                lineTo(14.2f, 8.0f)
                close()
            }
            // Plus sign.
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12.0f, 11.6f)
                lineTo(12.0f, 16.4f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(9.6f, 14.0f)
                lineTo(14.4f, 14.0f)
            }
        }.build()
    }

    /**
     * Save-all icon: the [Save] body with a second, smaller sheet tucked behind the
     * lower-right corner, i.e. "write every open document".
     */
    val SaveAll: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.SaveAll",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                // Rear sheet.
                moveTo(11.4f, 6.6f)
                lineTo(21.4f, 6.6f)
                lineTo(21.4f, 21.4f)
                lineTo(11.4f, 21.4f)
                close()
                // Front sheet.
                moveTo(2.6f, 2.6f)
                lineTo(15.6f, 2.6f)
                lineTo(18.4f, 5.4f)
                lineTo(18.4f, 17.4f)
                lineTo(2.6f, 17.4f)
                close()
                // Fold of the front sheet.
                moveTo(15.6f, 2.6f)
                lineTo(18.4f, 5.4f)
                lineTo(15.6f, 5.4f)
                close()
                // Label slot of the front sheet.
                moveTo(5.2f, 7.2f)
                lineTo(12.4f, 7.2f)
                lineTo(12.4f, 9.4f)
                lineTo(5.2f, 9.4f)
                close()
                // Save arrow cut into the front sheet.
                moveTo(7.4f, 11.0f)
                lineTo(9.0f, 11.0f)
                lineTo(9.0f, 13.4f)
                lineTo(11.0f, 13.4f)
                lineTo(8.2f, 16.0f)
                lineTo(5.4f, 13.4f)
                lineTo(7.4f, 13.4f)
                close()
            }
        }.build()
    }

    /**
     * Waving hand icon for the site preview button.
     *
     * An open palm with four fingers, a thumb and three motion arcs on the right
     * side, so the glyph reads as "a hand waving hello" at 24dp. Drawn as filled
     * paths only, which keeps it compatible with every Compose icon version in
     * use (the Material `WavingHand` glyph is not available in the pinned
     * `material-icons-extended` release).
     */
    val WavingHand: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.WavingHand",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Palm and fingers as one silhouette.
            path(fill = SolidColor(Color.Black)) {
                moveTo(9.4f, 12.2f)
                // Index finger.
                lineTo(9.0f, 6.4f)
                curveTo(8.9f, 5.4f, 9.6f, 4.6f, 10.4f, 4.7f)
                curveTo(11.1f, 4.8f, 11.5f, 5.4f, 11.6f, 6.2f)
                lineTo(12.1f, 10.0f)
                // Middle finger.
                lineTo(12.0f, 4.4f)
                curveTo(12.0f, 3.3f, 12.8f, 2.6f, 13.6f, 2.7f)
                curveTo(14.3f, 2.8f, 14.7f, 3.4f, 14.7f, 4.2f)
                lineTo(14.8f, 10.2f)
                // Ring finger.
                lineTo(14.9f, 5.6f)
                curveTo(14.9f, 4.6f, 15.6f, 3.9f, 16.4f, 4.0f)
                curveTo(17.1f, 4.1f, 17.5f, 4.7f, 17.5f, 5.5f)
                lineTo(17.4f, 10.6f)
                // Pinky finger.
                lineTo(17.6f, 7.6f)
                curveTo(17.6f, 6.7f, 18.3f, 6.0f, 19.0f, 6.1f)
                curveTo(19.7f, 6.2f, 20.1f, 6.8f, 20.0f, 7.6f)
                lineTo(19.7f, 14.4f)
                // Wrist.
                curveTo(19.6f, 17.6f, 18.2f, 20.4f, 15.4f, 21.3f)
                curveTo(12.8f, 22.1f, 10.2f, 20.8f, 9.0f, 18.6f)
                curveTo(8.1f, 16.9f, 7.8f, 14.8f, 8.0f, 12.9f)
                // Thumb.
                curveTo(7.4f, 12.5f, 6.5f, 11.7f, 6.0f, 10.7f)
                curveTo(5.6f, 9.8f, 6.0f, 8.9f, 6.8f, 8.7f)
                curveTo(7.5f, 8.5f, 8.1f, 9.2f, 8.4f, 10.2f)
                curveTo(8.7f, 11.0f, 9.0f, 11.6f, 9.4f, 12.2f)
                close()
            }
            // Motion arcs on the waving side.
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round) {
                moveTo(4.0f, 6.4f)
                curveTo(3.0f, 7.9f, 2.8f, 9.5f, 3.3f, 11.0f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round) {
                moveTo(1.5f, 4.6f)
                curveTo(0.0f, 6.8f, -0.2f, 9.4f, 0.9f, 11.7f)
            }
        }.build()
    }

    /**
     * Workspace-panel icon: an outlined frame with a filled sidebar, matching the
     * explorer glyphs above.
     *
     * Drawn at the same stroke weight as [Workspace] so the floating panel button, the
     * tab-strip toggle and the menu entry all read as one family instead of mixing a
     * solid card with line-art folders.
     */
    val PanelRect: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.PanelRect",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3.6f, 5.0f)
                lineTo(20.4f, 5.0f)
                lineTo(20.4f, 19.0f)
                lineTo(3.6f, 19.0f)
                close()
            }
            // Filled sidebar, the "panel is open" block.
            path(fill = SolidColor(Color.Black)) {
                moveTo(5.8f, 7.2f)
                lineTo(9.6f, 7.2f)
                lineTo(9.6f, 16.8f)
                lineTo(5.8f, 16.8f)
                close()
            }
            // Three text lines in the content area.
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.4f, strokeLineCap = StrokeCap.Round) {
                moveTo(11.6f, 8.4f)
                lineTo(18.2f, 8.4f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.4f, strokeLineCap = StrokeCap.Round) {
                moveTo(11.6f, 12.0f)
                lineTo(18.2f, 12.0f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.4f, strokeLineCap = StrokeCap.Round) {
                moveTo(11.6f, 15.6f)
                lineTo(15.8f, 15.6f)
            }
        }.build()
    }
}

/** Adds one circular petal to the current even-odd path. */
private fun androidx.compose.ui.graphics.vector.PathBuilder.petal(
    cx: Float,
    cy: Float,
    r: Float = 3.2f
) {
    circle(cx = cx, cy = cy, r = r)
}

/** Adds a circle to the path, built from four cubic curves. */
private fun androidx.compose.ui.graphics.vector.PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    // Magic constant of the quarter-circle cubic approximation.
    val k = r * 0.5523f
    moveTo(cx + r, cy)
    curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
    curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
    curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
    curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
    close()
}
