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

import android.annotation.SuppressLint
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.quaver.pupil.hitomi.GalleryBlock
import xyz.quaver.pupil.hitomi.GalleryInfo
import xyz.quaver.pupil.hitomi.imageUrlFromImage
import java.util.*
import kotlin.collections.ArrayList

@OptIn(ExperimentalStdlibApi::class)
fun String.wordCapitalize() : String {
    val result = ArrayList<String>()

    @SuppressLint("DefaultLocale")
    for (word in this.split(" "))
        result.add(word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() })

    return result.joinToString(" ")
}

private val suffix = listOf(
    "B",
    "kB",
    "MB",
    "GB",
    "TB" //really?
)

fun byteToString(byte: Long, precision : Int = 1) : String {
    var size = byte.toDouble(); var suffixIndex = 0

    while (size >= 1024) {
        size /= 1024
        suffixIndex++
    }

    return "%.${precision}f ${suffix[suffixIndex]}".format(size)
}

/**
 * Convert android generated ID to requestCode
 * to prevent java.lang.IllegalArgumentException: Can only use lower 16 bits for requestCode
 *
 * https://stackoverflow.com/questions/38072322/generate-16-bit-unique-ids-in-android-for-startactivityforresult
 */
fun Int.normalizeID() = this.and(0xFFFF)

fun OkHttpClient.Builder.proxyInfo(proxyInfo: ProxyInfo) = this.apply {
    proxy(proxyInfo.proxy())
    proxyInfo.authenticator()?.let {
        proxyAuthenticator(it)
    }
}

val formatMap = mapOf<String, GalleryBlock.() -> (String)>(
    "-id-" to { id.toString() },
    "-title-" to { title },
    "-artist-" to { if (artists.isNotEmpty()) artists.joinToString() else "N/A" },
    "-group-" to { if (groups.isNotEmpty()) groups.joinToString() else "N/A" }
    // TODO
)
/**
 * Formats download folder name with given Metadata
 */
fun GalleryBlock.formatDownloadFolder(): String =
    Preferences["download_folder_name", "[-id-] -title-"].let {
        formatMap.entries.fold(it) { str, (k, v) ->
            str.replace(k, v.invoke(this), true)
        }
    }.replace(Regex("""[*\\|"?><:/]"""), "").ellipsize(127)

fun GalleryBlock.formatDownloadFolderTest(format: String): String =
    format.let {
        formatMap.entries.fold(it) { str, (k, v) ->
            str.replace(k, v.invoke(this), true)
        }
    }.replace(Regex("""[*\\|"?><:/]"""), "").ellipsize(127)

suspend fun GalleryInfo.getRequestBuilders(): List<Request.Builder> {
    val galleryID = this.id.toIntOrNull() ?: 0
    return this.files.map {
        Request.Builder()
            .url(
                runCatching {
                    imageUrlFromImage(galleryID, it, false)
                }
                .onFailure {
                    FirebaseCrashlytics.getInstance().recordException(it)
                }
                .getOrDefault("https://a/")
            )
            .header("Referer", "https://hitomi.la/")
    }
}

fun String.ellipsize(n: Int): String =
    if (this.length > n)
        this.slice(0 until n) + "…"
    else
        this

operator fun JsonElement.get(index: Int) =
    this.jsonArray[index]

operator fun JsonElement.get(tag: String) =
    this.jsonObject[tag]

fun JsonElement.getOrNull(tag: String) = kotlin.runCatching {
    this.jsonObject.getOrDefault(tag, null)
}.getOrNull()

val JsonElement.content
    get() = this.jsonPrimitive.contentOrNull