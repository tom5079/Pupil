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

import androidx.compose.ui.res.stringArrayResource
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
import kotlinx.serialization.json.*
import java.io.File
import java.util.*

@Serializable
data class RemoteSourceInfo(
    val projectName: String,
    val name: String,
    val version: String
)

class Release(
    val version: String,
    val apkUrl: String,
    val releaseNotes: Map<Locale, String>
)

private val localeMap = mapOf(
    "한국어" to Locale.KOREAN,
    "日本語" to Locale.JAPANESE,
    "English" to Locale.ENGLISH
)

class PupilHttpClient(engine: HttpClientEngine) {
    private val httpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json()
        }
    }

    /**
     * Fetch a list of available sources from PupilSources repository.
     * Returns empty map when exception occurs
     */
    suspend fun getRemoteSourceList(): Map<String, RemoteSourceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            httpClient.get("https://tom5079.github.io/PupilSources/versions.json").body<Map<String, RemoteSourceInfo>>()
        }.getOrDefault(emptyMap())
    }

    /**
     * Downloads specific file from :url to :dest.
     * Returns flow that emits progress.
     * when value emitted by flow {
     *   in 0f .. 1f        -> downloading
     *   POSITIVE_INFINITY  -> download finised
     *   NEGATIVE_INFINITY  -> exception occured
     * }
     */
    fun downloadFile(url: String, dest: File) = flow {
        runCatching {
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
        }.onFailure {
            emit(Float.NEGATIVE_INFINITY)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Latest application release info from Github API.
     * Returns null when exception occurs.
     */
    suspend fun latestRelease(beta: Boolean = true): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val releases = Json.parseToJsonElement(
                httpClient.get("https://api.github.com/repos/tom5079/Pupil/releases").bodyAsText()
            ).jsonArray

            val latestRelease = releases.first { release ->
                beta || !release.jsonObject["prerelease"]!!.jsonPrimitive.boolean
            }.jsonObject

            val version = latestRelease["tag_name"]!!.jsonPrimitive.content

            val apkUrl = latestRelease["assets"]!!.jsonArray.first { asset ->
                val name = asset.jsonObject["name"]!!.jsonPrimitive.content
                name.startsWith("Pupil-v") && name.endsWith(".apk")
            }.jsonObject["browser_download_url"]!!.jsonPrimitive.content

            val releaseNotes: Map<Locale, String> = buildMap {
                val body = latestRelease["body"]!!.jsonPrimitive.content

                var locale: Locale? = null
                val stringBuilder = StringBuilder()
                body.lineSequence().forEach { line ->
                    localeMap[line.drop(3)]?.let { newLocale ->
                        if (locale != null) {
                            put(locale!!, stringBuilder.deleteCharAt(stringBuilder.length-1).toString())
                            stringBuilder.clear()
                        }
                        locale = newLocale
                        return@forEach
                    }

                    if (locale != null) stringBuilder.appendLine(line)
                }
                put(locale!!, stringBuilder.deleteCharAt(stringBuilder.length-1).toString())
            }

            Release(version, apkUrl, releaseNotes)
        }.getOrNull()
    }
}