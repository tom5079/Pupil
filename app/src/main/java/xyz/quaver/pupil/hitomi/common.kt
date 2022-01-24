/*
 *    Copyright 2019 tom5079
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package xyz.quaver.pupil.hitomi

import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.quaver.pupil.*
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val protocol = "https:"

val evaluationContext = Dispatchers.Main + Job()

/**
 * kotlinx.serialization.json.Json object for global use
 * properties should not be changed
 *
 * @see [https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-core/kotlinx-serialization-core/kotlinx.serialization.json/-json/index.html]
 */
val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    allowSpecialFloatingPointValues = true
    useArrayPolymorphism = true
}

suspend inline fun <reified T> WebView.evaluate(script: String): T = coroutineScope {
    var result: String? = null

    while (result == null) {
        try {
            while (!oldWebView && !(webViewReady && !webViewFailed)) delay(100)

            result = if (oldWebView)
                "null"
            else withContext(evaluationContext) {
                suspendCoroutine { continuation ->
                    evaluateJavascript(script) {
                        continuation.resume(it)
                    }
                }

            }
        } catch (e: CancellationException) {
            if (e.message != "reload") result = "null"
        }
    }

    json.decodeFromString(result)
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend inline fun <reified T> WebView.evaluatePromise(
    script: String,
    then: String = ".then(result => Callback.onResult(%uid, JSON.stringify(result))).catch(err => Callback.onError(%uid, String.raw`$script`, err.message, err.stack))"
): T = coroutineScope {
    var result: String? = null

    while (result == null) {
        try {
            while (!oldWebView && !(webViewReady && !webViewFailed)) delay(100)

            result = if (oldWebView)
                "null"
            else withContext(evaluationContext) {
                val uid = UUID.randomUUID().toString()

                val flow: Flow<Pair<String, String?>> = webViewFlow.transformWhile { (currentUid, result) ->
                    if (currentUid == uid) {
                        emit(currentUid to result)
                    }
                    currentUid != uid
                }

                launch {
                    evaluateJavascript((script + then).replace("%uid", "'$uid'"), null)
                }

                flow.first().second
            }
        } catch (e: CancellationException) {
            if (e.message != "reload") result = "null"
        }
    }

    json.decodeFromString(result)
}

@Suppress("EXPERIMENTAL_API_USAGE")
suspend fun getGalleryInfo(galleryID: Int): GalleryInfo =
    webView.evaluatePromise("get_gallery_info($galleryID)")

//common.js
const val domain = "ltn.hitomi.la"

val String?.js: String
    get() = if (this == null) "null" else "'$this'"

@OptIn(ExperimentalSerializationApi::class)
suspend fun urlFromUrlFromHash(galleryID: Int, image: GalleryFiles, dir: String? = null, ext: String? = null, base: String? = null): String =
    webView.evaluate(
        """
        url_from_url_from_hash(
            ${galleryID.toString().js},
            ${Json.encodeToString(image)},
            ${dir.js}, ${ext.js}, ${base.js}
        )
        """.trimIndent()
    )

suspend fun imageUrlFromImage(galleryID: Int, image: GalleryFiles, noWebp: Boolean) : String {
    return when {
        noWebp ->
            urlFromUrlFromHash(galleryID, image)
//        image.hasavif != 0 ->
//            urlFromUrlFromHash(galleryID, image, "avif", null, "a")
        image.haswebp != 0 ->
            urlFromUrlFromHash(galleryID, image, "webp", null, "a")
        else ->
            urlFromUrlFromHash(galleryID, image)
    }
}
