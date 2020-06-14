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

package xyz.quaver.hitomi

import xyz.quaver.json
import xyz.quaver.proxy
import java.net.URL

const val protocol = "https:"

@Suppress("EXPERIMENTAL_API_USAGE")
fun getGalleryInfo(galleryID: Int) =
    json.parse(
        GalleryInfo.serializer(),
        URL("$protocol//$domain/galleries/$galleryID.js").openConnection(proxy).getInputStream().use {
            it.reader().readText()
        }.replace("var galleryinfo = ", "")
    )

//common.js
var adapose = false
const val numberOfFrontends = 3
const val domain = "ltn.hitomi.la"
const val galleryblockdir = "galleryblock"
const val nozomiextension = ".nozomi"

fun subdomainFromGalleryID(g: Int) : String {
    if (adapose)
        return "0"

    val o = g % numberOfFrontends

    return (97+o).toChar().toString()
}

fun subdomainFromURL(url: String, base: String? = null) : String {
    var retval = "a"

    if (!base.isNullOrBlank())
        retval = base

    val b = 16
    val r = Regex("""/[0-9a-f]/([0-9a-f]{2})/""")
    val m = r.find(url) ?: return retval

    val g = m.groupValues[1].toIntOrNull(b) ?: return retval

    retval = subdomainFromGalleryID(g) + retval

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

fun imageUrlFromImage(galleryID: Int, image: GalleryFiles, noWebp: Boolean) : String {
    val webp = if (image.hash != null && image.haswebp != 0 && !noWebp)
        "webp"
    else
        null

    return urlFromUrlFromHash(galleryID, image, webp)
}