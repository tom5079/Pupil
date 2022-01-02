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

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.RateLimiter
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.ResponseBody
import okio.*
import xyz.quaver.pupil.*
import xyz.quaver.pupil.ui.ReaderActivity
import xyz.quaver.pupil.util.cleanCache
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.DownloadManager
import xyz.quaver.pupil.util.ellipsize
import xyz.quaver.pupil.util.normalizeID
import xyz.quaver.pupil.util.requestBuilders
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.log10

private typealias ProgressListener = (DownloadService.Tag, Long, Long, Boolean) -> Unit
class DownloadService : Service() {
    data class Tag(val galleryID: Int, val index: Int, val startId: Int? = null)

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

    private val notification = ConcurrentHashMap<Int, NotificationCompat.Builder?>()

    private fun initNotification(galleryID: Int) {
        val intent = Intent(this, ReaderActivity::class.java)
            .putExtra("galleryID", galleryID)

        val pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(galleryID, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val action =
            NotificationCompat.Action.Builder(0, getText(android.R.string.cancel),
                PendingIntent.getService(
                    this,
                    R.id.notification_download_cancel_action.normalizeID(),
                    Intent(this, DownloadService::class.java)
                        .putExtra(KEY_COMMAND, COMMAND_CANCEL)
                        .putExtra(KEY_ID, galleryID),
                    PendingIntent.FLAG_UPDATE_CURRENT),
            ).build()

        notification[galleryID] = NotificationCompat.Builder(this, "download").apply {
            setContentTitle(getString(R.string.reader_loading))
            setContentText(getString(R.string.reader_notification_text))
            setSmallIcon(R.drawable.ic_notification)
            setContentIntent(pendingIntent)
            addAction(action)
            setProgress(0, 0, true)
            setOngoing(true)
        }

        notify(galleryID)
    }

    @SuppressLint("RestrictedApi")
    private fun notify(galleryID: Int) {
        val max = progress[galleryID]?.size ?: 0
        val progress = progress[galleryID]?.count { it == Float.POSITIVE_INFINITY } ?: 0

        val notification = notification[galleryID] ?: return

        if (isCompleted(galleryID)) {
            notification
                .setContentText(getString(R.string.reader_notification_complete))
                .setProgress(0, 0, false)
                .setOngoing(false)
                .mActions.clear()

            notificationManager.cancel(galleryID)
        } else
            notification
                .setProgress(max, progress, false)
                .setContentText("$progress/$max")

        if (DownloadManager.getInstance(this).getDownloadFolder(galleryID) != null || galleryID == priority)
            notification.let { notificationManager.notify(galleryID, it.build()) }
        else
            notificationManager.cancel(galleryID)
    }
    //endregion

    //region ProgressListener
    @Suppress("UNCHECKED_CAST")
    private val progressListener: ProgressListener = { (galleryID, index), bytesRead, contentLength, done ->
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
                bufferedSource = source(responseBody.source()).buffer()

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

    private val rateLimiter = RateLimiter.create(2.0)
    private val rateLimitHost = Regex("..?\\.hitomi.la")

    private val interceptor: PupilInterceptor = { chain ->
        val request = chain.request()

        Log.d("PUPILD", "REQ")

        if (rateLimitHost.matches(request.url().host()))
            rateLimiter.acquire()

        Log.d("PUPILD", "ACQ ${request.url()}")

        var response = chain.proceed(request)
        var limit = 5

        if (!response.isSuccessful && limit > 0) {
            Thread.sleep(10000)
            if (rateLimitHost.matches(request.url().host()))
                rateLimiter.acquire()
            response = chain.proceed(request)
            limit -= 1
        }

        response.newBuilder()
            .body(response.body()?.let {
                ProgressResponseBody(request.tag(), it, progressListener)
            }).build()
    }
    //endregion

    //region Downloader
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
    val progress = ConcurrentHashMap<Int, MutableList<Float>>()
    var priority = 0

    fun isCompleted(galleryID: Int) = progress[galleryID]?.toList()?.all { it == Float.POSITIVE_INFINITY } == true

    private val callback = object: Callback {

        override fun onFailure(call: Call, e: IOException) {
            FirebaseCrashlytics.getInstance().recordException(e)

            if (e.message?.contains("cancel", true) == false) {
                val galleryID = (call.request().tag() as Tag).galleryID
            }
        }

        override fun onResponse(call: Call, response: Response) {
            val (galleryID, index, startId) = call.request().tag() as Tag
            val ext = call.request().url().encodedPath().split('.').last()

            kotlin.runCatching {
                val image = response.also { if (it.code() != 200) throw IOException("$galleryID $index ${response.request().url()} CODE ${it.code()}") }.body()?.use { it.bytes() } ?: throw Exception("Response null")
                val padding = ceil(progress[galleryID]?.size?.let { log10(it.toFloat()) } ?: 0F).toInt()

                CoroutineScope(Dispatchers.IO).launch {
                    kotlin.runCatching {
                        Cache.getInstance(this@DownloadService, galleryID).putImage(index, "${index.toString().padStart(padding, '0')}.$ext", image)
                    }.onSuccess {
                        progress[galleryID]?.set(index, Float.POSITIVE_INFINITY)
                        notify(galleryID)

                        if (isCompleted(galleryID)) {
                            if (DownloadManager.getInstance(this@DownloadService)
                                    .getDownloadFolder(galleryID) != null)
                                Cache.getInstance(this@DownloadService, galleryID).moveToDownload()

                            startId?.let { stopSelf(it) }
                        }
                    }.onFailure {
                        FirebaseCrashlytics.getInstance().recordException(it)
                    }
                }
            }.onFailure {
                FirebaseCrashlytics.getInstance().recordException(it)
            }
        }
    }

    fun cancel(startId: Int? = null) {
        client.dispatcher().queuedCalls().filter {
            it.request().tag() is Tag
        }.forEach {
            (it.request().tag() as? Tag)?.startId?.let { stopSelf(it) }
            it.cancel()
        }
        client.dispatcher().runningCalls().filter {
            it.request().tag() is Tag
        }.forEach {
            (it.request().tag() as? Tag)?.startId?.let { stopSelf(it) }
            it.cancel()
        }

        progress.clear()
        notification.clear()
        notificationManager.cancelAll()

        startId?.let { stopSelf(it) }
    }

    fun cancel(galleryID: Int, startId: Int? = null) {
        client.dispatcher().queuedCalls().filter {
            (it.request().tag() as? Tag)?.galleryID == galleryID
        }.forEach {
            (it.request().tag() as? Tag)?.startId?.let { stopSelf(it) }
            it.cancel()
        }
        client.dispatcher().runningCalls().filter {
            (it.request().tag() as? Tag)?.galleryID == galleryID
        }.forEach {
            (it.request().tag() as? Tag)?.startId?.let { stopSelf(it) }
            it.cancel()
        }

        progress.remove(galleryID)
        notification.remove(galleryID)
        notificationManager.cancel(galleryID)

        startId?.let { stopSelf(it) }
    }

    fun delete(galleryID: Int, startId: Int? = null) = CoroutineScope(Dispatchers.IO).launch {
        cancel(galleryID)
        DownloadManager.getInstance(this@DownloadService).deleteDownloadFolder(galleryID)
        Cache.delete(this@DownloadService, galleryID)

        startId?.let { stopSelf(it) }
    }

    fun download(galleryID: Int, priority: Boolean = false, startId: Int? = null): Job = CoroutineScope(Dispatchers.IO).launch {
        if (DownloadManager.getInstance(this@DownloadService).isDownloading(galleryID))
            return@launch

        cleanCache(this@DownloadService)

        val cache = Cache.getInstance(this@DownloadService, galleryID)

        initNotification(galleryID)

        val galleryInfo = cache.getGalleryInfo()

        // Gallery doesn't exist
        if (galleryInfo == null) {
            delete(galleryID)
            progress[galleryID] = mutableListOf()
            return@launch
        }

        histories.add(galleryID)

        progress[galleryID] = MutableList(galleryInfo.files.size) { 0F }

        cache.metadata.imageList?.let {
            it.forEachIndexed { index, image ->
                progress[galleryID]?.set(index, if (image != null) Float.POSITIVE_INFINITY else 0F)
            }
        }

        if (isCompleted(galleryID)) {
            if (DownloadManager.getInstance(this@DownloadService)
                    .getDownloadFolder(galleryID) != null )
                Cache.getInstance(this@DownloadService, galleryID).moveToDownload()

            notificationManager.cancel(galleryID)
            startId?.let { stopSelf(it) }
            return@launch
        }

        notification[galleryID]?.setContentTitle(galleryInfo.title?.ellipsize(30))
        notify(galleryID)

        val queued = mutableSetOf<Int>()

        if (priority) {
            client.dispatcher().queuedCalls().forEach {
                val queuedID = (it.request().tag() as? Tag)?.galleryID ?: return@forEach

                if (queued.add(queuedID))
                    cancel(queuedID)
            }
        }

        galleryInfo.requestBuilders.forEachIndexed { index, it ->
            if (progress[galleryID]?.get(index)?.isInfinite() == false) {
                val request = it.tag(Tag(galleryID, index, startId)).build()
                client.newCall(request).enqueue(callback)
            }
        }

        queued.forEach { download(it) }
    }
    //endregion

    companion object {
        const val KEY_COMMAND = "COMMAND"   // String
        const val KEY_ID = "ID"             // Int
        const val KEY_PRIORITY = "PRIORITY" // Boolean

        const val COMMAND_DOWNLOAD = "DOWNLOAD"
        const val COMMAND_CANCEL = "CANCEL"
        const val COMMAND_DELETE = "DELETE"

        private fun command(context: Context, extras: Intent.() -> Unit) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).apply(extras))
        }

