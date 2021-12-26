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
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import android.view.MotionEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.rememberInsetsPaddingValues
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.graphics.subsampledimage.*
import xyz.quaver.graphics.subsampledimage.ScaleTypes.CENTER_INSIDE
import xyz.quaver.io.FileX
import xyz.quaver.pupil.R
import xyz.quaver.pupil.db.AppDatabase
import xyz.quaver.pupil.proto.ReaderOptions
import xyz.quaver.pupil.proto.settingsDataStore
import xyz.quaver.pupil.ui.theme.Orange500
import xyz.quaver.pupil.util.FileXImageSource
import xyz.quaver.pupil.util.NetworkCache
import xyz.quaver.pupil.util.activity
import xyz.quaver.pupil.util.rememberFileXImageSource
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sign

private var _singleImage: ImageVector? = null
val SingleImage: ImageVector
    get() {
        if (_singleImage != null) {
            return _singleImage!!
        }

        _singleImage = materialIcon(name = "ReaderBase.SingleImage") {
            materialPath {
                moveTo(17.0f, 3.0f)
                lineTo(7.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(19.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(17.0f, 19.0f)
                lineTo(7.0f, 19.0f)
                lineTo(7.0f, 5.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(14.0f)
                close()
            }
        }

        return _singleImage!!
    }

private var _doubleImage: ImageVector? = null
val DoubleImage: ImageVector
    get() {
        if (_doubleImage != null) {
            return _doubleImage!!
        }

        _doubleImage = materialIcon(name = "ReaderBase.DoubleImage") {
            materialPath {
                moveTo(9.0f, 3.0f)
                lineTo(2.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(7.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(11.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(9.0f, 19.0f)
                lineTo(2.0f, 19.0f)
                lineTo(2.0f, 5.0f)
                horizontalLineToRelative(7.0f)
                verticalLineToRelative(14.0f)
                close()
                moveTo(21.0f, 3.0f)
                lineTo(14.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(7.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(23.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(21.0f, 19.0f)
                lineTo(14.0f, 19.0f)
                lineTo(14.0f, 5.0f)
                horizontalLineToRelative(7.0f)
                verticalLineToRelative(14.0f)
                close()
            }
        }

        return _doubleImage!!
    }

open class ReaderBaseViewModel(app: Application) : AndroidViewModel(app), DIAware {
    override val di by closestDI(app)

    private val logger = newLogger(LoggerFactory.default)

    private val cache: NetworkCache by instance()

    var fullscreen by mutableStateOf(false)

    private val database: AppDatabase by instance()

    var error by mutableStateOf(false)

    var imageCount by mutableStateOf(0)

    val imageList = mutableStateListOf<Uri?>()
    val progressList = mutableStateListOf<Float>()

    private val progressCollectJobs = ConcurrentHashMap<Int, Job>()

    private val totalProgressMutex = Mutex()
    var totalProgress by mutableStateOf(0)
        private set

    private var urls: List<String>? = null

    var loadJob: Job? = null
    @OptIn(ExperimentalCoroutinesApi::class)
    fun load(urls: List<String>, headerBuilder: HeadersBuilder.() -> Unit = { }) {
        this.urls = urls
        viewModelScope.launch {
            loadJob?.cancelAndJoin()
            progressList.clear()
            imageList.clear()
            totalProgressMutex.withLock {
                totalProgress = 0
            }

            imageCount = urls.size

            progressList.addAll(List(imageCount) { 0f })
            imageList.addAll(List(imageCount) { null })
            totalProgressMutex.withLock {
                totalProgress = 0
            }

            loadJob = launch {
                urls.forEachIndexed { index, url ->
                    when (val scheme = url.takeWhile { it != ':' }) {
                        "http", "https" -> {
                            val (flow, file) = cache.load {
                                url(url)
                                headers(headerBuilder)
                            }

                            imageList[index] = Uri.fromFile(file)
                            progressCollectJobs[index] = launch {
                                flow.takeWhile { it.isFinite() }.collect {
                                    progressList[index] = it
                                }

                                progressList[index] = flow.value
                                totalProgressMutex.withLock {
                                    totalProgress++
                                }
                            }
                        }
                        "content" -> {
                            imageList[index] = Uri.parse(url)
                            progressList[index] = Float.POSITIVE_INFINITY
                            totalProgressMutex.withLock {
                                totalProgress++
                            }
                        }
                        else -> throw IllegalArgumentException("Expected URL scheme 'http(s)' or 'content' but was '$scheme'")
                    }
                }
            }
        }
    }

    fun error(index: Int) {
        progressList[index] = Float.NEGATIVE_INFINITY
    }

    override fun onCleared() {
        urls?.let { cache.free(it) }
        cache.cleanup()
    }
}

val ReaderOptions.Orientation.isVertical: Boolean
    get() =
        this == ReaderOptions.Orientation.VERTICAL_DOWN ||
        this == ReaderOptions.Orientation.VERTICAL_UP
val ReaderOptions.Orientation.isReverse: Boolean
    get() =
        this == ReaderOptions.Orientation.VERTICAL_UP ||
        this == ReaderOptions.Orientation.HORIZONTAL_LEFT

@Composable
fun ReaderOptionsSheet(readerOptions: ReaderOptions, onOptionsChange: (ReaderOptions.Builder.() -> Unit) -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.h6) {
        Column(Modifier.padding(16.dp, 0.dp)) {
            val layout = readerOptions.layout
            val snap = readerOptions.snap
            val orientation = readerOptions.orientation
            val padding = readerOptions.padding

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Layout")

                Row {
                    listOf(
                        ReaderOptions.Layout.SINGLE_PAGE to SingleImage,
                        ReaderOptions.Layout.DOUBLE_PAGE to DoubleImage,
                        ReaderOptions.Layout.AUTO to Icons.Default.AutoFixHigh
                    ).forEach { (option, icon) ->
                        IconButton(onClick = {
                            onOptionsChange {
                                setLayout(option)
                            }
                        }) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint =
                                if (layout == option) MaterialTheme.colors.secondary
                                else LocalContentColor.current
                            )
                        }
                    }
                }
            }

            val infiniteTransition = rememberInfiniteTransition()

            val isReverse = orientation.isReverse
            val isVertical = orientation.isVertical

            val animationOrientation = if (isReverse) -1f else 1f
            val animationSpacing by animateFloatAsState(if (padding) 48f else 32f)
            val animationOffset by infiniteTransition.animateFloat(
                initialValue = animationOrientation * (if (snap) 0f else animationSpacing/2),
                targetValue = animationOrientation * (if (snap) -animationSpacing else -animationSpacing/2),
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 1000,
                        easing = if(snap) FastOutSlowInEasing else LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                )
            )
            val animationRotation by animateFloatAsState(if (isVertical) 90f else 0f)

            val setOrientation: (Boolean, Boolean) -> Unit = { isVertical, isReverse ->
                val orientation = when {
                    isVertical && !isReverse -> ReaderOptions.Orientation.VERTICAL_DOWN
                    isVertical && isReverse -> ReaderOptions.Orientation.VERTICAL_UP
                    !isVertical && !isReverse -> ReaderOptions.Orientation.HORIZONTAL_RIGHT
                    !isVertical && isReverse -> ReaderOptions.Orientation.HORIZONTAL_LEFT
                    else -> error("Invalid value")
                }

                onOptionsChange {
                    setOrientation(orientation)
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clipToBounds()
                    .rotate(animationRotation)
                    .align(Alignment.CenterHorizontally)
            ) {
                for (i in 0..4)
                    Icon(
                        SingleImage,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.CenterStart)
                            .offset((animationOffset + animationSpacing * (i - 2)).dp, 0.dp)
                    )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Orientation")

                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.caption) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("H")
                        Switch(checked = isVertical, onCheckedChange = {
                            setOrientation(!isVertical, isReverse)
                        })
                        Text("V")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Reverse")
                Switch(checked = isReverse, onCheckedChange = {
                    setOrientation(isVertical, !isReverse)
                })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Snap")

                Switch(checked = snap, onCheckedChange = {
                    onOptionsChange {
                        setSnap(!snap)
                    }
                })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Padding")

                Switch(checked = padding, onCheckedChange = {
                    onOptionsChange {
                        setPadding(!padding)
                    }
                })
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp))
        }
    }
}

@Composable
fun BoxScope.ReaderLazyList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    orientation: ReaderOptions.Orientation,
    onScroll: (direction: Float) -> Unit,
    content: LazyListScope.() -> Unit
) {
    val isReverse = orientation.isReverse

    val nestedScrollConnection = remember(orientation) { object: NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            onScroll(
                when (orientation) {
                    ReaderOptions.Orientation.VERTICAL_DOWN -> available.y.sign
                    ReaderOptions.Orientation.VERTICAL_UP -> -(available.y.sign)
                    ReaderOptions.Orientation.HORIZONTAL_RIGHT -> available.x.sign
                    ReaderOptions.Orientation.HORIZONTAL_LEFT -> -(available.x.sign)
                }
            )

            return Offset.Zero
        }
    } }

    when (orientation) {
        ReaderOptions.Orientation.VERTICAL_DOWN,
        ReaderOptions.Orientation.VERTICAL_UP ->
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .align(Alignment.TopStart)
                    .nestedScroll(nestedScrollConnection),
                state = state,
                contentPadding = rememberInsetsPaddingValues(LocalWindowInsets.current.navigationBars),
                reverseLayout = isReverse,
                content = content
            )
        ReaderOptions.Orientation.HORIZONTAL_RIGHT,
        ReaderOptions.Orientation.HORIZONTAL_LEFT ->
            LazyRow(
                modifier = modifier
                    .fillMaxSize()
                    .align(Alignment.CenterStart)
                    .nestedScroll(nestedScrollConnection),
                state = state,
                reverseLayout = isReverse,
                content = content
            )
    }
}

