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
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import java.io.File
import java.net.URL
import java.nio.charset.Charset
import java.util.*

fun getCachedGallery(context: Context, galleryID: Int) =
    getDownloadDirectory(context).findFile(galleryID.toString()) ?:
    DocumentFile.fromFile(File(context.cacheDir, "imageCache/$galleryID"))

fun getDownloadDirectory(context: Context) : DocumentFile {
    val uri = PreferenceManager.getDefaultSharedPreferences(context).getString("dl_location", null).let {
        if (it != null)
            Uri.parse(it)
        else
            Uri.fromFile(context.getExternalFilesDir(null))
    }

    return if (uri.toString().startsWith("file"))
        DocumentFile.fromFile(File(uri.path!!))
    else
        DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromFile(context.getExternalFilesDir(null)!!)
}

fun convertUpdateUri(context: Context, uri: Uri) : Uri =
    if (uri.toString().startsWith("file"))
        FileProvider.getUriForFile(context, context.applicationContext.packageName + ".provider", File(uri.path!!.substringAfter("file:///")))
    else
        uri

fun URL.download(context: Context, to: DocumentFile, onDownloadProgress: ((Long, Long) -> Unit)? = null) {
    context.contentResolver.openOutputStream(to.uri).use { out ->
        out!!

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

fun DocumentFile.isParentOf(file: DocumentFile?) : Boolean {
    var parent = file?.parentFile
    while (parent != null) {
        if (this.uri.path == parent.uri.path)
            return true

        parent = parent.parentFile
    }

    return false
}

fun DocumentFile.reader(context: Context, charset: Charset = Charsets.UTF_8) = context.contentResolver.openInputStream(uri)!!.reader(charset)
fun DocumentFile.readBytes(context: Context) = context.contentResolver.openInputStream(uri)!!.readBytes()
fun DocumentFile.readText(context: Context, charset: Charset = Charsets.UTF_8) = reader(context, charset).use { it.readText() }

fun DocumentFile.writeBytes(context: Context, array: ByteArray) = context.contentResolver.openOutputStream(uri)!!.write(array)
fun DocumentFile.writeText(context: Context, text: String, charset: Charset = Charsets.UTF_8) = writeBytes(context, text.toByteArray(charset))

fun DocumentFile.copyRecursively(
    context: Context,
    target: DocumentFile
) {
    if (!exists())
        throw Exception("The source file doesn't exist.")

    if (this.isFile)
        target.createFile("null", name!!)!!.writeBytes(
            context,
            readBytes(context)
        )
    else if (this.isDirectory) {
        target.createDirectory(name!!).also { newTarget ->
            listFiles().forEach { child ->
                child.copyRecursively(context, newTarget!!)
            }
        }
    }
}

fun DocumentFile.deleteRecursively() {

    if (this.isDirectory)
        listFiles().forEach {
            it.deleteRecursively()
        }

    this.delete()
}

fun DocumentFile.walk(state: LinkedList<DocumentFile> = LinkedList()) : Queue<DocumentFile> {
    if (state.isEmpty())
        state.push(this)

    listFiles().forEach {
        state.push(it)

        if (it.isDirectory) {
            it.walk(state)
        }
    }

    return state
}

fun File.copyTo(context: Context, target: DocumentFile) = target.writeBytes(context, this.readBytes())