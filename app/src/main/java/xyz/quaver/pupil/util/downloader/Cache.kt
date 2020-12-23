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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.quaver.io.FileX
import xyz.quaver.io.util.deleteRecursively
import xyz.quaver.io.util.getChild
import xyz.quaver.io.util.outputStream
import xyz.quaver.io.util.writeText
import xyz.quaver.pupil.sources.ItemInfo
import xyz.quaver.pupil.sources.sources
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class Metadata(
    var itemInfo: ItemInfo? = null,
    var imageList: MutableList<String?>? = null
) {
    fun copy(): Metadata = Metadata(itemInfo, imageList?.let { MutableList(it.size) { i -> it[i] } })
}

class Cache private constructor(context: Context, source: String, private val itemID: String) : ContextWrapper(context) {

    companion object {
        val instances = ConcurrentHashMap<String, Cache>()

        fun getInstance(context: Context, source: String, itemID: String): Cache {
            val key = "$source/$itemID"
            return instances[key] ?: synchronized(this) {
                instances[key] ?: Cache(context, source, itemID).also { instances[key] = it }
            }
        }

        @Synchronized
        fun delete(source: String, itemID: String) {
            val key = "$source/$itemID"

            instances[key]?.cacheFolder?.deleteRecursively()
            instances.remove("$source/$itemID")
        }
    }

    val source = sources[source]!!

    val downloadFolder: FileX?
        get() = DownloadManager.getInstance(this).getDownloadFolder(source.name, itemID)

    val cacheFolder: FileX
        get() = FileX(this, cacheDir, "imageCache/$source/$itemID").also {
            if (!it.exists())
                it.mkdirs()
        }

    val metadata: Metadata = kotlin.runCatching {
        Json.decodeFromString<Metadata>(findFile(".metadata")!!.readText())
    }.getOrDefault(Metadata())

    @Suppress("BlockingMethodInNonBlockingContext")
    fun setMetadata(change: (Metadata) -> Unit) {
        change.invoke(metadata)

        val file = cacheFolder.getChild(".metadata")

        kotlin.runCatching {
            if (!file.exists()) {
                file.createNewFile()
            }
            file.writeText(Json.encodeToString(metadata))
        }
    }

    private fun findFile(fileName: String): FileX? =
         downloadFolder?.let { downloadFolder -> downloadFolder.getChild(fileName).let {
            if (it.exists()) it else null
        } } ?: cacheFolder.getChild(fileName).let {
            if (it.exists()) it else null
        }

    fun putImage(index: Int, name: String, `is`: InputStream) {
        cacheFolder.getChild(name).also {
            if (!it.exists())
                it.createNewFile()
        }.outputStream()?.use {
            it.channel.truncate(0L)
            `is`.copyTo(it)
        }

        setMetadata { metadata -> metadata.imageList!![index] = name }
    }

    fun getImage(index: Int): FileX? {
        return metadata.imageList?.get(index)?.let { findFile(it) }
    }
}