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

package xyz.quaver.pupil.sources

import kotlinx.coroutines.channels.Channel
import xyz.quaver.pupil.R
import kotlin.reflect.KClass

data class SearchResult(
    val id: String,
    val title: String,
    val thumbnail: String,
    val artists: String,
    val extra: Map<ExtraType, suspend () -> String>,
    val tags: List<String>
) {
    enum class ExtraType {
        GROUP,
        CHARACTER,
        SERIES,
        TYPE,
        LANGUAGE,
        PAGECOUNT
    }

    companion object {
        val extraTypeMap = mapOf(
            ExtraType.SERIES to R.string.galleryblock_series,
            ExtraType.TYPE to R.string.galleryblock_type,
            ExtraType.LANGUAGE to R.string.galleryblock_language,
            ExtraType.PAGECOUNT to R.string.galleryblock_pagecount
        )
    }
}

interface Source<Query_SortMode: Enum<*>> {
    val querySortModeClass: KClass<Query_SortMode>?

    suspend fun query(query: String, range: IntRange, sortMode: Query_SortMode? = null) : Pair<Channel<SearchResult>, Int>
}