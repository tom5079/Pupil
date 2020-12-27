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

package xyz.quaver.pupil.util.downloader

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okio.*
import xyz.quaver.pupil.PupilInterceptor
import xyz.quaver.pupil.R
import xyz.quaver.pupil.client
import xyz.quaver.pupil.interceptors
import xyz.quaver.pupil.services.DownloadService
import xyz.quaver.pupil.sources.sources
import xyz.quaver.pupil.ui.ReaderActivity
import xyz.quaver.pupil.util.cleanCache
import xyz.quaver.pupil.util.normalizeID
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private typealias ProgressListener = (Downloader.Tag, Long, Long, Boolean) -> Unit
class Downloader private constructor(private val context: Context) {

    data class Tag(val source: String, val itemID: String, val index: Int)

    companion object {
        var instance: Downloader? = null

        fun getInstance(context: Context): Downloader {
            return instance ?: synchronized(this) {
                instance ?: Downloader(context).also {
                    interceptors[Tag::class] = it.interceptor
                    instance = it
                }
            }
        }
    }

    //region Notification
    private val notificationManager by lazy {
        NotificationManagerCompat.from(context)
    }

    private val serviceNotification by lazy {
        NotificationCompat.Builder(context, "downloader")
            .setContentTitle(context.getString(R.string.downloader_running))
            .setProgress(0, 0, false)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
    }

    private val notification = ConcurrentHashMap<String, NotificationCompat.Builder?>()

    private fun initNotification(source: String, itemID: String) {
        val key = "$source-$itemID"

        val intent = Intent(context, ReaderActivity::class.java)
            .putExtra("source", source)
            .putExtra("itemID", itemID)

        val pendingIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(itemID.hashCode(), PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val action =
            NotificationCompat.Action.Builder(0, context.getText(android.R.string.cancel),
                PendingIntent.getService(
                    context,
                    R.id.notification_download_cancel_action.normalizeID(),
                    Intent(context, DownloadService::class.java)
                        .putExtra(DownloadService.KEY_COMMAND, DownloadService.COMMAND_CANCEL)
                        .putExtra(DownloadService.KEY_ID, itemID),
                    PendingIntent.FLAG_UPDATE_CURRENT),
            ).build()

        notification[key] = NotificationCompat.Builder(context, "download").apply {
            setContentTitle(context.getString(R.string.reader_loading))
            setContentText(context.getString(R.string.reader_notification_text))
            setSmallIcon(R.drawable.ic_notification)
            setContentIntent(pendingIntent)
            addAction(action)
            setProgress(0, 0, true)
            setOngoing(true)
        }

        notify(source, itemID)
    }

    @SuppressLint("RestrictedApi")
    private fun notify(source: String, itemID: String) {
        val key = "$source-$itemID"
        val max = progress[key]?.size ?: 0
        val progress = progress[key]?.count { it == Float.POSITIVE_INFINITY } ?: 0

        val notification = notification[key] ?: return

        if (isCompleted(source, itemID)) {
            notification
                .setContentText(context.getString(R.string.reader_notification_complete))
                .setProgress(0, 0, false)
                .setOngoing(false)
                .mActions.clear()

            notificationManager.cancel(key.hashCode())
        } else
            notification
                .setProgress(max, progress, false)
                .setContentText("$progress/$max")
    }
    //endregion

    //region ProgressListener
    @Suppress("UNCHECKED_CAST")
    private val progressListener: ProgressListener = { (source, itemID, index), bytesRead, contentLength, done ->
        if (!done && progress["$source-$itemID"]?.get(index)?.isFinite() == true)
            progress["$source-$itemID"]?.set(index, bytesRead * 100F / contentLength)
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
                progressListener.invoke(tag as Tag, totalBytesRead, responseBody.contentLength(), bytesRead == -1L)

                return bytesRead
            }
        }
    }

    private val interceptor: PupilInterceptor = { chain ->
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

    private val callback = object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            val (source, itemID, index) = call.request().tag() as Tag

            FirebaseCrashlytics.getInstance().recordException(e)

            progress["$source-$itemID"]?.set(index, Float.NEGATIVE_INFINITY)
        }

        override fun onResponse(call: Call, response: Response) {
            val (source, itemID, index) = call.request().tag() as Tag
            val ext = call.request().url().encodedPath().takeLastWhile { it != '.' }

            if (response.code() != 200)
                throw IOException()

            response.body()?.use {
                Cache.getInstance(context, source, itemID).putImage(index, "$index.$ext", it.byteStream())
            }
            progress["$source-$itemID"]?.set(index, Float.POSITIVE_INFINITY)
        }
    }

    private val progress = ConcurrentHashMap<String, MutableList<Float>>()
    fun getProgress(source: String, itemID: String): List<Float>? {
        return progress["$source-$itemID"]
    }

    fun isCompleted(source: String, itemID: String) = progress["$source-$itemID"]?.all { it == Float.POSITIVE_INFINITY } == true

    fun cancel() {
        client.dispatcher().queuedCalls().filter {
            it.request().tag() is Tag
        }.forEach {
            it.cancel()
        }
        client.dispatcher().runningCalls().filter {
            it.request().tag() is Tag
        }.forEach {
            it.cancel()
        }

        progress.clear()
    }

    fun cancel(source: String, itemID: String) {
        client.dispatcher().queuedCalls().filter {
            (it.request().tag() as? Tag)?.let { tag ->
                tag.source == source && tag.itemID == itemID
            } == true
        }.forEach {
            it.cancel()
        }
        client.dispatcher().runningCalls().filter {
            (it.request().tag() as? Tag)?.let { tag ->
                tag.source == source && tag.itemID == itemID
            } == true
        }.forEach {
            it.cancel()
        }

        progress.remove("$source-$itemID")
    }

    fun retry(source: String, itemID: String) {
        cancel(source, itemID)
        download(source, itemID)
    }

    var onImageListLoadedCallback: ((List<String>) -> Unit)? = null
    fun download(source: String, itemID: String) = CoroutineScope(Dispatchers.IO).launch {
        if (isDownloading(source, itemID))
            return@launch

        initNotification(source, itemID)
        cleanCache(context)

        val source = sources[source] ?: return@launch
        val cache = Cache.getInstance(context, source.name, itemID)

        source.images(itemID).also {
            progress["${source.name}-$itemID"] = MutableList(it.size) { i ->
                if (cache.metadata.imageList?.get(i) == null) 0F else Float.POSITIVE_INFINITY
            }

            if (cache.metadata.imageList == null)
                cache.metadata.imageList = MutableList(it.size) { null }

            onImageListLoadedCallback?.invoke(it)
        }.forEachIndexed { index, url ->
            client.newCall(
                Request.Builder()
                    .tag(Tag(source.name, itemID, index))
                    .url(url)
                    .headers(Headers.of(source.getHeadersForImage(itemID, url)))
                    .build()
            ).enqueue(callback)
        }
    }

    fun isDownloading(source: String, itemID: String): Boolean {
        return (client.dispatcher().queuedCalls() + client.dispatcher().runningCalls()).any {
            (it.request().tag() as? Tag)?.let { tag ->
                tag.source == source && tag.itemID == itemID
            } == true
        }
    }

}