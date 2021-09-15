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

import android.app.Application
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.get
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.pupil.R
/*
class ImHentai(override val di: DI) : Source(), DIAware {

    private val app: Application by instance()
    private val client: HttpClient by instance()

    override val name: String
        get() = ImHentai.name
    override val iconResID: Int
        get() = R.drawable.ic_imhentai
    override val preferenceID: Int
        get() = R.xml.imhentai_preferences
    override val availableSortMode = app.resources.getStringArray(R.array.imhentai_sort_mode).toList()

    override suspend fun search(query: String, range: IntRange, sortMode: Int): Pair<Channel<ItemInfo>, Int> = withContext(Dispatchers.IO) {
        val channel = Channel<ItemInfo>()

        val doc = Jsoup.connect("https://imhentai.xxx/search/?key=$query").get()

        val count = countRegex.find(doc.getElementsByClass("heading2").text())?.groupValues?.get(1)?.toIntOrNull() ?: 0

        launch {
            doc.getElementsByClass("thumb")
        }

        return@withContext Pair(channel, count)
    }

    override suspend fun suggestion(query: String): List<SearchSuggestion> {
        TODO("Not yet implemented")
    }

    override suspend fun images(itemID: String): List<String> {
        TODO("Not yet implemented")
    }

    override suspend fun info(itemID: String): ItemInfo {
        TODO("Not yet implemented")
    }

    companion object {
        private const val name = "imhentai"
        private val countRegex = Regex("""\(\d+\) results found.""")
        private val idRegex = Regex("""/gallery/(\d+)/""")

        private fun transform(item: Element) {
            val caption = item.select(".caption a")
        }
    }

}*/