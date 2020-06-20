/*
 *    Copyright 2020 tom5079
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package xyz.quaver.hiyobi

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import xyz.quaver.Code
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.protocol
import xyz.quaver.json
import xyz.quaver.proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

fun getGalleryBlock(galleryID: Int) : GalleryBlock? {
    val url = "$protocol//api.$hiyobi/gallery/$galleryID"

    val galleryBlock = with (URL(url).openConnection(proxy) as HttpsURLConnection) {
        setRequestProperty("User-Agent", user_agent)
        setRequestProperty("Cookie", cookie)
        connectTimeout = 1000
        connect()

        inputStream.bufferedReader().use { it.readText() }
    }.let {
        json.parseJson(it).jsonObject
    }

    val galleryUrl = "reader/$galleryID"

    val thumbnails = listOf("$protocol//cdn.$hiyobi/tn/$galleryID.jpg")

    val title = galleryBlock["title"]?.contentOrNull ?: ""
    val artists = galleryBlock["artists"]?.jsonArray?.mapNotNull {
        it.jsonObject["value"]?.contentOrNull
    } ?: listOf()
    val series = galleryBlock["parodys"]?.jsonArray?.mapNotNull {
        it.jsonObject["value"]?.contentOrNull
    } ?: listOf()
    val type = when (galleryBlock["type"]?.intOrNull) {
        1 -> "doujinshi"
        2 -> "manga"
        3 -> "artistcg"
        4 -> "gamecg"
        else -> ""
    }

    val language = "korean"

    val relatedTags = galleryBlock["tags"]?.jsonArray?.mapNotNull {
        it.jsonObject["value"]?.contentOrNull
    } ?: listOf()

    return GalleryBlock(Code.HIYOBI, galleryID, galleryUrl, thumbnails, title, artists, series, type, language, relatedTags)
}