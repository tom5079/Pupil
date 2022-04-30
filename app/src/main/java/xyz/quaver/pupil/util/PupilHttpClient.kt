/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2022  tom5079
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

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class RemoteSourceInfo(
    val projectName: String,
    val name: String,
    val version: String
)

class PupilHttpClient(engine: HttpClientEngine) {
    private val httpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun getRemoteSourceList(): Map<String, RemoteSourceInfo> = withContext(Dispatchers.IO) {
        httpClient.get("https://tom5079.github.io/PupilSources/versions.json").body()
    }

    fun downloadApk(sourceInfo: RemoteSourceInfo, dest: File) = flow {
        val url =
            "https://github.com/tom5079/PupilSources/releases/download/${sourceInfo.name}-${sourceInfo.version}/${sourceInfo.projectName}-release.apk"

        httpClient.prepareGet(url).execute { response ->
            val channel = response.bodyAsChannel()
            val contentLength = response.contentLength() ?: -1
            var readBytes = 0f

            dest.outputStream().use { outputStream ->
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                    while (!packet.isEmpty) {
                        val bytes = packet.readBytes()
                        outputStream.write(bytes)

                        readBytes += bytes.size
                        emit(readBytes / contentLength)
                    }
                }
            }
        }

        emit(Float.POSITIVE_INFINITY)
    }.flowOn(Dispatchers.IO)
}