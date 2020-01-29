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
import android.util.SparseArray
import androidx.preference.PreferenceManager
import com.crashlytics.android.Crashlytics
import io.fabric.sdk.android.Fabric
import kotlinx.coroutines.*
import okhttp3.*
import okio.*
import xyz.quaver.hitomi.Reader
import xyz.quaver.hitomi.getReferer
import xyz.quaver.hitomi.urlFromUrlFromHash
import xyz.quaver.hiyobi.cookie
import xyz.quaver.hiyobi.createImgList
import xyz.quaver.hiyobi.user_agent
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

@UseExperimental(ExperimentalCoroutinesApi::class)
class DownloadWorker private constructor(context: Context) : ContextWrapper(context) {

    private val preferences : SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

    //region ProgressListener
    @Suppress("UNCHECKED_CAST")
    private val progressListener = object: ProgressListener {
        override fun update(tag: Any?, bytesRead: Long, contentLength: Long, done: Boolean) {
            val (galleryID, index) = (tag as? Pair<Int, Int>) ?: return

            if (!done && progress[galleryID]!![index] != Float.POSITIVE_INFINITY)
                progress[galleryID]!![index] = bytesRead * 100F / contentLength
        }
    }

    interface ProgressListener {
        fun update(tag: Any?, bytesRead : Long, contentLength: Long, done: Boolean)
    }

    class ProgressResponseBody(
        val tag: Any?,
        val responseBody: ResponseBody,
        val progressListener : ProgressListener
    ) : ResponseBody() {
        private var bufferedSource : BufferedSource? = null

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

    val queue = LinkedBlockingQueue<Int>()

    /*
    * KEY
    *  primary galleryID
    *  secondary index
    * PRIMARY VALUE
    *  MutableList -> Download in progress
    *  null -> Loading / Gallery doesn't exist
    * SECONDARY VALUE
    *  0 <= value < 100 -> Download in progress
    *  Float.POSITIVE_INFINITY -> Download completed
    *  Float.NaN -> Exception
    */
    val progress = SparseArray<MutableList<Float>?>()
    /*
    * KEY
    *  primary galleryID
    *  secondary index
    * PRIMARY VALUE
    *  MutableList -> Download in progress / Loading
    *  null -> Gallery doesn't exist
    * SECONDARY VALUE
    *  Throwable -> Exception
    *  null -> Download in progress / Loading
    */
    val exception = SparseArray<MutableList<Throwable?>?>()

    private val loop = loop()
    private val worker = SparseArray<Job?>()
    @Volatile var nRunners = 0

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            var response = chain.proceed(request)

            var retry = preferences.getInt("retry", 3)
            while (!response.isSuccessful && retry > 0) {
                response.close()
                response = chain.proceed(request)
                retry--
            }

            response.newBuilder()
                .body(ProgressResponseBody(request.tag(), response.body!!, progressListener))
                .build()
        }
        .dispatcher(Dispatcher(Executors.newSingleThreadExecutor()))
        .build()

    fun stop() {
        loop.cancel()
        for (i in 0..worker.size())
            worker[worker.keyAt(i)]?.cancel()

        client.dispatcher.cancelAll()
    }

    fun cancel(galleryID: Int) {
        worker[galleryID]?.cancel()

        client.dispatcher.queuedCalls()
            .filter { it.request().tag(Pair::class.java)?.first == galleryID }
            .forEach {
                it.cancel()
            }
    }

    private fun queueDownload(galleryID: Int, reader: Reader, index: Int, callback: Callback) {
        val cache = Cache(this@DownloadWorker).getImages(galleryID)
        val lowQuality = preferences.getBoolean("low_quality", false)

        //Cache exists :P
        cache?.get(index)?.let {
            progress[galleryID]!![index] = Float.POSITIVE_INFINITY

            return
        }

        val request = Request.Builder().apply {
            when (reader.code) {
                Reader.Code.HITOMI -> {
                    url(
                        urlFromUrlFromHash(
                            galleryID,
                            reader.galleryInfo[index],
                            if (lowQuality) "webp" else null
                        )
                    )
                    addHeader("Referer", getReferer(galleryID))
                }
                Reader.Code.HIYOBI -> {
                    url(createImgList(galleryID, reader, lowQuality)[index].path)
                    addHeader("User-Agent", user_agent)
                    addHeader("Cookie", cookie)
                }
                else -> {
                    //shouldn't be called anyway
                }
            }
            tag(galleryID to index)
        }.build()

        client.newCall(request).enqueue(callback)
    }

    private fun download(galleryID: Int) = CoroutineScope(Dispatchers.IO).launch {
        val reader = Cache(this@DownloadWorker).getReader(galleryID)

        //gallery doesn't exist
        if (reader == null) {
            progress.put(galleryID, null)
            exception.put(galleryID, null)
            nRunners--
            return@launch
        }

        progress.put(galleryID, reader.galleryInfo.map { 0F }.toMutableList())
        exception.put(galleryID, reader.galleryInfo.map { null }.toMutableList())

        for (i in reader.galleryInfo.indices) {
            val callback = object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (Fabric.isInitialized())
                        Crashlytics.logException(e)

                    progress[galleryID]!![i] = Float.NaN
                    exception[galleryID]!![i] = e

                    if (progress[galleryID]!!.all { !it.isFinite() })
                        nRunners--
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val res = it.body!!.bytes()
                        val ext =
                            call.request().url.encodedPath.split('.').last()

                        Cache(this@DownloadWorker).putImage(galleryID, "$i.$ext", res)
                        progress[galleryID]!![i] = Float.POSITIVE_INFINITY
                    }

                    if (progress[galleryID]!!.all { !it.isFinite() })
                        nRunners--
                }
            }

            queueDownload(galleryID, reader, i, callback)
        }
    }

    private fun loop() = CoroutineScope(Dispatchers.Default).launch {
        while (true) {
            if (queue.isEmpty() || nRunners > preferences.getInt("max_download", 4))
                continue

            val galleryID = queue.poll() ?: continue

            worker.put(galleryID, download(galleryID))
            nRunners++
        }
    }

}