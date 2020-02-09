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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.parse
import kotlinx.serialization.stringify
import xyz.quaver.Code
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

        val source = mapOf(
            Code.HITOMI to { xyz.quaver.hitomi.getGalleryBlock(galleryID) },
            Code.HIYOBI to { xyz.quaver.hiyobi.getGalleryBlock(galleryID) }
        )

        val galleryBlock = if (metadata?.galleryBlock == null)
           source.entries.map {
               CoroutineScope(Dispatchers.IO).async {
                   kotlin.runCatching {
                       it.value.invoke()
                   }.getOrNull()
               }
           }.firstOrNull {
               it.await() != null
           }?.await()
        else
            metadata.galleryBlock

        setCachedMetadata(
            galleryID,
            Metadata(Cache(this).getCachedMetadata(galleryID), galleryBlock = galleryBlock)
        )

        return galleryBlock
    }

    fun getReaderOrNull(galleryID: Int): Reader? {
        return getCachedMetadata(galleryID)?.reader
    }

    suspend fun getReader(galleryID: Int): Reader? {
        val metadata = getCachedMetadata(galleryID)
        val mirrors = preference.getString("mirrors", null)?.split('>') ?: listOf()

        val sources = mapOf(
            Code.HITOMI to { xyz.quaver.hitomi.getReader(galleryID) },
            Code.HIYOBI to { xyz.quaver.hiyobi.getReader(galleryID) }
        ).let {
            if (mirrors.isNotEmpty())
                it.toSortedMap(
                    Comparator { o1, o2 ->
                        mirrors.indexOf(o1.name) - mirrors.indexOf(o2.name)
                    }
                )
            else
                it
        }

        val reader = if (metadata?.reader == null) {
            CoroutineScope(Dispatchers.IO).async {
                var retval: Reader? = null

                for (source in sources) {
                    retval = kotlin.runCatching {
                        source.value.invoke()
                    }.getOrNull()

                    if (retval != null)
                        break
                }

                retval
            }.await()
        } else
            metadata.reader

        if (reader != null)
            setCachedMetadata(
                galleryID,
                Metadata(Cache(this).getCachedMetadata(galleryID), readers = reader)
            )

        return reader
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