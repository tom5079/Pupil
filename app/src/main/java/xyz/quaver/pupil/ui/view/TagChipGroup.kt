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

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import xyz.quaver.pupil.R
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.types.Tags

class TagChipGroup @JvmOverloads constructor(context: Context, attr: AttributeSet? = null, attrStyle: Int = R.attr.chipGroupStyle, val tags: Tags = Tags()) : ChipGroup(context, attr, attrStyle), MutableSet<Tag> by tags {

    object Defaults {
        val maxChipSize = 10
    }

    var maxChipSize: Int = Defaults.maxChipSize
        set(value) {
            field = value

            refresh()
        }

    private val moreView = Chip(context).apply {
        text = "…"

        setEnsureMinTouchTargetSize(false)

        setOnClickListener {
            removeView(this)

            for (i in maxChipSize until tags.size) {
                val tag = tags.elementAt(i)

                addView(TagChip(context, tag).apply {
                    setOnClickListener {
                        onClickListener?.invoke(tag)
                    }
                })
            }
        }
    }

    var onClickListener: ((Tag) -> Unit)? = null

    private fun applyAttributes(attr: TypedArray) {
        maxChipSize = attr.getInt(R.styleable.TagChipGroup_maxTag, Defaults.maxChipSize)
    }

    fun refresh() {
        this.removeAllViews()

        tags.take(maxChipSize).forEach {
            this.addView(TagChip(context, it).apply {
                setOnClickListener {
                    onClickListener?.invoke(this.tag)
                }
            })
        }

        if (maxChipSize > 0 && this.size > maxChipSize)
            addView(moreView)
    }

    init {
        applyAttributes(context.obtainStyledAttributes(attr, R.styleable.TagChipGroup))

        refresh()
    }

}