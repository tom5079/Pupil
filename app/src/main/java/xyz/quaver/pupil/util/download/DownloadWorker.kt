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

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.util.SparseArray
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.preference.PreferenceManager
import com.crashlytics.android.Crashlytics
import io.fabric.sdk.android.Fabric
import kotlinx.coroutines.*
import okhttp3.*
import okio.*
import xyz.quaver.Code
import xyz.quaver.hitomi.Reader
import xyz.quaver.hitomi.getReferer
import xyz.quaver.hitomi.imageUrlFromImage
import xyz.quaver.hiyobi.cookie
import xyz.quaver.hiyobi.createImgList
import xyz.quaver.hiyobi.user_agent
import xyz.quaver.proxy
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.ReaderActivity
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@UseExperimental(ExperimentalCoroutinesApi::class)
class DownloadWorker private constructor(context: Context) : ContextWrapper(context) {

    private val preferences : SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

    //region ProgressListener
    @Suppress("UNCHECKED_CAST")
    private val progressListener = object: ProgressListener {
        override fun update(tag: Any?, bytesRead: Long, contentLength: Long, done: Boolean) {
            val (galleryID, index) = (tag as? Pair<Int, Int>) ?: return

            if (!done && progress[galleryID]?.get(index)?.isFinite() == true)
                progress[galleryID]?.set(index, bytesRead * 100F / contentLength)
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
        override fun contentType() = responseBody.contentType() ?: null

        override fun source(): BufferedSource {
            if (bufferedSource == null)
                bufferedSource = Okio.buffer(source(responseBody.source()))

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

    val notificationManager = NotificationManagerCompat.from(context)

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
    val notification = SparseArray<NotificationCompat.Builder>()

    private val loop = loop()
    private val worker = SparseArray<Job?>()
    val clients = SparseArray<OkHttpClient>()

    val interceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        response.newBuilder()
            .body(ProgressResponseBody(request.tag(), response.body(), progressListener))
            .build()
    }
    fun buildClient() =
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .dispatcher(Dispatcher(Executors.newFixedThreadPool(4)))
            .proxy(proxy)
            .build()

    fun stop() {
        queue.clear()

        loop.cancel()
        for (i in 0..worker.size()) {
            val galleryID = worker.keyAt(i)

            Cache(this@DownloadWorker).setDownloading(galleryID, false)
            worker[galleryID]?.cancel()
        }

        for (i in 0 until clients.size()) {
            clients.valueAt(i).dispatcher().cancelAll()
        }
        clients.clear()

        progress.clear()
        exception.clear()
        notification.clear()
        notificationManager.cancelAll()
    }

    fun cancel(galleryID: Int) {
        queue.remove(galleryID)
        worker[galleryID]?.cancel()

        clients[galleryID]?.dispatcher()?.cancelAll()
        clients.remove(galleryID)

        progress.remove(galleryID)
        exception.remove(galleryID)
        notification.remove(galleryID)
        notificationManager.cancel(galleryID)

        if (progress.indexOfKey(galleryID) >= 0) {
            Cache(this@DownloadWorker).setDownloading(galleryID, false)
        }
    }

    fun isCompleted(galleryID: Int) = progress[galleryID]?.all { !it.isFinite() } == true

    private fun queueDownload(galleryID: Int, reader: Reader, index: Int, callback: Callback) {
        val lowQuality = preferences.getBoolean("low_quality", false)

        val request = Request.Builder().apply {
            when (reader.code) {
                Code.HITOMI -> {
                    url(
                        imageUrlFromImage(
                            galleryID,
                            reader.galleryInfo.files[index],
                            lowQuality
                        )
                    )
                    addHeader("Referer", getReferer(galleryID))
                }
                Code.HIYOBI -> {
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

        if (clients.get(galleryID) == null)
            clients.put(galleryID, buildClient())

        clients[galleryID]?.newCall(request)?.enqueue(callback)
    }

    private fun download(galleryID: Int) = CoroutineScope(Dispatchers.IO).launch {
        val reader = Cache(this@DownloadWorker).getReader(galleryID)

        //gallery doesn't exist
        if (reader == null) {
            progress.put(galleryID, null)
            exception.put(galleryID, null)

            Cache(this@DownloadWorker).setDownloading(galleryID, false)
            return@launch
        }

        val cache = Cache(this@DownloadWorker).getImages(galleryID)

        progress.put(galleryID, reader.galleryInfo.files.indices.map { index ->
            if (cache?.firstOrNull { it?.nameWithoutExtension?.toIntOrNull()  == index } != null)
                Float.POSITIVE_INFINITY
            else
                0F
        }.toMutableList())
        exception.put(galleryID, reader.galleryInfo.files.map { null }.toMutableList())

        if (notification[galleryID] == null)
            initNotification(galleryID)

        notification[galleryID].setContentTitle(reader.galleryInfo.title)
        notify(galleryID)

        if (isCompleted(galleryID)) {
            with(Cache(this@DownloadWorker)) {
                if (isDownloading(galleryID)) {
                    moveToDownload(galleryID)
                    setDownloading(galleryID, false)
                }
            }

            return@launch
        }

        for (i in reader.galleryInfo.files.indices) {
            val callback = object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.i("PUPILD", "FAIL ${call.request().tag()} (${e.message})")
                    if (Fabric.isInitialized() && e.message != "Canceled")
                        Crashlytics.logException(e)

                    progress[galleryID]?.set(i, Float.NaN)
                    exception[galleryID]?.set(i, e)

                    notify(galleryID)

                    CoroutineScope(Dispatchers.IO).launch {
                        if (isCompleted(galleryID) && clients.indexOfKey(galleryID) >= 0) {
                            clients.remove(galleryID)
                            with(Cache(this@DownloadWorker)) {
                                if (isDownloading(galleryID)) {
                                    moveToDownload(galleryID)
                                    setDownloading(galleryID, false)
                                }
                            }
                        }
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.i("PUPILD", "OK ${call.request().tag()}")

                    val ext = call.request().url().encodedPath().split('.').last()

                    try {
                        response.body().use {
                            Cache(this@DownloadWorker).putImage(galleryID, i, ext, it.byteStream())
                        }
                        progress[galleryID]?.set(i, Float.POSITIVE_INFINITY)

                        notify(galleryID)

                        CoroutineScope(Dispatchers.IO).launch {
                            if (isCompleted(galleryID) && clients.indexOfKey(galleryID) >= 0) {
                                clients.remove(galleryID)
                                with(Cache(this@DownloadWorker)) {
                                    if (isDownloading(galleryID)) {
                                        moveToDownload(galleryID)
                                        setDownloading(galleryID, false)
                                    }
                                }
                            }
                        }

                        Log.i("PUPILD", "SUCCESS ${call.request().tag()}")
                    } catch (e: Exception) {

                        progress[galleryID]?.set(i, Float.NaN)
                        exception[galleryID]?.set(i, e)

                        notify(galleryID)

                        CoroutineScope(Dispatchers.IO).launch {
                            if (isCompleted(galleryID) && clients.indexOfKey(galleryID) >= 0) {
                                clients.remove(galleryID)
                                with(Cache(this@DownloadWorker)) {
                                    if (isDownloading(galleryID)) {
                                        moveToDownload(galleryID)
                                        setDownloading(galleryID, false)
                                    }
                                }
                            }
                        }

                        File(Cache(this@DownloadWorker).getCachedGallery(galleryID), "%05d.$ext".format(i)).delete()

                        Log.i("PUPILD", "FAIL ON OK ${call.request().tag()} (${e.message})")
                    }
                }
            }

            if (progress[galleryID]?.get(i)?.isFinite() == true) {
                queueDownload(galleryID, reader, i, callback)
                Log.i("PUPILD", "$galleryID QUEUED $i")
            } else {
                Log.i("PUPILD", "$galleryID SKIPPED $i (${progress[galleryID]?.get(i)})")
            }
        }
    }

    private fun notify(galleryID: Int) {
        val max = progress[galleryID]?.size ?: 0
        val progress = progress[galleryID]?.count { !it.isFinite() } ?: 0

        Log.i("PUPILD", "NOTIFY $galleryID ${isCompleted(galleryID)} $progress/$max")

        if (isCompleted(galleryID)) {
            notification[galleryID]
                ?.setContentText(getString(R.string.reader_notification_complete))
                ?.setSmallIcon(android.R.drawable.stat_sys_download_done)
                ?.setProgress(0, 0, false)
                ?.setOngoing(false)

            notificationManager.cancel(galleryID)
        } else
            notification[galleryID]
                ?.setProgress(max, progress, false)
                ?.setContentText("$progress/$max")

        if (Cache(this).isDownloading(galleryID) && notification[galleryID] != null)
            notificationManager.notify(galleryID, notification[galleryID].build())
        else
            notificationManager.cancel(galleryID)
    }

    private fun initNotification(galleryID: Int) {
        val intent = Intent(this, ReaderActivity::class.java).apply {
            putExtra("galleryID", galleryID)
        }
        val pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(galleryID, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        notification.put(galleryID, NotificationCompat.Builder(this, "download").apply {
            setContentTitle(getString(R.string.reader_loading))
            setContentText(getString(R.string.reader_notification_text))
            setSmallIcon(android.R.drawable.stat_sys_download)                                  // had to use this because old android doesn't support VectorDrawable on Notification :P
            setContentIntent(pendingIntent)
            setProgress(0, 0, true)
            setOngoing(true)
        })
    }

    private fun loop() = CoroutineScope(Dispatchers.Default).launch {
        while (true) {
            if (queue.isEmpty())
                continue

            val galleryID = queue.peek() ?: continue

            if (clients.indexOfKey(galleryID) >= 0)    // Gallery already downloading!
                continue

            if (notification[galleryID] == null)
                initNotification(galleryID)

            if (Cache(this@DownloadWorker).isDownloading(galleryID))
                notificationManager.notify(galleryID, notification[galleryID].build())

            if (clients.size() >= preferences.getInt("max_download", 4))
                continue

            Log.i("PUPILD", "QUEUED $galleryID #${clients.size()+1}")

            worker.put(galleryID, download(galleryID))
            queue.poll()
        }
    }

}