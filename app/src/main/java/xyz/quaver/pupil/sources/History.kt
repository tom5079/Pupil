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

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.direct
import xyz.quaver.pupil.util.database

class History(override val di: DI) : Source(), DIAware {

    private val historyDao = direct.database().historyDao()

    override val name: String
        get() = "history"
    override val iconResID: Int
        get() = 0 //TODO
    override val availableSortMode: List<String> = emptyList()

    private val history = direct.database().historyDao()

    override suspend fun search(query: String, range: IntRange, sortMode: Int): Pair<Channel<ItemInfo>, Int> {
        val channel = Channel<ItemInfo>()

        CoroutineScope(Dispatchers.IO).launch {


            channel.close()
        }

        throw NotImplementedError("")
        //return Pair(channel, histories.map.size)
    }

    override suspend fun images(itemID: String): List<String> {
        throw NotImplementedError("")
    }

    override suspend fun info(itemID: String): ItemInfo {
        throw NotImplementedError("")
    }


    @Composable
    override fun SearchResult(itemInfo: ItemInfo, onEvent: (SearchResultEvent) -> Unit) {

    }

}