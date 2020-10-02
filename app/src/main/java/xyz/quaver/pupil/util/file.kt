/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2019  tom5079
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.quaver.pupil.histories
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.DownloadManager
import java.io.File

val mutex = Mutex()
fun cleanCache(context: Context) = CoroutineScope(Dispatchers.IO).launch {
    if (mutex.isLocked) return@launch

    mutex.withLock {
        val cacheFolder = File(context.cacheDir, "imageCache")
        val downloadManager = DownloadManager.getInstance(context)

        cacheFolder.listFiles { file ->
            val galleryID = file.name.toIntOrNull() ?: return@listFiles true

            !(downloadManager.downloadFolderMap.containsKey(galleryID) || histories.contains(galleryID))
        }?.forEach {
            it.deleteRecursively()
        }

        DownloadManager.getInstance(context).downloadFolderMap.keys.forEach {
            val folder = File(cacheFolder, it.toString())

            if (!downloadManager.isDownloading(it) && folder.exists()) {
                folder.deleteRecursively()
            }
        }

        val limit = (Preferences.get<String>("cache_limit").toLongOrNull() ?: 0L)*1024*1024*1024

        if (limit == 0L) return@withLock

        val cacheSize = {
            var size = 0L

            cacheFolder.walk().forEach {
                size += it.length()
            }

            size
        }

        if (cacheSize.invoke() > limit)
            while (cacheSize.invoke() > limit/2) {
                val caches = cacheFolder.list() ?: return@withLock

                (histories.firstOrNull {
                    caches.contains(it.toString()) && !downloadManager.isDownloading(it)
                } ?: return@withLock).let {
                    Cache.delete(it)
                }
            }
    }
}