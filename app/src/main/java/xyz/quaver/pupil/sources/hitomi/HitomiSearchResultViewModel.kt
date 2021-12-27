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

package xyz.quaver.pupil.sources.hitomi

import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import io.ktor.client.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import xyz.quaver.pupil.sources.composable.SearchBaseViewModel
import xyz.quaver.pupil.sources.hitomi.lib.GalleryBlock
import xyz.quaver.pupil.sources.hitomi.lib.doSearch
import xyz.quaver.pupil.sources.hitomi.lib.getGalleryBlock
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class HitomiSearchResultViewModel(
    private val client: HttpClient
) : SearchBaseViewModel<HitomiSearchResult>() {
    private var cachedQuery: String? = null
    private var cachedSortByPopularity: Boolean? = null
    private val cache = mutableListOf<Int>()

    private val galleryBlockCache = LruCache<Int, GalleryBlock>(100)

    var sortByPopularity by mutableStateOf(false)

    private var searchJob: Job? = null
    fun search() {
        val resultsPerPage = 25

        viewModelScope.launch {
            searchJob?.cancelAndJoin()

            searchResults.clear()
            searchBarOffset = 0
            loading = true
            error = false

            searchJob = launch {
                if (cachedQuery != query || cachedSortByPopularity != sortByPopularity || cache.isEmpty()) {
                    cachedQuery = null
                    cache.clear()

                    yield()

                    val result = runCatching {
                        doSearch(client, query, sortByPopularity)
                    }.onFailure {
                        error = true
                    }.getOrNull()

                    yield()

                    result?.let { cache.addAll(result) }
                    cachedQuery = query
                    totalItems = result?.size ?: 0
                    maxPage =
                        result?.let { ceil(result.size / resultsPerPage.toDouble()).toInt() }
                            ?: 0
                }

                yield()

                val range = max((currentPage-1)*resultsPerPage, 0) until min(currentPage*resultsPerPage, totalItems)

                cache.slice(range)
                    .forEach { galleryID ->
                        yield()
                        loading = false
                        kotlin.runCatching {
                            galleryBlockCache.get(galleryID) ?: getGalleryBlock(client, galleryID).also {
                                galleryBlockCache.put(galleryID, it)
                            }
                        }.onFailure {
                            error = true
                        }.getOrNull()?.let {
                            searchResults.add(transform(it))
                        }

                    }
            }

            viewModelScope.launch {
                searchJob?.join()
                loading = false
            }
        }
    }

    companion object {
        fun transform(galleryBlock: GalleryBlock) =
            HitomiSearchResult(
                galleryBlock.id.toString(),
                galleryBlock.title,
                galleryBlock.thumbnails.first(),
                galleryBlock.artists,
                galleryBlock.series,
                galleryBlock.type,
                galleryBlock.language,
                galleryBlock.relatedTags
            )
    }
}