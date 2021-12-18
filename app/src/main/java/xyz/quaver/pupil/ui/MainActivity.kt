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
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.direct
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.pupil.ui.theme.PupilTheme
import xyz.quaver.pupil.ui.viewmodel.MainViewModel
import xyz.quaver.pupil.util.source


class MainActivity : ComponentActivity(), DIAware {
    override val di by closestDI()

    private val model: MainViewModel by viewModels()

    private val logger = newLogger(LoggerFactory.default)

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PupilTheme {
                val navController = rememberNavController()

                NavHost(navController, startDestination = "main/{source}") {
                    composable("main/{source}") {
                        direct.source(it.arguments?.getString("source") ?: "hitomi.la")
                            .MainScreen(navController)
                    }

                    composable("search/{source}") {
                        direct.source(it.arguments?.getString("source") ?: "hitomi.la")
                            .Search(navController)
                    }

                    composable("reader/{source}/{itemID}") {
                        direct.source(it.arguments?.getString("source") ?: "hitomi.la")
                            .Reader(navController)
                    }
                }
            }
        }
    }
}
