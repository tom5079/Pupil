package xyz.quaver.pupil.util

import android.content.Context
import android.content.ContextWrapper
import androidx.core.content.ContextCompat
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
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

val source = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

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

    @UseExperimental(ImplicitReflectionSerializer::class)
    private fun load() {
        val lock = File(ContextCompat.getDataDir(this), "lock.json")

        if (!lock.exists()) {
            lock.createNewFile()
            lock.writeText("[]")
        }

        locks = ArrayList(Json(JsonConfiguration.Stable).parseList(lock.readText()))
    }

    @UseExperimental(ImplicitReflectionSerializer::class)
    private fun save() {
        val lock = File(ContextCompat.getDataDir(this), "lock.json")

        if (!lock.exists())
            lock.createNewFile()

        lock.writeText(Json(JsonConfiguration.Stable).stringify(locks?.toList() ?: listOf()))
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

    fun empty(): Boolean {
        return locks.isNullOrEmpty()
    }

    fun contains(type: Lock.Type): Boolean {
        return locks?.any { it.type == type } ?: false
    }

}