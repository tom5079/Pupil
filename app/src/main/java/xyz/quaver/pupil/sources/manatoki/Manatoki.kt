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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.jsoup.Jsoup
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.pupil.R
import xyz.quaver.pupil.sources.ItemInfo
import xyz.quaver.pupil.sources.Source

@Parcelize
class ManatokiItemInfo(
    override val itemID: String,
    override val title: String
) : ItemInfo {
    override val source: String = "manatoki.net"
}

class Manatoki(app: Application) : Source(), DIAware {
    override val di by closestDI(app)

    private val logger = newLogger(LoggerFactory.default)

    override val name = "manatoki.net"
    override val availableSortMode: List<String> = emptyList()
    override val iconResID: Int = R.drawable.manatoki

    override suspend fun search(
        query: String,
        range: IntRange,
        sortMode: Int
    ): Pair<Channel<ItemInfo>, Int> {
        TODO("Not yet implemented")
    }

    override suspend fun images(itemID: String): List<String> = coroutineScope {
        val jsoup = withContext(Dispatchers.IO) {
            Jsoup.connect("https://manatoki116.net/comic/$itemID").get()
        }

        val htmlData = jsoup
            .selectFirst(".view-padding > script")!!
            .data()
            .splitToSequence('\n')
            .fold(StringBuilder()) { sb, line ->
                if (!line.startsWith("html_data")) return@fold sb

                line.drop(12).dropLast(2).split('.').forEach {
                    if (it.isNotBlank()) sb.appendCodePoint(it.toInt(16))
                }
                sb
            }.toString()
        
        Jsoup.parse(htmlData)
            .select("img[^data-]:not([style])")
            .map {
                it.attributes()
                    .first { it.key.startsWith("data-") }
                    .value
            }
    }

    override suspend fun info(itemID: String): ItemInfo = coroutineScope {
        val jsoup = withContext(Dispatchers.IO) {
            Jsoup.connect("https://manatoki116.net/comic/$itemID").get()
        }

        val title = jsoup.selectFirst(".toon-title")!!.ownText()

        ManatokiItemInfo(
            itemID,
            title
        )
    }

}