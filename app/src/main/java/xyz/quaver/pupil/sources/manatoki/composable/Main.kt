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
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.insets.ui.Scaffold
import com.google.accompanist.insets.ui.TopAppBar
import io.ktor.client.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.kodein.di.compose.rememberInstance
import org.kodein.di.compose.rememberViewModel
import xyz.quaver.pupil.R
import xyz.quaver.pupil.proto.settingsDataStore
import xyz.quaver.pupil.sources.composable.SourceSelectDialog
import xyz.quaver.pupil.sources.manatoki.ManatokiDatabase
import xyz.quaver.pupil.sources.manatoki.MangaListing
import xyz.quaver.pupil.sources.manatoki.ReaderInfo
import xyz.quaver.pupil.sources.manatoki.getItem
import xyz.quaver.pupil.sources.manatoki.viewmodel.MainViewModel

@ExperimentalMaterialApi
@Composable
fun Main(navController: NavController) {
    val model: MainViewModel by rememberViewModel()

    val client: HttpClient by rememberInstance()

    val database: ManatokiDatabase by rememberInstance()
    val historyDao = remember { database.historyDao() }
    val recent by remember { historyDao.getRecentManga() }.collectAsState(emptyList())
    val recentManga = remember { mutableStateListOf<Thumbnail>() }

    LaunchedEffect(recent) {
        recentManga.clear()

        recent.forEach {
            if (isActive)
                client.getItem(it, onListing = {
                    recentManga.add(
                        Thumbnail(it.itemID, it.title, it.thumbnail)
                    )
                })
        }
    }

    val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)

    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        context.settingsDataStore.updateData {
            it.toBuilder()
                .setRecentSource("manatoki.net")
                .build()
        }
    }

    var sourceSelectDialog by remember { mutableStateOf(false) }

    if (sourceSelectDialog)
        SourceSelectDialog(navController, "manatoki.net") { sourceSelectDialog = false }

    LaunchedEffect(Unit) {
        model.load()
    }

    BackHandler {
        if (sheetState.currentValue == ModalBottomSheetValue.Hidden)
            navController.popBackStack()
        else
            coroutineScope.launch {
                sheetState.hide()
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

    val onListing: (MangaListing) -> Unit = {
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
    }

    val onReader: (ReaderInfo) -> Unit = { readerInfo ->
        coroutineScope.launch {
            sheetState.snapTo(ModalBottomSheetValue.Hidden)
            navController.navigate("manatoki.net/reader/${readerInfo.itemID}")
        }
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
                    client.getItem(it, onListing, onReader)
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("마나토끼")
                    },
                    actions = {
                        IconButton(onClick = { sourceSelectDialog = true }) {
                            Image(
                                painter = painterResource(id = R.drawable.manatoki),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                        }
                    },
                    contentPadding = rememberInsetsPaddingValues(
                        insets = LocalWindowInsets.current.statusBars,
                        applyBottom = false
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    modifier = Modifier.navigationBarsPadding(),
                    onClick = {
                        navController.navigate("manatoki.net/search")
                    }
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null
                    )
                }
            }
        ) { contentPadding ->
            Box(Modifier.padding(contentPadding)) {
                Column(
                    Modifier
                        .padding(8.dp, 0.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (recentManga.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "이어 보기",
                                style = MaterialTheme.typography.h5
                            )

                            IconButton(onClick = { navController.navigate("manatoki.net/recent") }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null
                                )
                            }
                        }

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(recentManga) { item ->
                                Thumbnail(
                                    item,
                                    Modifier
                                        .width(180.dp)
                                        .aspectRatio(6 / 7f)
                                ) {
                                    coroutineScope.launch {
                                        mangaListing = null
                                        sheetState.animateTo(ModalBottomSheetValue.Expanded)
                                    }
                                    coroutineScope.launch {
                                        client.getItem(it, onListing, onReader)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "최신화",
                            style = MaterialTheme.typography.h5
                        )

                        IconButton(onClick = { navController.navigate("manatoki.net/recent") }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null
                            )
                        }
                    }

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(model.recentUpload) { item ->
                            Thumbnail(item,
                                Modifier
                                    .width(180.dp)
                                    .aspectRatio(6 / 7f)) {
                                coroutineScope.launch {
                                    mangaListing = null
                                    sheetState.animateTo(ModalBottomSheetValue.Expanded)
                                }
                                coroutineScope.launch {
                                    client.getItem(it, onListing, onReader)
                                }
                            }
                        }
                    }

                    Divider()

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            BoardButton("마나게시판", Color(0xFF007DB4))
                            BoardButton("유머/가십", Color(0xFFF09614))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            BoardButton("역식자게시판", Color(0xFFA0C850))
                            BoardButton("원본게시판", Color(0xFFFF4500))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("만화 목록", style = MaterialTheme.typography.h5)

                        IconButton(onClick = { navController.navigate("manatoki.net/search") }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null
                            )
                        }
                    }
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(model.mangaList) { item ->
                            Thumbnail(item,
                                Modifier
                                    .width(180.dp)
                                    .aspectRatio(6f / 7)) {
                                coroutineScope.launch {
                                    mangaListing = null
                                    sheetState.animateTo(ModalBottomSheetValue.Expanded)
                                }
                                coroutineScope.launch {
                                    client.getItem(it, onListing, onReader)
                                }
                            }
                        }
                    }

                    Text("주간 베스트", style = MaterialTheme.typography.h5)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        model.topWeekly.forEachIndexed { index, item ->
                            Card(
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        mangaListing = null
                                        sheetState.animateTo(ModalBottomSheetValue.Expanded)
                                    }

                                    coroutineScope.launch {
                                        client.getItem(item.itemID, onListing, onReader)
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF64C3F5))
                                            .width(24.dp)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            (index + 1).toString(),
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    Text(
                                        item.title,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(0.dp, 4.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        item.count,
                                        color = Color(0xFFFF4500)
                                    )
                                }
                            }
                        }
                    }

                    Box(Modifier.navigationBarsPadding())
                }
            }
        }
    }
}
