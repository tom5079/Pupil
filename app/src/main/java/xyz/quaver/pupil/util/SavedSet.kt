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

package xyz.quaver.pupil.util

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*

class SavedSet <T: Any> (private val file: File, private val any: T, private val set: MutableSet<T> = mutableSetOf()) : MutableSet<T> by set {

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalSerializationApi::class)
    val serializer: KSerializer<List<T>>
        get() = ListSerializer(serializer(any::class.java) as KSerializer<T>)

    init {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            save()
        }
        load()
    }

    @Synchronized
    fun load() {
        set.clear()
        kotlin.runCatching {
            Json.decodeFromString(serializer, file.readText())
        }.onSuccess {
            set.addAll(it)
        }
    }

    @Synchronized
    @OptIn(ExperimentalSerializationApi::class)
    fun save() {
        file.writeText(Json.encodeToString(serializer, set.toList()))
    }

    @Synchronized
    override fun add(element: T): Boolean {
        load()

        set.remove(element)

        return set.add(element).also {
            save()
        }
    }

    @Synchronized
    override fun addAll(elements: Collection<T>): Boolean {
        load()

        set.removeAll(elements)

        return set.addAll(elements).also {
            save()
        }
    }

    @Synchronized
    override fun remove(element: T): Boolean {
        load()

        return set.remove(element).also {
            save()
        }
    }

    @Synchronized
    override fun clear() {
        set.clear()
        save()
    }

}