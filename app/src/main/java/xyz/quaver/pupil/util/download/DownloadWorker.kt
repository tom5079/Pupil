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
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.crashlytics.android.Crashlytics
import io.fabric.sdk.android.Fabric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.*
import okio.*
import xyz.quaver.hitomi.Reader
import xyz.quaver.hitomi.urlFromUrlFromHash
import xyz.quaver.hiyobi.createImgList
import java.io.FileInputStream
import java.io.IOException

@UseExperimental(ExperimentalCoroutinesApi::class)
class DownloadWorker private constructor(context: Context) : ContextWrapper(context) {

    val preferences : SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

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

    //region Singleton
    companion object {

        @Volatile private var instance: DownloadWorker? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: DownloadWorker(context).also { instance = it }
            }
    }
    //endregion

    val queue = Channel<Int>()
    /* VALUE
    *  0 <= value < 100 -> Download in progress
    *  Float.POSITIVE_INFINITY -> Download completed
    *  Float.NaN -> Exception
    */
    val progress = mutableMapOf<String, Float>()
    val result = mutableMapOf<String, ByteArray>()
    val exception = mutableMapOf<String, Throwable>()

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


    val progressListener = object: ProgressListener {
        override fun update(tag: Any?, bytesRead: Long, contentLength: Long, done: Boolean) {
            if (tag !is String)
                return

            if (progress[tag] != Float.POSITIVE_INFINITY)
                progress[tag] = bytesRead / contentLength.toFloat()
        }
    }
    init {
        CoroutineScope(Dispatchers.Unconfined).launch {
            while (!(queue.isEmpty && queue.isClosedForReceive)) {
                val lowQuality = preferences.getBoolean("low_quality", false)
                val galleryID = queue.receive()

                launch(Dispatchers.IO) io@{
                    val reader = Cache(context).getReader(galleryID) ?: return@io
                    val cache = Cache(context).getImages(galleryID)

                    reader.galleryInfo.forEachIndexed { index, galleryInfo ->
                        val tag = "$galleryID-$index"
                        val url = when(reader.code) {
                            Reader.Code.HITOMI ->
                                urlFromUrlFromHash(galleryID, galleryInfo, if (lowQuality) "webp" else null)
                            Reader.Code.HIYOBI ->
                                createImgList(galleryID, reader, lowQuality)[index].path
                            else -> ""  //Shouldn't be called anyways
                        }

                        //Cache exists :P
                        cache?.get(index)?.let {
                            result[tag] = FileInputStream(it).readBytes()
                            progress[tag] = Float.POSITIVE_INFINITY

                            return@io
                        }

                        val request = Request.Builder()
                            .url(url)
                            .tag(tag)
                            .build()

                        client.newCall(request).enqueue(object: Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                if (Fabric.isInitialized())
                                    Crashlytics.logException(e)

                                progress[tag] = Float.NaN
                                exception[tag] = e
                            }

                            override fun onResponse(call: Call, response: Response) {
                                response.use {
                                    result[tag] = (it.body?: return).bytes()
                                    progress[tag] = Float.POSITIVE_INFINITY
                                }
                            }
                        })
                    }
                }
            }
        }
    }

}