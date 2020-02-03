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
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import java.io.File
import java.net.URL

fun getCachedGallery(context: Context, galleryID: Int): File {
    return File(getDownloadDirectory(context), galleryID.toString()).let {
        when {
            it.exists() -> it
            else -> File(context.cacheDir, "imageCache/$galleryID")
        }
    }
}

fun getDownloadDirectory(context: Context): File {
    val dlLocation = PreferenceManager.getDefaultSharedPreferences(context).getInt("dl_location", 0)

    return ContextCompat.getExternalFilesDirs(context, null)[dlLocation]
}

fun URL.download(to: File, onDownloadProgress: ((Long, Long) -> Unit)? = null) {
    to.outputStream().use { out ->

        with(openConnection()) {
            val fileSize = contentLength.toLong()

            getInputStream().use {

                var bytesCopied: Long = 0
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                var bytes = it.read(buffer)
                while (bytes >= 0) {
                    out.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    onDownloadProgress?.invoke(bytesCopied, fileSize)
                    bytes = it.read(buffer)
                }

            }
        }

    }
}

fun File.isParentOf(file: File?) = file?.absolutePath?.startsWith(this.absolutePath) ?: false