        fun download(context: Context, galleryID: Int, priority: Boolean = false) {
            command(context) {
                putExtra(KEY_COMMAND, COMMAND_DOWNLOAD)
                putExtra(KEY_PRIORITY, priority)
                putExtra(KEY_ID, galleryID)
            }
        }

        fun cancel(context: Context, galleryID: Int? = null) {
            command(context) {
                putExtra(KEY_COMMAND, COMMAND_CANCEL)
                galleryID?.let { putExtra(KEY_ID, it) }
            }
        }

        fun delete(context: Context, galleryID: Int) {
            command(context) {
                putExtra(KEY_COMMAND, COMMAND_DELETE)
                putExtra(KEY_ID, galleryID)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(R.id.downloader_notification_id, serviceNotification.build())

        when (intent?.getStringExtra(KEY_COMMAND)) {
            COMMAND_DOWNLOAD -> intent.getIntExtra(KEY_ID, -1).let { if (it > 0)
                download(it, intent.getBooleanExtra(KEY_PRIORITY, false), startId)
            }
            COMMAND_CANCEL -> intent.getIntExtra(KEY_ID, -1).let { if (it > 0) cancel(it, startId) else cancel(startId = startId) }
            COMMAND_DELETE -> intent.getIntExtra(KEY_ID, -1).let { if (it > 0) delete(it, startId) }
        }

        return START_NOT_STICKY
    }

    inner class Binder : android.os.Binder() {
        val service = this@DownloadService
    }

    private val binder = Binder()
    override fun onBind(p0: Intent?) = binder

    override fun onCreate() {
        startForeground(R.id.downloader_notification_id, serviceNotification.build())
        interceptors[Tag::class] = interceptor
    }

    override fun onDestroy() {
        interceptors.remove(Tag::class)
    }
}