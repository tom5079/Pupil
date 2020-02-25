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

import android.annotation.TargetApi
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
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
    PreferenceManager.getDefaultSharedPreferences(context).getString("dl_location", null).let {
        if (it != null && !it.startsWith("content"))
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

// Credits go to https://stackoverflow.com/questions/34927748/android-5-0-documentfile-from-tree-uri/36162691#36162691
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
fun getVolumeIdFromTreeUri(uri: Uri) =
    DocumentsContract.getTreeDocumentId(uri).split(':').let {
        if (it.isNotEmpty())
            it[0]
        else
            null
    }

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
fun getDocumentPathFromTreeUri(uri: Uri) =
    DocumentsContract.getTreeDocumentId(uri).split(':').let {
        if (it.size >= 2)
            it[1]
        else
            File.separator
    }

fun getFullPathFromTreeUri(context: Context, uri: Uri) : String? {
    val volumePath = getVolumePath(context, getVolumeIdFromTreeUri(uri) ?: return null).let {
        it ?: return File.separator

        if (it.endsWith(File.separator))
            it.dropLast(1)
        else
            it
    }

    val documentPath = getDocumentPathFromTreeUri(uri).let {
        if (it.endsWith(File.separator))
            it.dropLast(1)
        else
            it
    }

    return if (documentPath.isNotEmpty()) {
        if (documentPath.startsWith(File.separator))
            volumePath + documentPath
        else
            volumePath + File.separator + documentPath
    } else
        volumePath
}

// Huge thanks to avluis(https://github.com/avluis)
// This code is originated from Hentoid(https://github.com/avluis/Hentoid) under Apache-2.0 license.
fun Uri.toFile(context: Context): File? {
    val path = this.path ?: return null

    val pathSeparator = path.indexOf(':')
    val folderName = path.substring(pathSeparator+1)

    // Determine whether the designated file is
    // - on a removable media (e.g. SD card, OTG)
    // or
    // - on the internal phone memory
    val removableMediaFolderRoots = getExtSdCardPaths(context)

    /* First test is to compare root names with known roots of removable media
     * In many cases, the SD card root name is shared between pre-SAF (File) and SAF (DocumentFile) frameworks
     * (e.g. /storage/3437-3934 vs. /tree/3437-3934)
     * This is what the following block is trying to do
     */
    for (s in removableMediaFolderRoots) {
        val sRoot = s.substring(s.lastIndexOf(File.separatorChar))
        val root = path.substring(0, pathSeparator).let {
            it.substring(it.lastIndexOf(File.separatorChar))
        }

        if (sRoot.equals(root, true)) {
            return File(s + File.separatorChar + folderName)
        }
    }
    /* In some other cases, there is no common name (e.g. /storage/sdcard1 vs. /tree/3437-3934)
     * We can use a slower method to translate the Uri obtained with SAF into a pre-SAF path
     * and compare it to the known removable media volume names
     */
    val root = getFullPathFromTreeUri(context, this)

    for (s in removableMediaFolderRoots) {
        if (root?.startsWith(s) == true) {
            return File(root)
        }
    }

    return File(context.getExternalFilesDir(null)?.canonicalPath?.substringBeforeLast("/Android/data") ?: return null, folderName)
}

fun File.isParentOf(another: File) =
    another.absolutePath.startsWith(this.absolutePath)