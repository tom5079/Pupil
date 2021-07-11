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
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.orhanobut.logger.Logger
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI
import org.kodein.di.instance
import xyz.quaver.pupil.adapters.ReaderItem
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.util.ImageCache
import xyz.quaver.pupil.util.notify
import xyz.quaver.pupil.util.source

@Suppress("UNCHECKED_CAST")
class ReaderViewModel(app: Application) : AndroidViewModel(app), DIAware {

    override val di by closestDI()

    private val cache: ImageCache by instance()

    private val _title = MutableLiveData<String>()
    val title = _title as LiveData<String>

    private val _images = MutableLiveData<List<String>>()
    val images: LiveData<List<String>> = _images

    private var _readerItems = MutableLiveData<MutableList<ReaderItem>>()
    val readerItems = _readerItems as LiveData<List<ReaderItem>>

    @OptIn(ExperimentalCoroutinesApi::class)
    fun load(sourceName: String, itemID: String) {
        val source: Source by source(sourceName)

        viewModelScope.launch {
            _title.value = withContext(Dispatchers.IO) {
                source.info(itemID)
            }.title
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                source.images(itemID)
            }.let { images ->
                _readerItems.value = MutableList(images.size) { ReaderItem(0F, null) }
                _images.value = images

                images.forEachIndexed { index, image ->
                    when (val scheme = image.takeWhile { it != ':' }) {
                        "http", "https" -> {
                            val file = cache.load {
                                url(image)
                                headers(source.getHeadersBuilderForImage(itemID, image))
                            }

                            val channel = cache.channels[image] ?: error("Channel is null")

                            if (channel.isClosedForReceive) {
                                _readerItems.value!![index] =
                                    ReaderItem(_readerItems.value!![index].progress, Uri.fromFile(file))
                                _readerItems.notify()
                            } else {
                                channel.invokeOnClose { e ->
                                    viewModelScope.launch {
                                        if (e == null) {
                                            _readerItems.value!![index] =
                                                ReaderItem(_readerItems.value!![index].progress, Uri.fromFile(file))
                                            _readerItems.notify()
                                        } else {
                                            Logger.e(index.toString())
                                            Logger.e(e, e.message ?: "")
                                        }
                                    }
                                }

                                launch {
                                    kotlin.runCatching {
                                        for (progress in channel) {
                                            _readerItems.value!![index] =
                                                ReaderItem(progress, _readerItems.value!![index].image)
                                            _readerItems.notify()
                                        }
                                    }
                                }
                            }
                        }
                        "content" -> {
                            _readerItems.value!![index] = ReaderItem(100f, Uri.parse(image))
                            _readerItems.notify()
                        }
                        else -> throw IllegalArgumentException("Expected URL scheme 'http(s)' or 'content' but was '$scheme'")
                    }
                }
            }
        }
    }

    override fun onCleared() {
        CoroutineScope(Dispatchers.IO).launch {
            cache.cleanup()
            images.value?.let { cache.free(it) }
        }
    }

}