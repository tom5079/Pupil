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
import java.net.URLDecoder

@Serializable
data class Gallery(
    val related: List<Int>,
    val langList: List<Pair<String, String>>,
    val cover: String,
    val title: String,
    val artists: List<String>,
    val groups: List<String>,
    val type: String,
    val language: String,
    val series: List<String>,
    val characters: List<String>,
    val tags: List<String>,
    val thumbnails: List<String>
)
fun getGallery(galleryID: Int) : Gallery {
    val url = Jsoup.connect("https://hitomi.la/galleries/$galleryID.html").get()
        .select("a").attr("href")

    val doc = Jsoup.connect(url).get()

    val related = Regex("\\d+")
        .findAll(doc.select("script").first().html())
        .map {
            it.value.toInt()
        }.toList()

    val langList = doc.select("#lang-list a").map {
            Pair(it.text(), "$protocol//hitomi.la${it.attr("href")}")
        }

    val cover = protocol + doc.selectFirst(".cover img").attr("src")
    val title = doc.selectFirst(".gallery h1 a").text()
    val artists = doc.select(".gallery h2 a").map { it.text() }
    val groups = doc.select(".gallery-info a[href~=^/group/]").map { it.text() }
    val type = doc.selectFirst(".gallery-info a[href~=^/type/]").text()

    val language = {
        val href = doc.select(".gallery-info a[href~=^/index-.+-1.html]").attr("href")
        href.slice(7 until href.indexOf("-1"))
    }.invoke()

    val series = doc.select(".gallery-info a[href~=^/series/]").map { it.text() }
    val characters = doc.select(".gallery-info a[href~=^/character/]").map { it.text() }

    val tags = doc.select(".gallery-info a[href~=^/tag/]").map {
        val href = URLDecoder.decode(it.attr("href"), "UTF-8")
        href.slice(5 until href.indexOf('-'))
    }

    val thumbnails = getGalleryInfo(galleryID).map { galleryInfo ->
        urlFromUrlFromHash(galleryID, galleryInfo, "smalltn", "jpg", "tn")
    }

    return Gallery(related, langList, cover, title, artists, groups, type, language, series, characters, tags, thumbnails)
}