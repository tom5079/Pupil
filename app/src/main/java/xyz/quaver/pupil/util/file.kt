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
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import java.io.File

fun getCachedGallery(context: Context, galleryID: Int): File {
    return File(getDownloadDirectory(context), galleryID.toString()).let {
        when {
            it.exists() -> it
            else -> File(context.cacheDir, "imageCache/$galleryID")
        }
    }
}

@Suppress("DEPRECATION")
fun getDownloadDirectory(context: Context): File {/*
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        context.getExternalFilesDir(".Pupil")!!
    else
        File(Environment.getExternalStorageDirectory(), ".Pupil")*/
    val temp = context.getSharedPreferences("directory", Context.MODE_PRIVATE)?.getString("directory","")
    val uri = Uri.parse(temp)
    val path =
    return File("$temp/.Pupil")
}