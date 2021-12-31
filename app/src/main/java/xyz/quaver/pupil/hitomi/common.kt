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
import com.hippo.quickjs.android.QuickJS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import xyz.quaver.json
import xyz.quaver.readText
import java.net.URL

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

interface gg {
    fun m(g: Int): Int
    val b: String
    fun s(h: String): String

    companion object {
        @Volatile private var instance: gg? = null

        fun getInstance(): gg =
            instance ?: synchronized(this) {
                instance ?: object: gg {
                    private val ggjs by lazy { URL("https://ltn.hitomi.la/gg.js").readText() }
                    private val quickJS = QuickJS.Builder().build()

                    override fun m(g: Int): Int =
                        quickJS.createJSRuntime().use { runtime ->
                            runtime.createJSContext().use { context ->
                                context.evaluate(ggjs, "gg.js")
                                context.evaluate("gg.m($g)", "gg.js", Int::class.java)
                            }
                        }

                    override val b: String
                        get() =
                            quickJS.createJSRuntime().use { runtime ->
                                runtime.createJSContext().use { context ->
                                    context.evaluate(ggjs, "gg.js")
                                    context.evaluate("gg.b", "gg.js", String::class.java)
                                }
                            }

                    override fun s(h: String): String =
                        quickJS.createJSRuntime().use { runtime ->
                            runtime.createJSContext().use { context ->
                                context.evaluate(ggjs, "gg.js")
                                context.evaluate("gg.s('$h')", "gg.js", String::class.java)
                            }
                        }
                }.also { instance = it }
            }
    }
}

fun subdomainFromURL(url: String, base: String? = null) : String {
    var retval = "b"

    if (!base.isNullOrBlank())
        retval = base

    val b = 16

    val r = Regex("""/[0-9a-f]{61}([0-9a-f]{2})([0-9a-f])""")
    val m = r.find(url) ?: return "a"

    val g = m.groupValues.let { it[2]+it[1] }.toIntOrNull(b)

    if (g != null) {
        retval = (97+ gg.getInstance().m(g)).toChar().toString() + retval
    }

    return retval
}

fun urlFromUrl(url: String, base: String? = null) : String {
    return url.replace(Regex("""//..?\.hitomi\.la/"""), "//${subdomainFromURL(url, base)}.hitomi.la/")
}


fun fullPathFromHash(hash: String) : String =
    "${gg.getInstance().b}${gg.getInstance().s(hash)}/$hash"

fun realFullPathFromHash(hash: String): String =
    hash.replace(Regex("""^.*(..)(.)$"""), "$2/$1/$hash")

fun urlFromHash(galleryID: Int, image: GalleryFiles, dir: String? = null, ext: String? = null) : String {
    val ext = ext ?: dir ?: image.name.takeLastWhile { it != '.' }
    val dir = dir ?: "images"
    return "https://a.hitomi.la/$dir/${fullPathFromHash(image.hash)}.$ext"
}

fun urlFromUrlFromHash(galleryID: Int, image: GalleryFiles, dir: String? = null, ext: String? = null, base: String? = null) =
    if (base == "tn")
        urlFromUrl("https://a.hitomi.la/$dir/${realFullPathFromHash(image.hash)}.$ext", base)
    else
        urlFromUrl(urlFromHash(galleryID, image, dir, ext), base)

fun rewriteTnPaths(html: String) =
    html.replace(Regex("""//tn\.hitomi\.la/[^/]+/[0-9a-f]/[0-9a-f]{2}/[0-9a-f]{64}""")) { url ->
        urlFromUrl(url.value, "tn")
    }

fun imageUrlFromImage(galleryID: Int, image: GalleryFiles, noWebp: Boolean) : String {
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
