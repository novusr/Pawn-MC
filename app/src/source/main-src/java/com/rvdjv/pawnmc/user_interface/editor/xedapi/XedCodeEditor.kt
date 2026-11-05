package com.rvdjv.pawnmc.user_interface.editor.xedapi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorRenderer

/** Separates the line and column numbers. */
internal const val LINE_COLUMN_SEPARATOR = "-"

/** Sora editor with line and column labels. */
class XedCodeEditor(context: Context) : CodeEditor(context) {

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text != null && text.length == 1 && skipExistingClosingCharacter(text[0])) return true
                return super.commitText(text, newCursorPosition)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength == 1 && afterLength == 0 && deleteIndentLevel()) return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun deleteSurroundingTextInCodePoints(
                beforeLength: Int,
                afterLength: Int
            ): Boolean {
                if (beforeLength == 1 && afterLength == 0 && deleteIndentLevel()) return true
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL && deleteIndentLevel()) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (!event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            return super.onKeyShortcut(keyCode, event)
        }
        val action = when (keyCode) {
            KeyEvent.KEYCODE_A -> android.R.id.selectAll
            KeyEvent.KEYCODE_C -> android.R.id.copy
            KeyEvent.KEYCODE_X -> android.R.id.cut
            KeyEvent.KEYCODE_V -> android.R.id.paste
            else -> return super.onKeyShortcut(keyCode, event)
        }
        return when (action) {
            android.R.id.selectAll -> {
                val lastLine = text.lineCount - 1
                setSelectionRegion(0, 0, lastLine, text.getLine(lastLine).length)
                true
            }
            android.R.id.copy -> copySelection()
            android.R.id.cut -> cutSelection()
            android.R.id.paste -> pasteClipboard()
            else -> super.onKeyShortcut(keyCode, event)
        }
    }

    private fun copySelection(): Boolean {
        val selection = cursor
        if (selection.leftLine == selection.rightLine && selection.leftColumn == selection.rightColumn) return false
        val copied = text.subContent(selection.leftLine, selection.leftColumn, selection.rightLine, selection.rightColumn).toString()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return false
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Xed", copied))
        return true
    }

    private fun cutSelection(): Boolean {
        if (!copySelection()) return false
        val selection = cursor
        text.delete(selection.leftLine, selection.leftColumn, selection.rightLine, selection.rightColumn)
        return true
    }

    private fun pasteClipboard(): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return false
        val pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: return false
        val selection = cursor
        text.insert(selection.leftLine, selection.leftColumn, pasted)
        return true
    }

    private fun skipExistingClosingCharacter(typed: Char): Boolean {
        if (typed !in CLOSING_SYMBOLS) return false
        val currentCursor = cursor
        if (currentCursor.leftLine != currentCursor.rightLine ||
            currentCursor.leftColumn != currentCursor.rightColumn
        ) return false
        val line = currentCursor.leftLine
        val column = currentCursor.leftColumn
        val lineText = text.getLine(line).toString()
        if (column >= lineText.length || lineText[column] != typed) return false
        setSelection(line, column + 1)
        return true
    }

    private fun deleteIndentLevel(): Boolean {
        val currentCursor = cursor
        if (currentCursor.leftLine != currentCursor.rightLine ||
            currentCursor.leftColumn != currentCursor.rightColumn
        ) return false

        val line = currentCursor.leftLine
        val column = currentCursor.leftColumn
        if (column == 0) return false

        val lineText = text.getLine(line).toString()
        if (column > lineText.length) return false
        val prefix = lineText.substring(0, column)
        if (prefix.any { it != ' ' && it != '\t' }) return false

        val startColumn = if (prefix.last() == '\t') {
            column - 1
        } else {
            (column - INDENT_SIZE).coerceAtLeast(0)
        }
        text.delete(line, startColumn, line, column)
        return true
    }

    /** Extra width for the column label. */
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

    /** Updates the gutter width after text or font changes. */
    fun refreshColumnSuffixWidth() {
        val newWidth = if (isLineNumberEnabled) measureColumnSuffix() else 0f
        if (newWidth != columnSuffixWidth) {
            columnSuffixWidth = newWidth
            requestLayout()
            invalidate()
        }
    }

    /** Returns the extra gutter width. */
    fun getColumnSuffixWidth(): Float = columnSuffixWidth

    private fun measureColumnSuffix(): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typefaceText
            textSize = textSizePx
        }
        val digits = digitsOf(maxOf(cursor.leftColumn, cursor.rightColumn) + 1)
        val sample = "8".repeat(digits)
        return paint.measureText(LINE_COLUMN_SEPARATOR + sample)
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

    private companion object {
        const val INDENT_SIZE = 4
        val CLOSING_SYMBOLS = setOf(')', '}', ']')
    }
}

