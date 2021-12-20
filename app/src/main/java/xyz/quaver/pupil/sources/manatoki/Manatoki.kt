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
package xyz.quaver.pupil.sources.manatoki

import android.app.Application
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.insets.ui.Scaffold
import com.google.accompanist.insets.ui.TopAppBar
import io.ktor.client.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.compose.rememberInstance
import org.kodein.di.instance
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.pupil.R
import xyz.quaver.pupil.db.AppDatabase
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.sources.composable.ReaderBase
import xyz.quaver.pupil.sources.composable.ReaderBaseViewModel
import xyz.quaver.pupil.sources.composable.SourceSelectDialog
import xyz.quaver.pupil.sources.manatoki.composable.BoardButton
import xyz.quaver.pupil.sources.manatoki.composable.MangaListingBottomSheet
import xyz.quaver.pupil.sources.manatoki.composable.Thumbnail
import xyz.quaver.pupil.sources.manatoki.viewmodel.MainViewModel
import xyz.quaver.pupil.ui.theme.Orange500

class Manatoki(app: Application) : Source(), DIAware {
    override val di by closestDI(app)

    private val logger = newLogger(LoggerFactory.default)

    private val client: HttpClient by instance()

    override val name = "manatoki.net"
    override val iconResID = R.drawable.manatoki

    private val readerInfoMutex = Mutex()
    private val readerInfoCache = LruCache<String, ReaderInfo>(25)

    override fun NavGraphBuilder.navGraph(navController: NavController) {
        navigation(route = name, startDestination = "manatoki.net/") {
            composable("manatoki.net/") { Main(navController) }
            composable("manatoki.net/reader/{itemID}") { Reader(navController) }
        }
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun Main(navController: NavController) {
        val model: MainViewModel = viewModel()

        val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
        var mangaListing: MangaListing? by rememberSaveable { mutableStateOf(null) }

        val coroutineScope = rememberCoroutineScope()

        val onListing: (MangaListing) -> Unit = {
            mangaListing = it
        }

        val onReader: (ReaderInfo) -> Unit = { readerInfo ->
            coroutineScope.launch {
                readerInfoMutex.withLock {
                    readerInfoCache.put(readerInfo.itemID, readerInfo)
                }
                sheetState.snapTo(ModalBottomSheetValue.Hidden)
                navController.navigate("manatoki.net/reader/${readerInfo.itemID}")
            }
        }

        var sourceSelectDialog by remember { mutableStateOf(false) }

        if (sourceSelectDialog)
            SourceSelectDialog(navController, name) { sourceSelectDialog = false }

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

        ModalBottomSheetLayout(
            sheetState = sheetState,
            sheetShape = RoundedCornerShape(32.dp, 32.dp, 0.dp, 0.dp),
            sheetContent = {
                MangaListingBottomSheet(mangaListing) {
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
                            Text("박사장 게섯거라")
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
                }
            ) { contentPadding ->
                Box(Modifier.padding(contentPadding)) {
                    Column(
                        Modifier
                            .padding(8.dp, 0.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "최신화",
                            style = MaterialTheme.typography.h5
                        )

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(model.recentUpload) { item ->
                                Thumbnail(item) {
                                    coroutineScope.launch {
                                        mangaListing = null
                                        sheetState.show()
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

                        Text("만화 목록", style = MaterialTheme.typography.h5)
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(model.mangaList) { item ->
                                Thumbnail(item) {
                                    coroutineScope.launch {
                                        mangaListing = null
                                        sheetState.show()
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
                                            sheetState.show()
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
                                            modifier = Modifier.weight(1f).padding(0.dp, 4.dp),
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

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun Reader(navController: NavController) {
        val model: ReaderBaseViewModel = viewModel()

        val database: AppDatabase by rememberInstance()
        val bookmarkDao = database.bookmarkDao()

        val coroutineScope = rememberCoroutineScope()

        val itemID = navController.currentBackStackEntry?.arguments?.getString("itemID")
        var readerInfo: ReaderInfo? by rememberSaveable { mutableStateOf(null) }

        LaunchedEffect(itemID) {
            if (itemID != null)
                readerInfoMutex.withLock {
                    readerInfoCache.get(itemID)?.let {
                        readerInfo = it
                        model.load(it.urls)
                    } ?: run {
                        model.error = true
                    }
                }
        }

        val bookmark by bookmarkDao.contains(name, itemID ?: "").observeAsState(false)

        val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
        var mangaListing: MangaListing? by rememberSaveable { mutableStateOf(null) }

        BackHandler {
            when {
                sheetState.isVisible -> coroutineScope.launch { sheetState.hide() }
                model.fullscreen -> model.fullscreen = false
                else -> navController.popBackStack()
            }
        }

        ModalBottomSheetLayout(
            sheetState = sheetState,
            sheetShape = RoundedCornerShape(32.dp, 32.dp, 0.dp, 0.dp),
            sheetContent = {
                MangaListingBottomSheet(mangaListing) {
                    coroutineScope.launch {
                        client.getItem(
                            it,
                            onReader = {
                                coroutineScope.launch {
                                    readerInfoMutex.withLock {
                                        readerInfoCache.put(it.itemID, it)
                                    }
                                    navController.navigate("manatoki.net/reader/${it.itemID}") {
                                        popUpTo("manatoki.net/reader/$itemID") { inclusive = true }
                                    }
                                }
                            }
                        )
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
                                            if (bookmark) bookmarkDao.delete(name, it)
                                            else          bookmarkDao.insert(name, it)
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
                    FloatingActionButton(
                        modifier = Modifier.navigationBarsPadding(),
                        onClick = {
                            readerInfo?.let {
                                coroutineScope.launch {
                                    sheetState.show()
                                }

                                coroutineScope.launch {
                                    if (mangaListing?.itemID != it.listingItemID)
                                        client.getItem(it.listingItemID, onListing = {
                                            mangaListing = it
                                        })
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = null
                        )
                    }
                }
            ) { contentPadding ->
                ReaderBase(
                    Modifier.padding(contentPadding),
                    model
                )
            }
        }
    }
}