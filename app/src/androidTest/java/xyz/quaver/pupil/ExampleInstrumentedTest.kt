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
import android.webkit.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xyz.quaver.pupil.hitomi.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Before
    fun init() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        runBlocking {
            withContext(Dispatchers.Main) {
                webView = WebView(appContext).apply {
                    settings.javaScriptEnabled = true

                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onResult(uid: String, result: String) {
                            _webViewFlow.tryEmit(uid to result)
                        }
                    }, "Callback")

                    loadDataWithBaseURL(
                        "https://hitomi.la/",
                        """
                            <script src="https://ltn.hitomi.la/jquery.min.js"></script>
                            <script src="https://ltn.hitomi.la/common.js"></script>
                            <script src="https://ltn.hitomi.la/search.js"></script>
                            <script src="https://ltn.hitomi.la/searchlib.js"></script>
                            <script src="https://ltn.hitomi.la/results.js></script>
                        """.trimIndent(),
                        "text/html",
                        null,
                        null
                    )
                }
            }
        }
    }

    @Test
    fun test_getGalleryIDsFromNozomi() {
        runBlocking {
            val result = getGalleryIDsFromNozomi(null, "index", "all")

            Log.d("PUPILD", "getGalleryIDsFromNozomi: ${result.size}")
        }
    }

    @Test
    fun test_getGalleryIDsForQuery() {
        runBlocking {
            val result = getGalleryIDsForQuery("female:crotch tattoo")

            Log.d("PUPILD", "getGalleryIDsForQuery: ${result.size}")
        }
    }

    @Test
    fun test_getSuggestionsForQuery() {
        runBlocking {
            val result = getSuggestionsForQuery("fem")

            Log.d("PUPILD", "getSuggestionsForQuery: ${result.size}")
        }
    }

    @Test
    fun test_urlFromUrlFromHash() {
        runBlocking {
            val galleryInfo = getGalleryInfo(2102416)

            val result = galleryInfo.files.map {
                imageUrlFromImage(2102416, it, false)
            }

            Log.d("PUPILD", result.toString())
        }
    }

    @Test
    fun test_getGalleryInfo() {
        runBlocking {
            val galleryInfo = getGalleryInfo(2102416)

            Log.d("PUPILD", galleryInfo.toString())
        }
    }

    @Test
    fun test_getGalleryBlock() {
        runBlocking {
            val block = getGalleryBlock(2102731)

            Log.d("PUPILD", block.toString())
        }
    }
}