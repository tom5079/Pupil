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

package xyz.quaver.pupil.services

import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.SparseArray
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.ResponseBody
import okio.*
import xyz.quaver.pupil.R

private typealias ProgressListener = (Any?, Long, Long, Boolean) -> Unit

class Cache(context: Context) : ContextWrapper(context) {



}

class DownloadService : Service() {

    //region Notification
    private val notificationManager by lazy {
        NotificationManagerCompat.from(this)
    }

    private val serviceNotification by lazy {
        NotificationCompat.Builder(this, "downloader")
            .setContentTitle(getString(R.string.downloader_running))
            .setProgress(0, 0, false)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
    }
    //endregion

    //region ProgressListener
    @Suppress("UNCHECKED_CAST")
    private val progressListener: ProgressListener = listener@{ tag, bytesRead, contentLength, done ->
            val (galleryID, index) = (tag as? Pair<Int, Int>) ?: return@listener

            if (!done && progress[galleryID]?.get(index)?.isFinite() == true)
                progress[galleryID]?.set(index, bytesRead * 100F / contentLength)
    }

    private class ProgressResponseBody(
        val tag: Any?,
        val responseBody: ResponseBody,
        val progressListener : ProgressListener
    ) : ResponseBody() {
        private var bufferedSource : BufferedSource? = null

        override fun contentLength() = responseBody.contentLength()
        override fun contentType() = responseBody.contentType()

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
                progressListener.invoke(tag, totalBytesRead, responseBody.contentLength(), bytesRead == -1L)

                return bytesRead
            }
        }
    }

    val interceptor = Interceptor { chain ->
        val request = chain.request()
        var response = chain.proceed(request)

        var retry = 5
        while (!response.isSuccessful && retry > 0) {
            response = chain.proceed(request)
            retry--
        }

        response.newBuilder()
            .body(response.body()?.let {
                ProgressResponseBody(request.tag(), it, progressListener)
            }).build()
    }
    //endregion

    /**
    * KEY
    *  primary galleryID
    *  secondary index
    * PRIMARY VALUE
    *  MutableList -> Download in progress
    *  null -> Loading / Gallery doesn't exist
    * SECONDARY VALUE
    *  0 <= value < 100 -> Download in progress
    *  Float.POSITIVE_INFINITY -> Download completed
    */
    val progress = SparseArray<MutableList<Float>?>()

    override fun onCreate() {
        startForeground(R.id.downloader_notification_id, serviceNotification.build())
    }

    override fun onDestroy() {

    }


    inner class Binder : android.os.Binder() {
        val service = this@DownloadService
    }

    private val binder = Binder()
    override fun onBind(p0: Intent?) = binder

    fun load(galleryID: Int) {
        
    }

    fun download(galleryID: Int) = CoroutineScope(Dispatchers.IO).launch {

    }
}