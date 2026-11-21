package com.rvdjv.pawnmc.user_interface.common

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * How a scrollable surface is measured by the scrollbar.
 *
 * The two implementations cover the two ways Compose exposes scrolling here: a pixel based
 * [ScrollState] (the compiler output, the settings column) and an item based [LazyListState]
 * (the main screen).
 */
internal interface ScrollMetrics {
    val maxValue: Float
    val value: Float
    val visibleFraction: Float
    fun scrollToFraction(fraction: Float)
}

private class PixelScrollMetrics(
    private val state: ScrollState,
    private val coroutineScope: CoroutineScope
) : ScrollMetrics {
    override val maxValue: Float get() = state.maxValue.toFloat()
    override val value: Float get() = state.value.toFloat()
    // True viewport proportion: the thumb grows with how much of the content is on
    // screen, and reaches the full track exactly when nothing is left to scroll.
    override val visibleFraction: Float
        get() = if (maxValue <= 0f) 1f else (state.viewportSize / (maxValue + state.viewportSize)).coerceIn(0.06f, 1f)
    override fun scrollToFraction(fraction: Float) {
        val target = (fraction.coerceIn(0f, 1f) * maxValue).roundToInt()
        coroutineScope.launch { state.scrollTo(target) }
    }
}

private class LazyScrollMetrics(
    private val state: LazyListState,
    private val coroutineScope: CoroutineScope
) : ScrollMetrics {
    override val maxValue: Float
        get() = (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0).toFloat()
    override val value: Float
        get() = state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / 10_000f
    override val visibleFraction: Float get() = 0.12f
    override fun scrollToFraction(fraction: Float) {
        val last = maxValue.toInt()
        if (last <= 0) return
        coroutineScope.launch {
            // Scrolling to the last item never clears `canScrollForward` on its own when
            // that item is taller than the viewport plus its trailing padding - the bar
            // then looks stuck one notch above the bottom (the "stuck at 9" report). The
            // fix is the same move the FAB makes: hop to the last item first, then keep
            // scrolling forward until the list says it cannot, which lands exactly on
            // the bottom of the content.
            val target = (fraction.coerceIn(0f, 1f) * last).roundToInt()
            state.scrollToItem(target)
            var guard = 0
            while (fraction >= 1f && state.canScrollForward && guard < 64) {
                state.scrollBy(state.layoutInfo.viewportEndOffset.toFloat())
                guard++
            }
        }
    }
}

/**
 * Vertical scrollbar with the same two-number framing as the Xed gutter bar.
 *
 * The label above the thumb is the unit at the top of the thumb, the label below it the
 * unit at its bottom; for the item lists those are item numbers, for the compiler output
 * they are line numbers. Both move while scrolling, so the user can read where the thumb
 * currently sits without counting rows.
 *
 * Interaction: the whole bar is live. A tap anywhere on the track jumps the thumb there
 * (centre of the thumb on the tapped spot), and dragging the thumb - or the track, which
 * behaves as a handle while a drag is running - scrolls the content continuously.
 *
 * This is the shared painter; the two public entry points are
 * [ScrollStateVerticalScrollbar] and [LazyListScrollbar]. They are deliberately *not*
 * overloads of one name: two `@Composable` functions whose only difference is the scroll
 * state type make a call ambiguous under the Compose compiler.
 */
