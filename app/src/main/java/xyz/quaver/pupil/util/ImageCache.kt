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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.text.toByteArray

class NetworkCache(context: Context) : DIAware {
    override val di by closestDI(context)

    private val client: HttpClient by instance()

    private val cacheDir = context.cacheDir

    private val _channels = ConcurrentHashMap<String, Channel<Float>>()
    val channels = _channels as Map<String, Channel<Float>>

    private val requests = mutableMapOf<String, Job>()

    private val networkScope = CoroutineScope(Executors.newFixedThreadPool(4).asCoroutineDispatcher())

    private val logger = newLogger(LoggerFactory.default)

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun load(requestBuilder: HttpRequestBuilder.() -> Unit): File = coroutineScope {
        val request = HttpRequestBuilder().apply(requestBuilder)

        val url = request.url.buildString()
        val hash = sha256(url.toByteArray()).joinToString("") { "%02x".format(it) }

        val file = File(cacheDir, "$hash.${url.takeLastWhile { it != '.' }}")

        val progressChannel = if (_channels[url]?.isClosedForSend == false)
            _channels[url]!!
        else
            Channel<Float>(1, BufferOverflow.DROP_OLDEST).also { _channels[url] = it }

        if (file.exists())
            progressChannel.close()
        else
            requests[url] = networkScope.launch {
                kotlin.runCatching {
                    file.createNewFile()

                    client.request<HttpStatement>(request).execute { httpResponse ->
                        val responseChannel: ByteReadChannel = httpResponse.receive()
                        val contentLength = httpResponse.contentLength() ?: -1
                        var readBytes = 0f

                        file.outputStream().use { outputStream ->
                            while (!responseChannel.isClosedForRead) {
                                val packet = responseChannel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                                while (!packet.isEmpty) {
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

        return@coroutineScope file
    }
}