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
import java.util.*
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

        client.downloadApk("http://a/", tempFile).collect()

        assertArrayEquals(expected, tempFile.readBytes())
    }

    @Test
    fun latestRelease() = runTest {
        val expectedVersion = "5.3.7"
        val expectedApkUrl = "https://github.com/tom5079/Pupil/releases/download/5.3.7/Pupil-v5.3.7.apk"
        val expectedUpdateNotes = mapOf(
            Locale.KOREAN to """
                * 가끔씩 무한로딩 걸리는 현상 수정
                * 백업시 즐겨찾기 태그도 백업되게 수정
                * 이전 안드로이드에서 앱이 튕기는 오류 수정
            """.trimIndent(),
            Locale.JAPANESE to """
                * 稀に接続不可になるバグを修正
                * お気に入りタグを含むようバックアップ機能を修正
                * 旧バージョンのアンドロイドでアプリがクラッシュするバグを解決
            """.trimIndent(),
            Locale.ENGLISH to """
                * Fixed occasional outage
                * Updated backup/restore feature to include favorite tags
                * Fixed app crashing on older Androids
            """.trimIndent()
        )

        val mockEngine = MockEngine { _ ->
            val response = javaClass.getResource("/releases.json")!!.readText()
            respond(response)
        }

        val client = PupilHttpClient(mockEngine)

        val release = client.latestRelease()

        assertEquals(expectedVersion, release.version)
        assertEquals(expectedApkUrl, release.apkUrl)

        println(expectedUpdateNotes)
        println(release.updateNotes)
        assertEquals(expectedUpdateNotes, release.updateNotes)
    }

}