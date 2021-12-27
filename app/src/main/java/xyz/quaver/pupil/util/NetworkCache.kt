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
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import java.io.File
import java.io.IOException
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

    private val flowMutex = Mutex()
    private val flow = ConcurrentHashMap<String, MutableStateFlow<Float>>()

    private val requestsMutex = Mutex()
    private val requests = ConcurrentHashMap<String, Job>()

    private val activeFilesMutex = Mutex()
    private val activeFiles = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private fun urlToFilename(url: String): String =
        sha256(url.toByteArray()).joinToString("") { "%02x".format(it) }

    fun cleanup() = CoroutineScope(Dispatchers.IO).launch {
        if (cacheDir.size() > CACHE_LIMIT)
            cacheDir.listFiles { file -> file.name !in activeFiles }?.forEach { it.delete() }
    }

    fun free(urls: List<String>) = CoroutineScope(Dispatchers.IO).launch {
        requestsMutex.withLock {
            urls.forEach {
                requests[it]?.cancel()
            }
        }
        flowMutex.withLock {
            urls.forEach {
                flow.remove(it)
            }
        }
        activeFilesMutex.withLock {
            urls.forEach {
                activeFiles.remove(urlToFilename(it))
            }
        }
    }

    fun clear() = CoroutineScope(Dispatchers.IO).launch {
        requests.values.forEach { it.cancel() }
        flow.clear()
        activeFiles.clear()
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun load(force: Boolean = false, requestBuilder: HttpRequestBuilder.() -> Unit): Pair<StateFlow<Float>, File> = coroutineScope {
        val request = HttpRequestBuilder().apply(requestBuilder)

        val url = request.url.buildString()

        val fileName = urlToFilename(url)
        val file = File(cacheDir, fileName)
        activeFiles.add(fileName)

        val progressFlow = flowMutex.withLock {
            if (flow.contains(url)) {
                flow[url]!!
            } else MutableStateFlow(0f).also { flow[url] = it }
        }

        requestsMutex.withLock {
            if (!requests.contains(url) || force) {
                if (force) requests[url]?.cancelAndJoin()

                requests[url] = networkScope.launch {
                    runCatching {
                        if (!force && file.exists()) {
                            progressFlow.emit(Float.POSITIVE_INFINITY)
                        } else {
                            cacheDir.mkdirs()
                            file.createNewFile()

                            client.request<HttpStatement>(request).execute { httpResponse ->
                                if (!httpResponse.status.isSuccess()) throw IOException("${request.url} failed with code ${httpResponse.status.value}")
                                val responseChannel: ByteReadChannel = httpResponse.receive()
                                val contentLength = httpResponse.contentLength() ?: -1
                                var readBytes = 0f

                                file.outputStream().use { outputStream ->
                                    outputStream.channel.truncate(0)
                                    while (!responseChannel.isClosedForRead) {
                                        if (!isActive) {
                                            file.delete()
                                            break
                                        }

                                        val packet =
                                            responseChannel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                                        while (!packet.isEmpty) {
                                            if (!isActive) {
                                                file.delete()
                                                break
                                            }

                                            val bytes = packet.readBytes()
                                            outputStream.write(bytes)

                                            readBytes += bytes.size
                                            progressFlow.emit(readBytes / contentLength)
                                        }
                                    }
                                }
                                progressFlow.emit(Float.POSITIVE_INFINITY)
                            }
                        }
                    }.onFailure {
                        logger.warning(it)
                        file.delete()
                        FirebaseCrashlytics.getInstance().recordException(it)
                        progressFlow.emit(Float.NEGATIVE_INFINITY)
                        requestsMutex.withLock {
                            requests.remove(url)
                        }
                    }
                }
            }
        }

        return@coroutineScope progressFlow to file
    }
}