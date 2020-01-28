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
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.parse
import kotlinx.serialization.stringify
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.Reader
import java.io.File

class Cache(context: Context) : ContextWrapper(context) {

    private val preference = PreferenceManager.getDefaultSharedPreferences(this)

    // Search in this order
    // Download -> Cache
    fun getCachedGallery(galleryID: Int) : File? {
        var file : File

        ContextCompat.getExternalFilesDirs(this, null).forEach {
            file = File(it, galleryID.toString())

            if (file.exists())
                return file
        }

        file = File(cacheDir, "imageCache/$galleryID")

        return if (file.exists())
             file
        else
             null
    }

    @UseExperimental(ImplicitReflectionSerializer::class)
    fun getCachedMetadata(galleryID: Int) : Metadata? {
        val file = File(getCachedGallery(galleryID) ?: return null, ".metadata")

        if (!file.exists())
            return null

        return try {
            Json.parse(file.readText())
        } catch (e: Exception) {
            //File corrupted
            file.delete()
            null
        }
    }

    @UseExperimental(ImplicitReflectionSerializer::class)
    fun setCachedMetadata(galleryID: Int, metadata: Metadata) {
        val file = File(getCachedGallery(galleryID), ".metadata")

        if (!file.exists())
            return

        try {
            file.writeText(Json.stringify(metadata))
        } catch (e: Exception) {

        }
    }

    suspend fun getGalleryBlock(galleryID: Int): GalleryBlock? {
        val metadata = Cache(this).getCachedMetadata(galleryID)

        val galleryBlock = if (metadata?.galleryBlock == null)
           withContext(Dispatchers.IO) {
               try {
                   xyz.quaver.hitomi.getGalleryBlock(galleryID)
               } catch (e: Exception) {
                   null
               }
            }
        else
            metadata.galleryBlock

        setCachedMetadata(
            galleryID,
            Metadata(metadata, galleryBlock = galleryBlock)
        )

        return galleryBlock
    }

    suspend fun getReader(galleryID: Int): Reader? {
        val metadata = getCachedMetadata(galleryID)

        val readers = if (metadata?.readers == null) {
             listOf(
                { xyz.quaver.hitomi.getReader(galleryID) },
                { xyz.quaver.hiyobi.getReader(galleryID) }
            ).map {
                CoroutineScope(Dispatchers.IO).async {
                    try {
                        it.invoke()
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        } else {
            metadata.readers
        }

        if (readers.isNotEmpty())
            setCachedMetadata(
                galleryID,
                Metadata(metadata, readers = readers)
            )

        val mirrors = preference.getString("mirrors", "")!!.split('>')

        return readers.firstOrNull {
            mirrors.contains(it.code.name)
        }
    }

    suspend fun getImage(galleryID: Int): File? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}