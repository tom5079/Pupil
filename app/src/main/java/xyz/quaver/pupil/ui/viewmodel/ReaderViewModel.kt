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
import androidx.compose.runtime.*
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
import xyz.quaver.pupil.db.History
import xyz.quaver.pupil.sources.ItemInfo
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.util.NetworkCache
import xyz.quaver.pupil.util.source

@Suppress("UNCHECKED_CAST")
class ReaderViewModel(app: Application) : AndroidViewModel(app), DIAware {

    override val di by closestDI()

    private val cache: NetworkCache by instance()

    private val logger = newLogger(LoggerFactory.default)

    var isFullscreen by mutableStateOf(false)

    private val database: AppDatabase by instance()

    private val historyDao = database.historyDao()
    private val bookmarkDao = database.bookmarkDao()

    lateinit var bookmark: LiveData<Boolean>
        private set

    var error by mutableStateOf(false)
        private set

    var source by mutableStateOf<Source?>(null)
        private set
    var itemID by mutableStateOf<String?>(null)
        private set
    var title by mutableStateOf<String?>(null)
        private set

    private val totalProgressMutex = Mutex()
    var totalProgress by mutableStateOf(0)
        private set
    var imageCount by mutableStateOf(0)
        private set

    private var images: List<String>? = null
    val imageList = mutableStateListOf<Uri?>()
    val progressList = mutableStateListOf<Float>()

    val sourceIcon by derivedStateOf {
        source?.iconResID
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
                source = uri.host?.let { direct.source(it) } ?: error("Invalid host")
                itemID = when (uri.host) {
                    "hitomi.la" ->
                        Regex("([0-9]+).html").find(lastPathSegment)?.groupValues?.get(1) ?: error("Invalid itemID")
                    "hiyobi.me" -> lastPathSegment
                    "e-hentai.org" -> uri.pathSegments[1]
                    else -> error("Invalid host")
                }
            }
        } else {
            source = intent.getStringExtra("source")?.let { direct.source(it) } ?: error("Invalid source")
            itemID = intent.getStringExtra("id") ?: error("Invalid itemID")
            title = intent.getParcelableExtra<ItemInfo>("payload")?.title
        }

        bookmark = bookmarkDao.contains(source!!.name, itemID!!)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun load() {
        val source = source ?: return
        val itemID = itemID ?: return

        viewModelScope.launch {
            launch(Dispatchers.IO) {
                historyDao.insert(History(source.name, itemID))
            }
        }

        viewModelScope.launch {
            if (title == null)
                title = withContext(Dispatchers.IO) {
                    kotlin.runCatching {
                        source.info(itemID)
                    }.getOrNull()
                }?.title
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                kotlin.runCatching {
                    source.images(itemID)
                }.onFailure {
                    error = true
                }.getOrNull()
            }?.let { images ->
                this@ReaderViewModel.images = images

                imageCount = images.size

                progressList.addAll(List(imageCount) { 0f })
                imageList.addAll(List(imageCount) { null })
                totalProgressMutex.withLock {
                    totalProgress = 0
                }

                images.forEachIndexed { index, image ->
                    logger.info {
                        progressList.toList().toString()
                    }
                    when (val scheme = image.takeWhile { it != ':' }) {
                        "http", "https" -> {
                            val (channel, file) = cache.load {
                                url(image)
                                headers(source.getHeadersBuilderForImage(itemID, image))
                            }

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
                                            error(index)
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
                            imageList[index] = Uri.parse(image)
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
        source?.name?.let { source ->
        itemID?.let { itemID ->
        bookmark.value?.let { bookmark ->
            CoroutineScope(Dispatchers.IO).launch {
                if (bookmark) bookmarkDao.delete(source, itemID)
                else          bookmarkDao.insert(source, itemID)
            }
        } } }
    }

    override fun onCleared() {
        cache.cleanup()
        images?.let { cache.free(it) }
    }

}