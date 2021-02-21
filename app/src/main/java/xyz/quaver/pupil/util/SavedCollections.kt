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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.serializer
import java.io.File

class SavedMap <K: Any, V: Any> (private val file: File, anyKey: K, anyValue: V, private val map: MutableMap<K, V> = mutableMapOf()) : MutableMap<K, V> by map {

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalSerializationApi::class)
    val serializer: KSerializer<Map<K, V>> = MapSerializer(serializer(anyKey::class.java) as KSerializer<K>, serializer(anyValue::class.java) as KSerializer<V>)

    init {
        if (!file.exists()) {
            save()
        }
        load()
    }

    @Synchronized
    fun load() {
        map.clear()
        kotlin.runCatching {
            decodeFromString(serializer, file.readText())
        }.onSuccess {
            map.putAll(it)
        }
    }

    @Synchronized
    fun save() {
        file.parentFile?.mkdirs()
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

class SavedSourceSet(private val file: File) {
    private val _map = mutableMapOf<String, MutableList<String>>()
    val map: Map<String, List<String>> = _map

    private val serializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    @Synchronized
    fun load() {
        _map.clear()
        kotlin.runCatching {
            decodeFromString(serializer, file.readText())
        }.onSuccess {
            it.forEach { (k, v) ->
                _map[k] = v.toMutableList()
            }
        }
    }

    @Synchronized
    fun save() {
        file.parentFile?.mkdirs()
        if (!file.exists())
            file.createNewFile()

        file.writeText(Json.encodeToString(serializer, _map))
    }

    operator fun get(key: String) = _map[key]

    @Synchronized
    fun add(source: String, value: String) {
        load()

        _map[source]?.remove(value)

        if (!_map.containsKey(source))
            _map[source] = mutableListOf()
        else
            _map[source]!!.add(value)

        save()
    }

    @Synchronized
    fun addAll(from: Map<String, Set<String>>) {
        load()

        for (source in from.keys) {
            if (_map.containsKey(source)) {
                _map[source]!!.removeAll(from[source]!!)
                _map[source]!!.addAll(from[source]!!)
            } else {
                _map[source] = from[source]!!.toMutableList()
            }
        }

        save()
    }

    @Synchronized
    fun remove(source: String, value: String): Boolean {
        load()

        return (_map[source]?.remove(value) ?: false).also {
            save()
        }
    }

    @Synchronized
    fun clear() {
        _map.clear()
        save()
    }

}