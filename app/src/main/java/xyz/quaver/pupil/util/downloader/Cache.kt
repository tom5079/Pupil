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
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import xyz.quaver.io.FileX
import xyz.quaver.io.util.*
import xyz.quaver.pupil.client
import xyz.quaver.pupil.hitomi.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class OldGalleryInfo(
    val language_localname: String? = null,
    val language: String? = null,
    val date: String? = null,
    val files: List<OldGalleryFiles>,
    val id: Int? = null,
    val type: String? = null,
    val title: String? = null
)

@Serializable
data class OldGalleryFiles(
    val width: Int,
    val hash: String,
    val haswebp: Int = 0,
    val name: String,
    val height: Int,
    val hasavif: Int = 0,
    val hasavifsmalltn: Int? = 0
)

@Serializable
data class OldMetadata(
    var galleryBlock: GalleryBlock? = null,
    var reader: OldGalleryInfo? = null,
    var imageList: MutableList<String?>? = null
) {
    fun copy(): OldMetadata = OldMetadata(galleryBlock, reader, imageList?.let { MutableList(it.size) { i -> it[i] } })
}

@Serializable
data class Metadata(
    var galleryBlock: GalleryBlock? = null,
    var galleryInfo: GalleryInfo? = null,
    var imageList: MutableList<String?>? = null
) {
    constructor(old: OldMetadata) : this(old.galleryBlock, getGalleryInfo(old.galleryBlock?.id ?: throw Exception()), old.imageList)
    fun copy(): Metadata = Metadata(galleryBlock, galleryInfo, imageList?.let { MutableList(it.size) { i -> it[i] } })
}

class Cache private constructor(context: Context, val galleryID: Int) : ContextWrapper(context) {

    companion object {
        val instances = ConcurrentHashMap<Int, Cache>()

        fun getInstance(context: Context, galleryID: Int) =
            instances[galleryID] ?: synchronized(this) {
                instances[galleryID] ?: Cache(context, galleryID).also { instances.put(galleryID, it) }
            }

        @Synchronized
        fun delete(context: Context, galleryID: Int) {
            File(context.cacheDir, "imageCache/$galleryID").deleteRecursively()
            instances.remove(galleryID)
        }
    }

    init {
        cacheFolder.mkdirs()
    }

    var metadata = kotlin.runCatching {
        findFile(".metadata")?.readText()?.let { metadata ->
            kotlin.runCatching {
                json.decodeFromString<Metadata>(metadata)
            }.getOrElse {
                Metadata(json.decodeFromString<OldMetadata>(metadata))
            }
        }
    }.onFailure { it.printStackTrace() }.getOrNull() ?: Metadata()

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
        return metadata.galleryBlock
            ?: withContext(Dispatchers.IO) {
                try {
                    getGalleryBlock(galleryID).also {
                        setMetadata { metadata -> metadata.galleryBlock = it }
                    }
                } catch (e: Exception) { return@withContext null }
            }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun getThumbnail(): Uri =
        findFile(".thumbnail")?.uri
            ?: getGalleryBlock()?.thumbnails?.firstOrNull()?.let { withContext(Dispatchers.IO) {
                kotlin.runCatching {
                    val request = Request.Builder()
                        .url(it)
                        .header("Referer", "https://hitomi.la/")
                        .build()

                    client.newCall(request).execute().also { if (it.code() != 200) throw IOException() }.body()?.use { it.bytes() }
                }.getOrNull()?.let { thumbnail -> kotlin.runCatching {
                    cacheFolder.getChild(".thumbnail").also {
                        if (!it.exists())
                            it.createNewFile()

                        it.writeBytes(thumbnail)
                    }
                }.getOrNull()?.uri }
            } } ?: Uri.EMPTY

    suspend fun getGalleryInfo(): GalleryInfo? {

        return metadata.galleryInfo
            ?: withContext(Dispatchers.IO) {
                try {
                    getGalleryInfo(galleryID).also {
                        setMetadata { metadata ->
                            metadata.galleryInfo = it

                            if (metadata.imageList == null)
                                metadata.imageList = MutableList(it.files.size) { null }
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            }
    }

    fun getImage(index: Int): FileX? =
        metadata.imageList?.getOrNull(index)?.let { findFile(it) }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun putImage(index: Int, fileName: String, data: ByteArray) = coroutineScope {
        val file = cacheFolder.getChild(fileName)

        if (!file.exists())
            file.createNewFile()

        file.writeBytes(data)
        setMetadata { metadata -> metadata.imageList!![index] = fileName }
    }

    private val lock = ConcurrentHashMap<Int, Mutex>()
    @Suppress("BlockingMethodInNonBlockingContext")
    fun moveToDownload() = CoroutineScope(Dispatchers.IO).launch {
        val downloadFolder = downloadFolder ?: return@launch

        if (lock[galleryID]?.isLocked == true)
            return@launch

        (lock[galleryID] ?: Mutex().also { lock[galleryID] = it }).withLock {
            val cacheMetadata = cacheFolder.getChild(".metadata")
            val downloadMetadata = downloadFolder.getChild(".metadata")

            if (!cacheMetadata.exists())
                return@launch

            if (cacheMetadata.exists()) {
                kotlin.runCatching {
                    if (!downloadMetadata.exists())
                        downloadMetadata.createNewFile()

                    downloadMetadata.writeText(Json.encodeToString(metadata))
                }
            }

            val cacheThumbnail = cacheFolder.getChild(".thumbnail")
            val downloadThumbnail = downloadFolder.getChild(".thumbnail")

            if (cacheThumbnail.exists()) {
                kotlin.runCatching {
                    if (!downloadThumbnail.exists())
                        downloadThumbnail.createNewFile()

                    downloadThumbnail.outputStream()?.use { target -> target.channel.truncate(0L); cacheThumbnail.inputStream()?.use { source ->
                        source.copyTo(target)
                    } }
                    cacheThumbnail.delete()
                }
            }

            metadata.imageList?.forEach { imageName ->
                imageName ?: return@forEach
                val target = downloadFolder.getChild(imageName)
                val source = cacheFolder.getChild(imageName)

                if (!source.exists())
                    return@forEach

                kotlin.runCatching {
                    if (!target.exists())
                        target.createNewFile()

                    target.outputStream()?.use { target -> target.channel.truncate(0L); source.inputStream()?.use { source ->
                        source.copyTo(target)
                    } }
                }
            }

            cacheFolder.deleteRecursively()
        }
    }
}