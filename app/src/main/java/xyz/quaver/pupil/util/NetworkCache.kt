/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2021 tom5079
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
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.util

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import xyz.quaver.hitomi.sha256
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.text.toByteArray

private const val CACHE_LIMIT = 100*1024*1024 // 100M

class NetworkCache(context: Context) : DIAware {
    override val di by closestDI(context)

    private val logger = newLogger(LoggerFactory.default)

    private val client: HttpClient by instance()
    private val networkScope = CoroutineScope(Executors.newFixedThreadPool(4).asCoroutineDispatcher())

    private val cacheDir = File(context.cacheDir, "networkcache")

    private val channel = ConcurrentHashMap<String, Channel<Float>>()
    private val requests = ConcurrentHashMap<String, Job>()
    private val activeFiles = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private fun urlToFilename(url: String): String {
        val hash = sha256(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return "$hash.${url.takeLastWhile { it != '.' }}"
    }

    fun cleanup() = CoroutineScope(Dispatchers.IO).launch {
        if (cacheDir.size() > CACHE_LIMIT)
            cacheDir.listFiles { file -> file.name !in activeFiles }?.forEach { it.delete() }
    }

    fun free(urls: List<String>) = urls.forEach {
        requests[it]?.cancel()
        channel.remove(it)
        activeFiles.remove(urlToFilename(it))
    }

    fun clear() = CoroutineScope(Dispatchers.IO).launch {
        requests.values.forEach { it.cancel() }
        channel.clear()
        activeFiles.clear()
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun load(requestBuilder: HttpRequestBuilder.() -> Unit): Pair<Channel<Float>, File> = coroutineScope {
        val request = HttpRequestBuilder().apply(requestBuilder)

        val url = request.url.buildString()

        val fileName = urlToFilename(url)
        val file = File(cacheDir, fileName)
        activeFiles.add(fileName)

        val progressChannel = if (channel[url]?.isClosedForSend == false)
            channel[url]!!
        else
            Channel<Float>(1, BufferOverflow.DROP_OLDEST).also { channel[url] = it }

        if (file.exists())
            progressChannel.close()
        else
            requests[url] = networkScope.launch {
                kotlin.runCatching {
                    cacheDir.mkdirs()
                    file.createNewFile()

                    client.request<HttpStatement>(request).execute { httpResponse ->
                        val responseChannel: ByteReadChannel = httpResponse.receive()
                        val contentLength = httpResponse.contentLength() ?: -1
                        var readBytes = 0f

                        file.outputStream().use { outputStream ->
                            while (!responseChannel.isClosedForRead) {
                                if (!isActive) {
                                    file.delete()
                                    break
                                }

                                val packet = responseChannel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                                while (!packet.isEmpty) {
                                    if (!isActive) {
                                        file.delete()
                                        break
                                    }

                                    val bytes = packet.readBytes()
                                    outputStream.write(bytes)

                                    readBytes += bytes.size
                                    progressChannel.trySend(readBytes / contentLength)
                                }
                            }
                        }
                        progressChannel.close()
                    }
                }.onFailure {
                    logger.warning(it)
                    file.delete()
                    FirebaseCrashlytics.getInstance().recordException(it)
                    progressChannel.close(it)
                }
            }

        return@coroutineScope progressChannel to file
    }
}