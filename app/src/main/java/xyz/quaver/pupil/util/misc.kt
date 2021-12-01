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
import android.content.Context
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toAndroidRect
import androidx.lifecycle.MutableLiveData
import kotlinx.serialization.json.*
import org.kodein.di.DIAware
import org.kodein.di.DirectDIAware
import org.kodein.di.direct
import org.kodein.di.instance
import xyz.quaver.graphics.subsampledimage.ImageSource
import xyz.quaver.graphics.subsampledimage.newBitmapRegionDecoder
import xyz.quaver.io.FileX
import xyz.quaver.io.util.inputStream
import xyz.quaver.pupil.db.AppDatabase
import xyz.quaver.pupil.sources.ItemInfo
import xyz.quaver.pupil.sources.SourceEntries
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList

@OptIn(ExperimentalStdlibApi::class)
fun String.wordCapitalize() : String {
    val result = ArrayList<String>()

    @SuppressLint("DefaultLocale")
    for (word in this.split(" "))
        result.add(word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })

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

val formatMap = mapOf<String, ItemInfo.() -> (String)>(
    "-id-" to { itemID },
    "-title-" to { title },
    // TODO
)
/**
 * Formats download folder name with given Metadata
 */
fun ItemInfo.formatDownloadFolder(format: String = Preferences["download_folder_name", "[-id-] -title-"]): String =
    format.let {
        formatMap.entries.fold(it) { str, (k, v) ->
            str.replace(k, v.invoke(this), true)
        }
    }.replace(Regex("""[*\\|"?><:/]"""), "").ellipsize(127)

fun String.ellipsize(n: Int): String =
    if (this.length > n)
        this.slice(0 until n) + "…"
    else
        this

operator fun JsonElement.get(index: Int) =
    this.jsonArray[index]

operator fun JsonElement.get(tag: String) =
    this.jsonObject[tag]

val JsonElement.content
    get() = this.jsonPrimitive.contentOrNull

fun List<MenuItem>.findMenu(itemID: Int): MenuItem? {
    return firstOrNull { it.itemId == itemID }
}

fun <E> MutableLiveData<MutableList<E>>.notify() {
    this.value = this.value
}

fun InputStream.copyTo(out: OutputStream, onCopy: (totalBytesCopied: Long, bytesJustCopied: Int) -> Unit): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var bytes = read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        onCopy(bytesCopied, bytes)
        bytes = read(buffer)
    }
    return bytesCopied
}

fun DIAware.source(source: String) = lazy { direct.source(source) }
fun DirectDIAware.source(source: String) = instance<SourceEntries>().toMap()[source]!!

fun DIAware.database() = lazy { direct.database() }
fun DirectDIAware.database() = instance<AppDatabase>()

fun View.hide() {
    visibility = View.INVISIBLE
}

fun View.show() {
    visibility = View.VISIBLE
}

class FileXImageSource(file: FileX): ImageSource {
    private val decoder = newBitmapRegionDecoder(file.inputStream()!!)

    override val imageSize by lazy { Size(decoder.width.toFloat(), decoder.height.toFloat()) }

    override fun decodeRegion(region: Rect, sampleSize: Int): ImageBitmap =
        decoder.decodeRegion(region.toAndroidRect(), BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }).asImageBitmap()
}

@Composable
fun rememberFileXImageSource(file: FileX) = remember {
    FileXImageSource(file)
}