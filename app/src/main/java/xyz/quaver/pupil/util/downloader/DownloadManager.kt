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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import xyz.quaver.io.FileX
import xyz.quaver.io.util.*
import xyz.quaver.pupil.client
import xyz.quaver.pupil.services.DownloadService
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.formatDownloadFolder

class DownloadManager private constructor(context: Context) : ContextWrapper(context) {

    companion object {
        @Volatile private var instance: DownloadManager? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: DownloadManager(context).also { instance = it }
            }
    }

    val defaultDownloadFolder = FileX(this, getExternalFilesDir(null)!!)

    val downloadFolder: FileX
        get() = kotlin.runCatching {
            FileX(this, Preferences.get<String>("download_folder"))
        }.getOrElse {
            Preferences["download_folder"] = defaultDownloadFolder.uri.toString()
            defaultDownloadFolder
        }

    private var prevDownloadFolder: FileX? = null
    private var downloadFolderMapInstance: MutableMap<Int, String>? = null
    val downloadFolderMap: MutableMap<Int, String>
        @Synchronized
        get() {
            if (prevDownloadFolder != downloadFolder) {
                prevDownloadFolder = downloadFolder
                downloadFolderMapInstance = run {
                    val file = downloadFolder.getChild(".download")
                    val data = if (file.exists())
                        kotlin.runCatching {
                            file.readText()?.let{ Json.decodeFromString<MutableMap<Int, String>>(it) }
                        }.onFailure { file.delete() }.getOrNull()
                    else
                        null
                    data ?: run {
                        file.createNewFile()
                        mutableMapOf()
                    }
                }
            }

            return downloadFolderMapInstance ?: mutableMapOf()
        }


    @Synchronized
    fun isDownloading(galleryID: Int): Boolean {
        val isThisGallery: (Call) -> Boolean = { !it.isCanceled && (it.request().tag() as? DownloadService.Tag)?.galleryID == galleryID }

        return downloadFolderMap.containsKey(galleryID)
                && client.dispatcher().let { it.queuedCalls().any(isThisGallery) || it.runningCalls().any(isThisGallery) }
    }

    @Synchronized
    fun getDownloadFolder(galleryID: Int): FileX? =
        downloadFolderMap[galleryID]?.let { downloadFolder.getChild(it) }

    fun addDownloadFolder(galleryID: Int) = CoroutineScope(Dispatchers.IO).launch {
        val name = Cache.getInstance(this@DownloadManager, galleryID).getGalleryBlock()
            ?.formatDownloadFolder() ?: return@launch

        val folder = downloadFolder.getChild(name)

        downloadFolderMap[galleryID] = name

        downloadFolder.getChild(".download").let { if (!it.exists()) it.createNewFile() }
        downloadFolder.getChild(".download").writeText(Json.encodeToString(downloadFolderMap))

        if (folder.exists()) return@launch
        folder.mkdir()
    }

    @Synchronized
    fun deleteDownloadFolder(galleryID: Int) {
        downloadFolderMap[galleryID]?.let {
            kotlin.runCatching {
                downloadFolder.getChild(it).deleteRecursively()
                downloadFolderMap.remove(galleryID)

                downloadFolder.getChild(".download").let { if (!it.exists()) it.createNewFile() }
                downloadFolder.getChild(".download").writeText(Json.encodeToString(downloadFolderMap))
            }
        }
    }
}