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

package xyz.quaver.pupil.sources.hitomi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import xyz.quaver.hiyobi.*
import xyz.quaver.pupil.sources.SearchResult
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.util.wordCapitalize

class Hiyobi : Source<Enum<*>> {
    override val querySortModeClass = null

    override suspend fun query(query: String, range: IntRange, sortMode: Enum<*>?): Pair<Channel<SearchResult>, Int> {
        val channel = Channel<SearchResult>()

        val (results, total) = if (query.isEmpty())
            list(range)
        else
            search(query, range)

        CoroutineScope(Dispatchers.Unconfined).launch {
            results.forEach {
                channel.send(transform(it))
            }

            channel.close()
        }

        return Pair(channel, total)
    }

    companion object {
        fun transform(galleryBlock: GalleryBlock): SearchResult =
            SearchResult(
                galleryBlock.id,
                galleryBlock.title,
                "https://cdn.$hiyobi/tn/${galleryBlock.id}.jpg",
                galleryBlock.artists.joinToString { it.value.wordCapitalize() },
                mapOf(
                    SearchResult.ExtraType.CHARACTER to { galleryBlock.characters.joinToString { it.value.wordCapitalize() } },
                    SearchResult.ExtraType.SERIES to { galleryBlock.parodys.joinToString { it.value.wordCapitalize() } },
                    SearchResult.ExtraType.TYPE to { galleryBlock.type.name.wordCapitalize() },
                    SearchResult.ExtraType.PAGECOUNT to { getGalleryInfo(galleryBlock.id).files.size.toString() }
                ),
                galleryBlock.tags.map { it.value }
            )
    }

}