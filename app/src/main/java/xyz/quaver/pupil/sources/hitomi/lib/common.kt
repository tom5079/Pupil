/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2021 tom5079
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
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.sources.hitomi.lib

import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

const val protocol = "https:"

private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    allowSpecialFloatingPointValues = true
    useArrayPolymorphism = true
}

suspend fun getGalleryInfo(client: HttpClient, galleryID: Int): GalleryInfo = withContext(Dispatchers.IO) {
    json.decodeFromString(
        client.get<String>("$protocol//$domain/galleries/$galleryID.js")
            .replace("var galleryinfo = ", "")
    )
}

//common.js
const val domain = "ltn.hitomi.la"
const val galleryblockextension = ".html"
const val galleryblockdir = "galleryblock"
const val nozomiextension = ".nozomi"

fun subdomainFromGalleryID(g: Int, numberOfFrontends: Int) : String {
    val o = g % numberOfFrontends

    return (97+o).toChar().toString()
}

fun subdomainFromURL(url: String, base: String? = null) : String {
    var retval = "b"

    if (!base.isNullOrBlank())
        retval = base

    var numberOfFrontends = 2
    val b = 16

    val r = Regex("""/[0-9a-f]/([0-9a-f]{2})/""")
    val m = r.find(url) ?: return "a"

    val g = m.groupValues[1].toIntOrNull(b)

    if (g != null) {
        val o = when {
            g < 0x7c -> 1
            else -> 0
        }

        // retval = subdomainFromGalleryID(g, numberOfFrontends) + retval
        retval = (97+o).toChar().toString() + retval
    }

    return retval
}

fun urlFromURL(url: String, base: String? = null) : String {
    return url.replace(Regex("""//..?\.hitomi\.la/"""), "//${subdomainFromURL(url, base)}.hitomi.la/")
}


fun fullPathFromHash(hash: String?) : String? {
    return when {
        (hash?.length ?: 0) < 3 -> hash
        else -> hash!!.replace(Regex("^.*(..)(.)$"), "$2/$1/$hash")
    }
}

@Suppress("NAME_SHADOWING", "UNUSED_PARAMETER")
fun urlFromHash(galleryID: Int, image: GalleryFiles, dir: String? = null, ext: String? = null) : String {
    val ext = ext ?: dir ?: image.name.split('.').last()
    val dir = dir ?: "images"
    return "$protocol//a.hitomi.la/$dir/${fullPathFromHash(image.hash)}.$ext"
}

fun urlFromUrlFromHash(galleryID: Int, image: GalleryFiles, dir: String? = null, ext: String? = null, base: String? = null) =
    urlFromURL(urlFromHash(galleryID, image, dir, ext), base)

fun rewriteTnPaths(html: String) =
    html.replace(Regex("""//tn\.hitomi\.la/[^/]+/[0-9a-f]/[0-9a-f]{2}/""")) { url ->
        urlFromURL(url.value, "tn")
    }

fun imageUrlFromImage(galleryID: Int, image: GalleryFiles, noWebp: Boolean) : String {
    return when {
        noWebp ->
            urlFromUrlFromHash(galleryID, image)
        //image.hasavif != 0 ->
        //    urlFromUrlFromHash(galleryID, image, "avif", null, "a")
        image.haswebp != 0 ->
            urlFromUrlFromHash(galleryID, image, "webp", null, "a")
        else ->
            urlFromUrlFromHash(galleryID, image)
    }
}