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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.quaver.io.FileX
import xyz.quaver.io.util.readText
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

    val downloadFolder = {
        val uri: String = Preferences["download_directory"]

        if (!URLUtil.isValidUrl(uri))
            Preferences["download_directory"] = defaultDownloadFolder

        kotlin.runCatching {
            FileX(this, uri)
        }.getOrElse {
            Preferences["download_directory"] = defaultDownloadFolder
            FileX(this, defaultDownloadFolder)
        }
    }.invoke()

    private val downloadFolderMap: MutableMap<Int, String> =
        kotlin.runCatching {
            FileX(this@DownloadFolderManager, downloadFolder, ".download").readText()?.let {
                Json.decodeFromString<MutableMap<Int, String>>(it)
            }
        }.getOrNull() ?: mutableMapOf()
    private val downloadFolderMapMutex = Mutex()

    @Synchronized
    fun getDownloadFolder(galleryID: Int): FileX? =
        downloadFolderMap[galleryID]?.let { FileX(this, downloadFolder, it) }

    @Synchronized
    fun addDownloadFolder(galleryID: Int, name: String) {
        if (downloadFolderMap.containsKey(galleryID))
            return

        if (FileX(this@DownloadFolderManager, downloadFolder, name).mkdir()) {
            downloadFolderMap[galleryID] = name

            CoroutineScope(Dispatchers.IO).launch { downloadFolderMapMutex.withLock {
                FileX(this@DownloadFolderManager, downloadFolder, ".download").writeText(Json.encodeToString(downloadFolderMap))
            } }
        }
    }

    @Synchronized
    fun removeDownloadFolder(galleryID: Int) {
        if (!downloadFolderMap.containsKey(galleryID))
            return

        downloadFolderMap[galleryID]?.let {
            if (FileX(this@DownloadFolderManager, downloadFolder, it).delete()) {
                downloadFolderMap.remove(galleryID)

                CoroutineScope(Dispatchers.IO).launch { downloadFolderMapMutex.withLock {
                    FileX(this@DownloadFolderManager, downloadFolder, ".download").writeText(Json.encodeToString(downloadFolderMap))
                } }
            }
        }
    }
}