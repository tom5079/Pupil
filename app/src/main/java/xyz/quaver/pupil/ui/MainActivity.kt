/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2019  tom5079
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
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.*
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.compose.withDI
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.pupil.*
import xyz.quaver.pupil.R
import xyz.quaver.pupil.sources.SearchResultEvent
import xyz.quaver.pupil.types.*
import xyz.quaver.pupil.ui.composable.FloatingActionButtonState
import xyz.quaver.pupil.ui.composable.FloatingSearchBar
import xyz.quaver.pupil.ui.composable.MultipleFloatingActionButton
import xyz.quaver.pupil.ui.composable.SubFabItem
import xyz.quaver.pupil.ui.dialog.SourceSelectDialog
import xyz.quaver.pupil.ui.dialog.SourceSelectDialogItem
import xyz.quaver.pupil.ui.theme.PupilTheme
import xyz.quaver.pupil.ui.view.ProgressCardView
import xyz.quaver.pupil.ui.viewmodel.MainViewModel
import xyz.quaver.pupil.util.*
import kotlin.math.*

private enum class NavigationIconState {
    MENU,
    ARROW
}

class MainActivity : ComponentActivity(), DIAware {
    override val di by closestDI()

    private val model: MainViewModel by viewModels()

    private val logger = newLogger(LoggerFactory.default)

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PupilTheme {
                val focusManager = LocalFocusManager.current

                var isFabExpanded by remember { mutableStateOf(FloatingActionButtonState.COLLAPSED) }
                var isFabVisible by remember { mutableStateOf(true) }

                val searchBarHeight = LocalDensity.current.run { 56.dp.roundToPx() }
                var searchBarOffset by remember { mutableStateOf(0) }

                val navigationIcon = remember { DrawerArrowDrawable(this) }
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

                var openSourceSelectDialog by remember { mutableStateOf(false) }

                LaunchedEffect(navigationIconProgress) {
                    navigationIcon.progress = navigationIconProgress
                }

                if (openSourceSelectDialog)
                    SourceSelectDialog {
                        openSourceSelectDialog = false
                    }

                Scaffold(
                    floatingActionButton = {
                        MultipleFloatingActionButton(
                            listOf(
                                SubFabItem(
                                    Icons.Default.Block,
                                    stringResource(R.string.main_fab_cancel)
                                ),
                                SubFabItem(
                                    painterResource(R.drawable.ic_jump),
                                    stringResource(R.string.main_jump_title)
                                ),
                                SubFabItem(
                                    Icons.Default.Shuffle,
                                    stringResource(R.string.main_fab_random)
                                ),
                                SubFabItem(
                                    painterResource(R.drawable.numeric),
                                    stringResource(R.string.main_open_gallery_by_id)
                                ),
                            ),
                            visible = isFabVisible,
                            targetState = isFabExpanded,
                            onStateChanged = {
                                isFabExpanded = it
                            }
                        )
                    }
                ) {
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            Modifier
                                .fillMaxSize()
                                .nestedScroll(object : NestedScrollConnection {
                                    override fun onPreScroll(
                                        available: Offset,
                                        source: NestedScrollSource
                                    ): Offset {
                                        searchBarOffset =
                                            (searchBarOffset + available.y.roundToInt()).coerceIn(
                                                -searchBarHeight,
                                                0
                                            )

                                        isFabVisible = available.y > 0f

                                        return Offset.Zero
                                    }
                                }),
                            contentPadding = PaddingValues(0.dp, 56.dp, 0.dp, 0.dp)
                        ) {
                            items(model.searchResults, key = { it.itemID }) { itemInfo ->
                                ProgressCardView(
                                    progress = 0.5f
                                ) {
                                    model.source.SearchResult(itemInfo = itemInfo) { event ->
                                        when (event.type) {
                                            SearchResultEvent.Type.OPEN_READER -> {
                                                startActivity(
                                                    Intent(
                                                        this@MainActivity,
                                                        ReaderActivity::class.java
                                                    ).apply {
                                                        putExtra("source", model.source.name)
                                                        putExtra("id", itemInfo.itemID)
                                                    })
                                            }
                                            else -> TODO("")
                                        }
                                    }
                                }
                            }
                        }

                        if (model.loading)
                            CircularProgressIndicator(Modifier.align(Alignment.Center))

                        FloatingSearchBar(
                            modifier = Modifier.offset(0.dp, LocalDensity.current.run { searchBarOffset.toDp() }),
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
                            actions = {
                                Image(
                                    painterResource(model.source.iconResID),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).clickable {
                                        openSourceSelectDialog = true
                                    }
                                )
                                Icon(
                                    Icons.Default.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                )
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                )
                            },
                            onTextFieldFocused = { navigationIconState = NavigationIconState.ARROW },
                            onTextFieldUnfocused = { navigationIconState = NavigationIconState.MENU; model.resetAndQuery() }
                        )
                    }
                }
            }
        }
    }
}
