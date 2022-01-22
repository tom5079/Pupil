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
import android.content.Intent
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.io.use

class ApkDownloadManager(private val context: Context, private val client: HttpClient) {
    fun download(projectName: String, sourceName: String, version: String) = flow {
        val url = "https://github.com/tom5079/PupilSources/releases/download/$sourceName-$version/$projectName-release.apk"

        val file = File(context.externalCacheDir, "apks/$sourceName-$version.apk").also {
            it.parentFile?.mkdir()
            it.delete()
        }

        client.get<HttpStatement>(url).execute { response ->
            val channel: ByteReadChannel = response.receive()
            val contentLength = response.contentLength() ?: -1
            var readBytes = 0f

            file.outputStream().use { outputStream ->
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

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            setDataAndType(uri, MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"))
        }

        context.startActivity(intent)
    }.flowOn(Dispatchers.IO)
}