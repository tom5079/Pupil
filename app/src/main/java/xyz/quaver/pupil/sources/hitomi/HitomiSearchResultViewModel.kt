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
import kotlinx.coroutines.*
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.doSearch
import xyz.quaver.hitomi.getGalleryBlock
import xyz.quaver.pupil.db.AppDatabase
import xyz.quaver.pupil.sources.composable.SearchBaseViewModel

class HitomiSearchResultViewModel(app: Application) : SearchBaseViewModel<HitomiSearchResult>(app), DIAware {
    override val di by closestDI(app)

    private val database: AppDatabase by instance()
    private val bookmarkDao = database.bookmarkDao()

    init {
        search()
    }

    private var searchJob: Job? = null
    fun search() {
        searchJob?.cancel()
        searchResults.clear()
        searchJob = CoroutineScope(Dispatchers.IO).launch {
            val result = doSearch("female:loli")

            yield()

            result.take(25).forEach {
                yield()
                searchResults.add(transform(getGalleryBlock(it)))
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