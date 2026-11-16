package com.rvdjv.pawnmc.user_interface.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * How a scrollable surface is measured by the scrollbar.
 *
 * The two implementations cover the two ways Compose exposes scrolling here: a pixel based
 * [ScrollState] (the compiler output) and an item based [LazyListState] (the screens).
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
    override val visibleFraction: Float
        get() = if (maxValue <= 0f) 1f else (1f - maxValue / (maxValue + 1f)).coerceIn(0.08f, 1f)
    override fun scrollToFraction(fraction: Float) {
        coroutineScope.launch {
            state.scrollTo((fraction.coerceIn(0f, 1f) * maxValue).roundToInt())
        }
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
            state.scrollToItem((fraction.coerceIn(0f, 1f) * last).roundToInt())
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

        // Track.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(SB_WIDTH)
                .padding(horizontal = 6.dp, vertical = SB_LABEL_ROOM)
                .clip(RoundedCornerShape(4.dp))
                .background(trackColor.copy(alpha = 0.6f))
                .pointerInput(metrics, maxScroll) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, dragAmount ->
                        change.consume()
                        val deltaFraction = dragAmount.y / usablePx
                        scope.launch {
                            metrics.scrollToFraction(progress + deltaFraction)
                        }
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
            // "Nearest number above" and "nearest number below" the bar.
            Text(
                text = (topLine + 1).toString(),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = activeColor,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            Text(
                text = (bottomLine + 1).toString(),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = activeColor,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
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

private val SB_WIDTH = 22.dp
private val SB_MIN_THUMB = 28.dp
private val SB_LABEL_ROOM = 14.dp

/** Lines in [text], at least one, for the bar's unit count. */
internal fun lineCountOf(text: String): Int =
    if (text.isEmpty()) 1 else text.count { it == '\n' } + 1

/** Rows a pixel scroll covers, at a nominal 48 px per row of laid-out content. */
private fun estimatedLineCount(maxScrollPx: Int): Int =
    ((maxScrollPx.coerceAtLeast(0) / 48) + 2).coerceAtLeast(1)
