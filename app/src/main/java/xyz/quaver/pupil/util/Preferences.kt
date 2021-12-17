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

@Deprecated("Use DataStore")
object Preferences: SharedPreferences by preferences {

    val defMap = mapOf(
        String::class to "",
        Int::class to -1,
        Long::class to -1L,
        Boolean::class to false,
        Set::class to emptySet<Any>()
    )

    operator fun set(key: String, value: String) = edit().putString(key, value).apply()
    operator fun set(key: String, value: Int) = edit().putInt(key, value).apply()
    operator fun set(key: String, value: Long) = edit().putLong(key, value).apply()
    operator fun set(key: String, value: Boolean) = edit().putBoolean(key, value).apply()
    operator fun set(key: String, value: Set<String>) = edit().putStringSet(key, value).apply()

    @Suppress("UNCHECKED_CAST")
    inline operator fun <reified T: Any> get(key: String, defaultVal: T = defMap[T::class] as T): T = (all[key] as? T) ?: defaultVal

    fun remove(key: String) {
        edit().remove(key).apply()
    }
}