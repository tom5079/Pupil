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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.insets.ui.Scaffold
import com.google.accompanist.insets.ui.TopAppBar
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import xyz.quaver.graphics.subsampledimage.*
import xyz.quaver.io.FileX
import xyz.quaver.pupil.R
import xyz.quaver.pupil.db.AppDatabase
import xyz.quaver.pupil.ui.theme.Orange500
import xyz.quaver.pupil.util.NetworkCache
import xyz.quaver.pupil.util.activity
import xyz.quaver.pupil.util.rememberFileXImageSource
import kotlin.math.abs

open class ReaderBaseViewModel(app: Application) : AndroidViewModel(app), DIAware {
    override val di by closestDI(app)

    private val cache: NetworkCache by instance()

    var isFullscreen by mutableStateOf(false)

    private val database: AppDatabase by instance()

    var error by mutableStateOf(false)

    var title by mutableStateOf<String?>(null)

    var imageCount by mutableStateOf(0)

    val imageList = mutableStateListOf<Uri?>()
    val progressList = mutableStateListOf<Float>()

    private val totalProgressMutex = Mutex()
    var totalProgress by mutableStateOf(0)
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    fun load(urls: List<String>, headerBuilder: HeadersBuilder.() -> Unit = { }) {
        viewModelScope.launch {
            imageCount = urls.size

            progressList.addAll(List(imageCount) { 0f })
            imageList.addAll(List(imageCount) { null })
            totalProgressMutex.withLock {
                totalProgress = 0
            }

            urls.forEachIndexed { index, url ->
                when (val scheme = url.takeWhile { it != ':' }) {
                    "http", "https" -> {
                        val (channel, file) = cache.load {
                            url(url)
                            headers(headerBuilder)
                        }

                        if (channel.isClosedForReceive) {
                            imageList[index] = Uri.fromFile(file)
                            totalProgressMutex.withLock {
                                totalProgress++
                            }
                        } else {
                            channel.invokeOnClose { e ->
                                viewModelScope.launch {
                                    if (e == null) {
                                        imageList[index] = Uri.fromFile(file)

                                    } else {
                                        error(index)
                                    }
                                    imageList[index] = Uri.fromFile(file)
                                    totalProgressMutex.withLock {
                                        totalProgress++
                                    }
                                }
                            }

                            launch {
                                kotlin.runCatching {
                                    for (progress in channel) {
                                        progressList[index] = progress
                                    }
                                }
                            }
                        }
                    }
                    "content" -> {
                        imageList[index] = Uri.parse(url)
                        progressList[index] = 1f
                    }
                    else -> throw IllegalArgumentException("Expected URL scheme 'http(s)' or 'content' but was '$scheme'")
                }
            }
        }
    }

    fun error(index: Int) {
        progressList[index] = -1f
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderBase(
    model: ReaderBaseViewModel,
    icon: @Composable () -> Unit = { },
    bookmark: Boolean = false,
    onToggleBookmark: () -> Unit = { }
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var isFABExpanded by remember { mutableStateOf(FloatingActionButtonState.COLLAPSED) }

    val scaffoldState = rememberScaffoldState()
    val snackbarCoroutineScope = rememberCoroutineScope()

    LaunchedEffect(model.isFullscreen) {
        context.activity?.window?.let { window ->
            ViewCompat.getWindowInsetsController(window.decorView)?.let {
                if (model.isFullscreen) {
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

    Scaffold(
        topBar = {
            if (!model.isFullscreen)
                TopAppBar(
                    title = {
                        Text(
                            model.title ?: stringResource(R.string.reader_loading),
                            color = MaterialTheme.colors.onSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        IconButton(onClick = { }) {
                            icon()
                        }

                        IconButton(onClick = onToggleBookmark) {
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
            if (!model.isFullscreen)
                MultipleFloatingActionButton(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        SubFabItem(
                            icon = Icons.Default.Fullscreen,
                            label = stringResource(id = R.string.reader_fab_fullscreen)
                        ) {
                            model.isFullscreen = true
                        }
                    ),
                    targetState = isFABExpanded,
                    onStateChanged = {
                        isFABExpanded = it
                    }
                )
        },
        scaffoldState = scaffoldState,
        snackbarHost = { scaffoldState.snackbarHostState }
    ) { contentPadding ->
        Box(Modifier.padding(contentPadding)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = rememberInsetsPaddingValues(LocalWindowInsets.current.navigationBars)
            ) {
                itemsIndexed(model.imageList) { i, uri ->
                    val state = rememberSubSampledImageState(ScaleTypes.FIT_WIDTH)

                    Box(
                        Modifier
                            .wrapContentHeight(state, 500.dp)
                            .fillMaxWidth()
                            .border(1.dp, Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uri == null)
                            model.progressList.getOrNull(i)?.let { progress ->
                                if (progress < 0f)
                                    Icon(Icons.Filled.BrokenImage, null)
                                else
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        LinearProgressIndicator(progress)
                                        Text((i + 1).toString())
                                    }
                            }
                        else {
                            val imageSource = kotlin.runCatching {
                                rememberFileXImageSource(FileX(context, uri))
                            }.getOrNull()

                            if (imageSource == null)
                                Icon(Icons.Default.BrokenImage, contentDescription = null)
                            else
                                SubSampledImage(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .run {
                                            if (model.isFullscreen)
                                                doubleClickCycleZoom(state, 2f)
                                            else
                                                combinedClickable(
                                                    onLongClick = {

                                                    }
                                                ) {
                                                    model.isFullscreen = true
                                                }
                                        },
                                    imageSource = imageSource,
                                    state = state
                                )
                        }
                    }
                }
            }

            if (model.totalProgress != model.imageCount)
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    progress = model.progressList.map { abs(it) }.sum() / model.progressList.size,
                    color = MaterialTheme.colors.secondary
                )

            SnackbarHost(
                scaffoldState.snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}