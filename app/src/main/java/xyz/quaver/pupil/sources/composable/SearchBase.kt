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

import android.app.Application
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class NavigationIconState {
    MENU,
    ARROW
}

open class SearchBaseViewModel<T>(app: Application) : AndroidViewModel(app) {
    val searchResults = mutableStateListOf<T>()

    var sortModeIndex by mutableStateOf(0)
        private set

    var currentPage by mutableStateOf(1)

    var totalItems by mutableStateOf(0)
        private set

    var maxPage by mutableStateOf(0)
        private set

    val prevPageAvailable by derivedStateOf { currentPage > 1 }
    val nextPageAvailable by derivedStateOf { currentPage <= maxPage }

    var query by mutableStateOf("")

    var loading by mutableStateOf(false)
        private set

    //region UI
    var isFabVisible by  mutableStateOf(true)
    var searchBarOffset by mutableStateOf(0)
    //endregion
}

@Composable
fun <T> SearchBase(
    model: SearchBaseViewModel<T> = viewModel(),
    fabSubMenu: List<SubFabItem> = emptyList(),
    actions: @Composable RowScope.() -> Unit = { },
    onSearch: () -> Unit = { },
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var isFabExpanded by remember { mutableStateOf(FloatingActionButtonState.COLLAPSED) }

    val navigationIcon = remember { DrawerArrowDrawable(context) }
    var navigationIconState by remember { mutableStateOf(NavigationIconState.MENU) }
    val navigationIconTransition = updateTransition(navigationIconState, label = "navigationIconTransition")
    val navigationIconProgress by navigationIconTransition.animateFloat(
        label = "navigationIconProgress"
    ) { state ->
        when (state) {
            NavigationIconState.MENU -> 0f
            NavigationIconState.ARROW -> 1f
        }
    }

    val pageTurnIndicatorHeight = LocalDensity.current.run { 64.dp.toPx() }
    val searchBarHeight = LocalDensity.current.run { 64.dp.roundToPx() }

    var overscroll: Float? by remember { mutableStateOf(null) }

    LaunchedEffect(navigationIconProgress) {
        navigationIcon.progress = navigationIconProgress
    }

    Scaffold(
        floatingActionButton = {
            MultipleFloatingActionButton(
                items = fabSubMenu,
                visible = model.isFabVisible,
                targetState = isFabExpanded,
                onStateChanged = {
                    isFabExpanded = it
                }
            )
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .offset(
                        0.dp,
                        overscroll?.let { overscroll -> LocalDensity.current.run { overscroll.toDp() } }
                            ?: 0.dp)
                    .nestedScroll(object : NestedScrollConnection {
                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            val overscrollSnapshot = overscroll

                            if (overscrollSnapshot == null || overscrollSnapshot == 0f) {
                                model.searchBarOffset = (model.searchBarOffset + available.y.roundToInt()).coerceIn(-searchBarHeight, 0)

                                model.isFabVisible = available.y > 0f

                                return Offset.Zero
                            } else {
                                val newOverscroll =
                                    if (overscrollSnapshot > 0f && available.y < 0f)
                                        max(overscrollSnapshot + available.y, 0f)
                                    else if (overscrollSnapshot < 0f && available.y > 0f)
                                        min(overscrollSnapshot + available.y, 0f)
                                    else
                                        overscrollSnapshot

                                return Offset(0f, newOverscroll - overscrollSnapshot).also {
                                    overscroll = newOverscroll
                                }
                            }
                        }

                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            if (available.y == 0f || source == NestedScrollSource.Fling) return Offset.Zero

                            return overscroll?.let {
                                val newOverscroll = (it + available.y).coerceIn(
                                    -pageTurnIndicatorHeight,
                                    pageTurnIndicatorHeight
                                )

                                Offset(0f, newOverscroll - it).also {
                                    overscroll = newOverscroll
                                }
                            } ?: Offset.Zero
                        }
                    }).pointerInput(Unit) {
                        forEachGesture {
                            awaitPointerEventScope {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var pointer = down.id
                                overscroll = 0f

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val dragEvent = event.changes.fastFirstOrNull { it.id == pointer }!!

                                    if (dragEvent.changedToUpIgnoreConsumed()) {
                                        val otherDown = event.changes.fastFirstOrNull { it.pressed }
                                        if (otherDown == null) {
                                            dragEvent.consumePositionChange()
                                            overscroll = null
                                            break
                                        } else
                                            pointer = otherDown.id
                                    }
                                }
                            }
                        }
                    },
                content = content
            )

            if (model.loading)
                CircularProgressIndicator(Modifier.align(Alignment.Center))

            FloatingSearchBar(
                modifier = Modifier.offset(0.dp, LocalDensity.current.run { model.searchBarOffset.toDp() }),
                query = model.query,
                onQueryChange = { model.query = it },
                navigationIcon = {
                    Icon(
                        painter = rememberDrawablePainter(navigationIcon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = false)
                            ) {
                                focusManager.clearFocus()
                            }
                    )
                },
                actions = actions,
                onTextFieldFocused = { navigationIconState = NavigationIconState.ARROW },
                onTextFieldUnfocused = { navigationIconState = NavigationIconState.MENU; onSearch() }
            )
        }
    }
}