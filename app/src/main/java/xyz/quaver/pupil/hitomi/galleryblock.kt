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

package xyz.quaver.pupil.hitomi

import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import xyz.quaver.pupil.webView
import xyz.quaver.readText
import java.net.URL
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import javax.net.ssl.HttpsURLConnection

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

suspend fun getGalleryBlock(galleryID: Int) : GalleryBlock {
    val url = "$protocol//$domain/$galleryblockdir/$galleryID$extension"

    val html: String = webView.evaluatePromise(
        """
        $.get('$url').always(function(data, status) {
            if (status === 'success') {
                Callback.onResult(%uid, data);
            }
        }); 
        """.trimIndent(),
        then = ""
    )

    val doc = Jsoup.parse(html)

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

    return GalleryBlock(galleryID, galleryUrl, thumbnails, title, artists, series, type, language, relatedTags)
}