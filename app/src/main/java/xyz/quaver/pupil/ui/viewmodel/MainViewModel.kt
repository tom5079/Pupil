/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2021  tom5079
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
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.pupil.proto.settingsDataStore
import xyz.quaver.pupil.sources.History
import xyz.quaver.pupil.sources.ItemInfo
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.util.source
import kotlin.math.ceil
import kotlin.random.Random

@Suppress("UNCHECKED_CAST")
class MainViewModel(app: Application) : AndroidViewModel(app), DIAware {
    override val di by closestDI()

    private val logger = newLogger(LoggerFactory.default)

    val searchResults = mutableStateListOf<ItemInfo>()

    private val resultsPerPage = app.settingsDataStore.data.map {
        it.resultsPerPage
    }

    var loading by mutableStateOf(false)
        private set

    private var queryJob: Job? = null
    private var suggestionJob: Job? = null

    var query by mutableStateOf("")
    private val queryStack = mutableListOf<String>()

    private val defaultSourceFactory: (String) -> Source = {
        direct.source(it)
    }
    private var sourceFactory: (String) -> Source = defaultSourceFactory
    var source by mutableStateOf(sourceFactory("hitomi.la"))
        private set

    var sortModeIndex by mutableStateOf(0)
        private set

    var currentPage by mutableStateOf(1)

    var totalItems by mutableStateOf(0)
        private set

    val maxPage by derivedStateOf {
        resultsPerPage.map {
            ceil(totalItems / it.toDouble()).toInt()
        }
    }

    fun setSourceAndReset(sourceName: String) {
        source = sourceFactory(sourceName)
        sortModeIndex = 0

        query = ""
        resetAndQuery()
    }

    fun resetAndQuery() {
        queryStack.add(query)
        currentPage = 1

        query()
    }

    fun setModeAndReset(mode: MainMode) {
        sourceFactory = when (mode) {
            MainMode.SEARCH, MainMode.DOWNLOADS -> defaultSourceFactory
            MainMode.HISTORY -> { { direct.instance<String, History>(arg = it) } }
            else -> return
        }

        setSourceAndReset(
            when {
                mode == MainMode.DOWNLOADS -> "downloads"
                //source.value is Downloads -> "hitomi.la"
                else -> source.name
            }
        )
    }

    fun query() {
        suggestionJob?.cancel()
        queryJob?.cancel()

        loading = true
        searchResults.clear()

        queryJob = viewModelScope.launch {
            val resultsPerPage = resultsPerPage.first()

            logger.info {
                resultsPerPage.toString()
            }

            val (channel, count) = source.search(
                query,
                (currentPage - 1) * resultsPerPage until currentPage * resultsPerPage,
                sortModeIndex
            )

            totalItems = count

            for (result in channel) {
                yield()
                searchResults.add(result)
            }

            loading = false
        }
    }

    fun random(callback: (ItemInfo) -> Unit) {
        if (totalItems == 0)
            return

        val random = Random.Default.nextInt(totalItems)

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                source.search(
                    query,
                    random .. random,
                    sortModeIndex
                ).first.receive()
            }.let(callback)
        }
    }

    /**
     * @return true if backpress is consumed, false otherwise
     */
    fun onBackPressed(): Boolean {
        if (queryStack.removeLastOrNull() == null || queryStack.isEmpty())
            return false

        query = queryStack.removeLast()
        resetAndQuery()
        return true
    }

    enum class MainMode {
        SEARCH,
        HISTORY,
        DOWNLOADS,
        FAVORITES
    }

}