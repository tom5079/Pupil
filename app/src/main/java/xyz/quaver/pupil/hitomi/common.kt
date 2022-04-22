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

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import xyz.quaver.pupil.client
import java.io.IOException
import java.net.URL
import java.util.concurrent.Executors
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

const val protocol = "https:"

@Serializable
data class Artist(
    val artist: String,
    val url: String
)

@Serializable
data class Group(
    val group: String,
    val url: String
)

@Serializable
data class Parody(
    val parody: String,
    val url: String
)

@Serializable
data class Character(
    val character: String,
    val url: String
)

@Serializable
data class Tag(
    val tag: String,
    val url: String,
    val female: String? = null,
    val male: String? = null
)

@Serializable
data class Language(
    val galleryid: String,
    val url: String,
    val language_localname: String,
    val name: String
)

@Serializable
data class GalleryInfo(
    val id: String,
    val title: String,
    val japanese_title: String? = null,
    val language: String? = null,
    val type: String,
    val date: String,
    val artists: List<Artist>? = null,
    val groups: List<Group>? = null,
    val parodys: List<Parody>? = null,
    val tags: List<Tag>? = null,
    val related: List<Int> = emptyList(),
    val languages: List<Language> = emptyList(),
    val characters: List<Character>? = null,
    val scene_indexes: List<Int>? = emptyList(),
    val files: List<GalleryFiles> = emptyList()
)

val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    allowSpecialFloatingPointValues = true
    useArrayPolymorphism = true
}

typealias HeaderSetter = (Request.Builder) -> Request.Builder
fun URL.readText(settings: HeaderSetter? = null): String {
    val request = Request.Builder()
        .url(this).let {
            settings?.invoke(it) ?: it
        }.build()

    return client.newCall(request).execute().also{ if (it.code() != 200) throw IOException("CODE ${it.code()}") }.body()?.use { it.string() } ?: throw IOException()
}

fun URL.readBytes(settings: HeaderSetter? = null): ByteArray {
    val request = Request.Builder()
        .url(this).let {
            settings?.invoke(it) ?: it
        }.build()

    return client.newCall(request).execute().also { if (it.code() != 200) throw IOException("CODE ${it.code()}") }.body()?.use { it.bytes() } ?: throw IOException()
}

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

val evaluationContext = Dispatchers.Main + Job()

object gg {
    private var lastRetrieval: Instant? = null

    private val mutex = Mutex()

    private var mDefault = 0
    private val mMap = mutableMapOf<Int, Int>()

    private var b = ""

    @OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    private suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (lastRetrieval == null || (lastRetrieval!! + 1.minutes) < now()) {
                val ggjs: String = suspendCancellableCoroutine { continuation ->
                    val call = client.newCall(Request.Builder().url("https://ltn.hitomi.la/gg.js").build())

                    call.enqueue(object: Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (continuation.isCancelled) return
                            continuation.resumeWithException(e)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            if (!call.isCanceled) {
                                response.body()?.use {
                                    continuation.resume(it.string()) {
                                        call.cancel()
                                    }
                                }
                            }
                        }
                    })

                    continuation.invokeOnCancellation {
                        call.cancel()
                    }
                }

                mDefault = Regex("var o = (\\d)").find(ggjs)!!.groupValues[1].toInt()
                val o = Regex("o = (\\d); break;").find(ggjs)!!.groupValues[1].toInt()

                mMap.clear()
                Regex("case (\\d+):").findAll(ggjs).forEach {
                    val case = it.groupValues[1].toInt()
                    mMap[case] = o
                }

                b = Regex("b: '(.+)'").find(ggjs)!!.groupValues[1]

                lastRetrieval = now()
            }
        }
    }

    suspend fun m(g: Int): Int {
        refresh()

        return mMap[g] ?: mDefault
    }

    suspend fun b(): String {
        refresh()
        return b
    }
    fun s(h: String): String {
        val m = Regex("(..)(.)$").find(h)
        return m!!.groupValues.let { it[2]+it[1] }.toInt(16).toString(10)
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

suspend fun rewriteTnPaths(html: String) {
    html.replace(Regex("""//tn\.hitomi\.la/[^/]+/[0-9a-f]/[0-9a-f]{2}/[0-9a-f]{64}""")) { url ->
        runBlocking {
            urlFromUrl(url.value, "tn")
        }
    }
}

suspend fun imageUrlFromImage(galleryID: Int, image: GalleryFiles, noWebp: Boolean) : String {
    return urlFromUrlFromHash(galleryID, image, "webp", null, "a")
//    return when {
//        noWebp ->
//            urlFromUrlFromHash(galleryID, image)
////        image.hasavif != 0 ->
////            urlFromUrlFromHash(galleryID, image, "avif", null, "a")
//        image.haswebp != 0 ->
//            urlFromUrlFromHash(galleryID, image, "webp", null, "a")
//        else ->
//            urlFromUrlFromHash(galleryID, image)
//    }
}
