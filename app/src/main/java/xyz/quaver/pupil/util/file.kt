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
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Array
import java.net.URL

@Suppress("DEPRECATION")
@Deprecated("Use downloader.Cache instead")
fun getCachedGallery(context: Context, galleryID: Int) =
    File(getDownloadDirectory(context), galleryID.toString()).let {
        if (it.exists())
            it
        else
            File(context.cacheDir, "imageCache/$galleryID")
    }

@Suppress("DEPRECATION")
@Deprecated("Use downloader.Cache instead")
fun getDownloadDirectory(context: Context) =
    Preferences.get<String>("dl_location").let {
        if (it.isNotEmpty() && !it.startsWith("content"))
            File(it)
        else
            context.getExternalFilesDir(null)!!
    }

@Suppress("DEPRECATION")
@Deprecated("Use FileX instead")
fun File.isParentOf(another: File) =
    another.absolutePath.startsWith(this.absolutePath)