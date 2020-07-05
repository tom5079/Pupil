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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.list
import kotlinx.serialization.builtins.serializer
import java.io.File

class Histories(private val file: File) : ArrayList<Int>() {

    val serializer: KSerializer<List<Int>> = Int.serializer().list

    init {
        if (!file.exists())
            file.parentFile?.mkdirs()

        try {
            load()
        } catch (e: Exception) {
            save()
        }
    }

    fun load() : Histories {
        return apply {
            super.clear()
            addAll(
                json.parse(
                    serializer,
                    file.bufferedReader().use { it.readText() }
                )
            )
        }
    }

    fun save() {
        file.writeText(json.stringify(serializer, this))
    }

    override fun add(element: Int): Boolean {
        load()

        if (contains(element))
            super.remove(element)

        super.add(0, element)

        save()

        return true
    }

    override fun addAll(elements: Collection<Int>): Boolean {
        load()

        for (e in elements) {
            if (contains(e))
                super.remove(e)
            super.add(0, e)
        }

        save()

        return true
    }

    override fun remove(element: Int): Boolean {
        load()
        val retval = super.remove(element)
        save()

        return retval
    }

    override fun clear() {
        super.clear()
        save()
    }
}