/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
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
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.pupil.db.AppDatabase
import xyz.quaver.pupil.db.Bookmark
import xyz.quaver.pupil.db.History
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.util.NetworkCache
import xyz.quaver.pupil.util.source

@Suppress("UNCHECKED_CAST")
class ReaderViewModel(app: Application) : AndroidViewModel(app), DIAware {

    override val di by closestDI()

    private val cache: NetworkCache by instance()

    private val logger = newLogger(LoggerFactory.default)

    val isFullscreen = MutableLiveData(false)

    private val database: AppDatabase by instance()

    private val historyDao = database.historyDao()
    private val bookmarkDao = database.bookmarkDao()

    private val _source = MutableLiveData<String>()
    val source = _source as LiveData<String>

    private val _itemID = MutableLiveData<String>()
    val itemID = _itemID as LiveData<String>

    private val _title = MutableLiveData<String>()
    val title = _title as LiveData<String>

    private val totalProgressMutex = Mutex()
    var totalProgress by mutableStateOf(0)
        private set
    var imageCount by mutableStateOf(0)
        private set

    val imageList = mutableStateListOf<Uri?>()
    val progressList = mutableStateListOf<Float>()

    val isBookmarked = Transformations.switchMap(MediatorLiveData<Pair<Source, String>>().apply {
        addSource(source) { source -> itemID.value?.let { itemID -> source to itemID } }
        addSource(itemID) { itemID -> source.value?.let { source -> source to itemID } }
    }) { (source, itemID) ->
        bookmarkDao.contains(source.name, itemID)
    }

    val sourceInstance = Transformations.map(source) {
        direct.source(it)
    }

    val sourceIcon = Transformations.map(sourceInstance) {
        it.iconResID
    }

    /**
     * Parses source and itemID from the intent
     *
     * @throws IllegalStateException when the intent has no recognizable source and/or itemID
     */
    fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            val lastPathSegment = uri?.lastPathSegment
            if (uri != null && lastPathSegment != null) {
                _source.value = uri.host ?: error("Source cannot be null")
                _itemID.value = when (uri.host) {
                    "hitomi.la" ->
                        Regex("([0-9]+).html").find(lastPathSegment)?.groupValues?.get(1) ?: error("Invalid itemID")
                    "hiyobi.me" -> lastPathSegment
                    "e-hentai.org" -> uri.pathSegments[1]
                    else -> error("Invalid host")
                }
            }
        } else {
            _source.value = intent.getStringExtra("source") ?: error("Invalid source")
            _itemID.value = intent.getStringExtra("id") ?: error("Invalid itemID")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun load() {
        val source: Source by source(source.value ?: return)
        val itemID = itemID.value ?: return

        viewModelScope.launch {
            launch(Dispatchers.IO) {
                historyDao.insert(History(source.name, itemID))
            }
        }

        viewModelScope.launch {
            _title.value = withContext(Dispatchers.IO) {
                source.info(itemID)
            }.title
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                source.images(itemID)
            }.let { images ->
                imageCount = images.size

                progressList.addAll(List(imageCount) { 0f })
                imageList.addAll(List(imageCount) { null })

                images.forEachIndexed { index, image ->
                    when (val scheme = image.takeWhile { it != ':' }) {
                        "http", "https" -> {
                            val file = cache.load {
                                url(image)
                                headers(source.getHeadersBuilderForImage(itemID, image))
                            }

                            val channel = cache.channels[image] ?: error("Channel is null")

                            if (channel.isClosedForReceive) {
                                imageList[index] = Uri.fromFile(file)
                                totalProgressMutex.withLock {
                                    totalProgress++
                                }
                            } else {
                                channel.invokeOnClose { e ->
                                    viewModelScope.launch {
                                        if (e == null) {
                                            imageList[index] = Uri.fromFile(file)
                                            totalProgressMutex.withLock {
                                                totalProgress++
                                            }
                                        } else {
                                            TODO("Handle error")
                                        }
                                    }
                                }

                                launch {
                                    kotlin.runCatching {
                                        for (progress in channel) {
                                            progressList[index] = progress
                                        }
                                    }
                                }
                            }
                        }
                        "content" -> {
                            progressList[index] = 1f
                        }
                        else -> throw IllegalArgumentException("Expected URL scheme 'http(s)' or 'content' but was '$scheme'")
                    }
                }
            }
        }
    }

    fun error(index: Int) {
        progressList[index] = -1f
    }

    fun toggleBookmark() {
        val bookmark = source.value?.let { source -> itemID.value?.let { itemID -> Bookmark(source, itemID) } } ?: return

        CoroutineScope(Dispatchers.IO).launch {
            if (bookmarkDao.contains(bookmark).value ?: return@launch)
                bookmarkDao.delete(bookmark)
            else
                bookmarkDao.insert(bookmark)
        }
    }

}