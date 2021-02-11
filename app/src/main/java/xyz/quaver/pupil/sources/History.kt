/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2021  tom5079
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

import android.util.Log
import com.orhanobut.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.di
import org.kodein.di.instance
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.pupil.util.SavedSourceSet
import xyz.quaver.pupil.util.source

class History(override val di: DI, source: String) : Source<DefaultSortMode, SearchSuggestion>(), DIAware {

    private val source: AnySource by source(source)
    private val histories: SavedSourceSet by instance(tag = "histories")

    override val name: String
        get() = source.name
    override val iconResID: Int
        get() = source.iconResID
    override val availableSortMode: Array<DefaultSortMode> = DefaultSortMode.values()

    override suspend fun search(query: String, range: IntRange, sortMode: Enum<*>): Pair<Channel<ItemInfo>, Int> {
        val channel = Channel<ItemInfo>()

        CoroutineScope(Dispatchers.IO).launch {
            Logger.d(histories.map)
            histories.map[source.name]?.forEach {
                channel.send(source.info(it))
            }
        }

        return Pair(channel, histories.map.size)
    }

    override suspend fun suggestion(query: String): List<SearchSuggestion> {
        return source.suggestion(query)
    }

    override suspend fun images(itemID: String): List<String> {
        return source.images(itemID)
    }

    override suspend fun info(itemID: String): ItemInfo {
        return source.info(itemID)
    }


}