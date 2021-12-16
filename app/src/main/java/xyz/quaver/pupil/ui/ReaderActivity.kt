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

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.annotation.ExperimentalCoilApi
import com.google.accompanist.appcompattheme.AppCompatTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import xyz.quaver.pupil.ui.theme.PupilTheme
import xyz.quaver.pupil.ui.viewmodel.ReaderViewModel
import xyz.quaver.pupil.util.FileXImageSource
import kotlin.math.abs

class ReaderActivity : ComponentActivity(), DIAware {
    override val di by closestDI()

    private val model: ReaderViewModel by viewModels()

    private val logger = newLogger(LoggerFactory.default)

    @OptIn(ExperimentalCoilApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.handleIntent(intent)
        model.load()

        setContent {
            var isFABExpanded by remember { mutableStateOf(FloatingActionButtonState.COLLAPSED) }
            val isFullscreen by model.isFullscreen.observeAsState(false)
            val imageSources = remember { mutableStateListOf<ImageSource?>() }
            val imageHeights = remember { mutableStateListOf<Float?>() }
            val states = remember { mutableStateListOf<SubSampledImageState>() }

            LaunchedEffect(model.imageList.count { it != null }) {
                if (imageSources.isEmpty() && model.imageList.isNotEmpty())
                    imageSources.addAll(List(model.imageList.size) { null })

                if (states.isEmpty() && model.imageList.isNotEmpty())
                    states.addAll(List(model.imageList.size) { SubSampledImageState(ScaleTypes.FIT_WIDTH, Bounds.FORCE_OVERLAP_OR_CENTER) })

                if (imageHeights.isEmpty() && model.imageList.isNotEmpty())
                    imageHeights.addAll(List(model.imageList.size) { null })

                logger.info {
                    "${model.imageList.count { it == null }} nulls"
                }

                model.imageList.forEachIndexed { i, image ->
                    if (imageSources[i] == null && image != null)
                        imageSources[i] = kotlin.runCatching {
                            FileXImageSource(FileX(this@ReaderActivity, image))
                        }.onFailure {
                            logger.warning(it)
                            model.error(i)
                        }.getOrNull()
                }

                logger.info {
                    "${imageSources.count { it == null }} nulls"
                }
            }

            WindowInsetsControllerCompat(window, window.decorView).run {
                if (isFullscreen) {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else
                    show(WindowInsetsCompat.Type.systemBars())
            }

            PupilTheme {
                Scaffold(
                    topBar = {
                        if (!isFullscreen)
                            TopAppBar(
                                title = {
                                    Text(
                                        model.title ?: stringResource(R.string.reader_loading),
                                        color = MaterialTheme.colors.onSecondary
                                    )
                                },
                                actions = {
                                    model.sourceIcon?.let { sourceIcon ->
                                        Image(
                                            modifier = Modifier.size(36.dp),
                                            painter = painterResource(id = sourceIcon),
                                            contentDescription = null
                                        )
                                    }
                                }
                            )
                    },
                    floatingActionButton = {
                        if (!isFullscreen)
                            MultipleFloatingActionButton(
                                items = listOf(
                                    SubFabItem(
                                        icon = Icons.Default.Fullscreen,
                                        label = stringResource(id = R.string.reader_fab_fullscreen)
                                    ) {
                                        model.isFullscreen.postValue(true)
                                    }
                                ),
                                targetState = isFABExpanded,
                                onStateChanged = {
                                    isFABExpanded = it
                                }
                            )
                    }
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(imageSources) { i, imageSource ->
                            LaunchedEffect(states[i].canvasSize, states[i].imageSize) {
                                if (imageHeights.isNotEmpty() && imageHeights[i] == null)
                                    states[i].canvasSize?.let { canvasSize ->
                                    states[i].imageSize?.let { imageSize ->
                                        imageHeights[i] = imageSize.height * canvasSize.width / imageSize.width
                                    } }
                            }

                            Box(
                                Modifier
                                    .height(
                                        imageHeights
                                            .getOrNull(i)
                                            ?.let { with(LocalDensity.current) { it.toDp() } }
                                            ?: 500.dp)
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
                                else
                                    SubSampledImage(
                                        modifier = Modifier.fillMaxSize(),
                                        imageSource = imageSource,
                                        state = states[i]
                                    )
                            }
                        }
                    }

                    if (model.totalProgress != model.imageCount)
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = model.progressList.map { abs(it) }.sum() / model.progressList.size,
                            color = colorResource(id = R.color.colorAccent)
                        )
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
            model.isFullscreen.value == true -> model.isFullscreen.postValue(false)
            else -> super.onBackPressed()
        }
    }
}