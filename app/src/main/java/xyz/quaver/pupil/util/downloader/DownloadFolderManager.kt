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
import android.webkit.URLUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import xyz.quaver.io.FileX
import xyz.quaver.io.util.getChild
import xyz.quaver.io.util.readText
import xyz.quaver.pupil.client
import xyz.quaver.pupil.services.DownloadService
import xyz.quaver.pupil.util.Preferences

class DownloadFolderManager private constructor(context: Context) : ContextWrapper(context) {

    companion object {
        @Volatile private var instance: DownloadFolderManager? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: DownloadFolderManager(context).also { instance = it }
            }
    }

    val defaultDownloadFolder = FileX(this, getExternalFilesDir(null)!!)

    val downloadFolder: FileX
        get() = {
            kotlin.runCatching {
                FileX(this, Preferences.get<String>("download_folder"))
            }.getOrElse {
                Preferences["download_folder"] = defaultDownloadFolder.uri.toString()
                defaultDownloadFolder
            }
        }.invoke()

    private val downloadFolderMapMutex = Mutex()
    private val downloadFolderMap: MutableMap<Int, String> = runBlocking { downloadFolderMapMutex.withLock {
        kotlin.runCatching {
            downloadFolder.getChild(".download").readText()?.let {
                Json.decodeFromString<MutableMap<Int, String>>(it)
            }
        }.getOrNull() ?: mutableMapOf()
    } }

    fun isDownloading(galleryID: Int): Boolean {
        val isThisGallery: (Call) -> Boolean = { (it.request().tag() as? DownloadService.Tag)?.galleryID == galleryID }

        return downloadFolderMap.containsKey(galleryID)
                && client.dispatcher().let { it.queuedCalls().any(isThisGallery) || it.runningCalls().any(isThisGallery) }
    }

    fun getDownloadFolder(galleryID: Int): FileX? = runBlocking { downloadFolderMapMutex.withLock {
        downloadFolderMap[galleryID]?.let { downloadFolder.getChild(it) }
    } }

    fun addDownloadFolder(galleryID: Int, name: String) { runBlocking { downloadFolderMapMutex.withLock {
        if (downloadFolderMap.containsKey(galleryID))
            return@withLock

        val folder = downloadFolder.getChild(name)

        if (!folder.exists())
            folder.mkdirs()

        downloadFolderMap[galleryID] = name

        CoroutineScope(Dispatchers.IO).launch { downloadFolderMapMutex.withLock {
           downloadFolder.getChild(".download").let {
               it.createNewFile()
               it.writeText(Json.encodeToString(downloadFolderMap))
           }
        } }
    } } }

    fun deleteDownloadFolder(galleryID: Int) { runBlocking { downloadFolderMapMutex.withLock {
        if (!downloadFolderMap.containsKey(galleryID))
            return@withLock

        downloadFolderMap[galleryID]?.let {
            if (downloadFolder.getChild(it).delete()) {
                downloadFolderMap.remove(galleryID)

                CoroutineScope(Dispatchers.IO).launch { downloadFolderMapMutex.withLock {
                    downloadFolder.getChild(".download").let {
                        it.createNewFile()
                        it.writeText(Json.encodeToString(downloadFolderMap))
                    }
                } }
            }
        }
    } } }
}