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

package xyz.quaver.pupil.ui.view

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import xyz.quaver.pupil.R
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.util.wordCapitalize

@SuppressLint("ViewConstructor")
class TagChip(context: Context, tag: Tag) : Chip(context) {

    val tag: Tag =
        tag.let {
            when {
                it.area != null -> it
                else -> Tag("tag", tag.tag)
            }
        }

    private val languages = context.resources.getStringArray(R.array.languages).map {
        it.split("|").let { split ->
            Pair(split[0], split[1])
        }
    }.toMap()

    init {
        chipIcon = when(tag.area) {
            "male" -> {
                setChipBackgroundColorResource(R.color.material_blue_700)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                ContextCompat.getDrawable(context, R.drawable.gender_male_white)
            }
            "female" -> {
                setChipBackgroundColorResource(R.color.material_pink_600)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                ContextCompat.getDrawable(context, R.drawable.gender_female_white)
            }
            else -> null
        }

        text = when (tag.area) {
            "language" -> languages[tag.tag]
            else -> tag.tag.wordCapitalize()
        }

        setEnsureMinTouchTargetSize(false)
    }

}