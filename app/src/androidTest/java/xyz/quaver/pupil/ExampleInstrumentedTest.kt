package xyz.quaver.pupil

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xyz.quaver.hitomi.fetchNozomi
import xyz.quaver.hiyobi.cookie
import xyz.quaver.hiyobi.getReader
import xyz.quaver.hiyobi.user_agent
import xyz.quaver.pupil.ui.LockActivity
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

        Log.d("Pupil", fetchNozomi().first.size.toString())
    }

    @Test
    fun checkCacheDir() {
        val activityTestRule = ActivityTestRule<LockActivity>(LockActivity::class.java)
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        activityTestRule.launchActivity(Intent())

        while(true);
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
