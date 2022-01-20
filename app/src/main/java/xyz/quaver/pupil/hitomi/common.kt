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
import android.widget.Toast
import com.google.common.collect.ConcurrentHashMultiset
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.quaver.json
import xyz.quaver.pupil.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val protocol = "https:"

val evaluationContext = Dispatchers.Main + Job()

suspend fun WebView.evaluate(script: String): String = coroutineScope {
    var result: String? = null

    while (result == null) {
        try {
            result = withContext(evaluationContext) {
                while (webViewFailed || !webViewReady) yield()

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

    result
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun WebView.evaluatePromise(
    script: String,
    then: String = ".then(result => Callback.onResult(%uid, JSON.stringify(result))).catch(err => Callback.onError(%uid, JSON.stringify(err)))"
): String = coroutineScope {
    var result: String? = null

    while (result == null) {
        try {
            result = withContext(evaluationContext) {
                while (webViewFailed || !webViewReady) yield()

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

    result
}

@Suppress("EXPERIMENTAL_API_USAGE")
suspend fun getGalleryInfo(galleryID: Int): GalleryInfo {
    val result = webView.evaluatePromise("get_gallery_info($galleryID)")

    return json.decodeFromString(result)
}

//common.js
const val domain = "ltn.hitomi.la"
const val galleryblockdir = "galleryblock"
const val nozomiextension = ".nozomi"

val String?.js: String
    get() = if (this == null) "null" else "'$this'"

@OptIn(ExperimentalSerializationApi::class)
suspend fun urlFromUrlFromHash(galleryID: Int, image: GalleryFiles, dir: String? = null, ext: String? = null, base: String? = null): String {
    val result = webView.evaluate(
        """
        url_from_url_from_hash(
            ${galleryID.toString().js},
            ${Json.encodeToString(image)},
            ${dir.js}, ${ext.js}, ${base.js}
        )
        """.trimIndent()
    )

    return Json.decodeFromString(result)
}

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
