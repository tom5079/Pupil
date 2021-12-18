/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2021 tom5079
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
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.sources.hitomi.lib

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.net.URLDecoder

//galleryblock.js
suspend fun fetchNozomi(
    client: HttpClient,
    area: String? = null,
    tag: String = "index",
    language: String = "all",
    start: Int = -1,
    count: Int = -1
) : Pair<List<Int>, Int> = withContext(Dispatchers.IO) {
    val url =
        when(area) {
            null -> "$protocol//$domain/$tag-$language$nozomiextension"
            else -> "$protocol//$domain/$area/$tag-$language$nozomiextension"
        }

    val response: HttpResponse = client.get(url) {
        headers {
            if (start != -1 && count != -1) {
                val startByte = start*4
                val endByte = (start+count)*4-1

                append("Range", "bytes=$startByte-$endByte")
            }
        }
    }

    val totalItems = response.headers["Content-Range"]!!
        .replace(Regex("^[Bb]ytes \\d+-\\d+/"), "").toInt() / 4

    response.readBytes().asIterable().chunked(4) {
        (it[0].toInt() and 0xFF)          or
        ((it[1].toInt() and 0xFF) shl 8)  or
        ((it[2].toInt() and 0xFF) shl 16) or
        ((it[3].toInt() and 0xFF) shl 24)
    } to totalItems
}

@Serializable
data class GalleryBlock(
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

suspend fun getGalleryBlock(client: HttpClient, galleryID: Int) : GalleryBlock = withContext(Dispatchers.IO) {
    val url = "$protocol//$domain/$galleryblockdir/$galleryID$extension"

    val doc = Jsoup.parse(rewriteTnPaths(client.get(url)))

    val galleryUrl = doc.selectFirst("h1 > a")!!.attr("href")

    val thumbnails = doc.select(".dj-img-cont img").map { protocol + it.attr("src") }

    val title = doc.selectFirst("h1 > a")!!.text()
    val artists = doc.select(".artist-list a").map{ it.text() }
    val series = doc.select(".dj-content a[href~=^/series/]").map { it.text() }
    val type = doc.selectFirst("a[href~=^/type/]")!!.text()

    val language = run {
        val href = doc.select("a[href~=^/index.+\\.html\$]").attr("href")
        Regex("""index-([^-]+)(-.+)?\.html""").find(href)?.groupValues?.getOrNull(1) ?: ""
    }

    val relatedTags = doc.select(".relatedtags a").map {
        val href = URLDecoder.decode(it.attr("href"), "UTF-8")
        href.slice(5 until href.indexOf("-all"))
    }

    GalleryBlock(galleryID, galleryUrl, thumbnails, title, artists, series, type, language, relatedTags)
}
