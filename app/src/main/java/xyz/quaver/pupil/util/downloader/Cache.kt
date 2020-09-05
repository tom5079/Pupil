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

package xyz.quaver.pupil.util.downloader

import android.content.Context
import android.content.ContextWrapper
import android.util.SparseArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import xyz.quaver.Code
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.Reader
import xyz.quaver.io.FileX
import xyz.quaver.io.util.getChild
import xyz.quaver.io.util.readBytes
import xyz.quaver.io.util.readText
import xyz.quaver.io.util.writeBytes
import xyz.quaver.pupil.client
import xyz.quaver.pupil.util.Preferences

@Serializable
data class Metadata(
    var galleryBlock: GalleryBlock? = null,
    var reader: Reader? = null,
    var imageList: MutableList<String?>? = null
) {
    fun copy(): Metadata = Metadata(galleryBlock, reader, imageList?.let { MutableList(it.size) { i -> it[i] } })
}

class Cache private constructor(context: Context, val galleryID: Int) : ContextWrapper(context) {

    companion object {
        val instances = SparseArray<Cache>()

        fun getInstance(context: Context, galleryID: Int) =
            instances[galleryID] ?: synchronized(this) {
                instances[galleryID] ?: Cache(context, galleryID).also { instances.put(galleryID, it) }
            }

        @Synchronized
        fun delete(galleryID: Int) {
            instances[galleryID]?.cacheFolder?.deleteRecursively()
            instances.delete(galleryID)
        }
    }

    init {
        cacheFolder.mkdirs()
    }

    var metadata = kotlin.runCatching {
        findFile(".metadata")?.readText()?.let {
            Json.decodeFromString<Metadata>(it)
        }
    }.getOrNull() ?: Metadata()

    val downloadFolder: FileX?
        get() = DownloadManager.getInstance(this).getDownloadFolder(galleryID)

    val cacheFolder: FileX
        get() = FileX(this, cacheDir, "imageCache/$galleryID").also {
            if (!it.exists())
                it.mkdirs()
        }

    fun findFile(fileName: String): FileX? =
         downloadFolder?.let { downloadFolder -> downloadFolder.getChild(fileName).let {
            if (it.exists()) it else null
        } } ?: cacheFolder.getChild(fileName).let {
            if (it.exists()) it else null
        }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun setMetadata(change: (Metadata) -> Unit) {
        change.invoke(metadata)

        val file = cacheFolder.getChild(".metadata")

        kotlin.runCatching {
            if (!file.exists()) {
                file.createNewFile()
            }
            file.writeText(Json.encodeToString(metadata))
        }
    }

    suspend fun getGalleryBlock(): GalleryBlock? {
        val sources = listOf(
            { xyz.quaver.hitomi.getGalleryBlock(galleryID) },
            { xyz.quaver.hiyobi.getGalleryBlock(galleryID) }
        )

        return metadata.galleryBlock
            ?: withContext(Dispatchers.IO) {
                var galleryBlock: GalleryBlock? = null

                for (source in sources) {
                    galleryBlock = try {
                        source.invoke()
                    } catch (e: Exception) { null }

                    if (galleryBlock != null)
                        break
                }

                galleryBlock?.also {
                    setMetadata { metadata -> metadata.galleryBlock = it }
                }
            }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun getThumbnail(): ByteArray? =
        findFile(".thumbnail")?.readBytes()
            ?: getGalleryBlock()?.thumbnails?.firstOrNull()?.let { withContext(Dispatchers.IO) {
                kotlin.runCatching {
                    val request = Request.Builder()
                        .url(it)
                        .build()

                    client.newCall(request).execute().body()?.use { it.bytes() }
                }.getOrNull()?.also { kotlin.run {
                    cacheFolder.getChild(".thumbnail").writeBytes(it)
                } }
            } }

    suspend fun getReader(): Reader? {
        val mirrors = Preferences.get<String>("mirrors").let { if (it.isEmpty()) emptyList() else it.split('>') }

        val sources = mapOf(
            Code.HITOMI to { xyz.quaver.hitomi.getReader(galleryID) },
            Code.HIYOBI to { xyz.quaver.hiyobi.getReader(galleryID) }
        ).let {
            if (mirrors.isNotEmpty())
                it.toSortedMap{ o1, o2 -> mirrors.indexOf(o1.name) - mirrors.indexOf(o2.name) }
            else
                it
        }

        return metadata.reader
            ?: withContext(Dispatchers.IO) {
                var reader: Reader? = null

                for (source in sources) {
                    reader = try {
                        source.value.invoke()
                    } catch (e: Exception) {
                        null
                    }

                   if (reader != null)
                       break
                }

                reader?.also {
                    setMetadata { metadata ->
                        metadata.reader = it

                        if (metadata.imageList == null)
                            metadata.imageList = MutableList(reader.galleryInfo.files.size) { null }
                    }
                }
            }
    }

    fun getImage(index: Int): FileX? =
        metadata.imageList?.get(index)?.let { findFile(it) }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun putImage(index: Int, fileName: String, data: ByteArray) {
        val file = cacheFolder.getChild(fileName)

        file.createNewFile()
        file.writeBytes(data)
        setMetadata { metadata -> metadata.imageList!![index] = fileName }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun moveToDownload() = CoroutineScope(Dispatchers.IO).launch {
        val downloadFolder = downloadFolder ?: return@launch

        metadata.imageList?.forEach { imageName ->
            imageName ?: return@forEach
            val target = downloadFolder.getChild(imageName)
            val source = cacheFolder.getChild(imageName)

            if (!source.exists())
                return@forEach

            kotlin.runCatching {
                target.createNewFile()
                source.readBytes()?.let { target.writeBytes(it) }
            }
        }

        val cacheMetadata = cacheFolder.getChild(".metadata")
        val downloadMetadata = downloadFolder.getChild(".metadata")

        if (cacheMetadata.exists()) {
            kotlin.runCatching {
                downloadMetadata.createNewFile()
                downloadMetadata.writeText(Json.encodeToString(metadata))
                cacheMetadata.delete()
            }
        }

        cacheFolder.delete()
    }
}