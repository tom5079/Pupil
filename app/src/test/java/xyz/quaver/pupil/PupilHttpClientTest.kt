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

package xyz.quaver.pupil

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.quaver.pupil.util.PupilHttpClient
import xyz.quaver.pupil.util.RemoteSourceInfo
import java.io.File
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class PupilHttpClientTest {

    val tempFile = File.createTempFile("pupilhttpclienttest", ".apk").also {
        it.deleteOnExit()
    }

    @Test
    fun getRemoteSourceList() = runTest {
        val expected = buildMap {
            put("hitomi.la", RemoteSourceInfo("hitomi", "hitomi.la", "0.0.1"))
        }

        val mockEngine = MockEngine { _ ->
            respond(Json.encodeToString(expected), headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.contentType))
        }

        val client = PupilHttpClient(mockEngine)

        assertEquals(expected, client.getRemoteSourceList())
    }
    
    @Test
    fun downloadApk() = runTest {
        val expected = Random.Default.nextBytes(1000000) // 1MB

        val mockEngine = MockEngine { _ ->
            respond(expected, headers = headersOf(HttpHeaders.ContentType, "application/vnd.android.package-archive"))
        }

        val client = PupilHttpClient(mockEngine)

        client.downloadApk(RemoteSourceInfo("", "", ""), tempFile).collect()

        assertArrayEquals(expected, tempFile.readBytes())
    }

}