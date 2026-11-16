package com.rvdjv.pawnmc.user_interface.editor.xedapi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor

/** Width reserved on the right of the code for the scrollbar and its two labels. */
internal val GUTTER_SCROLLBAR_WIDTH = 28.dp

/** Corner rounding shared by the Compose background and the painted bar. */
internal val GutterScrollbarShape = RoundedCornerShape(6.dp)

/**
 * Vertical scrollbar beside the code, between two line numbers.
 *
 * The column paints:
 *
 * ```
 *        12        <- line at the top edge of the thumb
 *        |
 *        |         <- draggable thumb
 *        |
 *        19        <- line at the bottom edge of the thumb
 * ```
 *
 * Both numbers travel with the thumb, so at the top of the file they read `1` and the last
 * line of the first screenful, and while scrolling the lower one keeps closing in on the
 * end of the visible range.
 *
 * ## Why it listens instead of polling
 *
 * The first version repainted itself inside `onDraw` by comparing `offsetY` with the value
 * of the previous frame. That never worked: `onDraw` is only called when something else
 * invalidates the view, so a scroll produced no draw at all and the bar looked frozen. The
 * bar now subscribes to sora's own scroll/selection events and repaints from there, which is
 * the same signal the editor uses to move the text under the finger.
 */
@Composable
internal fun GutterScrollbar(
    editor: CodeEditor?,
    modifier: Modifier = Modifier,
    activeColor: Color,
    trackColor: Color
) {
    if (editor == null) return

    // Repaint trigger: every scroll tick bumps this, which recreates the AndroidView's
    // draw state. Kept as state (not a view field) so the redraw also survives the view
    // instance being replaced by AndroidView when the editor changes.
    var scrollTick by remember(editor) { mutableStateOf(0) }

    DisposableEffect(editor) {
        // `ScrollEvent` fires for finger scrolls, flings, `jumpToLine` and programmatic
        // moves; `SelectionChangeEvent` covers the caret jumps that scroll the view.
        //
        // The returned `SubscriptionReceipt` is the documented way to detach a listener -
        // this build has no `unsubscribeEvent`.
        val scrollReceipt =
            editor.subscribeEvent(ScrollEvent::class.java) { _, _ -> scrollTick++ }
        val selectionReceipt =
            editor.subscribeEvent(SelectionChangeEvent::class.java) { _, _ -> scrollTick++ }
        onDispose {
            runCatching { scrollReceipt.unsubscribe() }
            runCatching { selectionReceipt.unsubscribe() }
        }
    }

    // Line the drag was started on; -1 means "no drag in progress".
    var dragAnchorLine by remember(editor) { mutableStateOf(-1) }
    val currentAnchor by rememberUpdatedState(dragAnchorLine)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(GUTTER_SCROLLBAR_WIDTH)
            .clipToBounds()
            .background(trackColor.copy(alpha = trackColor.alpha * 0.35f), GutterScrollbarShape)
            .pointerInput(editor) {
                // Snapshot the size here: inside the gesture callbacks the receiver is the
                // pointer scope, so `size` there is the touch-slop size - not the column.
                val columnHeight = this.size.height.toFloat().coerceAtLeast(1f)
                detectTapGestures { offset ->
                    val metrics = GutterScrollbarMetrics.of(editor)
                    editor.ensurePositionVisible(
                        metrics.lineAtY(offset.y / columnHeight).coerceAtLeast(0),
                        0
                    )
                    scrollTick++
                }
            }
            .pointerInput(editor) {
                val columnHeight = this.size.height.toFloat().coerceAtLeast(1f)
                detectDragGestures(
                    onDragStart = { offset ->
                        dragAnchorLine = GutterScrollbarMetrics.of(editor)
                            .lineAtY(offset.y / columnHeight)
                    },
                    onDragEnd = { dragAnchorLine = -1 },
                    onDragCancel = { dragAnchorLine = -1 }
                ) { change, dragAmount ->
                    change.consume()
                    val metrics = GutterScrollbarMetrics.of(editor)
                    if (metrics.lineCount <= 1) return@detectDragGestures
                    val linesPerPixel = (metrics.lineCount - 1).toFloat() / columnHeight
                    val anchor = currentAnchor
                    val next = if (anchor >= 0) {
                        (anchor + (dragAmount.y * linesPerPixel)).toInt()
                    } else {
                        metrics.firstLine + (dragAmount.y / metrics.lineHeight).toInt()
                    }
                    editor.ensurePositionVisible(next.coerceAtLeast(0), 0)
                    scrollTick++
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView<GutterScrollbarView>(
            factory = { ctx: Context ->
                GutterScrollbarView(ctx, editor, activeColor.toArgb(), trackColor.toArgb())
            },
            update = { view: GutterScrollbarView ->
                view.editor = editor
                view.activeColor = activeColor.toArgb()
                view.trackColor = trackColor.toArgb()
                // The tick is read here so this update runs on every scroll and the view
                // repaints immediately; without the read the update block would be skipped
                // when nothing else changed and the bar would stay frozen.
                view.scrollTick = scrollTick
                view.invalidate()
            },
            modifier = Modifier
                .fillMaxHeight()
                .width(GUTTER_SCROLLBAR_WIDTH)
        )
    }
}

/**
 * What the bar needs to know about the editor, read fresh on every draw.
 *
 * The offset comes from sora's `offsetY`; the line height is derived from the text size
 * because this build exposes no row-height getter. Everything is `Float` up front - see the
 * note in [of] about numeric widening.
 */
internal class GutterScrollbarMetrics(
    val offsetY: Float,
    val viewportHeight: Float,
    val lineHeight: Float,
    val lineCount: Int
) {
    /** Lines that fit into the viewport (at least one, so the maths never divides by 0). */
    val visibleLines: Float
        get() = if (lineHeight <= 0f) 1f else (viewportHeight / lineHeight).coerceAtLeast(1f)

    /** Lines hidden beyond the viewport. */
    val scrollableLines: Float
        get() = (lineCount - visibleLines).coerceAtLeast(0f)

    /** First line currently drawn at the top of the viewport. */
    val firstLine: Int
        get() = (offsetY / lineHeight.coerceAtLeast(1f)).toInt()
            .coerceIn(0, (lineCount - 1).coerceAtLeast(0))

    /** Line under the [fraction] of a track. */
    fun lineAtY(fraction: Float): Int {
        if (lineCount <= 1) return 0
        return (fraction.coerceIn(0f, 1f) * (lineCount - 1)).toInt()
    }

    companion object {
        fun of(editor: CodeEditor): GutterScrollbarMetrics {
            val density = editor.resources.displayMetrics.density
            // `getRowHeight()` exists and returns an Int: it already includes the line
            // spacing, so it is the exact row pitch the editor draws with. Deriving it
            // from the text size (the previous approach) was off by the spacing and made
            // the thumb jump.
            val rowPx: Int = editor.rowHeight
            val measured: Float = rowPx.toFloat()
            val lineHeight: Float = when {
                measured > 0f && measured.isFinite() -> measured
                else -> 16f * density
            }
            // `offsetY` is an Int in this sora build; converting here keeps the field a
            // Float for every consumer of the metrics.
            val offset: Float = editor.offsetY.toFloat()
            return GutterScrollbarMetrics(
                offsetY = offset,
                viewportHeight = editor.height.toFloat(),
                lineHeight = lineHeight,
                lineCount = editor.text.lineCount
            )
        }
    }
}

/**
 * The painted bar: track, thumb and the two numbers that frame it.
 *
 * The view is stateless apart from [scrollTick]: the Compose layer bumps the tick from
 * sora's scroll events and calls `invalidate()`, so the paint below always runs on the
 * frame of the movement instead of waiting for an unrelated redraw.
 */
internal class GutterScrollbarView(
    context: Context,
    var editor: CodeEditor,
    var activeColor: Int,
    var trackColor: Int
) : View(context) {

    /** Bumped by the Compose layer on every scroll; read to force a repaint. */
    var scrollTick: Int = 0

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()

    init {
        // Decoration only: touches are handled by the Compose surface around this view.
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val metrics = GutterScrollbarMetrics.of(editor)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f || metrics.lineCount <= 0) return

        val labelSize = (metrics.lineHeight * 0.66f).coerceIn(dp(9f), dp(12f))
        labelPaint.textSize = labelSize
        // Room for the two numbers: they are the "nearest number above / below" the bar.
        val labelGap = labelSize + dp(5f)
        val trackTop = labelGap
        val trackBottom = (height - labelGap).coerceAtLeast(trackTop + dp(12f))

        // Track.
        val trackWidth = dp(3f)
        val trackLeft = (width - trackWidth) / 2f
        trackPaint.color = trackColor
        rect.set(trackLeft, trackTop, trackLeft + trackWidth, trackBottom)
        canvas.drawRoundRect(rect, trackWidth / 2f, trackWidth / 2f, trackPaint)

        val total = metrics.lineCount.toFloat()
        val visible = metrics.visibleLines
        val usable = (trackBottom - trackTop).coerceAtLeast(1f)

        // Thumb: as long as the visible share of the document, never shorter than a grip.
        val thumbHeight = (usable * (visible / total)).coerceIn(dp(16f), usable)
        val maxScroll = metrics.scrollableLines
        val progress = if (maxScroll <= 0f) {
            0f
        } else {
            ((metrics.offsetY / metrics.lineHeight) / maxScroll).coerceIn(0f, 1f)
        }
        val thumbTop = trackTop + (usable - thumbHeight) * progress
        val thumbWidth = dp(7f)
        val thumbLeft = (width - thumbWidth) / 2f
        thumbPaint.color = activeColor
        rect.set(thumbLeft, thumbTop, thumbLeft + thumbWidth, thumbTop + thumbHeight)
        canvas.drawRoundRect(rect, thumbWidth / 2f, thumbWidth / 2f, thumbPaint)

        // The numbers framing the thumb: the line at its top edge and the line at its
        // bottom edge. The edge lines are derived from the *scroll* extent, not from the
        // thumb length: the thumb is clamped to a minimum grip.
        val topLine = (progress * metrics.scrollableLines).toInt()
            .coerceIn(0, metrics.lineCount - 1)
        val bottomLine = (progress * metrics.scrollableLines + visible - 1f).toInt()
            .coerceIn(topLine, metrics.lineCount - 1)

        labelPaint.color = activeColor
        canvas.drawText(
            (topLine + 1).toString(),
            width / 2f,
            (trackTop - labelSize * 0.30f).coerceAtLeast(labelSize),
            labelPaint
        )
        canvas.drawText(
            (bottomLine + 1).toString(),
            width / 2f,
            (trackBottom + labelSize * 0.95f).coerceAtMost(height - dp(1f)),
            labelPaint
        )
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
