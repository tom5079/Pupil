/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2021  tom5079
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

package xyz.quaver.pupil.util

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import xyz.quaver.io.FileX
import xyz.quaver.io.util.*
import xyz.quaver.pupil.sources.Source

class DownloadManager constructor(context: Context) : ContextWrapper(context), DIAware {

    override val di by closestDI(context)

    private val defaultDownloadFolder = FileX(this, getExternalFilesDir(null)!!)

    val downloadFolder: FileX
        get() = kotlin.runCatching {
            FileX(this, Preferences.get<String>("download_folder"))
        }.getOrElse {
            Preferences["download_folder"] = defaultDownloadFolder.uri.toString()
            defaultDownloadFolder
        }

    private var prevDownloadFolder: FileX? = null
    private var downloadFolderMapInstance: MutableMap<String, String>? = null
    private val downloadFolderMap: MutableMap<String, String>
        @Synchronized
        get() {
            if (prevDownloadFolder != downloadFolder) {
                prevDownloadFolder = downloadFolder
                downloadFolderMapInstance = run {
                    val file = downloadFolder.getChild(".download")
                    val data = if (file.exists())
                        kotlin.runCatching {
                            file.readText()?.let<String, MutableMap<String, String>> { Json.decodeFromString(it) }
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

    val downloads: Map<String, String>
        get() = downloadFolderMap

    @Synchronized
    fun getDownloadFolder(source: String, itemID: String): FileX? =
        downloadFolderMap["$source-$itemID"]?.let { downloadFolder.getChild(it) }

    @Synchronized
    fun download(source: String, itemID: String) = CoroutineScope(Dispatchers.IO).launch {
        val source: Source by source(source)
        val info = async { source.info(itemID) }
        val images = async { source.images(itemID) }

        val name = info.await().formatDownloadFolder()

        val folder = downloadFolder.getChild("$source/$name")

        if (folder.exists())
            return@launch

        folder.mkdir()

        downloadFolderMap["$source/$itemID"] = folder.name

        downloadFolder.getChild(".download").let { if (!it.exists()) it.createNewFile() }
        downloadFolder.getChild(".download").writeText(Json.encodeToString(downloadFolderMap))
    }

    @Synchronized
    fun delete(source: String, itemID: String) {
        downloadFolderMap["$source/$itemID"]?.let {
            kotlin.runCatching {
                downloadFolder.getChild(it).deleteRecursively()
                downloadFolderMap.remove("$source/$itemID")

                downloadFolder.getChild(".download").let { if (!it.exists()) it.createNewFile() }
                downloadFolder.getChild(".download").writeText(Json.encodeToString(downloadFolderMap))
            }
        }
    }
}