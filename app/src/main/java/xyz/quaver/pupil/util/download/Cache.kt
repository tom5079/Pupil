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
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.parse
import kotlinx.serialization.stringify
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.Reader
import java.io.File

class Cache(context: Context) : ContextWrapper(context) {

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

    fun getGalleryBlock(galleryID: Int): GalleryBlock {
        var meta = Cache(this).getCachedMetadata(galleryID)

        if (meta == null) {
            meta = Metadata(galleryBlock = xyz.quaver.hitomi.getGalleryBlock(galleryID))

            Cache(this).setCachedMetadata(
                galleryID,
                meta
            )
        } else if (meta.galleryBlock == null)
            Cache(this).setCachedMetadata(
                galleryID,
                meta.apply {
                    galleryBlock = xyz.quaver.hitomi.getGalleryBlock(galleryID)
                }
            )

        return meta.galleryBlock!!
    }

    fun getReaders(galleryID: Int): List<Reader> {
        var meta = getCachedMetadata(galleryID)

        if (meta == null) {
            meta = Metadata(reader = mutableListOf(xyz.quaver.hitomi.getReader(galleryID)))

            setCachedMetadata(
                galleryID,
                meta
            )
        } else if (meta.reader == null)
            setCachedMetadata(
                galleryID,
                meta.apply {
                    reader = mutableListOf(xyz.quaver.hitomi.getReader(galleryID))
                }
            )
        else if (!meta.reader!!.any { it.code == Reader.Code.HITOMI })
            setCachedMetadata(
                galleryID,
                meta.apply {
                    reader!!.add(xyz.quaver.hitomi.getReader(galleryID))
                }
            )

        return meta.reader!!
    }

    fun getImage(galleryID: Int, index: Int): File {
        val cache = getCachedGallery(galleryID)

        if (cache == null)
            ;//TODO: initiate image download

        return File(cache, "%04d".format(index))
    }

}