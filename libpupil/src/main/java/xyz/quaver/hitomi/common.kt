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

const val protocol = "https:"

//common.js
var adapose = false
const val numberOfFrontends = 2
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

    if (base != null)
        retval = base

    val r = Regex("""/\d*(\d)/""")
    val m = r.find(url)

    m ?: return retval

    var g = m.groups[1]!!.value.toIntOrNull()

    g ?: return retval

    if (g == 1)
        g = 0

    retval = subdomainFromGalleryID(g) + retval

    return retval
}

fun urlFromURL(url: String, base: String? = null) : String {
    return url.replace(Regex("//..?\\.hitomi\\.la/"), "//${subdomainFromURL(url, base)}.hitomi.la/")
}