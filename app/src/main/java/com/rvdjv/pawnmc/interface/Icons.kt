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
 */
object _Icons {

    /** Save icon: a storage device with a label slot and a save arrow. */
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

    /** Code editing icon: a document sheet with a `</>` mark. */
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
     * Workspace icon: an outlined editor frame with an inset sidebar.
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
     * Settings icon: a dinner plate and a fork.
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
                // --- Plate: rim ring (outer circle minus the inner hole) with a
                // solid face circle re-filled inside the hole. ---
                circle(cx = 9.6f, cy = 12f, r = 6.8f)
                circle(cx = 9.6f, cy = 12f, r = 4.5f)
                circle(cx = 9.6f, cy = 12f, r = 2.4f)

                // --- Fork head, tapering into the handle. ---
                moveTo(17.2f, 3f)
                lineTo(21.2f, 3f)
                lineTo(21.2f, 9f)
                lineTo(19.7f, 9.6f)
                lineTo(19.7f, 20.8f)
                lineTo(18.7f, 20.8f)
                lineTo(18.7f, 9.6f)
                lineTo(17.2f, 9f)
                close()

                // --- Holes: the two gaps that split the head into three tines. ---
                moveTo(18.2f, 3f)
                lineTo(18.7f, 3f)
                lineTo(18.7f, 8.4f)
                lineTo(18.2f, 8.4f)
                close()
                moveTo(19.7f, 3f)
                lineTo(20.2f, 3f)
                lineTo(20.2f, 8.4f)
                lineTo(19.7f, 8.4f)
                close()
            }
        }.build()
    }

    /**
     * Flower icon: six round petals around a punched-out centre.
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
     * Hotspot icon used by the floating compile button of the editor.
     */
    val Hotspot: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.Hotspot",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Stroked arcs, drawn as full horizontal curves wrapping the centre dot
            // like a hotspot beacon. Cubic curves are used because PathBuilder has
            // no quadratic helper.
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
                moveTo(7.2f, 13.2f)
                curveTo(7.2f, 10.9f, 9.4f, 9.1f, 12f, 9.1f)
                curveTo(14.6f, 9.1f, 16.8f, 10.9f, 16.8f, 13.2f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
                moveTo(3.4f, 13.2f)
                curveTo(3.4f, 8.8f, 7.2f, 5.2f, 12f, 5.2f)
                curveTo(16.8f, 5.2f, 20.6f, 8.8f, 20.6f, 13.2f)
            }
            // Solid dot: the hotspot emitter in the middle.
            path(fill = SolidColor(Color.Black)) {
                circle(cx = 12f, cy = 13.2f, r = 2.2f)
            }
        }.build()
    }

    /**
     * Closed-folder glyph for the workspace explorer.
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
     * Open-folder glyph, used while a directory is expanded.
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

    /** Document glyph of a Pawn source file. */
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
            /*
             * Diagonal stroke of the P. Drawn first so the bowl can close over it
             * without a seam showing at the join.
             */
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(8.6f, 18.4f)
                lineTo(13.0f, 10.4f)
            }
            /*
             * Bowl of the P: a closed loop sitting on the diagonal, sized to read at
             * tab-strip height where the previous brace pair turned to mush.
             */
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10.4f, 10.4f)
                lineTo(14.4f, 10.4f)
                lineTo(15.6f, 13.0f)
                lineTo(13.6f, 15.2f)
                lineTo(11.4f, 14.0f)
                close()
            }
        }.build()
    }

    /** Include-file glyph: [FileRect] with a `+` in the body. */
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

    /** Save-all icon: the [Save] body with a second sheet behind it. */
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

    /** Waving hand icon for the site preview button. */
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

    /** Workspace-panel icon: an outlined frame with a filled sidebar. */
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

    /** Screen-rotation glyph for the portrait / landscape toggle. */
    val ScreenRotation: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.ScreenRotation",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Portrait phone body: rounded-rectangle outline.
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7.6f, 6.4f)
                lineTo(16.4f, 6.4f)
                lineTo(16.4f, 20.4f)
                lineTo(7.6f, 20.4f)
                close()
            }
            // Speaker notch across the top of the phone.
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.4f, strokeLineCap = StrokeCap.Round) {
                moveTo(10.6f, 8.6f)
                lineTo(13.4f, 8.6f)
            }
            // Curved rotation arrow: sweeps from the right side of the phone over
            // its top and lands on the left side with an arrowhead.
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(19.6f, 11.4f)
                curveTo(20.8f, 8.6f, 20.2f, 5.6f, 17.6f, 3.6f)
                curveTo(15.6f, 2.2f, 13.2f, 2.0f, 11.2f, 3.0f)
            }
            // Arrowhead pointing down-left, where the arc lands.
            path(fill = SolidColor(Color.Black)) {
                moveTo(10.6f, 1.6f)
                lineTo(13.6f, 4.8f)
                lineTo(9.2f, 5.4f)
                close()
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
