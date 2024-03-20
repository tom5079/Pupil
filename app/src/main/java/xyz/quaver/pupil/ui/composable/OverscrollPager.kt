package xyz.quaver.pupil.ui.composable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastFirstOrNull
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.theme.Blue300
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun OverscrollPager(
    prevPage: Int?,
    nextPage: Int?,
    onPageTurn: (Int) -> Unit,
    prevPageTurnIndicatorOffset: Dp = 0.dp,
    nextPageTurnIndicatorOffset: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val pageTurnIndicatorHeight = LocalDensity.current.run { 64.dp.toPx() }

    var overscroll: Float? by remember { mutableStateOf(null) }

    var size: Size? by remember { mutableStateOf(null) }
    val circleRadius = (size?.width ?: 0f) / 2

    val topCircleRadius by animateFloatAsState(if (overscroll?.let { it >= pageTurnIndicatorHeight } == true) circleRadius else 0f, label = "topCircleRadius")
    val bottomCircleRadius by animateFloatAsState(if (overscroll?.let { it <= -pageTurnIndicatorHeight } == true) circleRadius else 0f, label = "bottomCircleRadius")

    val prevPageTurnIndicatorOffsetPx = LocalDensity.current.run { prevPageTurnIndicatorOffset.toPx() }
    val nextPageTurnIndicatorOffsetPx = LocalDensity.current.run { nextPageTurnIndicatorOffset.toPx() }

    if (topCircleRadius != 0f || bottomCircleRadius != 0f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                Blue300,
                center = Offset(this.center.x, prevPageTurnIndicatorOffsetPx),
                radius = topCircleRadius
            )
            drawCircle(
                Blue300,
                center = Offset(this.center.x, this.size.height-nextPageTurnIndicatorOffsetPx),
                radius = bottomCircleRadius
            )
        }

    val isOverscrollOverHeight = overscroll?.let { abs(it) >= pageTurnIndicatorHeight } == true
    LaunchedEffect(isOverscrollOverHeight) {
        if (isOverscrollOverHeight) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Box(
        Modifier
            .fillMaxHeight()
            .onGloballyPositioned {
                size = it.size.toSize()
            }
    ) {
        overscroll?.let { overscroll ->
            if (overscroll > 0f && prevPage != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(0.dp, prevPageTurnIndicatorOffset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(stringResource(R.string.move_to_page, prevPage))
                }
            }

            if (overscroll < 0f && nextPage != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(0.dp, -nextPageTurnIndicatorOffset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.move_to_page, nextPage))
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset(
                    0.dp,
                    overscroll
                        ?.coerceIn(-pageTurnIndicatorHeight, pageTurnIndicatorHeight)
                        ?.let { overscroll -> LocalDensity.current.run { overscroll.toDp() } }
                        ?: 0.dp)
                .nestedScroll(object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        val overscrollSnapshot = overscroll

                        return if (overscrollSnapshot == null || overscrollSnapshot == 0f) {
                            Offset.Zero
                        } else {
                            val newOverscroll =
                                if (overscrollSnapshot > 0f && available.y < 0f)
                                    max(overscrollSnapshot + available.y, 0f)
                                else if (overscrollSnapshot < 0f && available.y > 0f)
                                    min(overscrollSnapshot + available.y, 0f)
                                else
                                    overscrollSnapshot

                            Offset(0f, newOverscroll - overscrollSnapshot).also {
                                overscroll = newOverscroll
                            }
                        }
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        if (
                            available.y == 0f ||
                            prevPage == null && available.y > 0f ||
                            nextPage == null && available.y < 0f
                        ) return Offset.Zero

                        return overscroll?.let {
                            overscroll = it + available.y
                            Offset(0f, available.y)
                        } ?: Offset.Zero
                    }
                })
                .pointerInput(prevPage, nextPage) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var pointer = down.id
                        overscroll = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val dragEvent =
                                event.changes.fastFirstOrNull { it.id == pointer }!!

                            if (dragEvent.changedToUpIgnoreConsumed()) {
                                val otherDown = event.changes.fastFirstOrNull { it.pressed }
                                if (otherDown == null) {
                                    if (dragEvent.positionChange() != Offset.Zero) dragEvent.consume()
                                    overscroll?.let {
                                        if (abs(it) > pageTurnIndicatorHeight) {
                                            if (it > 0 && prevPage != null) onPageTurn(prevPage)
                                            if (it < 0 && nextPage != null) onPageTurn(nextPage)
                                        }
                                    }
                                    overscroll = null
                                    break
                                } else
                                    pointer = otherDown.id
                            }
                        }
                    }
                }
        ) {
            content()
        }
    }
}