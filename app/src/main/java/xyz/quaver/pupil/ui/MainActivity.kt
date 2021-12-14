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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.pupil.*
import xyz.quaver.pupil.R
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.types.*
import xyz.quaver.pupil.ui.composable.FloatingActionButtonState
import xyz.quaver.pupil.ui.composable.FloatingSearchBar
import xyz.quaver.pupil.ui.composable.MultipleFloatingActionButton
import xyz.quaver.pupil.ui.composable.SubFabItem
import xyz.quaver.pupil.ui.theme.PupilTheme
import xyz.quaver.pupil.ui.view.ProgressCardView
import xyz.quaver.pupil.ui.viewmodel.MainViewModel
import xyz.quaver.pupil.util.*
import kotlin.math.*

class MainActivity : ComponentActivity(), DIAware {
    override val di by closestDI()

    private val model: MainViewModel by viewModels()

    private val logger = newLogger(LoggerFactory.default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val source: Source? by model.source.observeAsState(null)
            val loading: Boolean by model.loading.observeAsState(false)

            var query by remember { mutableStateOf("") }

            var isFabExpanded by remember { mutableStateOf(FloatingActionButtonState.COLLAPSED) }

            val lazyListState = rememberLazyListState()

            val searchBarHeight = LocalDensity.current.run { 56.dp.roundToPx() }
            var searchBarOffset by remember { mutableStateOf(0) }

            LaunchedEffect(lazyListState) {
                var lastOffset = 0

                snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
                    .distinctUntilChanged()
                    .collect { newOffset ->
                        val dy = newOffset - lastOffset
                        lastOffset = newOffset

                        if (abs(dy) < searchBarHeight)
                            searchBarOffset = (searchBarOffset-dy).coerceIn(-searchBarHeight, 0)
                    }
            }

            PupilTheme {
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
                            targetState = isFabExpanded,
                            onStateChanged = {
                                isFabExpanded = it
                            }
                        )
                    }
                ) {
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            state = lazyListState,
                            contentPadding = PaddingValues(0.dp, 56.dp, 0.dp, 0.dp)
                        ) {
                            items(model.searchResults, key = { it.itemID }) { itemInfo ->
                                ProgressCardView(
                                    progress = 0.5f,
                                    onClick = {
                                        startActivity(
                                            Intent(
                                                this@MainActivity,
                                                ReaderActivity::class.java
                                            ).apply {
                                                putExtra("source", model.source.value!!.name)
                                                putExtra("id", itemInfo.itemID)
                                            })
                                    }
                                ) {
                                    source?.SearchResult(itemInfo = itemInfo)
                                }
                            }
                        }

                        if (loading)
                            CircularProgressIndicator(Modifier.align(Alignment.Center))

                        FloatingSearchBar(
                            modifier = Modifier.offset(0.dp, LocalDensity.current.run { searchBarOffset.toDp() }),
                            query = query,
                            onQueryChange = { query = it },
                            actions = {
                                Icon(
                                    Icons.Default.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
