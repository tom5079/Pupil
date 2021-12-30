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

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.navigationBarsWithImePadding
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.insets.ui.Scaffold
import com.google.accompanist.insets.ui.TopAppBar
import io.ktor.client.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kodein.di.compose.rememberInstance
import org.kodein.di.compose.rememberViewModel
import xyz.quaver.pupil.sources.composable.ModalTopSheetLayout
import xyz.quaver.pupil.sources.composable.ModalTopSheetState
import xyz.quaver.pupil.sources.composable.OverscrollPager
import xyz.quaver.pupil.sources.manatoki.*
import xyz.quaver.pupil.sources.manatoki.viewmodel.*

@ExperimentalFoundationApi
@ExperimentalMaterialApi
@Composable
fun Search(navController: NavController) {
    val model: SearchViewModel by rememberViewModel()

    val client: HttpClient by rememberInstance()

    val database: ManatokiDatabase by rememberInstance()
    val historyDao = remember { database.historyDao() }

    var searchFocused by remember { mutableStateOf(false) }
    val handleOffset by animateDpAsState(if (searchFocused) 0.dp else (-36).dp)

    val drawerState = rememberSwipeableState(ModalTopSheetState.Hidden)
    val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)

    val coroutineScope = rememberCoroutineScope()

    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        model.search()
    }

    BackHandler {
        when {
            sheetState.isVisible -> coroutineScope.launch { sheetState.hide() }
            drawerState.currentValue != ModalTopSheetState.Hidden ->
                coroutineScope.launch { drawerState.animateTo(ModalTopSheetState.Hidden) }
            else -> navController.popBackStack()
        }
    }

    var mangaListing: MangaListing? by rememberSaveable { mutableStateOf(null) }
    var recentItem: String? by rememberSaveable { mutableStateOf(null) }
    val mangaListingListState = rememberLazyListState()
    var mangaListingListSize: Size? by remember { mutableStateOf(null) }
    val mangaListingInteractionSource = remember { mutableStateMapOf<String, MutableInteractionSource>() }
    val navigationBarsPadding = LocalDensity.current.run {
        rememberInsetsPaddingValues(
            LocalWindowInsets.current.navigationBars
        ).calculateBottomPadding().toPx()
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(32.dp, 32.dp, 0.dp, 0.dp),
        sheetContent = {
            MangaListingBottomSheet(
                mangaListing,
                onListSize = { mangaListingListSize = it },
                rippleInteractionSource = mangaListingInteractionSource,
                listState = mangaListingListState,
                recentItem = recentItem
            ) {
                coroutineScope.launch {
                    client.getItem(it, onReader = {
                        launch {
                            sheetState.snapTo(ModalBottomSheetValue.Hidden)
                            navController.navigate("manatoki.net/reader/${it.itemID}")
                        }
                    })
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures { focusManager.clearFocus() }
                },
            topBar = {
                TopAppBar(
                    title = {
                        TextField(
                            model.stx,
                            modifier = Modifier
                                .onFocusChanged {
                                    searchFocused = it.isFocused
                                }
                                .fillMaxWidth(),
                            onValueChange = { model.stx = it },
                            placeholder = { Text("제목") },
                            textStyle = MaterialTheme.typography.subtitle1,
                            singleLine = true,
                            trailingIcon = {
                                if (model.stx != "" && searchFocused)
                                    IconButton(onClick = { model.stx = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            tint = contentColorFor(MaterialTheme.colors.primarySurface)
                                        )
                                    }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                    coroutineScope.launch {
                                        drawerState.animateTo(ModalTopSheetState.Hidden)
                                    }
                                    coroutineScope.launch {
                                        model.search()
                                    }
                                }
                            ),
                            colors = TextFieldDefaults.textFieldColors(
                                textColor = contentColorFor(MaterialTheme.colors.primarySurface),
                                placeholderColor = contentColorFor(MaterialTheme.colors.primarySurface).copy(alpha = 0.75f),
                                backgroundColor = Color.Transparent,
                                cursorColor = MaterialTheme.colors.secondary,
                                disabledIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                errorIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
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
                    contentPadding = rememberInsetsPaddingValues(
                        LocalWindowInsets.current.statusBars,
                        applyBottom = false
                    )
                )
            }
        ) { contentPadding ->
            Box(Modifier.padding(contentPadding)) {
                ModalTopSheetLayout(
                    modifier = Modifier.run {
                        if (drawerState.currentValue == ModalTopSheetState.Hidden)
                            offset(0.dp, handleOffset)
                        else
                            navigationBarsWithImePadding()
                    },
                    drawerState = drawerState,
                    drawerContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp, 0.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("작가")
                            TextField(model.artist, onValueChange = { model.artist = it })

                            Text("발행")
                            FlowRow(mainAxisSpacing = 4.dp, crossAxisSpacing = 4.dp) {
                                Chip("전체", model.publish.isEmpty()) {
                                    model.publish = ""
                                }
                                availablePublish.forEach {
                                    Chip(it, model.publish == it) {
                                        model.publish = it
                                    }
                                }
                            }

                            Text("초성")
                            FlowRow(mainAxisSpacing = 4.dp, crossAxisSpacing = 4.dp) {
                                Chip("전체", model.jaum.isEmpty()) {
                                    model.jaum = ""
                                }
                                availableJaum.forEach {
                                    Chip(it, model.jaum == it) {
                                        model.jaum = it
                                    }
                                }
                            }

                            Text("장르")
                            FlowRow(mainAxisSpacing = 4.dp, crossAxisSpacing = 4.dp) {
                                Chip("전체", model.tag.isEmpty()) {
                                    model.tag.clear()
                                }
                                availableTag.forEach {
                                    Chip(it, model.tag.contains(it)) {
                                        if (model.tag.contains(it))
                                            model.tag.remove(it)
                                        else
                                            model.tag[it] = it
                                    }
                                }
                            }

                            Text("정렬")
                            FlowRow(mainAxisSpacing = 4.dp, crossAxisSpacing = 4.dp) {
                                Chip("기본", model.sst.isEmpty()) {
                                    model.sst = ""
                                }
                                availableSst.entries.forEach { (k, v) ->
                                    Chip(v, model.sst == k) {
                                        model.sst = k
                                    }
                                }
                            }

                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(8.dp))
                        }
                    }
                ) {
                    OverscrollPager(
                        currentPage = model.page,
                        prevPageAvailable = model.page > 1,
                        nextPageAvailable = model.page < model.maxPage,
                        nextPageTurnIndicatorOffset = rememberInsetsPaddingValues(
                            LocalWindowInsets.current.navigationBars
                        ).calculateBottomPadding(),
                        onPageTurn = {
                            model.page = it
                            coroutineScope.launch {
                                model.search(resetPage = false)
                            }
                        }
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                GridCells.Adaptive(minSize = 200.dp),
                                contentPadding = rememberInsetsPaddingValues(
                                    LocalWindowInsets.current.navigationBars
                                )
                            ) {
                                items(model.result) { item ->
                                    Thumbnail(
                                        Thumbnail(item.itemID, item.title, item.thumbnail),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(3f / 4)
                                            .padding(8.dp)
                                    ) {
                                        coroutineScope.launch {
                                            mangaListing = null
                                            sheetState.animateTo(ModalBottomSheetValue.Expanded)
                                        }
                                        coroutineScope.launch {
                                            client.getItem(it, onListing = {
                                                mangaListing = it

                                                coroutineScope.launch {
                                                    val recentItemID = historyDao.getAll(it.itemID).firstOrNull() ?: return@launch
                                                    recentItem = recentItemID

                                                    while (mangaListingListState.layoutInfo.totalItemsCount != it.entries.size) {
                                                        delay(100)
                                                    }

                                                    val interactionSource = mangaListingInteractionSource.getOrPut(recentItemID) {
                                                        MutableInteractionSource()
                                                    }

                                                    val targetIndex =
                                                        it.entries.indexOfFirst { entry -> entry.itemID == recentItemID }

                                                    mangaListingListState.scrollToItem(targetIndex)

                                                    mangaListingListSize?.let { sheetSize ->
                                                        val targetItem =
                                                            mangaListingListState.layoutInfo.visibleItemsInfo.first {
                                                                it.key == recentItemID
                                                            }

                                                        if (targetItem.offset == 0) {
                                                            mangaListingListState.animateScrollBy(
                                                                -(sheetSize.height - navigationBarsPadding - targetItem.size)
                                                            )
                                                        }

                                                        delay(200)

                                                        with(interactionSource) {
                                                            val interaction =
                                                                PressInteraction.Press(
                                                                    Offset(
                                                                        sheetSize.width / 2,
                                                                        targetItem.size / 2f
                                                                    )
                                                                )


                                                            emit(interaction)
                                                            emit(PressInteraction.Release(interaction))
                                                        }
                                                    }
                                                }
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
    }
}