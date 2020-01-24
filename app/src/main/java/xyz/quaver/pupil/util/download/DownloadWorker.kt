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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.*
import java.util.concurrent.Executors

class DownloadWorker(context: Context) : ContextWrapper(context) {

    interface ProgressListener {
        fun update(bytesRead : Long, contentLength: Long, done: Boolean)
    }

    //region ProgressResponseBody
    class ProgressResponseBody(
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
                progressListener.update(totalBytesRead, responseBody.contentLength(), bytesRead == -1L)

                return bytesRead
            }

        }
    }
    //endregion

    val queue = Channel<Int>()
    val worker = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

    val progressListener = object: ProgressListener {
        override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {

        }
    }
    val client = OkHttpClient.Builder()
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).let { originalResponse ->
                originalResponse.newBuilder()
                    .body(ProgressResponseBody(originalResponse.body!!, progressListener))
                    .build()
            }
        }.build()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val galleryID = queue.receive()

                val reader = Cache(context).getReaders(galleryID)
            }
        }
    }

}