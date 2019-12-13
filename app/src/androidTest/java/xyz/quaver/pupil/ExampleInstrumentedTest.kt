/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2019  tom5079
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

@file:Suppress("UNUSED_VARIABLE")

package xyz.quaver.pupil

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import xyz.quaver.hiyobi.cookie
import xyz.quaver.hiyobi.createImgList
import xyz.quaver.hiyobi.getReader
import xyz.quaver.hiyobi.user_agent
import xyz.quaver.pupil.ui.LockActivity
import xyz.quaver.pupil.util.getDownloadDirectory
import xyz.quaver.pupil.util.updateOldReaderGalleries
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Log.i("PUPILD", getDownloadDirectory(appContext).absolutePath ?: "")
        assertEquals("xyz.quaver.pupil", appContext.packageName)
    }

    @Test
    fun checkCacheDir() {
        val activityTestRule = ActivityTestRule(LockActivity::class.java)
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        activityTestRule.launchActivity(Intent())
    }

    @Test
    fun test_doSearch() {
        val reader = getReader( 1426382)

        val data: ByteArray

        with(URL(createImgList(1426382, reader)[0].path).openConnection() as HttpsURLConnection) {
            setRequestProperty("User-Agent", user_agent)
            setRequestProperty("Cookie", cookie)

            data = inputStream.readBytes()
        }

        Log.d("Pupil", data.size.toString())
    }

    @UseExperimental(ImplicitReflectionSerializer::class)
    @Test
    fun test_deleteCodeFromReader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val json = Json(JsonConfiguration.Stable)

        listOf(
            getDownloadDirectory(context),
            File(context.cacheDir, "imageCache")
        ).forEach { root ->
            root.listFiles()?.forEach gallery@{ gallery ->
                val reader = json.parseJson(File(gallery, "reader.json").apply {
                    if (!exists())
                        return@gallery
                }.readText())
                    .jsonObject.toMutableMap()

                Log.d("PUPILD", gallery.name)

                reader.remove("code")

                File(gallery, "reader.json").writeText(JsonObject(reader).toString())
            }
        }
    }

    @Test
    fun test_updateOldReader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        updateOldReaderGalleries(context)
    }
}