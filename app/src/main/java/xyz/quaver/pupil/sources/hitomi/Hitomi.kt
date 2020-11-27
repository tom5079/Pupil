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

import kotlinx.coroutines.yield
import xyz.quaver.hitomi.doSearch
import xyz.quaver.hitomi.getGalleryBlock
import xyz.quaver.pupil.sources.Source
import kotlin.math.min
import kotlin.math.max

class Hitomi : Source<Hitomi.SortMode, Hitomi.SearchResult> {

    override val querySortModeClass = SortMode::class
    override val queryResultClass = SearchResult::class

    enum class SortMode {
        NEWEST,
        POPULAR
    }

    data class SearchResult(
        override val id: String,
        override val title: String,
        override val thumbnail: String,
        override val artists: List<String>,
    ) : xyz.quaver.pupil.sources.SearchResult

    var cachedQuery: String? = null
    val cache = mutableListOf<Int>()

    override suspend fun query(query: String, range: IntRange, sortMode: SortMode?): Pair<List<SearchResult>, Int> {
        if (cachedQuery != query) {
            cachedQuery = null
            cache.clear()
            yield()
            doSearch(query, sortMode == SortMode.POPULAR).let {
                yield()
                cache.addAll(it)
            }
            cachedQuery = query
        }

        val sanitizedRange = max(0, range.first) .. min(range.last, cache.size-1)
        return Pair(cache.slice(sanitizedRange).map {
            getGalleryBlock(it).let { gallery ->
                SearchResult(
                    gallery.id.toString(),
                    gallery.title,
                    gallery.thumbnails.first(),
                    gallery.artists
                )
            }
        }, cache.size)
    }

}