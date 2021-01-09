/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2021  tom5079
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

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking
import okhttp3.*
import org.kodein.di.DIAware
import org.kodein.di.android.di
import org.kodein.di.instance
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ImageCache(context: Context) : DIAware {
    override val di by di(context)

    private val client: OkHttpClient by instance()

    val cacheFolder = File(context.cacheDir, "imageCache")
    val cache = SavedMap(File(cacheFolder, ".cache"), "", "")

    private val _channels = ConcurrentHashMap<String, Channel<Float>>()
    val channels = _channels as Map<String, Channel<Float>>

    @Synchronized
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun cleanup() = coroutineScope {
        val LIMIT = 100*1024*1024

        cacheFolder.listFiles { it -> it.canonicalPath !in cache }?.forEach { it.delete() }

        if (cacheFolder.size() > LIMIT)
            do {
                cache.entries.firstOrNull { !channels.containsKey(it.key) }?.let {
                    File(it.value).delete()
                    cache.remove(it.key)
                }
            } while (cacheFolder.size() > LIMIT / 2)
    }

    fun free(images: List<String>) {
        client.dispatcher().let { it.queuedCalls() + it.runningCalls() }
            .filter { it.request().url().toString() in images }
            .forEach { it.cancel() }

        images.forEach { _channels.remove(it) }
    }

    @Synchronized
    suspend fun clear() = coroutineScope {
        client.dispatcher().queuedCalls().forEach { it.cancel() }

        cacheFolder.listFiles()?.forEach { it.delete() }
        cache.clear()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun load(request: Request): File {
        val key = request.url().toString()

        val channel = if (_channels[key]?.isClosedForSend == false)
            _channels[key]!!
        else
            Channel<Float>(1, BufferOverflow.DROP_OLDEST).also { _channels[key] = it }

        return cache[key]?.let {
            channel.close()
            File(it)
        } ?: File(cacheFolder, "${UUID.randomUUID()}.${key.takeLastWhile { it != '.' }}").also { file ->
            client.newCall(request).enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    file.delete()
                    cache.remove(call.request().url().toString())

                    FirebaseCrashlytics.getInstance().recordException(e)
                    channel.close(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.code() != 200) {
                        file.delete()
                        cache.remove(call.request().url().toString())

                        channel.close(IOException("HTTP Response code is not 200"))

                        response.close()
                        return
                    }

                    response.body()?.use { body ->
                        if (!file.exists())
                            file.createNewFile()

                        body.byteStream().copyTo(file.outputStream()) { bytes, _ ->
                            channel.sendBlocking(bytes / body.contentLength().toFloat() * 100)
                        }
                    }

                    channel.close()
                }
            })
        }.also { cache[key] = it.canonicalPath }
    }
}