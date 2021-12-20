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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toAndroidRect
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
import xyz.quaver.pupil.sources.SourceEntries
import java.security.MessageDigest

operator fun JsonElement.get(index: Int) =
    this.jsonArray[index]

operator fun JsonElement.get(tag: String) =
    this.jsonObject[tag]

val JsonElement.content
    get() = this.jsonPrimitive.contentOrNull

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

class FileXImageSource(val file: FileX): ImageSource {
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

fun sha256(data: ByteArray) : ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(data)
}

val Context.activity: Activity?
    get() {
        var currentContext = this
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity)
                return currentContext
            currentContext = currentContext.baseContext
        }

        return null
    }