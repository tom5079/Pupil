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
import android.util.Log
import android.util.SparseArray
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import xyz.quaver.Code
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.Reader
import xyz.quaver.io.FileX
import xyz.quaver.io.util.*
import xyz.quaver.pupil.client
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.formatDownloadFolder
import kotlin.io.deleteRecursively
import kotlin.io.writeText

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
        private val mutex = Mutex()
        private val instances = SparseArray<Cache>()

        fun getInstance(context: Context, galleryID: Int) =
            instances[galleryID] ?: runBlocking { mutex.withLock {
                instances[galleryID] ?: Cache(context, galleryID).also { instances.put(galleryID, it) }
            } }

        fun delete(galleryID: Int) { runBlocking { mutex.withLock {
            instances[galleryID]?.galleryFolder?.deleteRecursively()
            instances.delete(galleryID)
        } } }
    }

    init {
        galleryFolder.mkdirs()
    }

    private val mutex = Mutex()
    var metadata = kotlin.runCatching {
        findFile(".metadata")?.readText()?.let {
            Json.decodeFromString<Metadata>(it)
        }
    }.getOrNull() ?: Metadata()

    val downloadFolder: FileX?
        get() = DownloadFolderManager.getInstance(this).getDownloadFolder(galleryID)

    val cacheFolder: FileX
        get() = FileX(this, cacheDir, "imageCache/$galleryID")

    val galleryFolder: FileX
        get() = DownloadFolderManager.getInstance(this).getDownloadFolder(galleryID)
            ?: FileX(this, cacheDir, "imageCache/$galleryID")

    fun findFile(fileName: String): FileX? =
        cacheFolder.getChild(fileName).let {
            if (it.exists()) it else null
        } ?: downloadFolder?.let { downloadFolder -> downloadFolder.getChild(fileName).let {
            if (it.exists()) it else null
        } }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun setMetadata(change: (Metadata) -> Unit) { mutex.withLock {
        change.invoke(metadata)

        val file = galleryFolder.getChild(".metadata")

        CoroutineScope(Dispatchers.IO).launch {
            kotlin.runCatching {
                file.createNewFile()
                file.writeText(Json.encodeToString(metadata))
            }
        }
    } }

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

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun getThumbnail(): ByteArray? =
        findFile(".thumbnail")?.readBytes()
            ?: getGalleryBlock()?.thumbnails?.firstOrNull()?.let { withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(it)
                    .build()

                kotlin.runCatching {
                    client.newCall(request).execute().body()?.use { it.bytes() }
                }.getOrNull()?.also { kotlin.run {
                    galleryFolder.getChild(".thumbnail").writeBytes(it)
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
    suspend fun putImage(index: Int, fileName: String, data: ByteArray) {
        val file = galleryFolder.getChild(fileName)

        file.createNewFile()
        file.writeBytes(data)
        setMetadata { metadata -> metadata.imageList!![index] = fileName }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun moveToDownload() = CoroutineScope(Dispatchers.IO).launch {
        if (downloadFolder == null)
            DownloadFolderManager.getInstance(this@Cache).addDownloadFolder(galleryID, this@Cache.formatDownloadFolder())

        metadata.imageList?.forEach { imageName ->
            imageName ?: return@forEach

            Log.i("PUPIL", downloadFolder?.uri.toString())
            val target = downloadFolder!!.getChild(imageName)
            val source = cacheFolder.getChild(imageName)

            if (!source.exists())
                return@forEach

            kotlin.runCatching {
                target.createNewFile()
                source.readBytes()?.let { target.writeBytes(it) }
            }
        }

        Log.i("PUPIL", downloadFolder?.uri.toString())
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