@Composable
private fun ScrollbarBody(
    metrics: ScrollMetrics,
    unitCount: Int,
    /** Rows visible in the list (lazy variant only); drives the thumb when known. */
    viewportRowCount: Int = 0,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    /** Draws both numbers; off for very short panels. */
    showLabels: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    // Track height in px, measured live - used to turn pointer movement into fractions.
    var trackHeightPx by remember { mutableStateOf(0) }
    // Fraction the active drag last scrolled to, so each drag event is a delta on top of
    // the previous one instead of an absolute jump.
    var dragLastFraction by remember { mutableStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .width(SB_WIDTH)
            .fillMaxHeight()
    ) {
        val barHeight = maxHeight
        val usablePx = with(density) { (barHeight - SB_LABEL_ROOM * 2).toPx() }
            .coerceAtLeast(1f)
        val safeUnits = unitCount.coerceAtLeast(1)
        val thumbFraction = if (viewportRowCount > 0) {
            (viewportRowCount.toFloat() / safeUnits).coerceIn(0.06f, 1f)
        } else {
            metrics.visibleFraction.coerceIn(0.06f, 1f)
        }
        val thumbPx = (usablePx * thumbFraction).coerceAtLeast(with(density) { SB_MIN_THUMB.toPx() })
        val maxScroll = metrics.maxValue.coerceAtLeast(0.001f)
        val progress = (metrics.value / maxScroll).coerceIn(0f, 1f)

        val topLine = (progress * (safeUnits - 1)).roundToInt().coerceIn(0, safeUnits - 1)
        val bottomLine = (progress * (safeUnits - 1) + (thumbFraction * (safeUnits - 1)))
            .roundToInt()
            .coerceIn(topLine, safeUnits - 1)

        // Jump helper shared by tap and drag: a pointer y inside the paddings maps to a
        // scroll fraction, with the thumb centred on the touched position so the content
        // does not snap its middle to the finger.
        fun fractionFor(y: Float): Float {
            val track = trackHeightPx.coerceAtLeast(1).toFloat()
            val centre = usablePx / 2f
            return ((y - centre) / (track - usablePx).coerceAtLeast(1f)).coerceIn(0f, 1f)
        }

        // Track.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(SB_WIDTH)
                .padding(horizontal = 6.dp, vertical = SB_LABEL_ROOM)
                .clip(RoundedCornerShape(4.dp))
                .background(trackColor.copy(alpha = 0.6f))
                .onSizeChanged { trackHeightPx = it.height }
                .pointerInput(metrics, maxScroll) {
                    // Drag = grab the bar anywhere and scroll continuously. The pointer
                    // position maps straight onto a scroll fraction, so the content
                    // follows the finger one-to-one across the whole track - including
                    // the very bottom, which the old thumb-only drag never reached.
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragLastFraction = fractionFor(offset.y)
                            metrics.scrollToFraction(dragLastFraction)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, dragAmount ->
                        change.consume()
                        val span = (trackHeightPx.coerceAtLeast(1) - usablePx).coerceAtLeast(1f)
                        dragLastFraction = (dragLastFraction + dragAmount.y / span).coerceIn(0f, 1f)
                        metrics.scrollToFraction(dragLastFraction)
                    }
                }
                .pointerInput(metrics) {
                    // Tap = jump straight to the touched spot (thumb centred on it).
                    detectTapGestures { offset ->
                        metrics.scrollToFraction(fractionFor(offset.y))
                    }
                }
        ) {
            val thumbTop = (usablePx - thumbPx) * progress
            Box(
                modifier = Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            0,
                            thumbTop.roundToInt().coerceAtLeast(0)
                        )
                    }
                    .fillMaxWidth()
                    .height(with(density) { thumbPx.toDp() })
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (dragging) activeColor else activeColor.copy(alpha = 0.55f))
            )
        }

        if (showLabels) {
            // "Nearest number above" and "nearest number below" the bar. Both get a
            // translucent plate behind them: on the settings screen the top label used
            // to sit under the collapsing app bar and read as missing - the plate keeps
            // it visible and legible against whatever scrolls beneath it.
            LabelPlate(text = (topLine + 1).toString(), color = activeColor, modifier = Modifier.align(Alignment.TopCenter))
            LabelPlate(text = (bottomLine + 1).toString(), color = activeColor, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Small rounded plate that carries one scrollbar label and keeps it readable. */
@Composable
private fun LabelPlate(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(horizontal = 3.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/**
 * Pixel based variant, for a `Modifier.verticalScroll` container such as the output log or
 * the settings column.
 *
 * Named instead of overloaded: a Kotlin function cannot declare one `@Composable`
 * overload per scroll-state type, the parameter types collide in the Compose plugin and the
 * call site resolves to the lazy one.
 */
@Composable
internal fun ScrollStateVerticalScrollbar(
    state: ScrollState,
    /** Scrollable units (log lines) the labels count; a pixel scroll has no item list. */
    unitCount: Int = 0,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    showLabels: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val metrics = remember(state, scope) { PixelScrollMetrics(state, scope) }
    // A caller that knows its unit count (the compiler log) passes it. The settings
    // column does not - it is a pixel scroll over laid-out cards - so the count is
    // estimated from the scroll range in text rows; the labels then still move with the
    // thumb instead of being switched off.
    // Reading the two state properties here subscribes this composable to them, so the bar
    // recomposes when the scroll position or the range changes. Kotlin has no
    // expression-statements, so the reads have to be part of a real expression.
    val scrollPosition = state.value
    val scrollRange = state.maxValue
    val resolvedUnits = if (unitCount > 0) unitCount else estimatedLineCount(scrollRange)
    val effectiveLabels = showLabels && resolvedUnits > 0
    // The position is not needed for the geometry - `metrics` reads it live - only for the
    // recomposition subscription above.
    @Suppress("UNUSED_VARIABLE")
    val positionSubscription = scrollPosition
    ScrollbarBody(
        metrics = metrics,
        unitCount = resolvedUnits,
        modifier = modifier,
        activeColor = activeColor,
        trackColor = trackColor,
        showLabels = effectiveLabels
    )
}

/** Lazy list variant, for the main screen and any item-based list. */
@Composable
internal fun LazyListScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val scope = rememberCoroutineScope()
    val metrics = remember(state, scope) { LazyScrollMetrics(state, scope) }
    // Subscribes to the layout info so the bar follows the list while it scrolls.
    val info = state.layoutInfo
    val units = info.totalItemsCount.coerceAtLeast(1)
    // Read the visible rows too: the layout info snapshot above already carries them, and
    // touching the property keeps that read from being dropped by the compiler.
    val visibleRows = info.visibleItemsInfo.size
    ScrollbarBody(
        metrics = metrics,
        viewportRowCount = visibleRows,
        unitCount = units,
        modifier = modifier,
        activeColor = activeColor,
        trackColor = trackColor
    )
}

/**
 * Overlays a [ScrollStateVerticalScrollbar] on the trailing edge of the content in [Box]
 * scope.
 *
 * Used where the scrollable composable is already a child of a Box (the compiler output
 * cards); the bar floats over the padding instead of taking layout space, so the text is
 * not reflowed.
 */
@Composable
internal fun BoxScope.OverlayScrollbar(
    state: ScrollState,
    unitCount: Int,
    modifier: Modifier = Modifier
) {
    if (state.maxValue <= 0) return
    ScrollStateVerticalScrollbar(
        state = state,
        unitCount = unitCount,
        modifier = modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(vertical = 6.dp)
    )
}

// The main-interface bar was reported as "too big": the lane shrinks to a slim strip and
// the minimum thumb follows so a short list keeps a usable grab area.
private val SB_WIDTH = 14.dp
private val SB_MIN_THUMB = 24.dp
private val SB_LABEL_ROOM = 14.dp

/** Lines in [text], at least one, for the bar's unit count. */
internal fun lineCountOf(text: String): Int =
    if (text.isEmpty()) 1 else text.count { it == '\n' } + 1

/** Rows a pixel scroll covers, at a nominal 48 px per row of laid-out content. */
private fun estimatedLineCount(maxScrollPx: Int): Int =
    ((maxScrollPx.coerceAtLeast(0) / 48) + 2).coerceAtLeast(1)
