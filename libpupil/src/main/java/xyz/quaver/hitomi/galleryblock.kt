/*
 *    Copyright 2019 tom5079
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

package xyz.quaver.hitomi

import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import xyz.quaver.Code
import xyz.quaver.proxy
import java.net.URL
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import javax.net.ssl.HttpsURLConnection

//galleryblock.js
fun fetchNozomi(area: String? = null, tag: String = "index", language: String = "all", start: Int = -1, count: Int = -1) : Pair<List<Int>, Int> {
    val url =
            when(area) {
                null -> "$protocol//$domain/$tag-$language$nozomiextension"
                else -> "$protocol//$domain/$area/$tag-$language$nozomiextension"
            }

    with(URL(url).openConnection() as HttpsURLConnection) {
        requestMethod = "GET"

        if (start != -1 && count != -1) {
            val startByte = start*4
            val endByte = (start+count)*4-1

            setRequestProperty("Range", "bytes=$startByte-$endByte")
        }

        connect()

        val totalItems = getHeaderField("Content-Range")
            .replace(Regex("^[Bb]ytes \\d+-\\d+/"), "").toInt() / 4

        val nozomi = ArrayList<Int>()

        val arrayBuffer = ByteBuffer
            .wrap(inputStream.readBytes())
            .order(ByteOrder.BIG_ENDIAN)

        while (arrayBuffer.hasRemaining())
            nozomi.add(arrayBuffer.int)

        return Pair(nozomi, totalItems)
    }
}

@Serializable
data class GalleryBlock(
    val code: Code,
    val id: Int,
    val galleryUrl: String,
    val thumbnails: List<String>,
    val title: String,
    val artists: List<String>,
    val series: List<String>,
    val type: String,
    val language: String,
    val relatedTags: List<String>
)
fun getGalleryBlock(galleryID: Int) : GalleryBlock? {
    val url = "$protocol//$domain/$galleryblockdir/$galleryID$extension"

    val doc = Jsoup.connect(url).proxy(proxy).get()

    val galleryUrl = doc.selectFirst(".lillie").attr("href")

    val thumbnails = doc.select(".dj-img-cont img").map { protocol + it.attr("src") }

    val title = doc.selectFirst("h1.lillie > a").text()
    val artists = doc.select("div.artist-list a").map{ it.text() }
    val series = doc.select("a[href~=^/series/]").map { it.text() }
    val type = doc.selectFirst("a[href~=^/type/]").text()

    val language = {
        val href = doc.select("a[href~=^/index-.+-1.html]").attr("href")
        href.slice(7 until href.indexOf("-1"))
    }.invoke()

    val relatedTags = doc.select(".relatedtags a").map {
        val href = URLDecoder.decode(it.attr("href"), "UTF-8")
        href.slice(5 until href.indexOf("-all"))
    }

    return GalleryBlock(Code.HITOMI, galleryID, galleryUrl, thumbnails, title, artists, series, type, language, relatedTags)
}