/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
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

package xyz.quaver.pupil.sources

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import org.kodein.di.*
import xyz.quaver.pupil.sources.hitomi.Hitomi

abstract class Source {
    abstract val name: String
    abstract val iconResID: Int

    @Composable
    open fun MainScreen(navController: NavController) { }

    @Composable
    open fun Search(navController: NavController) { }

    @Composable
    open fun Reader(navController: NavController) { }
}

typealias SourceEntry = Pair<String, Source>
typealias SourceEntries = Set<SourceEntry>
val sourceModule = DI.Module(name = "source") {
    bindSet<SourceEntry>()

    listOf<(Application) -> (Source)>(
        { Hitomi(it) },
        //{ Hiyobi_io(it) },
        //{ Manatoki(it) }
    ).forEach { source ->
        inSet { singleton { source(instance()).let { it.name to it } } }
    }

    //bind { singleton { History(di) } }
   // inSet { singleton { Downloads(di).let { it.name to it as Source } } }
}