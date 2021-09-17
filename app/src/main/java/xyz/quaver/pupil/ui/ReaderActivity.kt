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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.accompanist.appcompattheme.AppCompatTheme
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.composable.FloatingActionButtonState
import xyz.quaver.pupil.ui.composable.MultipleFloatingActionButton
import xyz.quaver.pupil.ui.composable.SubFabItem
import xyz.quaver.pupil.ui.viewmodel.ReaderViewModel

class ReaderActivity : ComponentActivity(), DIAware {
    override val di by closestDI()

    private val model: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var isFABExpanded by remember { mutableStateOf(FloatingActionButtonState.COLLAPSED) }
            val isFullscreen by model.isFullscreen.observeAsState(false)

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
                                        "Reader",
                                        color = MaterialTheme.colors.onSecondary
                                    )
                                }
                            )
                    },
                    floatingActionButton = {
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

                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        model.handleIntent(intent)
    }
}