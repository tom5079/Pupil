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
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.*
import xyz.quaver.availableInHiyobi
import xyz.quaver.hitomi.Reader
import xyz.quaver.pupil.BuildConfig
import java.io.File
import java.net.URL

fun getReleases(url: String) : JsonArray {
    return try {
        URL(url).readText().let {
            Json(JsonConfiguration.Stable).parse(JsonArray.serializer(), it)
        }
    } catch (e: Exception) {
        JsonArray(emptyList())
    }
}

fun checkUpdate(url: String) : JsonObject? {
    val releases = getReleases(url)

    if (releases.isEmpty())
        return null

    return releases.firstOrNull {
        if (BuildConfig.PRERELEASE) {
            true
        } else {
            it.jsonObject["prerelease"]?.boolean == false
        }
    }?.let {
        if (it.jsonObject["tag_name"]?.content == BuildConfig.VERSION_NAME)
            null
        else
            it.jsonObject
    }
}

fun getApkUrl(releases: JsonObject) : Pair<String?, String?>? {
    return releases["assets"]?.jsonArray?.firstOrNull {
        Regex("Pupil-v.+\\.apk").matches(it.jsonObject["name"]?.content ?: "")
    }.let {
        if (it == null)
            null
        else
            Pair(it.jsonObject["browser_download_url"]?.content, it.jsonObject["name"]?.content)
    }
}

fun getOldReaderGalleries(context: Context) : List<File> {
    val oldGallery = mutableListOf<File>()

    listOf(
        getDownloadDirectory(context),
        File(context.cacheDir, "imageCache")
    ).forEach { root ->
        root.listFiles()?.forEach { gallery ->
            File(gallery, "reader.json").let { readerFile ->
                if (!readerFile.exists())
                    return@let

                Json(JsonConfiguration.Stable).parseJson(readerFile.readText()).jsonObject.let { reader ->
                    if (!reader.contains("code"))
                        oldGallery.add(gallery)
                }
            }
        }
    }

    return oldGallery
}

@UseExperimental(ImplicitReflectionSerializer::class)
fun updateOldReaderGalleries(context: Context) {

    val json = Json(JsonConfiguration.Stable)

   getOldReaderGalleries(context).forEach { gallery ->
       val reader = json.parseJson(File(gallery, "reader.json").readText())
           .jsonObject.toMutableMap()

       reader["code"] = when {
           (File(gallery, "images").list()?.
               all { !it.endsWith("webp") } ?: return@forEach) &&
                   availableInHiyobi(gallery.name.toInt()) -> json.toJson(Reader.Code.HIYOBI)
           else -> json.toJson(Reader.Code.HITOMI)
       }

       File(gallery, "reader.json").writeText(JsonObject(reader).toString())
   }

}