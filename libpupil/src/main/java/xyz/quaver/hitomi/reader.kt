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

import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

fun getReferer(galleryID: Int) = "https://hitomi.la/reader/$galleryID.html"

@Serializable
data class GalleryInfo(
    val width: Int,
    val hash: String? = null,
    val haswebp: Int = 0,
    val name: String,
    val height: Int
)

@Serializable
data class Reader(val code: Code, val title: String, val galleryInfo: List<GalleryInfo>) {
    enum class Code {
        HITOMI,
        HIYOBI,
        SORALA
    }
}

//Set header `Referer` to reader url to avoid 403 error
fun getReader(galleryID: Int) : Reader {
    val readerUrl = "https://hitomi.la/reader/$galleryID.html"

    val doc = Jsoup.connect(readerUrl).get()

   return Reader(Reader.Code.HITOMI, doc.title(), getGalleryInfo(galleryID))
}