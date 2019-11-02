/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2019  tom5079
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

package xyz.quaver.pupil.util

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.SparseArray
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.preference.PreferenceManager
import com.crashlytics.android.Crashlytics
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import xyz.quaver.availableInHiyobi
import xyz.quaver.hitomi.Reader
import xyz.quaver.hitomi.getReader
import xyz.quaver.hitomi.getReferer
import xyz.quaver.hitomi.urlFromUrlFromHash
import xyz.quaver.hiyobi.cookie
import xyz.quaver.hiyobi.createImgList
import xyz.quaver.hiyobi.user_agent
import xyz.quaver.pupil.Pupil
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.ReaderActivity
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule

class GalleryDownloader(
    base: Context,
    private val galleryID: Int,
    _notify: Boolean = false
) : ContextWrapper(base) {

    private val downloads = (applicationContext as Pupil).downloads
    var useHiyobi = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("use_hiyobi", false)

    var download: Boolean = false
        set(value) {
            if (value) {
                field = true
                notificationManager.notify(galleryID, notificationBuilder.build())

                if (reader?.isActive == false && downloadJob?.isActive != true) {
                    val data = File(getDownloadDirectory(this), galleryID.toString())
                    val cache = File(cacheDir, "imageCache/$galleryID")

                    if (File(cache, "images").exists() && !data.exists()) {
                        cache.copyRecursively(data, true)
                        cache.deleteRecursively()
                    }

                    field = false
                }

                downloads.add(galleryID)
            } else {
                field = false
            }

            onNotifyChangedHandler?.invoke(value)
        }

    private val reader: Deferred<Reader?>?
    private var downloadJob: Job? = null

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManagerCompat

    var onReaderLoadedHandler: ((Reader) -> Unit)? = null
    var onProgressHandler: ((Int) -> Unit)? = null
    var onDownloadedHandler: ((List<String>) -> Unit)? = null
    var onErrorHandler: ((Exception) -> Unit)? = null
    var onCompleteHandler: (() -> Unit)? = null
    var onNotifyChangedHandler: ((Boolean) -> Unit)? = null

    companion object : SparseArray<GalleryDownloader>()

    init {
        put(galleryID, this)

        initNotification()

        reader = CoroutineScope(Dispatchers.IO).async {
            try {
                download = _notify
                val json = Json(JsonConfiguration.Stable)
                val serializer = Reader.serializer()

                //Check cache
                val cache = File(getCachedGallery(this@GalleryDownloader, galleryID), "reader.json")

                try {
                    json.parse(serializer, cache.readText())
                } catch(e: Exception) {
                    cache.delete()
                }

                if (cache.exists()) {
                    val cached = json.parse(serializer, cache.readText())

                    if (cached.galleryInfo.isNotEmpty()) {
                        useHiyobi = availableInHiyobi(galleryID)

                        onReaderLoadedHandler?.invoke(cached)

                        return@async cached
                    }
                }

                //Cache doesn't exist. Load from internet
                val reader = when {
                    useHiyobi -> {
                        try {
                            xyz.quaver.hiyobi.getReader(galleryID)
                        } catch(e: Exception) {
                            useHiyobi = false
                            getReader(galleryID)
                        }
                    }
                    else -> {
                        getReader(galleryID)
                    }
                }

                if (reader.galleryInfo.isNotEmpty()) {
                    //Save cache
                    if (cache.parentFile?.exists() == false)
                        cache.parentFile!!.mkdirs()

                    cache.writeText(json.stringify(serializer, reader))
                }

                reader
            } catch (e: Exception) {
                Crashlytics.logException(e)
                onErrorHandler?.invoke(e)
                null
            }
        }
    }

    private fun webpUrlFromUrl(url: String) = url.replace("/galleries/", "/webp/") + ".webp"

    fun start() {
        downloadJob = CoroutineScope(Dispatchers.Default).launch {
            val reader = reader!!.await() ?: return@launch

            notificationBuilder.setContentTitle(reader.title)

            val list = ArrayList<String>()

            onReaderLoadedHandler?.invoke(reader)

            notificationBuilder
                .setProgress(reader.galleryInfo.size, 0, false)
                .setContentText("0/${reader.galleryInfo.size}")

            reader.galleryInfo.chunked(4).forEachIndexed { chunkIndex, chunked ->
                chunked.mapIndexed { i, galleryInfo ->
                    val index = chunkIndex*4+i

                    async(Dispatchers.IO) {
                        val url = when(useHiyobi) {
                            true -> createImgList(galleryID, reader)[index].path
                            false -> when (galleryInfo.haswebp) {
                                1 -> webpUrlFromUrl(
                                    urlFromUrlFromHash(
                                        galleryID,
                                        galleryInfo,
                                        true
                                    )
                                )
                                else -> urlFromUrlFromHash(galleryID, galleryInfo)
                            }
                        }

                        val name = "$index".padStart(4, '0')
                        val ext = url.split('.').last()

                        val cache = File(getCachedGallery(this@GalleryDownloader, galleryID), "images/$name.$ext")

                        if (!cache.exists())
                            try {
                                with(URL(url).openConnection() as HttpsURLConnection) {
                                    if (useHiyobi) {
                                        setRequestProperty("User-Agent", user_agent)
                                        setRequestProperty("Cookie", cookie)
                                    } else
                                        setRequestProperty("Referer", getReferer(galleryID))

                                    if (cache.parentFile?.exists() == false)
                                        cache.parentFile!!.mkdirs()

                                    inputStream.copyTo(FileOutputStream(cache))
                                }
                            } catch (e: Exception) {
                                cache.delete()

                                onErrorHandler?.invoke(e)

                                notificationBuilder
                                    .setContentTitle(reader.title)
                                    .setContentText(getString(R.string.reader_notification_error))
                                    .setProgress(0, 0, false)

                                notificationManager.notify(galleryID, notificationBuilder.build())
                            }

                        "images/$name.$ext"
                    }
                }.forEach {
                    list.add(it.await())

                    val index = list.size

                    onProgressHandler?.invoke(index)

                    notificationBuilder
                        .setProgress(reader.galleryInfo.size, index, false)
                        .setContentText("$index/${reader.galleryInfo.size}")

                    if (download)
                        notificationManager.notify(galleryID, notificationBuilder.build())

                    onDownloadedHandler?.invoke(list)
                }
            }

            Timer(false).schedule(1000) {
                notificationBuilder
                    .setContentTitle(reader.title)
                    .setContentText(getString(R.string.reader_notification_complete))
                    .setProgress(0, 0, false)

                if (download) {
                    File(cacheDir, "imageCache/${galleryID}").let {
                        if (it.exists()) {
                            val target = File(getDownloadDirectory(this@GalleryDownloader), galleryID.toString())

                            if (!target.exists())
                                target.mkdirs()

                            it.copyRecursively(target, true)
                            it.deleteRecursively()
                        }
                    }

                    notificationManager.notify(galleryID, notificationBuilder.build())

                    download = false
                }

                onCompleteHandler?.invoke()
            }

            remove(galleryID)
        }
    }

    fun cancel() {
        downloadJob?.cancel()

        remove(galleryID)
    }

    suspend fun cancelAndJoin() {
        downloadJob?.cancelAndJoin()

        remove(galleryID)
    }

    fun invokeOnReaderLoaded() {
        CoroutineScope(Dispatchers.Default).launch {
            onReaderLoadedHandler?.invoke(reader?.await() ?: return@launch)
        }
    }

    fun clearNotification() {
        notificationManager.cancel(galleryID)
    }

    fun invokeOnNotifyChanged() {
        onNotifyChangedHandler?.invoke(download)
    }

    private fun initNotification() {
        val intent = Intent(this, ReaderActivity::class.java).apply {
            putExtra("galleryID", galleryID)
        }
        val pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        notificationManager = NotificationManagerCompat.from(this)

        notificationBuilder = NotificationCompat.Builder(this, "download").apply {
            setContentTitle(getString(R.string.reader_loading))
            setContentText(getString(R.string.reader_notification_text))
            setSmallIcon(android.R.drawable.stat_sys_download)
            setContentIntent(pendingIntent)
            setProgress(0, 0, true)
            priority = NotificationCompat.PRIORITY_LOW
        }
    }

}