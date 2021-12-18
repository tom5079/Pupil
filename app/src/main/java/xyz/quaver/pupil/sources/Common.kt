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
import android.os.Parcelable
import androidx.compose.runtime.Composable
import io.ktor.http.*
import kotlinx.coroutines.channels.Channel
import org.kodein.di.*
import xyz.quaver.pupil.sources.manatoki.Manatoki

interface ItemInfo : Parcelable {
    val source: String
    val itemID: String
    val title: String
}

data class SearchResultEvent(val type: Type, val itemID: String, val payload: Parcelable? = null) {
    enum class Type {
        OPEN_READER,
        OPEN_DETAILS,
        NEW_QUERY
    }
}

abstract class Source {
    abstract val name: String
    abstract val iconResID: Int
    abstract val availableSortMode: List<String>

    abstract suspend fun search(query: String, range: IntRange, sortMode: Int): Pair<Channel<ItemInfo>, Int>
    abstract suspend fun images(itemID: String): List<String>
    abstract suspend fun info(itemID: String): ItemInfo

    @Composable
    open fun SearchResult(itemInfo: ItemInfo, onEvent: (SearchResultEvent) -> Unit = { }) { }

    open fun getHeadersBuilderForImage(itemID: String, url: String): HeadersBuilder.() -> Unit = { }
}

typealias SourceEntry = Pair<String, Source>
typealias SourceEntries = Set<SourceEntry>
val sourceModule = DI.Module(name = "source") {
    bindSet<SourceEntry>()

    listOf<(Application) -> (Source)>(
        { Hitomi(it) },
        { Hiyobi_io(it) },
        { Manatoki(it) }
    ).forEach { source ->
        inSet { singleton { source(instance()).let { it.name to it } } }
    }

    bind { singleton { History(di) } }
   // inSet { singleton { Downloads(di).let { it.name to it as Source } } }
}