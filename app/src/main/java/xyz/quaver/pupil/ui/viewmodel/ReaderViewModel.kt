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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import okhttp3.Headers
import okhttp3.Request
import org.kodein.di.DIAware
import org.kodein.di.android.x.di
import org.kodein.di.instance
import xyz.quaver.pupil.adapters.ReaderItem
import xyz.quaver.pupil.sources.AnySource
import xyz.quaver.pupil.util.ImageCache
import xyz.quaver.pupil.util.notify
import xyz.quaver.pupil.util.source

@Suppress("UNCHECKED_CAST")
class ReaderViewModel(app: Application) : AndroidViewModel(app), DIAware {

    override val di by di()

    private val cache: ImageCache by instance()

    private val _title = MutableLiveData<String>()
    val title = _title as LiveData<String>

    private val _images = MutableLiveData<List<String>>()
    val images: LiveData<List<String>> = _images

    private var _readerItems = MutableLiveData<MutableList<ReaderItem>>()
    val readerItems = _readerItems as LiveData<List<ReaderItem>>

    @OptIn(ExperimentalCoroutinesApi::class)
    fun load(sourceName: String, itemID: String) {
        val source: AnySource by source(sourceName)

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
                    val file = cache.load(
                        Request.Builder()
                            .url(image)
                            .headers(Headers.of(source.getHeadersForImage(itemID, image)))
                            .build()
                    )

                    val channel = cache.channels[image] ?: error("Channel is null")

                    channel.invokeOnClose { e ->
                        viewModelScope.launch {
                            if (e == null) {
                                _readerItems.value!![index] = ReaderItem(_readerItems.value!![index].progress, file)
                                _readerItems.notify()
                            }
                        }
                    }

                    launch {
                        for (progress in channel) {
                            _readerItems.value!![index] = ReaderItem(progress, _readerItems.value!![index].image)
                            _readerItems.notify()
                        }
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