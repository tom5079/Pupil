/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
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

package xyz.quaver.pupil.util.download

import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.parse
import kotlinx.serialization.stringify
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.Reader
import xyz.quaver.pupil.util.*
import java.io.File
import java.net.URL

class Cache(context: Context) : ContextWrapper(context) {

    private val preference = PreferenceManager.getDefaultSharedPreferences(this)

    // Search in this order
    // Download -> Cache
    fun getCachedGallery(galleryID: Int) : DocumentFile? {
        var file = getDownloadDirectory(this)?.findFile(galleryID.toString())

        if (file?.exists() == true)
            return file

        file = DocumentFile.fromFile(File(cacheDir, "imageCache/$galleryID"))

        return if (file.exists())
             file
        else
             null
    }

    @UseExperimental(ImplicitReflectionSerializer::class)
    fun getCachedMetadata(galleryID: Int) : Metadata? {
        val file = (getCachedGallery(galleryID) ?: return null).findFile(".metadata")

        if (file?.exists() != true)
            return null

        return try {
            Json.parse(file.readText(this))
        } catch (e: Exception) {
            //File corrupted
            file.delete()
            null
        }
    }

    @UseExperimental(ImplicitReflectionSerializer::class)
    fun setCachedMetadata(galleryID: Int, metadata: Metadata) {
        val file = getCachedGallery(galleryID)?.findFile(".metadata") ?:
        DocumentFile.fromFile(File(cacheDir, "imageCache/$galleryID").also {
            if (!it.exists())
                it.mkdirs()
        }).createFile("null", ".metadata") ?: return

        file.writeText(this, Json.stringify(metadata))
    }

    suspend fun getThumbnail(galleryID: Int): String? {
        val metadata = Cache(this).getCachedMetadata(galleryID)

        val thumbnail = if (metadata?.thumbnail == null)
            withContext(Dispatchers.IO) {
                val thumbnails = getGalleryBlock(galleryID)?.thumbnails
                try {
                    Base64.encodeToString(URL(thumbnails?.firstOrNull()).readBytes(), Base64.DEFAULT)
                } catch (e: Exception) {
                    null
                }
            }
        else
            metadata.thumbnail

        setCachedMetadata(
            galleryID,
            Metadata(Cache(this).getCachedMetadata(galleryID), thumbnail = thumbnail)
        )

        return thumbnail
    }

    suspend fun getGalleryBlock(galleryID: Int): GalleryBlock? {
        val metadata = Cache(this).getCachedMetadata(galleryID)

        val galleryBlock = if (metadata?.galleryBlock == null)
           listOf(
               { xyz.quaver.hitomi.getGalleryBlock(galleryID) },
               { xyz.quaver.hiyobi.getGalleryBlock(galleryID) }
           ).map {
               CoroutineScope(Dispatchers.IO).async {
                   kotlin.runCatching {
                       it.invoke()
                   }.getOrNull()
               }
           }.awaitAll().filterNotNull()
        else
            metadata.galleryBlock

        setCachedMetadata(
            galleryID,
            Metadata(Cache(this).getCachedMetadata(galleryID), galleryBlock = galleryBlock)
        )

        val mirrors = preference.getString("mirrors", "")!!.split('>')

        return galleryBlock.firstOrNull {
            mirrors.contains(it.code.name)
        } ?: galleryBlock.firstOrNull()
    }

    fun getReaderOrNull(galleryID: Int): Reader? {
        val metadata = getCachedMetadata(galleryID)

        val mirrors = preference.getString("mirrors", "")!!.split('>')

        return metadata?.readers?.firstOrNull {
            mirrors.contains(it.code.name)
        } ?: metadata?.readers?.firstOrNull()
    }

    suspend fun getReader(galleryID: Int): Reader? {
        val metadata = getCachedMetadata(galleryID)

        val readers = if (metadata?.readers == null) {
             listOf(
                { xyz.quaver.hitomi.getReader(galleryID) },
                { xyz.quaver.hiyobi.getReader(galleryID) }
            ).map {
                CoroutineScope(Dispatchers.IO).async {
                    kotlin.runCatching {
                        it.invoke()
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        } else {
            metadata.readers
        }

        if (readers.isNotEmpty())
            setCachedMetadata(
                galleryID,
                Metadata(Cache(this).getCachedMetadata(galleryID), readers = readers)
            )

        val mirrors = preference.getString("mirrors", "")!!.split('>')

        return readers.firstOrNull {
            mirrors.contains(it.code.name)
        } ?: readers.firstOrNull()
    }

    fun getImages(galleryID: Int): List<DocumentFile?>? {
        val gallery = getCachedGallery(galleryID) ?: return null
        val reader = getReaderOrNull(galleryID) ?: return null
        val images = gallery.listFiles()

        return reader.galleryInfo.indices.map { index ->
            images.firstOrNull { file -> file.name?.startsWith(index.toString()) == true }
        }
    }

    fun putImage(galleryID: Int, name: String, data: ByteArray) {
        val cache = getCachedGallery(galleryID) ?:
        DocumentFile.fromFile(File(cacheDir, "imageCache/$galleryID").also {
            if (!it.exists())
                it.mkdirs()
        }) ?: return

        if (!Regex("""^[0-9]+.+$""").matches(name))
            throw IllegalArgumentException("File name is not a number")

        cache.createFile("null", name)?.writeBytes(this, data)
    }

    fun moveToDownload(galleryID: Int) {
        val cache = getCachedGallery(galleryID)

        if (cache != null) {
            val download = getDownloadDirectory(this)!!

            if (!download.isParentOf(cache)) {
                cache.copyRecursively(this, download)
                cache.deleteRecursively()
            }
        } else
            getDownloadDirectory(this)?.createDirectory(galleryID.toString())
    }

    fun isDownloading(galleryID: Int) = getCachedMetadata(galleryID)?.isDownloading == true

    fun setDownloading(galleryID: Int, isDownloading: Boolean) {
        setCachedMetadata(galleryID, Metadata(getCachedMetadata(galleryID), isDownloading = isDownloading))
    }

}