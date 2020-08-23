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

import android.content.Context
import android.content.ContextWrapper
import androidx.core.content.ContextCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

fun hash(password: String): String {
    val bytes = password.toByteArray()
    val md = MessageDigest.getInstance("SHA-256")

    return md.digest(bytes).fold("") { str, it -> str + "%02x".format(it) }
}

// Ret1: SHA-256 Hash
// Ret2: Hash salt
fun hashWithSalt(password: String): Pair<String, String> {
    val salt = (0 until 12).map { source.random() }.joinToString()

    return Pair(hash(password+salt), salt)
}

const val source = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

@Serializable
data class Lock(val type: Type, val hash: String, val salt: String) {

    enum class Type {
        PATTERN,
        PIN,
        PASSWORD
    }

    companion object {
        fun generate(type: Type, password: String): Lock {
            val (hash, salt) = hashWithSalt(password)
            return Lock(type, hash, salt)
        }
    }

    fun match(password: String): Boolean {
        return hash(password+salt) == hash
    }
}

class LockManager(base: Context): ContextWrapper(base) {

    var locks: ArrayList<Lock>? = null

    init {
        load()
    }

    private fun load() {
        val lock = File(ContextCompat.getDataDir(this), "lock.json")

        if (!lock.exists()) {
            lock.createNewFile()
            lock.writeText("[]")
        }

        locks = Json.decodeFromString(lock.readText())
    }

    private fun save() {
        val lock = File(ContextCompat.getDataDir(this), "lock.json")

        if (!lock.exists())
            lock.createNewFile()

        lock.writeText(Json.encodeToString(locks?.toList() ?: listOf()))
    }

    fun add(lock: Lock) {
        remove(lock.type)
        locks?.add(lock)
        save()
    }

    fun remove(type: Lock.Type) {
        locks?.removeAll { it.type == type }
        save()
    }

    fun check(password: String): Boolean? {
        return locks?.any {
            it.match(password)
        }
    }

    fun isEmpty(): Boolean {
        return locks.isNullOrEmpty()
    }

    fun isNotEmpty(): Boolean = !isEmpty()

    fun contains(type: Lock.Type): Boolean {
        return locks?.any { it.type == type } ?: false
    }

}