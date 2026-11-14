package com.pocketsamp.server.user_interface.widget

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketsamp.server.user_interface.theme.SampThemeColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Slim right-edge scroll sidebar used by the PocketSAMP interface.
 *
 * A faint track plus a draggable thumb whose length is the share of the content on
 * screen, so a long console reads as long before it is touched. The strip measures its
 * own height, which is what makes it shrink and stretch with whatever container it is
 * dropped into - a full screen, a console card or a short page.
 *
 * It renders nothing when the content already fits, and the thumb never gets shorter
 * than [MinThumbHeight] so a very long log still leaves something to grab.
 */
object ScrollSidebarDefaults {
    val Width: Dp = 14.dp
    val ThumbWidth: Dp = 4.dp
    val MinThumbHeight: Dp = 28.dp
    val TrackPadding: Dp = 4.dp
}

/** Sidebar for a plain [ScrollState] (a `Column` with `verticalScroll`). */
@Composable
fun SimpleScrollSidebar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    thumbColor: Color = SampThemeColors.textMuted,
    trackColor: Color = SampThemeColors.card,
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

/** Sidebar for a [LazyListState]; position and length come from the laid-out items. */
@Composable
fun LazyScrollSidebar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = SampThemeColors.textMuted,
    trackColor: Color = SampThemeColors.card,
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

/**
 * Sidebar with a start/end line counter above and below the thumb.
 *
 * Used where the length itself is information: the console reports how many log lines
 * are buffered, the database page how many rows it holds. [startLabel]/[endLabel] are
 * plain strings so the caller owns localisation and the strip stays dumb.
 */
@Composable
fun LabeledScrollSidebar(
    progress: Float,
    visibleFraction: Float,
    startLabel: String,
    endLabel: String,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    thumbColor: Color = SampThemeColors.textMuted,
    trackColor: Color = SampThemeColors.card,
    width: Dp = 18.dp
) {
    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = startLabel,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = SampThemeColors.textMuted,
            maxLines = 1
        )
        SidebarStrip(
            progress = progress,
            visibleFraction = visibleFraction,
            thumbColor = thumbColor,
            trackColor = trackColor,
            width = width,
            modifier = Modifier.weight(1f),
            onSeekFraction = onSeekFraction
        )
        Text(
            text = endLabel,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = SampThemeColors.textMuted,
            maxLines = 1
        )
    }
}

/** Metrics of a lazy list, exposed so a labelled strip can draw its own counters. */
@Composable
fun rememberLazyScrollMetrics(listState: LazyListState): Pair<Float, Float> {
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
    return metrics
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
                    // The thumb jumps to the finger on press and then follows it, so a
                    // long fling never accumulates drift between recompositions.
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


