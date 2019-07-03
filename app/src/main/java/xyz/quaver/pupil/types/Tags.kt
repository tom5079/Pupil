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

import kotlinx.serialization.Serializable

@Serializable
data class Tag(val area: String?, val tag: String, val isNegative: Boolean = false) {
    companion object {
        fun parse(tag: String) : Tag {
            if (tag.first() == '-') {
                tag.substring(1).split(Regex(":"), 2).let {
                    return when(it.size) {
                        2 -> Tag(it[0], it[1], true)
                        else -> Tag(null, tag, true)
                    }
                }
            }
            tag.split(Regex(":"), 2).let {
                return when(it.size) {
                    2 -> Tag(it[0], it[1])
                    else -> Tag(null, tag)
                }
            }
        }
    }

    override fun toString(): String {
        return (if (isNegative) "-" else "") + when(area) {
            null -> tag
            else -> "$area:$tag"
        }
    }

    fun toQuery(): String {
        return toString().replace(' ', '_')
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Tag)
            return false

        if (other.area == area && other.tag == tag)
            return true

        return false
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}

class Tags(tag: List<Tag?>?) : ArrayList<Tag>() {

    companion object {
        fun parse(tags: String) : Tags {
            return Tags(
                tags.split(' ').map {
                    if (it.isNotEmpty())
                        Tag.parse(it)
                    else
                        null
                }
            )
        }
    }

    init {
        tag?.forEach {
            if (it != null)
                add(it)
        }
    }

    fun contains(element: String): Boolean {
        forEach {
            if (it.toString() == element)
                return true
        }

        return false
    }

    fun add(element: String): Boolean {
        return super.add(Tag.parse(element))
    }

    fun remove(element: String) {
        filter { it.toString() == element }.forEach {
            remove(it)
        }
    }

    fun removeByArea(area: String, isNegative: Boolean? = null) {
        filter { it.area == area && (if(isNegative == null) true else (it.isNegative == isNegative)) }.forEach {
            remove(it)
        }
    }

    override fun toString(): String {
        return joinToString(" ") { it.toString() }
    }

}