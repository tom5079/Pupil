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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.navigationBarsWithImePadding
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.insets.ui.Scaffold
import com.google.accompanist.insets.ui.TopAppBar
import io.ktor.client.*
import kotlinx.coroutines.delay
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
import xyz.quaver.pupil.proto.settingsDataStore
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.sources.composable.OverscrollPager
import xyz.quaver.pupil.sources.composable.ReaderBase
import xyz.quaver.pupil.sources.composable.ReaderBaseViewModel
import xyz.quaver.pupil.sources.composable.SourceSelectDialog
import xyz.quaver.pupil.sources.manatoki.composable.*
import xyz.quaver.pupil.sources.manatoki.viewmodel.*
import xyz.quaver.pupil.ui.theme.Orange500
import kotlin.math.max

private val imageUserAgent = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Mobile Safari/537.36"

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
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
            composable("manatoki.net/search") { Search(navController) }
            composable("manatoki.net/recent") { Recent(navController) }
        }
    }

    @Composable
    fun Main(navController: NavController) {
        val model: MainViewModel = viewModel()

        val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
        var mangaListing: MangaListing? by rememberSaveable { mutableStateOf(null) }

        val coroutineScope = rememberCoroutineScope()

        val onListing: (MangaListing) -> Unit = {
            mangaListing = it
        }

        val context = LocalContext.current
        LaunchedEffect(Unit) {
            context.settingsDataStore.updateData {
                it.toBuilder()
                    .setRecentSource(name)
                    .build()
            }
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

    @Composable
    fun Reader(navController: NavController) {
        val model: ReaderBaseViewModel = viewModel()

        val database: AppDatabase by rememberInstance()
        val bookmarkDao = database.bookmarkDao()

        val coroutineScope = rememberCoroutineScope()

        val itemID = navController.currentBackStackEntry?.arguments?.getString("itemID")
        var readerInfo: ReaderInfo? by rememberSaveable { mutableStateOf(null) }

        LaunchedEffect(Unit) {
            if (itemID != null)
                readerInfoMutex.withLock {
                    readerInfoCache.get(itemID)?.let {
                        readerInfo = it
                        model.load(it.urls) {
                            set("User-Agent", imageUserAgent)
                        }
                    } ?: run {
                        model.error = true
                    }
                }
        }

        val bookmark by bookmarkDao.contains(name, itemID ?: "").observeAsState(false)

        val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
        var mangaListing: MangaListing? by rememberSaveable { mutableStateOf(null) }
        val mangaListingRippleInteractionSource = remember { mutableStateListOf<MutableInteractionSource>() }
        val navigationBarsPadding = LocalDensity.current.run {
            rememberInsetsPaddingValues(
                LocalWindowInsets.current.navigationBars
            ).calculateBottomPadding().toPx()
        }

        val listState = rememberLazyListState()

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
                    onListSize = {
                        mangaListingListSize = it
                    },
                    rippleInteractionSource = mangaListingRippleInteractionSource,
                    listState = listState
                ) {
                    coroutineScope.launch {
                        client.getItem(
                            it,
                            onReader = {
                                coroutineScope.launch {
                                    readerInfoMutex.withLock {
                                        readerInfoCache.put(it.itemID, it)
                                    }
                                    navController.navigate("manatoki.net/reader/${it.itemID}") {
                                        popUpTo("manatoki.net/")
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
                    AnimatedVisibility(
                        !model.fullscreen,
                        enter = scaleIn(),
                        exit = scaleOut()
                    ) {
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

                                                mangaListingRippleInteractionSource.addAll(
                                                    List(max(it.entries.size - mangaListingRippleInteractionSource.size, 0)) {
                                                        MutableInteractionSource()
                                                    }
                                                )

                                                coroutineScope.launch {
                                                    while (listState.layoutInfo.totalItemsCount != it.entries.size) {
                                                        delay(100)
                                                    }

                                                    val targetIndex = it.entries.indexOfFirst { it.itemID == itemID }

                                                    listState.animateScrollToItem(targetIndex)

                                                    mangaListingListSize?.let { sheetSize ->
                                                        val targetItem = listState.layoutInfo.visibleItemsInfo.first {
                                                            it.key == itemID
                                                        }

                                                        if (targetItem.offset == 0) {
                                                            listState.animateScrollBy(
                                                                -(sheetSize.height - navigationBarsPadding - targetItem.size)
                                                            )
                                                        }

                                                        delay(200)

                                                        with (mangaListingRippleInteractionSource[targetIndex]) {
                                                            val interaction = PressInteraction.Press(
                                                                Offset(sheetSize.width/2, targetItem.size/2f)
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
                        ) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = null
                            )
                        }
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

    @Composable
    fun Recent(navController: NavController) {
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

            }
        }
    }

    @Composable
    fun Search(navController: NavController) {
        val model: SearchViewModel = viewModel()

        var searchFocused by remember { mutableStateOf(false) }
        val handleOffset by animateDpAsState(if (searchFocused) 0.dp else (-36).dp)

        val drawerState = rememberSwipeableState(SearchOptionDrawerStates.Hidden)
        val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)

        var mangaListing: MangaListing? by rememberSaveable { mutableStateOf(null) }

        val coroutineScope = rememberCoroutineScope()

        val focusManager = LocalFocusManager.current

        LaunchedEffect(Unit) {
            model.search()
        }

        BackHandler {
            when {
                sheetState.isVisible -> coroutineScope.launch { sheetState.hide() }
                drawerState.currentValue != SearchOptionDrawerStates.Hidden ->
                    coroutineScope.launch { drawerState.animateTo(SearchOptionDrawerStates.Hidden) }
                else -> navController.popBackStack()
            }
        }

        ModalBottomSheetLayout(
            sheetState = sheetState,
            sheetShape = RoundedCornerShape(32.dp, 32.dp, 0.dp, 0.dp),
            sheetContent = {
                MangaListingBottomSheet(mangaListing) {
                    coroutineScope.launch {
                        client.getItem(it, onReader = {
                            launch {
                                readerInfoMutex.withLock {
                                    readerInfoCache.put(it.itemID, it)
                                }
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
                                    },
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
                                            drawerState.animateTo(SearchOptionDrawerStates.Hidden)
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
                    SearchOptionDrawer(
                        modifier = Modifier.run {
                            if (drawerState.currentValue == SearchOptionDrawerStates.Hidden)
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
                                                sheetState.show()
                                            }
                                            coroutineScope.launch {
                                                client.getItem(it, onListing = {
                                                    mangaListing = it
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
}