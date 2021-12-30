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

package xyz.quaver.pupil.sources.manatoki.composable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.insets.ui.Scaffold
import com.google.accompanist.insets.ui.TopAppBar
import io.ktor.client.*
import kotlinx.coroutines.launch
import org.kodein.di.compose.rememberInstance
import org.kodein.di.compose.rememberViewModel
import xyz.quaver.pupil.sources.composable.OverscrollPager
import xyz.quaver.pupil.sources.manatoki.getItem
import xyz.quaver.pupil.sources.manatoki.viewmodel.RecentViewModel

@ExperimentalFoundationApi
@ExperimentalMaterialApi
@Composable
fun Recent(navController: NavController) {
    val model: RecentViewModel by rememberViewModel()

    val client: HttpClient by rememberInstance()

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        model.load()
    }

    BackHandler {
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("최신 업데이트")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.Default.NavigateBefore,
                            contentDescription = null
                        )
                    }
                },
                contentPadding = rememberInsetsPaddingValues(
                    LocalWindowInsets.current.statusBars,
                    applyBottom = false
                )
            )
        }
    ) { contentPadding ->
        Box(Modifier.padding(contentPadding)) {
            OverscrollPager(
                currentPage = model.page,
                prevPageAvailable = model.page > 1,
                nextPageAvailable = model.page < 10,
                nextPageTurnIndicatorOffset = rememberInsetsPaddingValues(
                    LocalWindowInsets.current.navigationBars
                ).calculateBottomPadding(),
                onPageTurn = {
                    model.page = it
                    model.load()
                }
            ) {
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        GridCells.Adaptive(minSize = 200.dp),
                        contentPadding = rememberInsetsPaddingValues(
                            LocalWindowInsets.current.navigationBars
                        )
                    ) {
                        items(model.result) {
                            Thumbnail(
                                it,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(3f / 4)
                                    .padding(8.dp)
                            ) {
                                coroutineScope.launch {
                                    client.getItem(it, onReader = {
                                        navController.navigate("manatoki.net/reader/${it.itemID}")
                                    })
                                }
                            }
                        }
                    }

                    if (model.loading)
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
