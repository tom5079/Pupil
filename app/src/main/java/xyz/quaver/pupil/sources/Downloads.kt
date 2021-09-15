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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
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
/*
class Downloads(override val di: DI) : Source(), DIAware {

    override val name: String
        get() = "downloads"
    override val iconResID: Int
        get() = R.drawable.ic_download
    override val preferenceID: Int
        get() = R.xml.download_preferences
    override val availableSortMode: List<String> = emptyList()

    private val downloadManager: DownloadManager by instance()

    override suspend fun search(query: String, range: IntRange, sortMode: Int): Pair<Channel<ItemInfo>, Int> {
        TODO()
        /*
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

        return Pair(channel, downloads.size)*/
    }

    override suspend fun suggestion(query: String): List<SearchSuggestion> {
        return emptyList()
    }

    override suspend fun images(itemID: String): List<String> {
        return downloadManager.downloadFolder.getChild(itemID).let {
            if (!it.exists()) null else images(it)
        }!!
    }

    override suspend fun info(itemID: String): ItemInfo {
        TODO("Not yet implemented")
/*        return transform(downloadManager.downloadFolder.getChild(itemID))*/
    }

    companion object {
        private fun images(folder: FileX): List<String>? =
            folder.list { _, name ->
                name.takeLastWhile { it != '.' } in listOf("jpg", "png", "gif", "webp")
            }?.toList()
/*
        suspend fun transform(folder: FileX): ItemInfo = withContext(Dispatchers.Unconfined) {
            kotlin.runCatching {
                Json.decodeFromString<ItemInfo>(folder.getChild(".metadata").readText())
            }.getOrNull() ?: run {
                val images = images(folder)
                ItemInfo(
                    "Downloads",
                    folder.name,
                    folder.name,
                    images?.firstOrNull() ?: "",
                    "",
                    mapOf(
                        ItemInfo.ExtraType.PAGECOUNT to async { images?.size?.toString() }
                    )
                )
            }
        }*/
    }

    @Composable
    override fun compose(itemInfo: ItemInfo) {
        TODO("Not yet implemented")
    }

}*/