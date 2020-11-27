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

import com.google.android.gms.vision.L
import kotlin.reflect.KClass

interface SearchResult {
    val id: String
    val title: String
    val thumbnail: String
    val artists: List<String>
}

// Might be better to use channel on Query_Result
interface Source<Query_SortMode: Enum<*>, Query_Result: SearchResult> {
    val querySortModeClass: KClass<Query_SortMode>
    val queryResultClass: KClass<Query_Result>

    suspend fun query(query: String, range: IntRange, sortMode: Query_SortMode? = null) : Pair<List<Query_Result>, Int>
}