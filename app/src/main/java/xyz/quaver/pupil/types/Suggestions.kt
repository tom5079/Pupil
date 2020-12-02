/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2019  tom5079
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

package xyz.quaver.pupil.types

import kotlinx.parcelize.Parcelize
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion

@Parcelize
class HistorySuggestion(override val body: String) : SearchSuggestion

@Parcelize
class NoResultSuggestion(override val body: String) : SearchSuggestion

@Parcelize
class LoadingSuggestion(override val body: String) : SearchSuggestion

@Parcelize
@Suppress("PARCELABLE_PRIMARY_CONSTRUCTOR_IS_EMPTY")
class FavoriteHistorySwitch(override val body: String) : SearchSuggestion