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
import androidx.lifecycle.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.parcelize.Parcelize
import org.kodein.di.*
import xyz.quaver.floatingsearchview.databinding.SearchSuggestionItemBinding
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.pupil.R
import xyz.quaver.pupil.sources.hitomi.Hitomi

interface ItemInfo : Parcelable {
    val source: String
    val itemID: String
    val title: String
}

@Parcelize
class DefaultSearchSuggestion(override val body: String) : SearchSuggestion

data class SearchResultEvent(val type: Type, val payload: String) {
    enum class Type {
        OPEN_READER,
        OPEN_DETAILS,
        NEW_QUERY,
        TOGGLE_FAVORITES
    }
}

abstract class Source {
    abstract val name: String
    abstract val iconResID: Int
    abstract val preferenceID: Int
    abstract val availableSortMode: List<String>

    abstract suspend fun search(query: String, range: IntRange, sortMode: Int): Pair<Channel<ItemInfo>, Int>
    abstract suspend fun suggestion(query: String): List<SearchSuggestion>
    abstract suspend fun images(itemID: String): List<String>
    abstract suspend fun info(itemID: String): ItemInfo

    @Composable
    open fun SearchResult(itemInfo: ItemInfo, onEvent: ((SearchResultEvent) -> Unit)? = null) { }

    open fun getHeadersBuilderForImage(itemID: String, url: String): HeadersBuilder.() -> Unit = { }

    open fun onSuggestionBind(binding: SearchSuggestionItemBinding, item: SearchSuggestion) {
        binding.leftIcon.setImageResource(R.drawable.tag)
    }
}

typealias SourceEntry = Pair<String, Source>
typealias SourceEntries = Set<SourceEntry>
val sourceModule = DI.Module(name = "source") {
    bindSet<SourceEntry>()

    listOf<(Application) -> (Source)>(
        { Hitomi(it) }
    ).forEach { source ->
        inSet { singleton { source.invoke(instance()).let { it.name to it } } }
    }

    bind { singleton { History(di) } }
   // inSet { singleton { Downloads(di).let { it.name to it as Source } } }
}