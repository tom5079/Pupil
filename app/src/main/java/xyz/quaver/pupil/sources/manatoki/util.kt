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

package xyz.quaver.pupil.sources.manatoki

import android.os.Parcelable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.common.util.concurrent.RateLimiter
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.util.concurrent.Executors

private val rateLimitCoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
private val rateLimiter = RateLimiter.create(10.0)

suspend fun waitForRateLimit() {
    withContext(rateLimitCoroutineDispatcher) {
        rateLimiter.acquire()
    }
    yield()
}

@Parcelize
@Serializable
data class MangaListingEntry(
    val itemID: String,
    val episode: Int,
    val title: String,
    val starRating: Float,
    val date: String,
    val viewCount: Int,
    val thumbsUpCount: Int
): Parcelable

@Parcelize
@Serializable
data class MangaListing(
    val itemID: String,
    val title: String,
    val thumbnail: String,
    val author: String,
    val tags: List<String>,
    val type: String,
    val thumbsUpCount: Int,
    val entries: List<MangaListingEntry>
): Parcelable

@Parcelize
@Serializable
data class ReaderInfo(
    val itemID: String,
    val title: String,
    val urls: List<String>,
    val listingItemID: String
): Parcelable

@ExperimentalMaterialApi
@Composable
fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit = { }) {
    Card(
        onClick = onClick,
        backgroundColor = if (selected) MaterialTheme.colors.secondary else MaterialTheme.colors.surface,
        shape = RoundedCornerShape(8.dp),
        elevation = 4.dp
    ) {
        Text(text, modifier = Modifier.padding(4.dp))
    }
}

suspend fun HttpClient.getItem(
    itemID: String,
    onListing: (MangaListing) -> Unit = { },
    onReader: (ReaderInfo) -> Unit = { }
) = coroutineScope {
    waitForRateLimit()
    val content: String = get("https://manatoki116.net/comic/$itemID")

    val doc = Jsoup.parse(content)

    yield()

    if (doc.getElementsByClass("serial-list").size == 0) {
        val htmlData = doc
            .selectFirst(".view-padding > script")!!
            .data()
            .splitToSequence('\n')
            .fold(StringBuilder()) { sb, line ->
                if (!line.startsWith("html_data")) return@fold sb

                line.drop(12).dropLast(2).split('.').forEach {
                    if (it.isNotBlank()) sb.appendCodePoint(it.toInt(16))
                }
                sb
            }.toString()

        val urls = Jsoup.parse(htmlData)
            .select("img[^data-]:not([style])")
            .map {
                it.attributes()
                    .first { it.key.startsWith("data-") }
                    .value
            }

        val title = doc.getElementsByClass("toon-title").first()!!.ownText()

        val listingItemID = doc.select("a:contains(전체목록)").first()!!.attr("href").takeLastWhile { it != '/' }

        onReader(
            ReaderInfo(
                itemID,
                title,
                urls,
                listingItemID
            )
        )
    } else {
        val titleBlock = doc.selectFirst("div.view-title")!!

        val title = titleBlock.select("div.view-content:not([itemprop])").first()!!.text()

        val author =
            titleBlock
                .select("div.view-content:not([itemprop]):contains(작가)")
                .first()!!
                .getElementsByTag("a")
                .first()!!
                .text()

        val tags =
            titleBlock
                .select("div.view-content:not([itemprop]):contains(분류)")
                .first()!!
                .getElementsByTag("a")
                .map { it.text() }

        val type =
            titleBlock
                .select("div.view-content:not([itemprop]):contains(발행구분)")
                .first()!!
                .getElementsByTag("a")
                .first()!!
                .text()

        val thumbnail =
            titleBlock.getElementsByTag("img").first()!!.attr("src")

        val thumbsUpCount =
            titleBlock.select("i.fa-thumbs-up + b").text().toInt()

        val entries =
            doc.select("div.serial-list .list-item").map {
                val episode = it.getElementsByClass("wr-num").first()!!.text().toInt()
                val (itemID, title) = it.getElementsByClass("item-subject").first()!!.let { subject ->
                    subject.attr("href").dropLastWhile { it != '?' }.dropLast(1).takeLastWhile { it != '/' } to subject.ownText()
                }
                val starRating = it.getElementsByClass("wr-star").first()!!.text().drop(1).takeWhile { it != ')' }.toFloat()
                val date = it.getElementsByClass("wr-date").first()!!.text()
                val viewCount = it.getElementsByClass("wr-hit").first()!!.text().replace(",", "").toInt()
                val thumbsUpCount = it.getElementsByClass("wr-good").first()!!.text().replace(",", "").toInt()

                MangaListingEntry(
                    itemID,
                    episode,
                    title,
                    starRating,
                    date,
                    viewCount,
                    thumbsUpCount
                )
            }

        onListing(
            MangaListing(
                itemID,
                title,
                thumbnail,
                author,
                tags,
                type,
                thumbsUpCount,
                entries
            )
        )
    }
}