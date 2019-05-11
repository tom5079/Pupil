package xyz.quaver.pupil

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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
    @ExperimentalUnsignedTypes
    fun test_doSearch() {
        Log.d("TEST", "Starting...")

        runBlocking {
            CoroutineScope(Dispatchers.Main).launch {
                Log.d("TEST", "This is started! wow")
            }.join()
        }

        Log.d("TEST", "Finished! ...Really?")
    }
}
