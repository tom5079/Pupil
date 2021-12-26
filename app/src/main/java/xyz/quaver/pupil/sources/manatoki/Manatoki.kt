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
package xyz.quaver.pupil.sources.manatoki

import android.app.Application
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.pupil.R
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.sources.manatoki.composable.Main
import xyz.quaver.pupil.sources.manatoki.composable.Reader
import xyz.quaver.pupil.sources.manatoki.composable.Recent
import xyz.quaver.pupil.sources.manatoki.composable.Search

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class,
    ExperimentalComposeUiApi::class
)
class Manatoki(app: Application) : Source(), DIAware {
    override val di by closestDI(app)

    private val logger = newLogger(LoggerFactory.default)

    override val name = "manatoki.net"
    override val iconResID = R.drawable.manatoki

    override fun NavGraphBuilder.navGraph(navController: NavController) {
        navigation(route = name, startDestination = "manatoki.net/") {
            composable("manatoki.net/") { Main(navController) }
            composable("manatoki.net/reader/{itemID}") { Reader(navController) }
            composable("manatoki.net/search") { Search(navController) }
            composable("manatoki.net/recent") { Recent(navController) }
        }
    }

}