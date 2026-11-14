package com.rvdjv.pawnmc.user_interface.widget

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Slim right-edge scroll sidebar shared by every scrolling surface of the app.
 *
 * It is a *scrollbar*, not an extra pane: a faint track with a draggable thumb whose
 * length is the share of the content currently on screen, so a long compiler log looks
 * taller than a short settings page before anything is touched. Both metrics are
 * clamped, so the strip neither vanishes nor grows past the room it is given:
 *
 *  - length   - proportional to the visible share, never shorter than
 *               [ScrollSidebarDefaults.MinThumbHeight] and never taller than the strip;
 *  - position - proportional to the scroll offset, so the thumb rests at the bottom
 *               once the content is scrolled to the end.
 *
 * The strip measures its own height at layout time, so one call fits a 240 dp output
 * card, a full-height settings page and a floating editor panel without any tuning.
 * It draws nothing when the content already fits.
 */
object ScrollSidebarDefaults {
    /** Width of the whole strip, finger area included. */
    val Width: Dp = 14.dp

    /** Width of the thumb itself; the rest of [Width] is finger room. */
    val ThumbWidth: Dp = 4.dp

    /** Shortest the thumb may get, so a very long log still leaves something to grab. */
    val MinThumbHeight: Dp = 28.dp

    /** Breathing room above and below the track. */
    val TrackPadding: Dp = 4.dp
}

/** Sidebar for a plain [ScrollState] (a `Column` with `verticalScroll`). */
@Composable
fun SimpleScrollSidebar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    thumbColor: Color = Color(0x8AFFFFFF),
    trackColor: Color = Color(0x1F000000),
    width: Dp = ScrollSidebarDefaults.Width
) {
    if (scrollState.maxValue <= 0) return
    val scope = rememberCoroutineScope()
    val visibleFraction = scrollState.viewportSize.toFloat() /
        (scrollState.maxValue + scrollState.viewportSize).coerceAtLeast(1)

    SidebarStrip(
        progress = scrollState.value.toFloat() / scrollState.maxValue,
        visibleFraction = visibleFraction,
        thumbColor = thumbColor,
        trackColor = trackColor,
        width = width,
        modifier = modifier,
        onSeekFraction = { fraction ->
            scope.launch {
                scrollState.scrollTo((scrollState.maxValue * fraction).roundToInt())
            }
        }
    )
}

/**
 * Sidebar for a [LazyListState].
 *
 * Position and length come from the laid-out items instead of the item count: a list of
 * 400 one-line entries and a list of 3 tall cards must not produce the same thumb.
 */
@Composable
fun LazyScrollSidebar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = Color(0x8AFFFFFF),
    trackColor: Color = Color(0x1F000000),
    width: Dp = ScrollSidebarDefaults.Width
) {
    val metrics by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (info.totalItemsCount <= 0 || visible.isEmpty()) {
                0f to 1f
            } else {
                val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
                val contentStart = info.beforeContentPadding - visible.first().offset
                val contentEnd = visible.last().offset + visible.last().size
                val spread = (contentEnd - contentStart - viewport).coerceAtLeast(0)
                val progress = if (spread == 0) 0f
                else (-visible.first().offset).toFloat().div(spread).coerceIn(0f, 1f)
                val share = viewport.toFloat().div(contentEnd - contentStart).coerceIn(0f, 1f)
                progress to share
            }
        }
    }
    val progress = metrics.first
    val visibleFraction = metrics.second
    if (visibleFraction >= 1f) return
    val scope = rememberCoroutineScope()
    val totalItems = listState.layoutInfo.totalItemsCount

    SidebarStrip(
        progress = progress,
        visibleFraction = visibleFraction,
        thumbColor = thumbColor,
        trackColor = trackColor,
        width = width,
        modifier = modifier,
        onSeekFraction = { fraction ->
            val target = (fraction * (totalItems - 1).coerceAtLeast(0)).roundToInt()
            scope.launch { listState.scrollToItem(target) }
        }
    )
}

/** The drawn sidebar: track, thumb, tap-to-jump and drag-to-pan. */
@Composable
private fun SidebarStrip(
    progress: Float,
    visibleFraction: Float,
    thumbColor: Color,
    trackColor: Color,
    width: Dp,
    modifier: Modifier,
    onSeekFraction: (Float) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
    ) {
        // The strip's own height decides the thumb: that is what makes the sidebar
        // grow and shrink with the container instead of a fixed pixel size.
        val stripHeightPx = with(density) {
            (maxHeight - ScrollSidebarDefaults.TrackPadding * 2).toPx()
        }.coerceAtLeast(1f)
        val minThumbPx = with(density) { ScrollSidebarDefaults.MinThumbHeight.toPx() }
        val thumbHeightPx = (stripHeightPx * visibleFraction.coerceIn(0f, 1f))
            .coerceIn(minThumbPx.coerceAtMost(stripHeightPx), stripHeightPx)
        val travelPx = (stripHeightPx - thumbHeightPx).coerceAtLeast(1f)
        val thumbOffsetPx = travelPx * progress.coerceIn(0f, 1f)
        val thumbHeightDp = with(density) { thumbHeightPx.toDp() }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = ScrollSidebarDefaults.TrackPadding)
                .pointerInput(stripHeightPx, thumbHeightPx) {
                    detectTapGestures { position ->
                        onSeekFraction(
                            ((position.y - thumbHeightPx / 2f) / travelPx).coerceIn(0f, 1f)
                        )
                    }
                }
                .pointerInput(stripHeightPx, thumbHeightPx) {
                    // The thumb jumps to the finger on press and then follows it, rather
                    // than accumulating deltas from a stale position: a drag that outruns
                    // the recomposition would otherwise lose or double its movement.
                    var dragFraction = progress.coerceIn(0f, 1f)
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragFraction = ((offset.y - thumbHeightPx / 2f) / travelPx)
                                .coerceIn(0f, 1f)
                            onSeekFraction(dragFraction)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragFraction = (dragFraction + dragAmount.y / travelPx)
                                .coerceIn(0f, 1f)
                            onSeekFraction(dragFraction)
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(ScrollSidebarDefaults.ThumbWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(trackColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                    .width(ScrollSidebarDefaults.ThumbWidth)
                    .height(thumbHeightDp)
                    .clip(RoundedCornerShape(50))
                    .background(thumbColor)
            )
        }
    }
}
