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
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.insets.ui.Scaffold
import com.google.accompanist.insets.ui.TopAppBar
import io.ktor.client.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kodein.di.compose.rememberInstance
import xyz.quaver.pupil.R
import xyz.quaver.pupil.db.AppDatabase
import xyz.quaver.pupil.sources.composable.ReaderBase
import xyz.quaver.pupil.sources.composable.ReaderBaseViewModel
import xyz.quaver.pupil.sources.manatoki.MangaListing
import xyz.quaver.pupil.sources.manatoki.ReaderInfo
import xyz.quaver.pupil.sources.manatoki.getItem
import xyz.quaver.pupil.ui.theme.Orange500
import kotlin.math.max

private val imageUserAgent = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Mobile Safari/537.36"

@ExperimentalAnimationApi
@ExperimentalFoundationApi
@ExperimentalMaterialApi
@ExperimentalComposeUiApi
@Composable
fun Reader(navController: NavController) {
    val model: ReaderBaseViewModel = viewModel()

    val client: HttpClient by rememberInstance()

    val database: AppDatabase by rememberInstance()
    val bookmarkDao = database.bookmarkDao()

    val coroutineScope = rememberCoroutineScope()

    val itemID = navController.currentBackStackEntry?.arguments?.getString("itemID")
    var readerInfo: ReaderInfo? by rememberSaveable { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        if (itemID != null)
            client.getItem(itemID, onReader = {
                readerInfo = it
                model.load(it.urls) {
                    set("User-Agent", imageUserAgent)
                }
            })
        else model.error = true
    }

    val bookmark by bookmarkDao.contains("manatoki.net", itemID ?: "").observeAsState(false)

    val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    var mangaListing: MangaListing? by rememberSaveable { mutableStateOf(null) }
    val mangaListingRippleInteractionSource = remember { mutableStateListOf<MutableInteractionSource>() }
    val navigationBarsPadding = LocalDensity.current.run {
        rememberInsetsPaddingValues(
            LocalWindowInsets.current.navigationBars
        ).calculateBottomPadding().toPx()
    }

    val bottomSheetListState = rememberLazyListState()
    val readerListState = rememberLazyListState()

    var scrollDirection by remember { mutableStateOf(0f) }

    BackHandler {
        when {
            sheetState.isVisible -> coroutineScope.launch { sheetState.hide() }
            model.fullscreen -> model.fullscreen = false
            else -> navController.popBackStack()
        }
    }

    var mangaListingListSize: Size? by remember { mutableStateOf(null) }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(32.dp, 32.dp, 0.dp, 0.dp),
        sheetContent = {
            MangaListingBottomSheet(
                mangaListing,
                currentItemID = itemID,
                onListSize = {
                    mangaListingListSize = it
                },
                rippleInteractionSource = mangaListingRippleInteractionSource,
                listState = bottomSheetListState
            ) {
                navController.navigate("manatoki.net/reader/$it") {
                    popUpTo("manatoki.net/")
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (!model.fullscreen)
                    TopAppBar(
                        title = {
                            Text(
                                readerInfo?.title ?: stringResource(R.string.reader_loading),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(
                                    Icons.Default.NavigateBefore,
                                    contentDescription = null
                                )
                            }
                        },
                        actions = {
                            IconButton({ }) {
                                Image(
                                    painter = painterResource(R.drawable.manatoki),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(onClick = {
                                itemID?.let {
                                    coroutineScope.launch {
                                        if (bookmark) bookmarkDao.delete("manatoki.net", it)
                                        else          bookmarkDao.insert("manatoki.net", it)
                                    }
                                }
                            }) {
                                Icon(
                                    if (bookmark) Icons.Default.Star else Icons.Default.StarOutline,
                                    contentDescription = null,
                                    tint = Orange500
                                )
                            }
                        },
                        contentPadding = rememberInsetsPaddingValues(
                            LocalWindowInsets.current.statusBars,
                            applyBottom = false
                        )
                    )
            },
            floatingActionButton = {
                val showNextButton by derivedStateOf {
                    (readerInfo?.nextItemID?.isNotEmpty() == true) && with (readerListState.layoutInfo) {
                        visibleItemsInfo.lastOrNull()?.index == totalItemsCount-1
                    }
                }
                val scale by animateFloatAsState(if (!showNextButton && (model.fullscreen || scrollDirection < 0f)) 0f else 1f)

                if (scale > 0f)
                    FloatingActionButton(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .scale(scale),
                        onClick = {
                            readerInfo?.let {
                                if (showNextButton) {
                                    navController.navigate("manatoki.net/reader/${it.nextItemID}") {
                                        popUpTo("manatoki.net/")
                                    }
                                } else {
                                    coroutineScope.launch {
                                        sheetState.show()
                                    }

                                    coroutineScope.launch {
                                        if (mangaListing?.itemID != it.listingItemID)
                                            client.getItem(it.listingItemID, onListing = {
                                                mangaListing = it

                                                mangaListingRippleInteractionSource.addAll(
                                                    List(
                                                        max(
                                                            it.entries.size - mangaListingRippleInteractionSource.size,
                                                            0
                                                        )
                                                    ) {
                                                        MutableInteractionSource()
                                                    }
                                                )

                                                coroutineScope.launch {
                                                    while (bottomSheetListState.layoutInfo.totalItemsCount != it.entries.size) {
                                                        delay(100)
                                                    }

                                                    val targetIndex =
                                                        it.entries.indexOfFirst { it.itemID == itemID }

                                                    bottomSheetListState.scrollToItem(targetIndex)

                                                    mangaListingListSize?.let { sheetSize ->
                                                        val targetItem =
                                                            bottomSheetListState.layoutInfo.visibleItemsInfo.first {
                                                                it.key == itemID
                                                            }

                                                        if (targetItem.offset == 0) {
                                                            bottomSheetListState.animateScrollBy(
                                                                -(sheetSize.height - navigationBarsPadding - targetItem.size)
                                                            )
                                                        }

                                                        delay(200)

                                                        with(mangaListingRippleInteractionSource[targetIndex]) {
                                                            val interaction =
                                                                PressInteraction.Press(
                                                                    Offset(
                                                                        sheetSize.width / 2,
                                                                        targetItem.size / 2f
                                                                    )
                                                                )


                                                            emit(interaction)
                                                            emit(
                                                                PressInteraction.Release(
                                                                    interaction
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            })
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(
                            if (showNextButton) Icons.Default.NavigateNext else Icons.Default.List,
                            contentDescription = null
                        )
                    }
            }
        ) { contentPadding ->
            ReaderBase(
                Modifier.padding(contentPadding),
                model = model,
                listState = readerListState,
                onScroll = { scrollDirection = it }
            )
        }
    }
}
