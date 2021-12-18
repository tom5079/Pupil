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
import android.content.Intent
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import xyz.quaver.graphics.subsampledimage.*
import xyz.quaver.io.FileX
import xyz.quaver.pupil.R
import xyz.quaver.pupil.db.AppDatabase
import xyz.quaver.pupil.util.FileXImageSource
import xyz.quaver.pupil.util.NetworkCache
import kotlin.math.abs

open class ReaderBaseViewModel(app: Application) : AndroidViewModel(app), DIAware {
    override val di by closestDI(app)

    private val cache: NetworkCache by instance()

    var isFullscreen by mutableStateOf(false)

    private val database: AppDatabase by instance()

    private val historyDao = database.historyDao()
    private val bookmarkDao = database.bookmarkDao()

    var error by mutableStateOf(false)

    var title by mutableStateOf<String?>(null)

    var imageCount by mutableStateOf(0)

    private var images: List<String>? = null
    val imageList = mutableStateListOf<Uri?>()
    val progressList = mutableStateListOf<Float>()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun load(urls: List<String>, headerBuilder: HeadersBuilder.() -> Unit = { }) {
        viewModelScope.launch {
            imageCount = urls.size

            progressList.addAll(List(imageCount) { 0f })
            imageList.addAll(List(imageCount) { null })

            urls.forEachIndexed { index, url ->
                when (val scheme = url.takeWhile { it != ':' }) {
                    "http", "https" -> {
                        val (channel, file) = cache.load {
                            url(url)
                            buildHeaders(headerBuilder)
                        }

                        if (channel.isClosedForReceive) {
                            imageList[index] = Uri.fromFile(file)
                        } else {
                            channel.invokeOnClose { e ->
                                viewModelScope.launch {
                                    if (e == null) {
                                        imageList[index] = Uri.fromFile(file)
                                    } else {
                                        error(index)
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
    bookmark: Boolean = false,
    onToggleBookmark: () -> Unit = { }
) {
    val context = LocalContext.current

    var isFABExpanded by remember { mutableStateOf(FloatingActionButtonState.COLLAPSED) }
    val imageSources = remember { mutableStateListOf<ImageSource?>() }
    val states = remember { mutableStateListOf<SubSampledImageState>() }

    val scaffoldState = rememberScaffoldState()
    val snackbarCoroutineScope = rememberCoroutineScope()

    LaunchedEffect(model.imageList.count { it != null }) {
        if (imageSources.isEmpty() && model.imageList.isNotEmpty())
            imageSources.addAll(List(model.imageList.size) { null })

        if (states.isEmpty() && model.imageList.isNotEmpty())
            states.addAll(List(model.imageList.size) {
                SubSampledImageState(ScaleTypes.FIT_WIDTH, Bounds.FORCE_OVERLAP_OR_CENTER).apply {
                    isGestureEnabled = true
                }
            })

        model.imageList.forEachIndexed { i, image ->
            if (imageSources[i] == null && image != null)
                imageSources[i] = kotlin.runCatching {
                    FileXImageSource(FileX(context, image))
                }.onFailure {
                    model.error(i)
                }.getOrNull()
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
                        //TODO
                    }
                )
        },
        floatingActionButton = {
            if (!model.isFullscreen)
                MultipleFloatingActionButton(
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
    ) {
        Box {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(imageSources) { i, imageSource ->
                    Box(
                        Modifier
                            .wrapContentHeight(states[i], 500.dp)
                            .fillMaxWidth()
                            .border(1.dp, Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageSource == null)
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
                            val haptic = LocalHapticFeedback.current

                            SubSampledImage(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .run {
                                        if (model.isFullscreen)
                                            doubleClickCycleZoom(states[i], 2f)
                                        else
                                            combinedClickable(
                                                onLongClick = {
                                                    haptic.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                    )

                                                    // TODO
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "xyz.quaver.pupil.fileprovider",
                                                        (imageSource as FileXImageSource).file
                                                    )
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            Intent(
                                                                Intent.ACTION_SEND
                                                            ).apply {
                                                                type = "image/*"
                                                                putExtra(
                                                                    Intent.EXTRA_STREAM,
                                                                    uri
                                                                )
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }, "Share image"
                                                        )
                                                    )
                                                }
                                            ) {
                                                model.isFullscreen = true
                                            }
                                    },
                                imageSource = imageSource,
                                state = states[i]
                            )
                        }
                    }
                }
            }

            if (model.progressList.any { abs(it) != 1f })
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