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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.graphics.subsampledimage.*
import xyz.quaver.io.FileX
import xyz.quaver.pupil.R
import xyz.quaver.pupil.db.AppDatabase
import xyz.quaver.pupil.ui.theme.Orange500
import xyz.quaver.pupil.util.NetworkCache
import xyz.quaver.pupil.util.activity
import xyz.quaver.pupil.util.rememberFileXImageSource
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

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
                            }
                        }
                        "content" -> {
                            imageList[index] = Uri.parse(url)
                            progressList[index] = Float.POSITIVE_INFINITY
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderBase(
    modifier: Modifier = Modifier,
    model: ReaderBaseViewModel
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val scaffoldState = rememberScaffoldState()
    val snackbarCoroutineScope = rememberCoroutineScope()

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
        LazyColumn(
            Modifier
                .fillMaxSize()
                .align(Alignment.TopStart),
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
                    val progress = model.progressList.getOrNull(i) ?: 0f

                    if (progress == Float.NEGATIVE_INFINITY)
                        Icon(Icons.Filled.BrokenImage, null, tint = Orange500)
                    else if (progress.isFinite())
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(progress)
                            Text((i + 1).toString())
                        }
                    else if (uri != null && progress == Float.POSITIVE_INFINITY) {
                        val imageSource = kotlin.runCatching {
                            rememberFileXImageSource(FileX(context, uri))
                        }.getOrNull()

                        if (imageSource != null)
                            SubSampledImage(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .run {
                                        if (model.fullscreen)
                                            doubleClickCycleZoom(state, 2f)
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
                                    model.error(i)
                                }
                            )
                    }
                }
            }
        }

        if (model.progressList.any { it.isFinite() })
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                progress = model.progressList.map { if (it.isInfinite()) 1f else abs(it) }.sum() / model.progressList.size,
                color = MaterialTheme.colors.secondary
            )

        SnackbarHost(
            scaffoldState.snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}