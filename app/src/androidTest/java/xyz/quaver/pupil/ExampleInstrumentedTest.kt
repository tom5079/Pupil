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

import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import xyz.quaver.hiyobi.cookie
import xyz.quaver.hiyobi.createImgList
import xyz.quaver.hiyobi.getReader
import xyz.quaver.hiyobi.user_agent
import xyz.quaver.pupil.ui.LockActivity
import xyz.quaver.pupil.util.download.Cache
import xyz.quaver.pupil.util.download.DownloadWorker
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
    }

    @Test
    fun checkCacheDir() {
        val activityTestRule = ActivityTestRule(LockActivity::class.java)
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        ContextCompat.getExternalFilesDirs(appContext, null).forEachIndexed { index, file ->
            Log.i("PUPILD", "$index: ${file?.absolutePath}")
        }
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

    @Test
    fun test_downloadWorker() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val galleryID = 515515

        val worker = DownloadWorker.getInstance(context)

        worker.queue.add(galleryID)

        while(worker.progress.indexOfKey(galleryID) < 0 || worker.progress[galleryID] != null) {
            Log.i("PUPILD", worker.progress[galleryID]?.joinToString(" ") ?: "null")

            if (worker.progress[galleryID]?.all { !it.isFinite() } == true)
                break
        }

        Log.i("PUPILD", "DONE!!")
    }

    @Test
    fun test_getReaderOrNull() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val galleryID = 1561552

        runBlocking {
            Log.i("PUPILD", Cache(context).getReader(galleryID)?.title ?: "null")
        }

        Log.i("PUPILD", Cache(context).getReaderOrNull(galleryID)?.title ?: "null")
    }
}