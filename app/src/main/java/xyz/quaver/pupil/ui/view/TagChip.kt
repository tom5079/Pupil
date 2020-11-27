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
import xyz.quaver.pupil.favoriteTags
import xyz.quaver.pupil.sources.hitomi.Hitomi
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.util.translations
import xyz.quaver.pupil.util.wordCapitalize

@SuppressLint("ViewConstructor")
class TagChip(context: Context, _tag: Tag) : Chip(context) {

    val tag: Tag =
        _tag.let {
            when {
                it.area != null -> it
                else -> Tag("tag", _tag.tag)
            }
        }

    init {
        when(tag.area) {
            "male" -> {
                setChipBackgroundColorResource(R.color.material_blue_700)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                setCloseIconTintResource(android.R.color.white)
                chipIcon = ContextCompat.getDrawable(context, R.drawable.gender_male_white)
            }
            "female" -> {
                setChipBackgroundColorResource(R.color.material_pink_600)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                setCloseIconTintResource(android.R.color.white)
                chipIcon = ContextCompat.getDrawable(context, R.drawable.gender_female_white)
            }
        }

        if (favoriteTags.contains(tag))
            setChipBackgroundColorResource(R.color.material_orange_500)

        isCloseIconVisible = true
        closeIcon = ContextCompat.getDrawable(context,
            if (favoriteTags.contains(tag))
                R.drawable.ic_star_filled
            else
                R.drawable.ic_star_empty
        )

        setOnCloseIconClickListener {
            if (favoriteTags.contains(tag)) {
                favoriteTags.remove(tag)
                closeIcon = ContextCompat.getDrawable(context, R.drawable.ic_star_empty)

                when(tag.area) {
                    "male" -> setChipBackgroundColorResource(R.color.material_blue_700)
                    "female" -> setChipBackgroundColorResource(R.color.material_pink_600)
                    else -> chipBackgroundColor = null
                }
            } else {
                favoriteTags.add(tag)
                closeIcon = ContextCompat.getDrawable(context, R.drawable.ic_star_filled)
                setChipBackgroundColorResource(R.color.material_orange_500)
            }
        }

        text = when (tag.area) {
            // TODO languageMap
            "language" -> Hitomi.languageMap[tag.tag]
            else -> (translations[tag.tag] ?: tag.tag).wordCapitalize()
        }

        setEnsureMinTouchTargetSize(false)
    }

}