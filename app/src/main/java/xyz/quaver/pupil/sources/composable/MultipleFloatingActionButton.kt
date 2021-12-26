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

import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class FloatingActionButtonState(private val isExpanded: Boolean) {
    COLLAPSED(false), EXPANDED(true);

    operator fun not() = lookupTable[!this.isExpanded]!!

    companion object {
        private val lookupTable = mapOf(
            false to COLLAPSED,
            true to EXPANDED
        )
    }
}

data class SubFabItem(
    val label: String? = null,
    val onClick: ((SubFabItem) -> Unit)? = null,
    val icon: @Composable () -> Unit
)

@Composable
fun MiniFloatingActionButton(
    modifier: Modifier = Modifier,
    item: SubFabItem,
    buttonScale: Float = 1f,
    labelAlpha: Float = 1f,
    labelOffset: Dp = 0.dp,
    onClick: ((SubFabItem) -> Unit)? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val elevation = FloatingActionButtonDefaults.elevation()
        val interactionSource = remember { MutableInteractionSource() }

        item.label?.let { label ->
            Surface(
                modifier = Modifier
                    .alpha(labelAlpha)
                    .offset(x = labelOffset),
                shape = RoundedCornerShape(4.dp),
                elevation = elevation.elevation(interactionSource).value
            ) {
                Text(modifier = Modifier.padding(8.dp, 4.dp), text = label)
            }
        }

        if (buttonScale > 0f)
            FloatingActionButton(
                modifier = Modifier
                    .size(40.dp)
                    .scale(buttonScale),
                onClick = { onClick?.invoke(item) },
                elevation = elevation,
                interactionSource = interactionSource,
                content = item.icon
            )
    }
}

@Composable
fun MultipleFloatingActionButton(
    items: List<SubFabItem>,
    modifier: Modifier = Modifier,
    fabIcon: ImageVector = Icons.Default.Add,
    visible: Boolean = true,
    targetState: FloatingActionButtonState = FloatingActionButtonState.COLLAPSED,
    onStateChanged: ((FloatingActionButtonState) -> Unit)? = null
) {
    val transition = updateTransition(targetState = targetState, label = "expand")

    val rotation by transition.animateFloat(
        label = "FABRotation",
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        }) { state ->
        when (state) {
            FloatingActionButtonState.COLLAPSED -> 0f
            FloatingActionButtonState.EXPANDED -> 45f
        }
    }

    if (!visible) onStateChanged?.invoke(FloatingActionButtonState.COLLAPSED)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var allCollapsed = true

        items.forEachIndexed { index, item ->
            val delay = when (targetState) {
                FloatingActionButtonState.COLLAPSED -> index
                FloatingActionButtonState.EXPANDED -> (items.size - index)
            } * 50

            val buttonScale by transition.animateFloat(
                label = "miniFAB scale",
                transitionSpec = {
                    tween(
                        durationMillis = 100,
                        delayMillis = delay
                    )
                }
            ) { state ->
                when (state) {
                    FloatingActionButtonState.COLLAPSED -> 0f
                    FloatingActionButtonState.EXPANDED -> 1f
                }
            }

            val labelAlpha by transition.animateFloat(
                label = "miniFAB alpha",
                transitionSpec = {
                    tween(
                        durationMillis = 150,
                        delayMillis = delay,
                    )
                }
            ) { state ->
                when (state) {
                    FloatingActionButtonState.COLLAPSED -> 0f
                    FloatingActionButtonState.EXPANDED -> 1f
                }
            }

            val labelOffset by transition.animateDp(
                label = "miniFAB offset",
                transitionSpec = {
                    keyframes {
                        durationMillis = 200
                        delayMillis = delay

                        when (targetState) {
                            FloatingActionButtonState.COLLAPSED -> {
                                0.dp at 0
                                64.dp at 200
                            }
                            FloatingActionButtonState.EXPANDED -> {
                                64.dp at 0
                                (-4).dp at 150 with LinearEasing
                                0.dp at 200 with FastOutLinearInEasing
                            }
                        }
                    }
                }
            ) { state ->
                when (state) {
                    FloatingActionButtonState.COLLAPSED -> 64.dp
                    FloatingActionButtonState.EXPANDED -> 0.dp
                }
            }

            MiniFloatingActionButton(
                modifier = Modifier.padding(end = 8.dp),
                item = item,
                buttonScale = buttonScale,
                labelAlpha = labelAlpha,
                labelOffset = labelOffset
            ) {
                item.onClick?.invoke(it)
            }

            if (buttonScale != 0f) allCollapsed = false
        }

        val visibilityTransition = updateTransition(targetState = visible || !allCollapsed, label = "visible")

        val scale by visibilityTransition.animateFloat(
            label = "main FAB scale"
        ) { state ->
            if (state) 1f else 0f
        }

        if (scale > 0f)
            FloatingActionButton(
                modifier = Modifier.scale(scale),
                onClick = {
                    onStateChanged?.invoke(!targetState)
                }
            ) {
                Icon(modifier = Modifier.rotate(rotation), imageVector = fabIcon, contentDescription = null)
            }
    }
}