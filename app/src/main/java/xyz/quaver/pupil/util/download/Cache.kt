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
import android.util.Log
import android.util.SparseArray
import androidx.preference.PreferenceManager
import com.crashlytics.android.Crashlytics
import kotlinx.coroutines.*
import xyz.quaver.Code
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.Reader
import xyz.quaver.proxy
import xyz.quaver.pupil.util.getCachedGallery
import xyz.quaver.pupil.util.getDownloadDirectory
import xyz.quaver.pupil.util.isParentOf
import xyz.quaver.pupil.util.json
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class Cache(context: Context) : ContextWrapper(context) {

    companion object {
        private val moving = mutableListOf<Int>()
    }

    private val locks = SparseArray<Lock>()
    private fun lock(galleryID: Int) {
        synchronized(locks) {
            if (locks.indexOfKey(galleryID) < 0)
                locks.put(galleryID, ReentrantLock())
        }

        locks[galleryID].lock()
    }

    private fun unlock(galleryID: Int) {
        locks[galleryID]?.unlock()
    }

    private val preference = PreferenceManager.getDefaultSharedPreferences(this)

    // Search in this order
    // Download -> Cache
    fun getCachedGallery(galleryID: Int) = getCachedGallery(this, galleryID).also {
        if (!it.exists())
            it.mkdirs()
    }

    fun getCachedMetadata(galleryID: Int) : Metadata? {
        val file = File(getCachedGallery(galleryID), ".metadata")

        if (!file.exists())
            return null

        return try {
            json.parse(Metadata.serializer(), file.readText())
        } catch (e: Exception) {
            //File corrupted
            file.delete()
            null
        }
    }

    fun setCachedMetadata(galleryID: Int, metadata: Metadata) {
        val file = File(getCachedGallery(galleryID), ".metadata").also {
            if (!it.exists())
                it.createNewFile()
        }

        file.writeText(json.stringify(Metadata.serializer(), metadata))
    }

    suspend fun getThumbnail(galleryID: Int): String? {
        val metadata = Cache(this).getCachedMetadata(galleryID)

        val thumbnail = if (metadata?.thumbnail == null)
            withContext(Dispatchers.IO) {
                val thumbnails = getGalleryBlock(galleryID)?.thumbnails
                try {
                    Base64.encodeToString(URL(thumbnails?.firstOrNull()).openConnection(proxy).getInputStream().use {
                        it.readBytes()
                    }, Base64.DEFAULT)
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

        val sources = listOf(
            { xyz.quaver.hitomi.getGalleryBlock(galleryID) },
            { xyz.quaver.hiyobi.getGalleryBlock(galleryID) }
        )

        val galleryBlock = if (metadata?.galleryBlock == null) {
            CoroutineScope(Dispatchers.IO).async {
                var galleryBlock: GalleryBlock? = null

                for (source in sources) {
                    galleryBlock = try {
                        source.invoke()
                    } catch (e: Exception) {
                        null
                    }

                    if (galleryBlock != null)
                        break
                }

                galleryBlock
            }.await() ?: return null
        }
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
                    retval = try {
                        source.value.invoke()
                    } catch (e: Exception) {
                        Crashlytics.logException(e)
                        null
                    }

                    if (retval != null)
                        break
                }

                retval
            }.await() ?: return null
        } else
            metadata.reader

        setCachedMetadata(
            galleryID,
            Metadata(Cache(this).getCachedMetadata(galleryID), readers = reader)
        )

        return reader
    }

    val imageNameRegex = Regex("""^\d+\..+$""")
    fun getImages(galleryID: Int): List<File?>? {
        val gallery = getCachedGallery(galleryID)

        return gallery.list { _, name ->
            imageNameRegex.matches(name)
        }?.map {
            File(gallery, it)
        }
    }

    val imageExtensions = listOf(
        "png",
        "jpg",
        "webp",
        "gif"
    )
    fun getImage(galleryID: Int, index: Int): File? {
        val gallery = getCachedGallery(galleryID)

        for (ext in imageExtensions) {
            File(gallery, "%05d.$ext".format(index)).let {
                if (it.exists())
                    return it
            }
        }

        return null
    }


    fun putImage(galleryID: Int, index: Int, ext: String, data: InputStream) {
        val cache = File(getCachedGallery(galleryID), "%05d.$ext".format(index)).also {
            if (!it.exists())
                it.createNewFile()
        }

        BufferedInputStream(data).use {
            it.copyTo(FileOutputStream(cache))
        }
    }

    fun moveToDownload(galleryID: Int) {
        if (moving.contains(galleryID))
            return

        CoroutineScope(Dispatchers.IO).launch {
            val cache = getCachedGallery(galleryID).also {
                if (!it.exists())
                    return@launch
            }
            val download = File(getDownloadDirectory(this@Cache), galleryID.toString())

            if (download.isParentOf(cache))
                return@launch

            Log.i("PUPILD", "MOVING ${cache.canonicalPath} --> ${download.canonicalPath}")

            cache.copyRecursively(download, true) { file, err ->
                Log.i("PUPILD", "MOVING ERROR ${file.canonicalPath} ${err.message}")
                OnErrorAction.SKIP
            }
            Log.i("PUPILD", "MOVED ${cache.canonicalPath}")

            Log.i("PUPILD", "DELETING ${cache.canonicalPath}")
            cache.deleteRecursively()
            Log.i("PUPILD", "DELETED ${cache.canonicalPath}")
        }
    }

    fun isDownloading(galleryID: Int) = getCachedMetadata(galleryID)?.isDownloading == true

    fun setDownloading(galleryID: Int, isDownloading: Boolean) {
        setCachedMetadata(galleryID, Metadata(getCachedMetadata(galleryID), isDownloading = isDownloading))
    }

}