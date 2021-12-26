/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2021 tom5079
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.sources.composable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastFirstOrNull
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.theme.LightBlue300
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

@Composable
fun OverscrollPager(
    currentPage: Int,
    prevPageAvailable: Boolean,
    nextPageAvailable: Boolean,
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

    val topCircleRadius by animateFloatAsState(if (overscroll?.let { it >= pageTurnIndicatorHeight } == true) circleRadius else 0f)
    val bottomCircleRadius by animateFloatAsState(if (overscroll?.let { it <= -pageTurnIndicatorHeight } == true) circleRadius else 0f)

    val prevPageTurnIndicatorOffsetPx = LocalDensity.current.run { prevPageTurnIndicatorOffset.toPx() }
    val nextPageTurnIndicatorOffsetPx = LocalDensity.current.run { nextPageTurnIndicatorOffset.toPx() }

    if (topCircleRadius != 0f || bottomCircleRadius != 0f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                LightBlue300,
                center = Offset(this.center.x, prevPageTurnIndicatorOffsetPx),
                radius = topCircleRadius
            )
            drawCircle(
                LightBlue300,
                center = Offset(this.center.x, this.size.height-pageTurnIndicatorHeight-nextPageTurnIndicatorOffsetPx),
                radius = bottomCircleRadius
            )
        }

    val isOverscrollOverHeight = overscroll?.let { abs(it) >= pageTurnIndicatorHeight } == true
    LaunchedEffect(isOverscrollOverHeight) {
        if (isOverscrollOverHeight) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Box(
        Modifier.onGloballyPositioned {
            size = it.size.toSize()
        }
    ) {
        overscroll?.let { overscroll ->
            if (overscroll > 0f)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(0.dp, prevPageTurnIndicatorOffset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.NavigateBefore,
                        contentDescription = null,
                        tint = MaterialTheme.colors.secondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(stringResource(R.string.main_move_to_page, currentPage - 1))
                }

            if (overscroll < 0f)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(0.dp, -nextPageTurnIndicatorOffset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.main_move_to_page, currentPage + 1))
                    Icon(
                        Icons.Default.NavigateNext,
                        contentDescription = null,
                        tint = MaterialTheme.colors.secondary,
                        modifier = Modifier.size(48.dp)
                    )
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
                            !prevPageAvailable && available.y > 0f ||
                            !nextPageAvailable && available.y < 0f
                        ) return Offset.Zero

                        return overscroll?.let {
                            overscroll = it + available.y
                            Offset(0f, available.y)
                        } ?: Offset.Zero
                    }
                })
                .pointerInput(currentPage) {
                    forEachGesture {
                        awaitPointerEventScope {
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
                                        dragEvent.consumePositionChange()
                                        overscroll?.let {
                                            if (abs(it) > pageTurnIndicatorHeight)
                                                onPageTurn(currentPage - it.sign.toInt())
                                        }
                                        overscroll = null
                                        break
                                    } else
                                        pointer = otherDown.id
                                }
                            }
                        }
                    }
                }
        ) {
            content()
        }
    }
}