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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.launch
import org.kodein.di.*
import org.kodein.di.android.closestDI
import org.kodein.di.android.subDI
import org.kodein.di.compose.rememberInstance
import xyz.quaver.pupil.BuildConfig
import xyz.quaver.pupil.sources.core.Source
import xyz.quaver.pupil.sources.core.util.LocalActivity
import xyz.quaver.pupil.sources.loadSource
import xyz.quaver.pupil.ui.theme.PupilTheme
import xyz.quaver.pupil.util.PupilHttpClient
import xyz.quaver.pupil.util.Release

class MainActivity : ComponentActivity(), DIAware {
    override val di by closestDI()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            PupilTheme {
                ProvideWindowInsets(windowInsetsAnimationsEnabled = true) {
                    val navController = rememberNavController()

                    val systemUiController = rememberSystemUiController()
                    val useDarkIcons = MaterialTheme.colors.isLight

                    val coroutineScope = rememberCoroutineScope()

                    val client: PupilHttpClient by rememberInstance()

                    val latestRelease by produceState<Release?>(null) {
                        value = null //client.latestRelease()
                    }

                    var dismissUpdate by remember { mutableStateOf(false) }

                    SideEffect {
                        systemUiController.setSystemBarsColor(
                            color = Color.Transparent,
                            darkIcons = useDarkIcons
                        )
                    }

                    latestRelease?.let { release ->
                        UpdateAlertDialog(
                            show = !dismissUpdate && release.version != BuildConfig.VERSION_NAME,
                            release = release,
                            onDismiss = { dismissUpdate = true }
                        )
                    }

                    NavHost(navController, "main") {
                        composable("main") {
                            var source by remember { mutableStateOf<Source?>(null) }

                            BackHandler(
                                enabled = source != null
                            ) {
                                source = null
                            }

                            Crossfade(source) { _source ->
                                if (_source == null)
                                    SourceSelector {
                                        coroutineScope.launch {
                                            source = loadSource(application, it)
                                        }
                                    }
                                else {
                                    CompositionLocalProvider(LocalActivity provides this@MainActivity) {
                                        _source.Entry()
                                    }
                                }
                            }
                        }
                        composable("settings") {

                        }
                    }
                }
            }
        }
    }
}
