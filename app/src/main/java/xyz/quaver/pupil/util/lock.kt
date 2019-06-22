package xyz.quaver.pupil.util

import android.content.Context
import android.content.ContextWrapper
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

data class Lock(private val type: Type, private val hash: String, private val salt: String) {

    enum class Type {
        PATTERN
    }

    fun match(password: String): Boolean {
        return hash(password+salt) == hash
    }
}

class LockManager(base: Context): ContextWrapper(base) {



}