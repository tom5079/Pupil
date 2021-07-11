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
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import xyz.quaver.io.FileX
import xyz.quaver.pupil.Pupil
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ImageCache(context: Context) : DIAware {
    override val di by closestDI(context)

    private val applicationContext: Pupil by instance()
    private val client: HttpClient by instance()

    val cacheFolder = File(context.cacheDir, "imageCache")
    val cache = SavedMap(File(cacheFolder, ".cache"), "", "")

    private val _channels = ConcurrentHashMap<String, Channel<Float>>()
    val channels = _channels as Map<String, Channel<Float>>

    private val requests = mutableMapOf<String, Job>()

    @Synchronized
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun cleanup() = coroutineScope {
        val LIMIT = 100*1024*1024

        cacheFolder.listFiles { it -> it.canonicalPath !in cache.values || it.name == ".cache" }?.forEach { it.delete() }

        if (cacheFolder.size() > LIMIT)
            do {
                cache.entries.firstOrNull { !channels.containsKey(it.key) }?.let {
                    File(it.value).delete()
                    cache.remove(it.key)
                }
            } while (cacheFolder.size() > LIMIT / 2)
    }

    fun free(images: List<String>) {
        images.forEach {
            requests[it]?.cancel()
        }

        images.forEach { _channels.remove(it) }
    }

    @Synchronized
    suspend fun clear() = coroutineScope {
        requests.values.forEach { it.cancel() }
        cacheFolder.listFiles()?.forEach { it.delete() }
        cache.clear()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun load(requestBuilder: HttpRequestBuilder.() -> Unit): File {
        val request = HttpRequestBuilder().apply(requestBuilder)

        val key = request.url.buildString()

        val progressChannel = if (_channels[key]?.isClosedForSend == false)
            _channels[key]!!
        else
            Channel<Float>(1, BufferOverflow.DROP_OLDEST).also { _channels[key] = it }

        return cache[key]?.let {
            progressChannel.close()
            File(it)
        } ?: File(cacheFolder, "${UUID.randomUUID()}.${key.takeLastWhile { it != '.' }}").also { file ->
            if (!file.exists())
                file.createNewFile()

            cache[key] = file.canonicalPath

            requests[key] = CoroutineScope(Dispatchers.IO).launch {
                kotlin.runCatching {
                    client.get<HttpStatement>(request).execute { httpResponse ->
                        val responseChannel: ByteReadChannel = httpResponse.receive()
                        val contentLength = httpResponse.contentLength() ?: -1
                        var readBytes = 0F

                        while (!responseChannel.isClosedForRead) {
                            val packet = responseChannel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                            while (!packet.isEmpty) {
                                val bytes = packet.readBytes()
                                file.appendBytes(bytes)
                                readBytes += bytes.size
                                progressChannel.trySend(readBytes / contentLength)
                            }
                        }
                        progressChannel.close()
                    }
                }.onFailure {
                    file.delete()
                    cache.remove(key)
                    FirebaseCrashlytics.getInstance().recordException(it)
                    progressChannel.close(it)
                }
            }
        }
    }
}