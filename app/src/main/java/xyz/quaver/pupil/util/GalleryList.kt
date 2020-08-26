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

package xyz.quaver.pupil.util

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class GalleryList(private val file: File, private val list: MutableSet<Int> = mutableSetOf()) : MutableSet<Int> by list {

    init {
        load()
    }

    fun load() {
        synchronized(this) {
            if (!file.exists())
                file.parentFile?.mkdirs()

            list.clear()
            list.addAll(
                Json.decodeFromString<List<Int>>(file.bufferedReader().use { it.readText() })
            )
        }
    }

    fun save() {
        synchronized(this) {
            file.writeText(Json.encodeToString(list))
        }
    }

    override fun add(element: Int): Boolean {
        load()

        return list.add(element).also {
            save()
        }
    }

    override fun addAll(elements: Collection<Int>): Boolean {
        load()

        return list.addAll(elements).also {
            save()
        }
    }

    override fun remove(element: Int): Boolean {
        load()

        return list.remove(element).also {
            save()
        }
    }

    override fun clear() {
        list.clear()
        save()
    }

}