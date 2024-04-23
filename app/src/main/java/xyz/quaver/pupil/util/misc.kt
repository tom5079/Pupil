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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.quaver.pupil.R
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

fun byteCount(codePoint: Int): Int = when (codePoint) {
    in 0 ..< 0x80 -> 1
    in 0x80 ..< 0x800 -> 2
    in 0x800 ..< 0x10000 -> 3
    in 0x10000 ..< 0x110000 -> 4
    else -> 0
}

fun String.ellipsize(n: Int): String = buildString {
    var count = 0
    var index = 0
    val codePointLength = this@ellipsize.codePointCount(0, this@ellipsize.length)

    while (index < codePointLength) {
        val nextCodePoint = this@ellipsize.codePointAt(index)
        val nextByte = byteCount(nextCodePoint)
        if (count + nextByte > 124) {
            append("…")
            break
        }
        appendCodePoint(nextCodePoint)
        count += nextByte
        index++
    }

}

operator fun JsonElement.get(index: Int) =
    this.jsonArray[index]

operator fun JsonElement.get(tag: String) =
    this.jsonObject[tag]

fun JsonElement.getOrNull(tag: String) = kotlin.runCatching {
    this.jsonObject.getOrDefault(tag, null)
}.getOrNull()

val JsonElement.content
    get() = this.jsonPrimitive.contentOrNull

fun checkNotificationEnabled(context: Context) =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

fun showNotificationPermissionExplanationDialog(context: Context) {
    AlertDialog.Builder(context)
        .setTitle(R.string.warning)
        .setMessage(R.string.notification_denied)
        .setPositiveButton(android.R.string.ok) { _, _ -> }
        .show()
}

fun requestNotificationPermission(
    activity: Activity,
    requestPermissionLauncher: ActivityResultLauncher<String>,
    showRationale: Boolean = true,
    ifGranted: () -> Unit,
) {
    when {
        checkNotificationEnabled(activity) -> ifGranted()
        showRationale && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS) ->
            showNotificationPermissionExplanationDialog(activity)
        else ->
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}