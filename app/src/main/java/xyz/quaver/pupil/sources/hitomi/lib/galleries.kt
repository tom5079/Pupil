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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
suspend fun getGallery(client: HttpClient, galleryID: Int) : Gallery = withContext(Dispatchers.IO) {
    val url = Jsoup.parse(client.get("https://hitomi.la/galleries/$galleryID.html"))
        .select("link").attr("href")

    val doc = Jsoup.parse(client.get(url))

    val related = Regex("\\d+")
        .findAll(doc.select("script").first()!!.html())
        .map {
            it.value.toInt()
        }.toList()

    val langList = doc.select("#lang-list a").map {
        Pair(it.text(), "$protocol//hitomi.la${it.attr("href")}")
    }

    val cover = protocol + doc.selectFirst(".cover img")!!.attr("src")
    val title = doc.selectFirst(".gallery h1 a")!!.text()
    val artists = doc.select(".gallery h2 a").map { it.text() }
    val groups = doc.select(".gallery-info a[href~=^/group/]").map { it.text() }
    val type = doc.selectFirst(".gallery-info a[href~=^/type/]")!!.text()

    val language = run {
        val href = doc.select(".gallery-info a[href~=^/index.+\\.html\$]").attr("href")
        Regex("""index-([^-]+)(-.+)?\.html""").find(href)?.groupValues?.getOrNull(1) ?: ""
    }

    val series = doc.select(".gallery-info a[href~=^/series/]").map { it.text() }
    val characters = doc.select(".gallery-info a[href~=^/character/]").map { it.text() }

    val tags = doc.select(".gallery-info a[href~=^/tag/]").map {
        val href = URLDecoder.decode(it.attr("href"), "UTF-8")
        href.slice(5 until href.indexOf('-'))
    }

    val thumbnails = getGalleryInfo(client, galleryID).files.map { galleryInfo ->
        urlFromUrlFromHash(galleryID, galleryInfo, "smalltn", "jpg", "tn")
    }

    Gallery(related, langList, cover, title, artists, groups, type, language, series, characters, tags, thumbnails)
}