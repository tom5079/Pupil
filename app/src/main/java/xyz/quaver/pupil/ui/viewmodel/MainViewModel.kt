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

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.*
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI
import org.kodein.di.direct
import org.kodein.di.instance
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.pupil.sources.*
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.source
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

@Suppress("UNCHECKED_CAST")
class MainViewModel(app: Application) : AndroidViewModel(app), DIAware {
    override val di by closestDI()

    private val _searchResults = MutableLiveData<List<ItemInfo>>()
    val searchResults = _searchResults as LiveData<List<ItemInfo>>

    private val _loading = MutableLiveData(false)
    val loading = _loading as LiveData<Boolean>

    private var queryJob: Job? = null
    private var suggestionJob: Job? = null

    val query = MutableLiveData<String>()
    private val queryStack = mutableListOf<String>()

    private val defaultSourceFactory: (String) -> Source = {
        direct.source(it)
    }
    private var sourceFactory: (String) -> Source = defaultSourceFactory
    private val _source = MutableLiveData<Source>()
    val source: LiveData<Source> = _source

    val availableSortMode = Transformations.map(_source) {
        it.availableSortMode
    }

    val sortModeIndex = MutableLiveData<Int>()

    val sourceIcon = Transformations.map(_source) {
        it.iconResID
    }

    private val _currentPage = MutableLiveData<Int>()
    val currentPage: LiveData<Int> = _currentPage

    private val totalItems = MutableLiveData<Int>()

    val totalPages = Transformations.map(totalItems) {
        val perPage = Preferences["per_page", "25"].toInt()

        ceil(it / perPage.toDouble()).roundToInt()
    }

    private val _suggestions = MutableLiveData<List<SearchSuggestion>>()
    val suggestions: LiveData<List<SearchSuggestion>> = _suggestions

    init {
        setSourceAndReset("hitomi.la")
    }

    fun setSourceAndReset(sourceName: String) {
        _source.value = sourceFactory(sourceName).also {
            sortModeIndex.value = 0
        }

        setQueryAndSearch()
    }

    fun setQueryAndSearch(query: String = "") {
        this.query.value = query
        queryStack.add(query)
        setPage(1)

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
                else -> source.value!!.name
            }
        )
    }

    fun query() {
        val perPage = Preferences["per_page", "25"].toInt()
        val source = _source.value ?: error("Source is null")
        val sortModeIndex = sortModeIndex.value ?: 0
        val currentPage = currentPage.value ?: 1

        suggestionJob?.cancel()
        queryJob?.cancel()

        _loading.value = true
        val results = mutableListOf<ItemInfo>()
        _searchResults.value = results

        queryJob = viewModelScope.launch {
            val channel = withContext(Dispatchers.IO) {
                val (channel, count) = source.search(
                    query.value ?: "",
                    (currentPage - 1) * perPage until currentPage * perPage,
                    sortModeIndex
                )

                totalItems.postValue(count)

                channel
            }

            for (result in channel) {
                yield()
                results.add(result)
                _searchResults.value = results.toList()
            }

            _loading.value = false
        }
    }

    fun prevPage() { _currentPage.value = _currentPage.value!! - 1 }
    fun nextPage() { _currentPage.value = _currentPage.value!! + 1 }
    fun setPage(page: Int) { _currentPage.value = page }

    fun random(callback: (ItemInfo) -> Unit) {
        if (totalItems.value!! == 0)
            return

        val random = Random.Default.nextInt(totalItems.value!!)

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _source.value?.search(
                    query.value + Preferences["default_query", ""],
                    random .. random,
                    sortModeIndex.value!!
                )?.first?.receive()
            }?.let(callback)
        }
    }

    fun suggestion() {
        suggestionJob?.cancel()

        _suggestions.value = mutableListOf()

        suggestionJob = viewModelScope.launch {
            @SuppressLint("NullSafeMutableLiveData")
            _suggestions.value = withContext(Dispatchers.IO) {
                kotlin.runCatching {
                    _source.value!!.suggestion(query.value!!)
                }.getOrElse { emptyList() }
            }
        }
    }

    /**
     * @return true if backpress is consumed, false otherwise
     */
    fun onBackPressed(): Boolean {
        if (queryStack.removeLastOrNull() == null || queryStack.isEmpty())
            return false

        setQueryAndSearch(queryStack.removeLast())
        return true
    }

    enum class MainMode {
        SEARCH,
        HISTORY,
        DOWNLOADS,
        FAVORITES
    }

}