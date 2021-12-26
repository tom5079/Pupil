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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import xyz.quaver.pupil.sources.manatoki.composable.Thumbnail
import xyz.quaver.pupil.sources.manatoki.manatokiUrl

class RecentViewModel(app: Application): AndroidViewModel(app), DIAware {
    override val di by closestDI(app)

    private val client: HttpClient by instance()

    var page by mutableStateOf(1)

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf(false)
        private set

    val result = mutableStateListOf<Thumbnail>()

    private var loadJob: Job? = null
    fun load() {
        viewModelScope.launch {
            loadJob?.cancelAndJoin()
            result.clear()
            loading = true

            loadJob = launch {
                runCatching {
                    val doc = Jsoup.parse(client.get("$manatokiUrl/bbs/page.php?hid=update&page=$page"))

                    doc.getElementsByClass("post-list").forEach {
                        val (itemID, title) = it.selectFirst(".post-subject > a")!!.let {
                            it.attr("href").takeLastWhile { it != '/' } to it.ownText()
                        }
                        val thumbnail = it.getElementsByTag("img").attr("src")

                        loading = false
                        result.add(Thumbnail(itemID, title, thumbnail))
                    }
                }.onFailure {
                    loading = false
                    error = true
                }
            }
        }
    }
}