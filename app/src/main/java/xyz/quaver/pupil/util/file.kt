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
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Array
import java.net.URL

fun getCachedGallery(context: Context, galleryID: Int) =
    File(getDownloadDirectory(context), galleryID.toString()).let {
        if (it.exists())
            it
        else
            File(context.cacheDir, "imageCache/$galleryID")
    }

fun getDownloadDirectory(context: Context) =
    Preferences.get<String>("dl_location").let {
        if (it.isNotEmpty() && !it.startsWith("content"))
            File(it)
        else
            context.getExternalFilesDir(null)!!
    }

fun URL.download(to: File, onDownloadProgress: ((Long, Long) -> Unit)? = null) {

    if (to.parentFile?.exists() == false)
        to.parentFile!!.mkdirs()

    if (!to.exists())
        to.createNewFile()

    FileOutputStream(to).use { out ->

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

fun getExtSdCardPaths(context: Context) =
    ContextCompat.getExternalFilesDirs(context, null).drop(1).map {
        it.absolutePath.substringBeforeLast("/Android/data").let {  path ->
            runCatching {
                File(path).canonicalPath
            }.getOrElse {
                path
            }
        }
    }

const val PRIMARY_VOLUME_NAME = "primary"
fun getVolumePath(context: Context, volumeID: String?): String? {
    return runCatching {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumeClass = Class.forName("android.os.storage.StorageVolume")

        val getVolumeList = storageVolumeClass.javaClass.getMethod("getVolumeList")
        val getUUID = storageVolumeClass.getMethod("getUuid")
        val getPath = storageVolumeClass.getMethod("getPath")
        val isPrimary = storageVolumeClass.getMethod("isPrimary")

        val result = getVolumeList.invoke(storageManager)!!

        val length = Array.getLength(result)

        for (i in 0 until length) {
            val storageVolumeElement = Array.get(result, i)
            val uuid = getUUID.invoke(storageVolumeElement) as? String
            val primary = isPrimary.invoke(storageVolumeElement) as? Boolean

            // primary volume?
            if (primary == true && volumeID == PRIMARY_VOLUME_NAME)
                return@runCatching getPath.invoke(storageVolumeElement) as? String

            // other volumes?
            if (volumeID == uuid) {
                return@runCatching getPath.invoke(storageVolumeElement) as? String
            }
        }
        return@runCatching null
    }.getOrNull()
}

fun File.isParentOf(another: File) =
    another.absolutePath.startsWith(this.absolutePath)