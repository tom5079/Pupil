package xyz.quaver.pupil

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import xyz.quaver.hiyobi.cookie
import xyz.quaver.hiyobi.getReader
import xyz.quaver.hiyobi.user_agent
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
        assertEquals("xyz.quaver.pupil", appContext.packageName)
    }

    @Test
    fun checkCacheDir() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(appContext.cacheDir, "imageCache/1412251/01.jpg.webp")

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)

        Log.d("Pupil", bitmap.byteCount.toString())
    }

    @Test
    fun test_doSearch() {
        val reader = getReader(1426382)

        val data: ByteArray

        with(URL(reader[0].url).openConnection() as HttpsURLConnection) {
            setRequestProperty("User-Agent", user_agent)
            setRequestProperty("Cookie", cookie)

            data = inputStream.readBytes()
        }

        Log.d("Pupil", data.size.toString())
    }
}