/** Draws the caret column beside relevant line numbers. */
private class ColumnLineNumberRenderer(host: CodeEditor) : EditorRenderer(host) {

    /** The renderer does not expose its editor reference. */
    private val host = host

    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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

        // Draw the line and column in a rounded gutter label.
        val metrics = host.lineNumberMetrics
        val top = host.getRowTop(row) - host.offsetY
        val bottom = host.getRowBottom(row) - host.offsetY
        val textHeight = metrics.descent - metrics.ascent
        val y = (top + bottom) / 2f - textHeight / 2f - metrics.ascent

        // The column is already one-based.
        val label = "${line + 1}$LINE_COLUMN_SEPARATOR$column"
        chipPaint.textSize = host.textSizePx * 0.82f
        chipPaint.typeface = host.typefaceLineNumber
        val textWidth = chipPaint.measureText(label)
        val padH = chipPaint.textSize * 0.5f
        val padV = chipPaint.textSize * 0.32f
        val chipHeight = textHeight + padV * 2
        val chipTop = (top + bottom) / 2f - chipHeight / 2f
        val chipRight = offsetX + width - padH * 0.4f
        val chipLeft = chipRight - textWidth - padH * 2

        chipPaint.style = Paint.Style.FILL
        chipPaint.color = CHIP_BACKGROUND
        canvas.drawRoundRect(
            chipLeft, chipTop, chipRight, chipTop + chipHeight,
            chipHeight / 2f, chipHeight / 2f, chipPaint
        )

        chipPaint.style = Paint.Style.STROKE
        chipPaint.strokeWidth = 1f
        chipPaint.color = CHIP_BORDER
        canvas.drawRoundRect(
            chipLeft, chipTop, chipRight, chipTop + chipHeight,
            chipHeight / 2f, chipHeight / 2f, chipPaint
        )

        chipPaint.style = Paint.Style.FILL
        chipPaint.color = CHIP_TEXT
        chipPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, (chipLeft + chipRight) / 2f, y, chipPaint)
    }

    /** Returns the displayed column for [line], or null when none applies. */
    private fun columnLabelFor(line: Int, row: Int): Int? {
        // Show the label only on the first wrapped row.
        val layout = host.layout ?: return null
        val rowInfo = layout.getRowAt(row)
        if (!rowInfo.isLeadingRow || rowInfo.lineIndex != line) return null

        val cursor = host.cursor
        val leftLine = cursor.leftLine
        val rightLine = cursor.rightLine
        val leftColumn = cursor.leftColumn
        val rightColumn = cursor.rightColumn

        if (leftLine == rightLine) {
            return if (line == leftLine) rightColumn + 1 else null
        }

        return when {
            line == rightLine -> rightColumn + 1
            line == leftLine -> leftColumn + 1
            line in leftLine until rightLine -> 1
            else -> null
        }
    }

    private companion object {
        // Gutter label colors.
        const val CHIP_BACKGROUND = 0x407B5CC7
        const val CHIP_BORDER = 0x60B7A6F5
        const val CHIP_TEXT = 0xFFE4DDF7.toInt()
    }
}