data class ReaderItemData(
    val index: Int,
    val size: Size?,
    val imageSource: ImageSource?
)

@ExperimentalFoundationApi
@Composable
fun ReaderItem(
    model: ReaderBaseViewModel,
    readerOptions: ReaderOptions,
    listSize: Size,
    images: List<ReaderItemData>,
    onTap: () -> Unit = { }
) {
    val (widthDp, heightDp) = LocalDensity.current.run { listSize.width.toDp() to listSize.height.toDp() }

    Row(
        modifier = when {
            readerOptions.padding -> Modifier.size(widthDp, heightDp)
            readerOptions.orientation.isVertical -> Modifier.fillMaxWidth()
            else -> Modifier.fillMaxHeight()
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        images.let { if (readerOptions.orientation.isReverse) it.reversed() else it }.forEach { (index, imageSize, imageSource) ->
            val state = rememberSubSampledImageState().apply {
                isGestureEnabled = true
            }

            val modifier = when {
                imageSize == null -> Modifier.weight(1f).height(heightDp)
                readerOptions.padding -> Modifier.fillMaxHeight().widthIn(0.dp, widthDp/images.size).aspectRatio(imageSize.width/imageSize.height)
                readerOptions.orientation.isVertical -> Modifier.weight(1f).aspectRatio(imageSize.width/imageSize.height)
                else -> Modifier.aspectRatio(imageSize.width/imageSize.height)
            }


            Box(
                modifier,
                contentAlignment = Alignment.Center
            ) {
                val progress = model.progressList.getOrNull(index) ?: 0f

                if (progress == Float.NEGATIVE_INFINITY)
                    Icon(Icons.Filled.BrokenImage, null)
                else if (progress.isFinite())
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(progress)
                        Text((index + 1).toString())
                    }
                else if (progress == Float.POSITIVE_INFINITY) {
                    SubSampledImage(
                        modifier = Modifier
                            .fillMaxSize()
                            .run {
                                if (model.fullscreen)
                                    doubleClickCycleZoom(state, 2f, onTap = onTap)
                                else
                                    combinedClickable(
                                        onLongClick = {

                                        }
                                    ) {
                                        model.fullscreen = true
                                    }
                            },
                        imageSource = imageSource,
                        state = state,
                        onError = {
                            model.error(index)
                        }
                    )
                }
            }
        }
    }
}

