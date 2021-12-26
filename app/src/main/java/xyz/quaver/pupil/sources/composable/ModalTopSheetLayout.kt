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

import android.util.Log
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import xyz.quaver.pupil.sources.composable.ModalTopSheetState.Expanded
import xyz.quaver.pupil.sources.composable.ModalTopSheetState.Hidden
import kotlin.math.roundToInt

class ModalTopSheetLayoutShape(
    private val cornerRadius: Dp,
    private val handleRadius: Dp
): Shape {

    private fun drawDrawerPath(
        size: Size,
        cornerRadius: Float,
        handleRadius: Float
    ) = Path().apply {
        reset()

        lineTo(x = size.width, y = 0f)

        lineTo(x = size.width, y = size.height - cornerRadius)

        arcTo(
            Rect(
                left = size.width - 2*cornerRadius,
                top = size.height - 2*cornerRadius,
                right = size.width,
                bottom = size.height
            ),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )

        lineTo(x = size.width / 2 + handleRadius, y = size.height)

        arcTo(
            Rect(
                left = size.width/2 - handleRadius,
                top = size.height - handleRadius,
                right = size.width/2 + handleRadius,
                bottom = size.height + handleRadius
            ),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false
        )

        lineTo(x = cornerRadius, y = size.height)

        arcTo(
            Rect(
                left = 0f,
                top =  size.height - 2*cornerRadius,
                right = 2*cornerRadius,
                bottom = size.height
            ),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )

        close()
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Generic(
        path = drawDrawerPath(
            size,
            density.run { cornerRadius.toPx() },
            density.run { handleRadius.toPx() }
        )
    )

}

enum class ModalTopSheetState {
    Hidden,
    Expanded
}

@Composable
private fun Scrim(
    color: Color,
    onDismiss: () -> Unit,
    visible: Boolean
) {
    if (color.isSpecified) {
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = TweenSpec()
        )
        val dismissModifier = if (visible) {
            Modifier.pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
        } else {
            Modifier
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .then(dismissModifier)
        ) {
            drawRect(color = color, alpha = alpha)
        }
    }
}

@Composable
@ExperimentalMaterialApi
fun ModalTopSheetLayout(
    drawerContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    drawerCornerRadius: Dp = SearchOptionDrawerDefaults.CornerRadius,
    drawerHandleRadius: Dp = SearchOptionDrawerDefaults.HandleRadius,
    drawerState: SwipeableState<ModalTopSheetState> = rememberSwipeableState(Hidden),
    drawerElevation: Dp = SearchOptionDrawerDefaults.Elevation,
    drawerBackgroundColor: Color = MaterialTheme.colors.surface,
    drawerContentColor: Color = contentColorFor(drawerBackgroundColor),
    scrimColor: Color = SearchOptionDrawerDefaults.scrimColor,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val nestedScrollConnection = remember {
        object: NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                return if (delta > 0 && source == NestedScrollSource.Drag)
                    Offset(0f, drawerState.performDrag(delta))
                else
                    Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return if (source == NestedScrollSource.Drag)
                    Offset(0f, drawerState.performDrag(available.y))
                else
                    Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity = Velocity.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                drawerState.performFling(available.y)
                return available
            }
        }
    }

    BoxWithConstraints {
        var sheetHeight by remember { mutableStateOf<Float?>(null) }

        Box(Modifier.fillMaxSize()) {
            content()
            Scrim(
                color = scrimColor,
                onDismiss = {
                    coroutineScope.launch { drawerState.animateTo(Hidden) }
                },
                visible = drawerState.targetValue != Hidden
            )
        }

        Surface(
            modifier
                .fillMaxWidth()
                .nestedScroll(nestedScrollConnection)
                .offset {
                    IntOffset(0, drawerState.offset.value.roundToInt())
                }
                .drawerSwipeable(drawerState, sheetHeight)
                .onGloballyPositioned {
                    sheetHeight = it.size.height.toFloat()
                },
            shape = ModalTopSheetLayoutShape(drawerCornerRadius, drawerHandleRadius),
            elevation = drawerElevation,
            color = drawerBackgroundColor,
            contentColor = drawerContentColor
        ) {
            Column(content = drawerContent)
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.BottomCenter)
                    .offset(0.dp, drawerHandleRadius)
            )
        }

        Box(
            modifier = Modifier
                .size(2*drawerHandleRadius, drawerHandleRadius)
                .align(Alignment.TopCenter)
                .pointerInput(drawerState) {
                    detectTapGestures {
                        coroutineScope.launch {
                            drawerState.animateTo(Expanded)
                        }
                    }
                }
        ) { }
    }
}

@ExperimentalMaterialApi
private fun Modifier.drawerSwipeable(
    drawerState: SwipeableState<ModalTopSheetState>,
    sheetHeight: Float?
) = this.then(
    if (sheetHeight != null) {
        val anchors = mapOf(
            -sheetHeight to Hidden,
            0f to Expanded
        )

        Modifier.swipeable(
            state = drawerState,
            anchors = anchors,
            orientation = Orientation.Vertical,
            enabled = drawerState.currentValue != Hidden,
            resistance = null
        )
    } else Modifier
)

object SearchOptionDrawerDefaults {
    val Elevation = 16.dp
    val scrimColor: Color
        @Composable
        get() = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
    val CornerRadius = 32.dp
    val HandleRadius = 32.dp
}