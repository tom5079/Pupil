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

import kotlinx.serialization.json.*
import xyz.quaver.pupil.BuildConfig
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