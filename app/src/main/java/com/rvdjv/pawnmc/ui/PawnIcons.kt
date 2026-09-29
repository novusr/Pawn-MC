package com.rvdjv.pawnmc.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
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
object PawnIcons {

    /**
     * Save icon: a storage device with a notched top-right corner, a label slot
     * across the top, and a downward save arrow cut into the body.
     *
     * Geometry is centred in the 24x24 viewport and kept within a 3..21 box so
     * the optical weight matches the Material icons it sits beside in the Xed
     * top bar.
     */
    val Save: ImageVector by lazy {
        ImageVector.Builder(
            name = "PawnIcons.Save",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillType = PathFillType.EvenOdd
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
            name = "PawnIcons.CodeEdit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillType = PathFillType.EvenOdd
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
}
