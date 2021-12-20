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
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.pupil.sources.manatoki.composable.Thumbnail
import xyz.quaver.pupil.sources.manatoki.waitForRateLimit

@Serializable
data class TopWeekly(
    val itemID: String,
    val title: String,
    val count: String
)

class MainViewModel(app: Application) : AndroidViewModel(app), DIAware {
    override val di by closestDI(app)

    private val logger = newLogger(LoggerFactory.default)

    private val client: HttpClient by instance()

    val recentUpload = mutableStateListOf<Thumbnail>()
    val mangaList = mutableStateListOf<Thumbnail>()
    val topWeekly = mutableStateListOf<TopWeekly>()

    private var loadJob: Job? = null

    fun load() {
        viewModelScope.launch {
            loadJob?.cancelAndJoin()
            recentUpload.clear()
            mangaList.clear()
            topWeekly.clear()

            loadJob = launch {
                runCatching {
                    waitForRateLimit()
                    val doc = Jsoup.parse(client.get("https://manatoki116.net/"))

                    yield()

                    val misoPostGallery = doc.select(".miso-post-gallery")

                    misoPostGallery[0]
                        .select(".post-image > a")
                        .forEach { entry ->
                            val itemID = entry.attr("href").takeLastWhile { it != '/' }
                            val title = entry.selectFirst("div.in-subject > b")!!.ownText()
                            val thumbnail = entry.selectFirst("img")!!.attr("src")

                            yield()
                            recentUpload.add(Thumbnail(itemID, title, thumbnail))
                        }

                    misoPostGallery[1]
                        .select(".post-image > a").also { logger.info { it.size.toString() } }
                        .forEach { entry ->
                            val itemID = entry.attr("href").takeLastWhile { it != '/' }
                            val title = entry.selectFirst("div.in-subject")!!.ownText()
                            val thumbnail = entry.selectFirst("img")!!.attr("src")

                            yield()
                            mangaList.add(Thumbnail(itemID, title, thumbnail))
                        }

                    val misoPostList = doc.select(".miso-post-list")

                    misoPostList[4]
                        .select(".post-row > a")
                        .forEach { entry ->
                            yield()
                            val itemID = entry.attr("href").takeLastWhile { it != '/' }
                            val title = entry.ownText()
                            val count = entry.selectFirst("span.count")!!.text()
                            topWeekly.add(TopWeekly(itemID, title, count))
                        }
                }.onFailure {
                    logger.warning(it)
                }
            }
        }
    }
}