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

package xyz.quaver.pupil.util.download

import android.content.Context
import android.content.ContextWrapper
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.*
import java.util.concurrent.Executors

@UseExperimental(ExperimentalCoroutinesApi::class)
class DownloadWorker(context: Context) : ContextWrapper(context) {

    val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    //region ProgressListener
    interface ProgressListener {
        fun update(tag: Any?, bytesRead : Long, contentLength: Long, done: Boolean)
    }

    class ProgressResponseBody(
        val tag: Any?,
        val responseBody: ResponseBody,
        val progressListener : ProgressListener
    ) : ResponseBody() {
        var bufferedSource : BufferedSource? = null

        override fun contentLength() = responseBody.contentLength()
        override fun contentType() = responseBody.contentType()

        override fun source(): BufferedSource {
            if (bufferedSource == null)
                bufferedSource = source(responseBody.source()).buffer()

            return bufferedSource!!
        }

        private fun source(source: Source) = object: ForwardingSource(source) {

            var totalBytesRead = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)

                totalBytesRead += if (bytesRead == -1L) 0L else bytesRead
                progressListener.update(tag, totalBytesRead, responseBody.contentLength(), bytesRead == -1L)

                return bytesRead
            }

        }
    }
    //endregion

    val queue = Channel<Int>()
    val progress = mutableMapOf<String, Double>()
    val worker = Executors.newCachedThreadPool().asCoroutineDispatcher()

    val progressListener = object: ProgressListener {
        override fun update(tag: Any?, bytesRead: Long, contentLength: Long, done: Boolean) {

        }
    }
    val client = OkHttpClient.Builder()
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            var response = chain.proceed(request)

            var retry = preferences.getInt("retry", 3)
            while (!response.isSuccessful && retry > 0) {
                response = chain.proceed(request)
                retry--
            }

            response.newBuilder()
                .body(ProgressResponseBody(request.tag(), response.body!!, progressListener))
                .build()
        }.build()

    init {
        val maxThread = preferences.getInt("max_thread", 4)

        CoroutineScope(Dispatchers.Unconfined).launch {
            while (!(queue.isEmpty && queue.isClosedForReceive)) {
                val lowQuality = preferences.getBoolean("low_quality", false)
                val galleryID = queue.receive()

                launch(Dispatchers.IO) {
                    val reader = Cache(context).getReader(galleryID) ?: return@launch

                    reader.galleryInfo.forEachIndexed { index, galleryInfo ->
                        when(reader.code) {

                        }
                    }
                }
            }
        }
    }

}