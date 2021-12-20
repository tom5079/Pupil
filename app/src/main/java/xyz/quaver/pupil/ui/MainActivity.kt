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
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.pupil.sources.SourceEntries
import xyz.quaver.pupil.ui.theme.PupilTheme


class MainActivity : ComponentActivity(), DIAware {
    override val di by closestDI()

    private val sources: SourceEntries by instance()

    private val logger = newLogger(LoggerFactory.default)

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            PupilTheme {
                ProvideWindowInsets {
                    val navController = rememberNavController()

                    val systemUiController = rememberSystemUiController()
                    val useDarkIcons = MaterialTheme.colors.isLight

                    SideEffect {
                        systemUiController.setSystemBarsColor(
                            color = Color.Transparent,
                            darkIcons = useDarkIcons
                        )
                    }

                    NavHost(navController, startDestination = "main") {
                        composable("main") {
                            var launched by rememberSaveable { mutableStateOf(false) }

                            LaunchedEffect(Unit) {
                                if (!launched) {
                                    val source = it.arguments?.getString("source") ?: "manatoki.net"
                                    navController.navigate(source)
                                    launched = true
                                } else {
                                    onBackPressed()
                                }
                            }
                        }
                        sources.forEach {
                            it.second.run {
                                navGraph(navController)
                            }
                        }
                    }
                }
            }
        }
    }
}
