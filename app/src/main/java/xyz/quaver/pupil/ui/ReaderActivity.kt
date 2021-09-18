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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import coil.request.ImageRequest
import coil.transform.BlurTransformation
import com.google.accompanist.appcompattheme.AppCompatTheme
import io.ktor.http.*
import okhttp3.Headers
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.composable.FloatingActionButtonState
import xyz.quaver.pupil.ui.composable.MultipleFloatingActionButton
import xyz.quaver.pupil.ui.composable.SubFabItem
import xyz.quaver.pupil.ui.viewmodel.ReaderViewModel

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
            val title by model.title.observeAsState(stringResource(R.string.reader_loading))
            val sourceIcon by model.sourceIcon.observeAsState()
            val images by model.images.observeAsState(emptyList())
            val source by model.sourceInstance.observeAsState()

            logger.debug { "target: ${R.drawable.hitomi} value: $sourceIcon" }

            WindowInsetsControllerCompat(window, window.decorView).run {
                if (isFullscreen) {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else
                    show(WindowInsetsCompat.Type.systemBars())
            }

            AppCompatTheme {
                Scaffold(
                    topBar = {
                        if (!isFullscreen)
                            TopAppBar(
                                title = {
                                    Text(
                                        title,
                                        color = MaterialTheme.colors.onSecondary
                                    )
                                },
                                actions = {
                                    sourceIcon?.let { sourceIcon ->
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
                                        model.isFullscreen.postValue(!isFullscreen)
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
                        verticalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        items(images) { image ->
                            Image(
                                modifier = Modifier.fillMaxWidth().heightIn(128.dp, 1000.dp),
                                painter = rememberImagePainter(
                                    ImageRequest.Builder(this@ReaderActivity)
                                        .data(image)
                                        .headers(
                                            Headers.headersOf(
                                                *(source!!.getHeadersForImage(model.itemID.value!!, image).entries.fold(arrayOf()) { acc, entry ->
                                                    acc + entry.key + entry.value
                                                })
                                            ).also {
                                                logger.debug {
                                                    image
                                                }
                                                logger.debug {
                                                    it.toString()
                                                }
                                            }
                                        )
                                        .transformations(BlurTransformation(this@ReaderActivity, 1f))
                                        .build()
                                ),
                                contentDescription = null
                            )
                        }
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
            model.isFullscreen.value == true -> model.isFullscreen.postValue(false)
            else -> super.onBackPressed()
        }
    }
}