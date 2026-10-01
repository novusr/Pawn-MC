package com.rvdjv.pawnmc.ui.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorRenderer

/** Separator drawn between a line number and its column label. */
internal const val LINE_COLUMN_SEPARATOR = " - "

/**
 * Editor that installs a renderer able to draw extra information in the
 * line-number area. See [ColumnLineNumberRenderer].
 */
class XedCodeEditor(context: Context) : CodeEditor(context) {

    /**
     * Extra room reserved (in px) on the right of the line-number area for the
     * `" - <column>"` label.
     */
    private var columnSuffixWidth: Float = 0f

    override fun onCreateRenderer(): EditorRenderer = ColumnLineNumberRenderer(this)

    override fun measureLineNumber(): Float {
        val base = super.measureLineNumber()
        if (!isLineNumberEnabled) {
            columnSuffixWidth = 0f
            return base
        }
        columnSuffixWidth = measureColumnSuffix()
        return base + columnSuffixWidth
    }

    override fun setTextSizePx(size: Float) {
        super.setTextSizePx(size)
        columnSuffixWidth = 0f
    }

    override fun setTypefaceText(typefaceText: Typeface) {
        super.setTypefaceText(typefaceText)
        columnSuffixWidth = 0f
    }

    /**
     * Recomputes the cached suffix width for the current font and document.
     * Cheap enough to call whenever the text changes.
     */
    fun refreshColumnSuffixWidth() {
        columnSuffixWidth = if (isLineNumberEnabled) measureColumnSuffix() else 0f
    }

    /** Width of the `" - <column>"` label, `0` while it is not measured. */
    fun getColumnSuffixWidth(): Float = columnSuffixWidth

    private fun measureColumnSuffix(): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typefaceText
            textSize = textSizePx
        }
        val digits = maxOf(3, digitsOf(widestColumnCount() + 1))
        val sample = "8".repeat(digits)
        // The label is drawn right next to the number, so only the part that
        // pushes it past the plain line number needs to be reserved.
        val extra = paint.measureText(LINE_COLUMN_SEPARATOR + sample) - paint.measureText(sample)
        return maxOf(0f, extra) + paint.textSize * 0.25f
    }

    private fun widestColumnCount(): Int {
        var widest = 0
        var line = 0
        while (line < lineCount) {
            val length = text.getColumnCount(line)
            if (length > widest) widest = length
            line++
        }
        return widest
    }

    private fun digitsOf(value: Int): Int {
        var digits = 1
        var remaining = value
        while (remaining >= 10) {
            remaining /= 10
            digits++
        }
        return digits
    }
}

/**
 * Draws the plain line numbers exactly like the stock renderer, and appends the
 * column of the caret (or of the selection ends) on the lines the current
 * selection touches.
 *
 * The state is read from the live editor on every draw, so the label follows
 * caret moves, selection changes and text edits without extra bookkeeping.
 */
private class ColumnLineNumberRenderer(host: CodeEditor) : EditorRenderer(host) {

    /**
     * Own reference to the editor.
     *
     * `EditorRenderer` keeps its own editor field private in this Sora version, so the
     * renderer has to keep the reference it was constructed with.
     */
    private val host = host

    private val suffixPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun drawLineNumber(
        canvas: Canvas,
        line: Int,
        row: Int,
        offsetX: Float,
        width: Float,
        color: Int
    ) {
        val owner = host as? XedCodeEditor
        val suffixWidth = owner?.getColumnSuffixWidth() ?: 0f
        val column = if (suffixWidth > 0f) columnLabelFor(line, row) else null
        if (owner == null || column == null) {
            super.drawLineNumber(canvas, line, row, offsetX, width, color)
            return
        }

        // Shrink the area handed to the stock drawing so the label lands between
        // the number and the divider instead of overflowing the panel.
        val numberWidth = width - suffixWidth
        super.drawLineNumber(canvas, line, row, offsetX, numberWidth, color)

        val metrics = host.lineNumberMetrics
        val y = (host.getRowBottom(row) + host.getRowTop(row)) / 2f -
            (metrics.descent - metrics.ascent) / 2f - metrics.ascent - host.offsetY
        suffixPaint.typeface = host.typefaceLineNumber
        suffixPaint.textSize = host.textSizePx
        suffixPaint.color = color
        suffixPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(LINE_COLUMN_SEPARATOR + column, offsetX + numberWidth, y, suffixPaint)
    }

    /**
     * Returns the 1-based column to display for [line], or `null` when the line
     * does not take part in the current selection.
     */
    private fun columnLabelFor(line: Int, row: Int): Int? {
        // With word wrap a line spans several rows; only the leading row carries
        // the label, otherwise it would repeat down the whole line.
        val layout = host.layout ?: return null
        val rowInfo = layout.getRowAt(row)
        if (!rowInfo.isLeadingRow || rowInfo.lineIndex != line) return null

        val cursor = host.cursor
        val leftLine = cursor.leftLine
        val rightLine = cursor.rightLine
        return when {
            line == leftLine -> cursor.leftColumn + 1
            line == rightLine -> cursor.rightColumn + 1
            line in leftLine until rightLine -> 1
            else -> null
        }
    }
}
