package com.pocketsamp.server.user_interface

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Hand drawn vector icons reused by PocketSAMP File Manager (mirrors Xed Editor workspace panel). */
object _Icons {

    /**
     * Document glyph: a sheet of paper with horizontal lines — editing, writing, paper, document.
     * Replaces the generic Material Description icon for files in the File Manager.
     */
    val FilePaper: ImageVector by lazy {
        ImageVector.Builder(
            name = "_Icons.FilePaper",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Sheet outline with folded top-right corner.
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
            // Solid fold at the top-right corner.
            path(fill = SolidColor(Color.Black)) {
                moveTo(14.2f, 3.6f)
                lineTo(18.6f, 8.0f)
                lineTo(14.2f, 8.0f)
                close()
            }
            // Horizontal writing lines on the body.
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(7.0f, 9.0f)
                lineTo(17.0f, 9.0f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(7.0f, 11.0f)
                lineTo(16.0f, 11.0f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(7.0f, 13.0f)
                lineTo(15.0f, 13.0f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(7.0f, 15.0f)
                lineTo(14.0f, 15.0f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(7.0f, 17.0f)
                lineTo(13.0f, 17.0f)
            }
        }.build()
    }

    /**
     * Closed-folder glyph for the File Manager — same shape as Xed Editor workspace panel.
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
     * Open-folder glyph for the File Manager — same shape as Xed Editor workspace panel.
     * Used while a directory is expanded.
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

    /** Circled letter glyph family — same circle, different letter inside. */
    private fun CircledLetter(letter: String): ImageVector =
        ImageVector.Builder(
            name = "_Icons.Circled$letter",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Outer circle (same for all three icons). Drawn with four cubic curves
            // because PathBuilder.addCircle is not available in this Compose version.
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                val k = 9f * 0.5523f
                moveTo(21f, 12f)
                curveTo(21f, 12f + k, 12f + k, 21f, 12f, 21f)
                curveTo(12f - k, 21f, 3f, 12f + k, 3f, 12f)
                curveTo(3f, 12f - k, 12f - k, 3f, 12f, 3f)
                curveTo(12f + k, 3f, 21f, 12f - k, 21f, 12f)
                close()
            }
            // Letter in the centre (same baseline, same size so all three read as a family).
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                when (letter) {
                    "B" -> {
                        moveTo(10.2f, 8.4f)
                        lineTo(13.8f, 8.4f)
                        curveTo(15.0f, 8.4f, 16.0f, 9.6f, 16.0f, 12.0f)
                        curveTo(16.0f, 14.4f, 15.0f, 15.6f, 13.8f, 15.6f)
                        lineTo(10.2f, 15.6f)
                        curveTo(9.0f, 15.6f, 8.2f, 14.4f, 8.2f, 12.0f)
                        curveTo(8.2f, 9.6f, 9.0f, 8.4f, 10.2f, 8.4f)
                    }
                    "i" -> {
                        moveTo(11.2f, 8.4f)
                        lineTo(12.8f, 8.4f)
                        lineTo(12.8f, 14.4f)
                        lineTo(11.2f, 14.4f)
                        close()
                        moveTo(11.6f, 7.0f)
                        lineTo(12.4f, 7.0f)
                        lineTo(12.4f, 7.8f)
                        lineTo(11.6f, 7.8f)
                        close()
                    }
                    "I" -> {
                        moveTo(11.4f, 8.4f)
                        lineTo(12.6f, 8.4f)
                        lineTo(12.6f, 15.6f)
                        lineTo(11.4f, 15.6f)
                        close()
                    }
                }
            }
        }.build()

    /** Circled uppercase B — replaces the bicycle icon on the Leave-PocketSAMP button. */
    val CircledB: ImageVector by lazy { CircledLetter("B") }

    /** Circled lowercase i — replaces the question-mark help icon. */
    val CircledI: ImageVector by lazy { CircledLetter("i") }

    /** Circled uppercase I — replaces the bug-report crash icon. */
    val CircledBigI: ImageVector by lazy { CircledLetter("I") }
}