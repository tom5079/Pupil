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

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import xyz.quaver.json
import xyz.quaver.pupil.Pupil
import xyz.quaver.pupil.webView
import xyz.quaver.readText
import java.net.URL
import java.nio.charset.Charset

const val protocol = "https:"

@Suppress("EXPERIMENTAL_API_USAGE")
fun getGalleryInfo(galleryID: Int) =
    json.decodeFromString<GalleryInfo>(
        URL("$protocol//$domain/galleries/$galleryID.js").readText()
            .replace("var galleryinfo = ", "")
    )

//common.js
const val domain = "ltn.hitomi.la"
const val galleryblockextension = ".html"
const val galleryblockdir = "galleryblock"
const val nozomiextension = ".nozomi"

@SuppressLint("SetJavaScriptEnabled")
object gg {
    suspend fun m(g: Int): Int = coroutineScope {
        var result: Int? = null

        launch(Dispatchers.Main) {
            while (webView.progress != 100) yield()

            webView.evaluateJavascript("gg.m($g)") {
                result = it.toInt()
            }
        }

        while (result == null) yield()

        result!!
    }

    suspend fun b(): String = coroutineScope {
        var result: String? = null

        launch(Dispatchers.Main) {
            while (webView.progress != 100) yield()

            webView.evaluateJavascript("gg.b") {
                result = it.replace("\"", "")
            }
        }

        while (result == null) yield()

        result!!
    }

    suspend fun s(h: String): String = coroutineScope {
        var result: String? = null

        launch(Dispatchers.Main) {
            while (webView.progress != 100) yield()

            webView.evaluateJavascript("gg.s('$h')") {
                result = it.replace("\"", "")
            }
        }

        while (result == null) yield()

        result!!
    }
}

suspend fun subdomainFromURL(url: String, base: String? = null) : String {
    var retval = "b"

    if (!base.isNullOrBlank())
        retval = base

    val b = 16

    val r = Regex("""/[0-9a-f]{61}([0-9a-f]{2})([0-9a-f])""")
    val m = r.find(url) ?: return "a"

    val g = m.groupValues.let { it[2]+it[1] }.toIntOrNull(b)

    if (g != null) {
        retval = (97+ gg.m(g)).toChar().toString() + retval
    }

    return retval
}

suspend fun urlFromUrl(url: String, base: String? = null) : String {
    return url.replace(Regex("""//..?\.hitomi\.la/"""), "//${subdomainFromURL(url, base)}.hitomi.la/")
}


suspend fun fullPathFromHash(hash: String) : String =
    "${gg.b()}${gg.s(hash)}/$hash"

fun realFullPathFromHash(hash: String): String =
    hash.replace(Regex("""^.*(..)(.)$"""), "$2/$1/$hash")

suspend fun urlFromHash(galleryID: Int, image: GalleryFiles, dir: String? = null, ext: String? = null) : String {
    val ext = ext ?: dir ?: image.name.takeLastWhile { it != '.' }
    val dir = dir ?: "images"
    return "https://a.hitomi.la/$dir/${fullPathFromHash(image.hash)}.$ext"
}

suspend fun urlFromUrlFromHash(galleryID: Int, image: GalleryFiles, dir: String? = null, ext: String? = null, base: String? = null) =
    if (base == "tn")
        urlFromUrl("https://a.hitomi.la/$dir/${realFullPathFromHash(image.hash)}.$ext", base)
    else
        urlFromUrl(urlFromHash(galleryID, image, dir, ext), base)

suspend fun rewriteTnPaths(html: String) =
    Regex("""//tn\.hitomi\.la/[^/]+/[0-9a-f]/[0-9a-f]{2}/[0-9a-f]{64}""").find(html)?.let { m ->
        html.replaceRange(m.range, urlFromUrl(m.value, "tn"))
    } ?: html

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
