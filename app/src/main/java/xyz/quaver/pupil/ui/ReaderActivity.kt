/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2019  tom5079
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
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.annotation.ExperimentalCoilApi
import kotlinx.coroutines.launch
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.graphics.subsampledimage.*
import xyz.quaver.io.FileX
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.composable.FloatingActionButtonState
import xyz.quaver.pupil.ui.composable.MultipleFloatingActionButton
import xyz.quaver.pupil.ui.composable.SubFabItem
import xyz.quaver.pupil.ui.theme.Orange500
import xyz.quaver.pupil.ui.theme.PupilTheme
import xyz.quaver.pupil.ui.viewmodel.ReaderViewModel
import xyz.quaver.pupil.util.FileXImageSource
import kotlin.math.abs

class ReaderActivity : ComponentActivity(), DIAware {
    override val di by closestDI()

    private val model: ReaderViewModel by viewModels()

    private val logger = newLogger(LoggerFactory.default)

    @OptIn(ExperimentalCoilApi::class, ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.handleIntent(intent)
        model.load()

        setContent {
            var isFABExpanded by remember { mutableStateOf(FloatingActionButtonState.COLLAPSED) }
            val imageSources = remember { mutableStateListOf<ImageSource?>() }
            val states = remember { mutableStateListOf<SubSampledImageState>() }
            val bookmark by model.bookmark.observeAsState(false)

            val scaffoldState = rememberScaffoldState()
            val snackbarCoroutineScope = rememberCoroutineScope()

            LaunchedEffect(model.imageList.count { it != null }) {
                if (imageSources.isEmpty() && model.imageList.isNotEmpty())
                    imageSources.addAll(List(model.imageList.size) { null })

                if (states.isEmpty() && model.imageList.isNotEmpty())
                    states.addAll(List(model.imageList.size) { SubSampledImageState(ScaleTypes.FIT_WIDTH, Bounds.FORCE_OVERLAP_OR_CENTER).apply {
                        isGestureEnabled = true
                    } })

                model.imageList.forEachIndexed { i, image ->
                    if (imageSources[i] == null && image != null)
                        imageSources[i] = kotlin.runCatching {
                            FileXImageSource(FileX(this@ReaderActivity, image))
                        }.onFailure {
                            logger.warning(it)
                            model.error(i)
                        }.getOrNull()
                }
            }

            WindowInsetsControllerCompat(window, window.decorView).run {
                if (model.isFullscreen) {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else
                    show(WindowInsetsCompat.Type.systemBars())
            }

            if (model.error)
                stringResource(R.string.reader_failed_to_find_gallery).let {
                    snackbarCoroutineScope.launch {
                        scaffoldState.snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Indefinite)
                    }
                }

            PupilTheme {
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
                                    Row(
                                        modifier = Modifier.padding(16.dp, 0.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                                    ) {
                                        Icon(
                                            if (bookmark) Icons.Default.Star else Icons.Default.StarOutline,
                                            contentDescription = null,
                                            tint = Orange500,
                                            modifier = Modifier.size(24.dp).clickable {
                                                model.toggleBookmark()
                                            }
                                        )
                                        model.sourceIcon?.let { sourceIcon ->
                                            Image(
                                                modifier = Modifier.size(24.dp),
                                                painter = painterResource(id = sourceIcon),
                                                contentDescription = null
                                            )
                                        }
                                    }
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
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                                                // TODO
                                                                val uri = FileProvider.getUriForFile(this@ReaderActivity, "xyz.quaver.pupil.fileprovider", (imageSource as FileXImageSource).file)
                                                                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                                                    type = "image/*"
                                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                }, "Share image"))
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

                        if (model.totalProgress != model.imageCount)
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter),
                                progress = model.progressList.map { abs(it) }
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
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        model.handleIntent(intent)
    }

    override fun onBackPressed() {
        when {
            model.isFullscreen -> model.isFullscreen = false
            else -> super.onBackPressed()
        }
    }
}