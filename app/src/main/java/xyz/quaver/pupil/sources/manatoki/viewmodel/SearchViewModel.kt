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

package xyz.quaver.pupil.sources.manatoki.viewmodel

import android.app.Application
import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

@Parcelize
@Serializable
data class SearchResult(
    val itemID: String,
    val title: String,
    val thumbnail: String,
    val artist: String,
    val type: String,
    val lastUpdate: String
): Parcelable

val availablePublish = listOf(
    "주간",
    "격주",
    "월간",
    "단편",
    "단행본",
    "완결"
)

val availableJaum = listOf(
    "ㄱ",
    "ㄴ",
    "ㄷ",
    "ㄹ",
    "ㅁ",
    "ㅂ",
    "ㅅ",
    "ㅇ",
    "ㅈ",
    "ㅊ",
    "ㅋ",
    "ㅌ",
    "ㅍ",
    "ㅎ",
    "0-9",
    "a-z"
)

val availableTag = listOf(
    "17",
    "BL",
    "SF",
    "TS",
    "개그",
    "게임",
    "도박",
    "드라마",
    "라노벨",
    "러브코미디",
    "먹방",
    "백합",
    "붕탁",
    "순정",
    "스릴러",
    "스포츠",
    "시대",
    "애니화",
    "액션",
    "음악",
    "이세계",
    "일상",
    "전생",
    "추리",
    "판타지",
    "학원",
    "호러"
)

val availableSst = mapOf(
    "as_view" to "인기순",
    "as_good" to "추천순",
    "as_comment" to "댓글순",
    "as_bookmark" to "북마크순"
)

class SearchViewModel(app: Application) : AndroidViewModel(app), DIAware {
    override val di by closestDI(app)

    private val logger = newLogger(LoggerFactory.default)

    private val client: HttpClient by instance()

    // 발행
    var publish by mutableStateOf("")
    // 초성
    var jaum by mutableStateOf("")
    // 장르
    val tag = mutableStateMapOf<String, String>()
    // 정렬
    var sst by mutableStateOf("")
    // 오름/내림
    var sod by mutableStateOf("")
    // 제목
    var stx by mutableStateOf("")
    // 작가
    var artist by mutableStateOf("")

    var page by mutableStateOf(1)
    var maxPage by mutableStateOf(0)

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf(false)
        private set

    val result = mutableStateListOf<SearchResult>()

    private var searchJob: Job? = null
    suspend fun search(resetPage: Boolean = true) = coroutineScope {
        searchJob?.cancelAndJoin()

        loading = true
        result.clear()
        if (resetPage) page = 1

        searchJob = launch {
            runCatching {
                val urlBuilder = StringBuilder("https://manatoki116.net/comic")

                if (page != 1) urlBuilder.append("/p$page")

                val args = mutableListOf<String>()

                if (publish.isNotEmpty()) args.add("publish=$publish")
                if (jaum.isNotEmpty()) args.add("jaum=$jaum")
                if (tag.isNotEmpty()) args.add("tag=${tag.keys.joinToString(",")}")
                if (sst.isNotEmpty()) args.add("sst=$sst")
                if (stx.isNotEmpty()) args.add("stx=$stx")
                if (artist.isNotEmpty()) args.add("artist=$artist")

                if (args.isNotEmpty()) urlBuilder.append('?')

                urlBuilder.append(args.joinToString("&"))

                val doc = Jsoup.parse(client.get(urlBuilder.toString()))

                maxPage = doc.getElementsByClass("pagination").first()!!.getElementsByTag("a").maxOf { it.text().toIntOrNull() ?: 0 }

                doc.getElementsByClass("list-item").forEach {
                    val itemID =
                        it.selectFirst(".img-item > a")!!.attr("href").takeLastWhile { it != '/' }
                    val title = it.getElementsByClass("title").first()!!.text()
                    val thumbnail = it.getElementsByTag("img").first()!!.attr("src")
                    val artist = it.getElementsByClass("list-artist").first()!!.text()
                    val type = it.getElementsByClass("list-publish").first()!!.text()
                    val lastUpdate = it.getElementsByClass("list-date").first()!!.text()

                    loading = false
                    result.add(
                        SearchResult(
                            itemID,
                            title,
                            thumbnail,
                            artist,
                            type,
                            lastUpdate
                        )
                    )
                }
            }.onFailure {
                loading = false
                error = true
            }
        }
    }
}