@ExperimentalFoundationApi
fun LazyListScope.ReaderLazyListContent(
    model: ReaderBaseViewModel,
    listSize: Size,
    imageSources: List<ImageSource?>,
    imageSizes: List<Size?>,
    readerOptions: ReaderOptions,
    onTap: () -> Unit = { }
) {
    when (readerOptions.layout) {
        ReaderOptions.Layout.SINGLE_PAGE ->
            itemsIndexed(imageSources) { index, source ->
                ReaderItem(model, readerOptions, listSize, listOf(ReaderItemData(index, imageSizes[index], source)))
            }
        ReaderOptions.Layout.DOUBLE_PAGE ->
            itemsIndexed(imageSources.chunked(2), key = { i, _ -> i*2 }) { chunkIndex, sourceList ->
                ReaderItem(model, readerOptions, listSize, sourceList.mapIndexed { i, it ->
                    val index = chunkIndex*2+i
                    ReaderItemData(index, imageSizes[index], it)
                }, onTap)
            }
        ReaderOptions.Layout.AUTO -> {
            val images = mutableListOf<List<Int>>()

            var i = 0
            while (i < imageSizes.size) {
                val list = mutableListOf(i)

                if (
                    imageSizes[i] != null &&
                    imageSizes.getOrNull(i+1) != null &&
                    listSize != Size.Zero &&
                    imageSizes[i]!!.width*listSize.height/imageSizes[i]!!.height +
                    imageSizes[i+1]!!.width*listSize.height/imageSizes[i+1]!!.height < listSize.width
                ) list.add(++i)

                images.add(list)
                i++
            }

            items(images, key = { it.first() }) { images ->
                ReaderItem(model, readerOptions, listSize, images.map { ReaderItemData(it, imageSizes[it], imageSources[it]) }, onTap)
            }
        }
        else -> itemsIndexed(imageSources) { index, source ->
            ReaderItem(model, readerOptions, listSize, listOf(ReaderItemData(index, imageSizes[index], source)), onTap)
        }
    }
}

