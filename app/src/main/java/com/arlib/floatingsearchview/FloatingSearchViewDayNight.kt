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

package com.arlib.floatingsearchview

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet

class FloatingSearchViewDayNight @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null)
    : FloatingSearchView(context, attrs) {

    // hack to remove color attributes which should not be reused
    override fun onSaveInstanceState(): Parcelable? {
        super.onSaveInstanceState()
        return null
    }
}