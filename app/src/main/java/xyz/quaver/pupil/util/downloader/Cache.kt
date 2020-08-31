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
import android.util.Base64
import android.util.SparseArray
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import xyz.quaver.Code
import xyz.quaver.hitomi.Gallery
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.Reader
import xyz.quaver.hitomi.getGallery
import xyz.quaver.io.FileX
import xyz.quaver.io.util.getChild
import xyz.quaver.io.util.readBytes
import xyz.quaver.io.util.readText
import xyz.quaver.io.util.writeBytes
import xyz.quaver.pupil.client
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.formatDownloadFolder

@Serializable
data class Metadata(
    var galleryBlock: GalleryBlock? = null,
    var gallery: Gallery? = null,
    var thumbnail: String? = null,
    var reader: Reader? = null,
    var imageList: MutableList<String?>? = null
) {
    fun copy(): Metadata = Metadata(galleryBlock, gallery, thumbnail, reader, imageList?.let { MutableList(it.size) { i -> it[i] } })
}

class Cache private constructor(context: Context, val galleryID: Int) : ContextWrapper(context) {

    companion object {
        private val instances = SparseArray<Cache>()

        fun getInstance(context: Context, galleryID: Int) =
            instances[galleryID] ?: synchronized(this) {
                instances[galleryID] ?: Cache(context, galleryID).also { instances.put(galleryID, it) }
            }
    }

    var metadata = kotlin.runCatching {
        findFile(".metadata")?.readText()?.let {
            Json.decodeFromString<Metadata>(it)
        }
    }.getOrNull() ?: Metadata()

    val downloadFolder: FileX?
        get() = DownloadFolderManager.getInstance(this).getDownloadFolder(galleryID)

    val cacheFolder: FileX
        get() = FileX(this, cacheDir, "imageCache/$galleryID")

    val cachedGallery: FileX
        get() = DownloadFolderManager.getInstance(this).getDownloadFolder(galleryID)
            ?: FileX(this, cacheDir, "imageCache/$galleryID")

    fun findFile(fileName: String): FileX? =
        cacheFolder.getChild(fileName).let {
            if (it.exists()) it else null
        } ?: downloadFolder?.let { downloadFolder -> downloadFolder.getChild(fileName).let {
            if (it.exists()) it else null
        } }

    @Synchronized
    fun setMetadata(change: (Metadata) -> Unit) {
        change.invoke(metadata)

        val file = cachedGallery.getChild(".metadata")

        CoroutineScope(Dispatchers.IO).launch {
            file.writeText(Json.encodeToString(Metadata))
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
                    launch { setMetadata { metadata -> metadata.galleryBlock = it } }
                }
            }
    }

    suspend fun getGallery(): Gallery? =
        metadata.gallery
            ?: withContext(Dispatchers.IO) {
                kotlin.runCatching {
                    getGallery(galleryID)
                }.getOrNull()?.also {
                    launch { setMetadata { metadata ->
                        metadata.gallery = it

                        if (metadata.imageList == null)
                            metadata.imageList = MutableList(it.thumbnails.size) { null }
                    } }
                }
            }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun getThumbnail(): String? =
        metadata.thumbnail
            ?: withContext(Dispatchers.IO) {
                getGalleryBlock()?.thumbnails?.firstOrNull()?.let { thumbnail ->
                    kotlin.runCatching {
                        val request = Request.Builder()
                            .url(thumbnail)
                            .build()

                        val image = client.newCall(request).execute().body()?.use { it.bytes() }

                        Base64.encodeToString(image, Base64.DEFAULT)
                    }.getOrNull()
                }?.also {
                    launch { setMetadata { metadata -> metadata.thumbnail = it } }
                }
            }

    suspend fun getReader(galleryID: Int): Reader? {
        val mirrors = Preferences.get<String>("mirrors").split('>')

        val sources = mapOf(
            Code.HITOMI to { xyz.quaver.hitomi.getReader(galleryID) },
            Code.HIYOBI to { xyz.quaver.hiyobi.getReader(galleryID) }
        ).toSortedMap { o1, o2 -> mirrors.indexOf(o1.name) - mirrors.indexOf(o2.name) }

        return metadata.reader
            ?: withContext(Dispatchers.IO) {
                var reader: Reader? = null

                for (source in sources) {
                    reader = try { withTimeoutOrNull(1000) {
                        source.value.invoke()
                    } } catch (e: Exception) { null }

                    if (reader != null)
                        break
                }

                reader?.also {
                    launch { setMetadata { metadata ->
                        metadata.reader = it

                        if (metadata.imageList == null)
                            metadata.imageList = MutableList(reader.galleryInfo.files.size) { null }
                    } }
                }
            }
    }

    fun getImage(index: Int): FileX? =
        metadata.imageList?.get(index)?.let { findFile(it) }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun putImage(index: Int, fileName: String, data: ByteArray) = CoroutineScope(Dispatchers.IO).launch {
        val file = FileX(this@Cache, cachedGallery, fileName).also {
            it.createNewFile()
        }

        file.writeBytes(data)

        setMetadata { metadata -> metadata.imageList!![index] = fileName }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun moveToDownload() = CoroutineScope(Dispatchers.IO).launch {
        if (downloadFolder == null)
            DownloadFolderManager.getInstance(this@Cache).addDownloadFolder(galleryID, this@Cache.formatDownloadFolder())

        metadata.imageList?.forEach {
            it ?: return@forEach

            val target = downloadFolder!!.getChild(it)
            val source = cacheFolder.getChild(it)

            if (!source.exists())
                return@forEach

            kotlin.runCatching {
                target.createNewFile()
                source.readBytes()?.let { target.writeBytes(it) }
            }
        }

        val cacheMetadata = cacheFolder.getChild(".metadata")
        val downloadMetadata = downloadFolder!!.getChild(".metadata")

        if (cacheMetadata.exists()) {
            kotlin.runCatching {
                downloadMetadata.createNewFile()
                cacheMetadata.readBytes()?.let { downloadMetadata.writeBytes(it) }
                cacheMetadata.delete()
            }
        }

        cacheFolder.delete()
    }
}