@ExperimentalComposeUiApi
@ExperimentalMaterialApi
@ExperimentalFoundationApi
@Composable
fun ReaderBase(
    modifier: Modifier = Modifier,
    model: ReaderBaseViewModel,
    onScroll: (direction: Float) -> Unit = { }
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val coroutineScope = rememberCoroutineScope()

    val scaffoldState = rememberScaffoldState()
    val snackbarCoroutineScope = rememberCoroutineScope()

    var scrollDirection by remember { mutableStateOf(0f) }
    val handleOffset by animateDpAsState(if (model.fullscreen || scrollDirection < 0f) (-36).dp else 0.dp)

    val mainReaderOptions by remember {
        context.settingsDataStore.data.map { it.mainReaderOption }
    }.collectAsState(ReaderOptions.getDefaultInstance())

    LaunchedEffect(scrollDirection) {
        onScroll(scrollDirection)
    }

    LaunchedEffect(model.fullscreen) {
        context.activity?.window?.let { window ->
            ViewCompat.getWindowInsetsController(window.decorView)?.let {
                if (model.fullscreen) {
                    it.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    it.hide(WindowInsetsCompat.Type.systemBars())
                } else
                    it.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (model.error)
        stringResource(R.string.reader_failed_to_find_gallery).let {
            snackbarCoroutineScope.launch {
                scaffoldState.snackbarHostState.showSnackbar(
                    it,
                    duration = SnackbarDuration.Indefinite
                )
            }
        }

    Box(modifier) {
        ModalTopSheetLayout(
            modifier = Modifier.offset(0.dp, handleOffset),
            drawerContent = {
                ReaderOptionsSheet(mainReaderOptions) { readerOptionsBlock ->
                    coroutineScope.launch {
                        context.settingsDataStore.updateData {
                            it.toBuilder().setMainReaderOption(
                                mainReaderOptions.toBuilder().apply(readerOptionsBlock).build()
                            ).build()
                        }
                    }
                }
            }
        ) {
            var listSize: Size? by remember { mutableStateOf(null) }
            val listState = rememberLazyListState()

            val nestedScrollConnection = remember { object: NestedScrollConnection {
                override suspend fun onPreFling(available: Velocity): Velocity {
                    return if (
                        mainReaderOptions.snap &&
                        listState.layoutInfo.visibleItemsInfo.size > 1
                    ) {
                        val velocity = when (mainReaderOptions.orientation) {
                            ReaderOptions.Orientation.VERTICAL_DOWN -> available.y
                            ReaderOptions.Orientation.VERTICAL_UP -> -(available.y)
                            ReaderOptions.Orientation.HORIZONTAL_RIGHT -> available.x
                            ReaderOptions.Orientation.HORIZONTAL_LEFT -> -(available.x)
                        }

                        val index = listState.firstVisibleItemIndex

                        coroutineScope.launch {
                            when {
                                velocity < 0f -> listState.animateScrollToItem(index+1)
                                else -> listState.animateScrollToItem(index)
                            }
                        }

                        available
                    } else Velocity.Zero

                }
            } }

            val imageSources = remember { mutableStateListOf<ImageSource?>() }
            val imageSizes = remember { mutableStateListOf<Size?>() }

            LaunchedEffect(model.totalProgress) {
                val size = model.progressList.size

                if (imageSources.size != size)
                    imageSources.addAll(List (size-imageSources.size) { null })

                if (imageSizes.size != size)
                    imageSizes.addAll(List (size-imageSizes.size) { null })

                coroutineScope.launch {
                    repeat(size) { i ->
                        val uri = model.imageList[i]

                        if (imageSources[i] == null && uri != null)
                            imageSources[i] = FileXImageSource(FileX(context, uri))

                        if (imageSizes[i] == null && model.progressList[i] == Float.POSITIVE_INFINITY)
                            imageSources[i]?.let {
                                imageSizes[i] = runCatching { it.imageSize }.getOrNull()
                            }
                    }
                }
            }

            ReaderLazyList(
                Modifier
                    .onGloballyPositioned { listSize = it.size.toSize() }
                    .nestedScroll(nestedScrollConnection),
                listState,
                mainReaderOptions.orientation,
                onScroll = { scrollDirection = it },
            ) {
                ReaderLazyListContent(
                    model,
                    listSize ?: Size.Zero,
                    imageSources,
                    imageSizes,
                    mainReaderOptions
                ) {
                    coroutineScope.launch {
                        listState.scrollToItem(listState.firstVisibleItemIndex + 1)
                    }
                }
            }

            if (model.progressList.any { it.isFinite() })
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    progress = model.progressList.map { if (it.isInfinite()) 1f else abs(it) }
                        .sum() / model.progressList.size,
                    color = MaterialTheme.colors.secondary
                )

            SnackbarHost(
                scaffoldState.snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

fun Modifier.doubleClickCycleZoom(
    state: SubSampledImageState,
    scale: Float = 2f,
    animationSpec: AnimationSpec<Rect> = spring(),
    onTap: () -> Unit = { },
) = composed {
    val initialImageRect by produceState<Rect?>(null, state.canvasSize, state.imageSize) {
        state.canvasSize?.let { canvasSize ->
            state.imageSize?.let { imageSize ->
                value = state.bound(state.scaleType(canvasSize, imageSize), canvasSize)
            } }
    }

    val coroutineScope = rememberCoroutineScope()

    pointerInput(Unit) {
        detectTapGestures(
            onTap = { onTap() },
            onDoubleTap = { centroid ->
                val imageRect = state.imageRect
                coroutineScope.launch {
                    if (imageRect == null || imageRect != initialImageRect)
                        state.resetImageRect(animationSpec)
                    else {
                        state.setImageRectWithBound(
                            Rect(
                                Offset(
                                    centroid.x - (centroid.x - imageRect.left) * scale,
                                    centroid.y - (centroid.y - imageRect.top) * scale
                                ),
                                imageRect.size * scale
                            ), animationSpec
                        )
                    }
                }
            }
        )
    }
}
