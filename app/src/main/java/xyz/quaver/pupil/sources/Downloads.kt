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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.io.FileX
import xyz.quaver.io.util.getChild
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.DownloadManager
import kotlin.math.max
import kotlin.math.min

class Downloads(override val di: DI) : Source<DefaultSortMode, SearchSuggestion>(), DIAware {

    override val name: String
        get() = "Downloads"
    override val iconResID: Int
        get() = R.drawable.ic_download
    override val preferenceID: Int
        get() = -1
    override val availableSortMode: Array<DefaultSortMode> = DefaultSortMode.values()

    private val downloadManager: DownloadManager by instance()

    private val applicationContext: Application by instance()

    override suspend fun search(query: String, range: IntRange, sortMode: Enum<*>): Pair<Channel<ItemInfo>, Int> {
        val downloads = downloadManager.downloads.toList()

        val channel = Channel<ItemInfo>()
        val sanitizedRange = max(0, range.first) .. min(range.last, downloads.size - 1)

        CoroutineScope(Dispatchers.IO).launch {
            downloads.slice(sanitizedRange).map { (_, folderName) ->
                transform(downloadManager.downloadFolder.getChild(folderName))
            }.forEach {
                channel.send(it)
            }

            channel.close()
        }

        return Pair(channel, downloads.size)
    }

    override suspend fun suggestion(query: String): List<SearchSuggestion> {
        return emptyList()
    }

    override suspend fun images(itemID: String): List<String> {
        TODO("Not yet implemented")
    }

    override suspend fun info(itemID: String): ItemInfo {
        TODO("Not yet implemented")
    }

    companion object {
        private fun firstImage(folder: FileX): String? =
            folder.list { _, name ->
                name.takeLastWhile { it != '.' } !in listOf("jpg", "png", "gif", "webp")
            }?.firstOrNull()

        fun transform(folder: FileX): ItemInfo =
            kotlin.runCatching {
                Json.decodeFromString<ItemInfo>(folder.getChild(".metadata").readText())
            }.getOrNull() ?:
            ItemInfo(
                "Downloads",
                "",
                folder.name,
                firstImage(folder) ?: "",
                ""
            )
    }

}