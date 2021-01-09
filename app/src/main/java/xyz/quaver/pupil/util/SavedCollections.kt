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

import androidx.annotation.RequiresApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File

class SavedSet <T: Any> (private val file: File, any: T, private val set: MutableSet<T> = mutableSetOf()) : MutableSet<T> by set {

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalSerializationApi::class)
    val serializer: KSerializer<Set<T>> = SetSerializer(serializer(any::class.java) as KSerializer<T>)

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
    fun save() {
        if (!file.exists())
            file.createNewFile()

        file.writeText(Json.encodeToString(serializer, set))
    }

    @Synchronized
    override fun add(element: T): Boolean {
        set.remove(element)

        return set.add(element).also {
            save()
        }
    }

    @Synchronized
    override fun addAll(elements: Collection<T>): Boolean {
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

class SavedMap <K: Any, V: Any> (private val file: File, anyKey: K, anyValue: V, private val map: MutableMap<K, V> = mutableMapOf()) : MutableMap<K, V> by map {

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalSerializationApi::class)
    val serializer: KSerializer<Map<K, V>> = MapSerializer(serializer(anyKey::class.java) as KSerializer<K>, serializer(anyValue::class.java) as KSerializer<V>)

    init {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            save()
        }
        load()
    }

    @Synchronized
    fun load() {
        map.clear()
        kotlin.runCatching {
            Json.decodeFromString(serializer, file.readText())
        }.onSuccess {
            map.putAll(it)
        }
    }

    @Synchronized
    fun save() {
        if (!file.exists())
            file.createNewFile()

        file.writeText(Json.encodeToString(serializer, map))
    }

    @Synchronized
    override fun put(key: K, value: V): V? {
        map.remove(key)

        return map.put(key, value).also {
            save()
        }
    }

    @Synchronized
    override fun putAll(from: Map<out K, V>) {
        for (key in from.keys) {
            map.remove(key)
        }

        map.putAll(from)

        save()
    }

    @Synchronized
    override fun remove(key: K): V? {
        return map.remove(key).also {
            save()
        }
    }

    @Synchronized
    @RequiresApi(24)
    override fun remove(key: K, value: V): Boolean {
        return map.remove(key, value).also {
            save()
        }
    }

    @Synchronized
    override fun clear() {
        map.clear()
        save()
    }

}