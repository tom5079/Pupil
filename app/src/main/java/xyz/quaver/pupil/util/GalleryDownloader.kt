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
import kotlinx.coroutines.*
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import xyz.quaver.hitomi.Reader
import xyz.quaver.hitomi.getReader
import xyz.quaver.hitomi.getReferer
import xyz.quaver.hiyobi.cookie
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

                val data = getCachedGallery(this, galleryID)
                val cache = File(cacheDir, "imageCache/$galleryID")

                if (File(cache, "images").exists() && !data.exists()) {
                    cache.copyRecursively(data, true)
                    cache.deleteRecursively()
                }

                if (reader?.isActive == false && downloadJob?.isActive != true)
                    field = false

                downloads.add(galleryID)
            } else {
                field = false
            }

            onNotifyChangedHandler?.invoke(value)
        }

    private val reader: Deferred<Reader>?
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
            download = _notify
            val json = Json(JsonConfiguration.Stable)
            val serializer = Reader.serializer()

            //Check cache
            val cache = File(getCachedGallery(this@GalleryDownloader, galleryID), "reader.json")

            if (cache.exists()) {
                val cached = json.parse(serializer, cache.readText())

                if (cached.readerItems.isNotEmpty()) {
                    useHiyobi = when {
                        cached.readerItems[0].url.contains("hitomi.la") -> false
                        else -> true
                    }

                    onReaderLoadedHandler?.invoke(cached)

                    return@async cached
                }
            }

            //Cache doesn't exist. Load from internet
            val reader = when {
                useHiyobi -> {
                    xyz.quaver.hiyobi.getReader(galleryID).let {
                        when {
                            it.readerItems.isEmpty() -> {
                                useHiyobi = false
                                getReader(galleryID)
                            }
                            else -> it
                        }
                    }
                }
                else -> {
                    getReader(galleryID)
                }
            }

            if (reader.readerItems.isNotEmpty()) {
                //Save cache
                if (cache.parentFile?.exists() == false)
                    cache.parentFile!!.mkdirs()

                cache.writeText(json.stringify(serializer, reader))
            }

            reader
        }
    }

    private fun webpUrlFromUrl(url: String) = url.replace("/galleries/", "/webp/") + ".webp"

    fun start() {
        downloadJob = CoroutineScope(Dispatchers.Default).launch {
            val reader = reader!!.await()

            if (reader.readerItems.isEmpty())
                onErrorHandler?.invoke(IOException("Couldn't retrieve Reader"))

            val list = ArrayList<String>()

            onReaderLoadedHandler?.invoke(reader)

            notificationBuilder
                .setProgress(reader.readerItems.size, 0, false)
                .setContentText("0/${reader.readerItems.size}")

            reader.readerItems.chunked(4).forEachIndexed { chunkIndex, chunked ->
                chunked.mapIndexed { i, it ->
                    val index = chunkIndex*4+i

                    async(Dispatchers.IO) {
                        val url = if (it.galleryInfo?.haswebp == 1) webpUrlFromUrl(it.url) else it.url

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

                        cache.absolutePath
                    }
                }.forEach {
                    list.add(it.await())

                    val index = list.size

                    onProgressHandler?.invoke(index)

                    notificationBuilder
                        .setProgress(reader.readerItems.size, index, false)
                        .setContentText("$index/${reader.readerItems.size}")

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
            setSmallIcon(R.drawable.ic_download)
            setContentIntent(pendingIntent)
            setProgress(0, 0, true)
            priority = NotificationCompat.PRIORITY_LOW
        }

        CoroutineScope(Dispatchers.Default).launch {
            while (reader == null) ;
            notificationBuilder.setContentTitle(reader.await().title)
        }
    }

}