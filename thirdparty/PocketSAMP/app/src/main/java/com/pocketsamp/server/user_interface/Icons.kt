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
}