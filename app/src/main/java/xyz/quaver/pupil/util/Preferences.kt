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

import android.content.SharedPreferences
import kotlin.reflect.KClass

lateinit var preferences: SharedPreferences

object Preferences: SharedPreferences by preferences {

    @Suppress("UNCHECKED_CAST")
    val putMap = mapOf<KClass<out Any>, (String, Any) -> Unit>(
        String::class to { k, v -> edit().putString(k, v as String).apply() },
        Int::class to { k, v -> edit().putBoolean(k, v as Boolean).apply() },
        Long::class to { k, v -> edit().putLong(k, v as Long).apply() },
        Boolean::class to { k, v -> edit().putBoolean(k, v as Boolean).apply() },
        Set::class to { k, v -> edit().putStringSet(k, v as Set<String>).apply() }
    )

    val defMap = mapOf(
        String::class to "",
        Int::class to -1,
        Long::class to -1,
        Boolean::class to false,
        Set::class to emptySet<Any>()
    )

    inline operator fun <reified T: Any> set(key: String, value: T) {
        putMap[T::class]?.invoke(key, value)
    }

    inline operator fun <reified T: Any> get(key: String, defaultVal: T = defMap[T::class] as T, setIfNull: Boolean = false): T =
        (all[key] as? T) ?: defaultVal.also { if (setIfNull) set(key, defaultVal) }

    fun remove(key: String) {
        edit().remove(key).apply()
    }
}