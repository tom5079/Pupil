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

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import io.ktor.client.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import xyz.quaver.pupil.db.AppDatabase
import xyz.quaver.pupil.sources.composable.SearchBaseViewModel
import xyz.quaver.pupil.sources.hitomi.lib.GalleryBlock
import xyz.quaver.pupil.sources.hitomi.lib.doSearch
import xyz.quaver.pupil.sources.hitomi.lib.getGalleryBlock
import kotlin.math.ceil

class HitomiSearchResultViewModel(app: Application) : SearchBaseViewModel<HitomiSearchResult>(app), DIAware {
    override val di by closestDI(app)

    private val client: HttpClient by instance()

    private val database: AppDatabase by instance()
    private val bookmarkDao = database.bookmarkDao()

    private var cachedQuery: String? = null
    private var cachedSortByPopularity: Boolean? = null
    private val cache = mutableListOf<Int>()

    var sortByPopularity by mutableStateOf(false)

    private var searchJob: Job? = null
    fun search() {
        val resultsPerPage = 25

        viewModelScope.launch {
            searchJob?.cancelAndJoin()

            searchResults.clear()
            searchBarOffset = 0
            loading = true

            searchJob = launch {
                if (cachedQuery != query || cachedSortByPopularity != sortByPopularity || cache.isEmpty()) {
                    cachedQuery = null
                    cache.clear()

                    yield()

                    val result = doSearch(client, query, sortByPopularity)

                    yield()

                    cache.addAll(result)
                    cachedQuery = query
                    totalItems = result.size
                    maxPage = ceil(result.size / resultsPerPage.toDouble()).toInt()
                }

                yield()

                cache.slice((currentPage-1)*resultsPerPage until currentPage*resultsPerPage).forEach { galleryID ->
                    searchResults.add(transform(getGalleryBlock(client, galleryID)))
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