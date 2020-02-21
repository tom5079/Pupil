/*
 *    Copyright 2020 tom5079
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

package xyz.quaver.hiyobi

import org.jsoup.Jsoup
import xyz.quaver.Code
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.protocol
import xyz.quaver.proxy

fun getGalleryBlock(galleryID: Int) : GalleryBlock? {
    val url = "$protocol//$hiyobi/info/$galleryID"

    val doc = Jsoup.connect(url).proxy(proxy).get()

    val galleryBlock = doc.selectFirst(".gallery-content")

    val galleryUrl = galleryBlock.selectFirst("a").attr("href")

    val thumbnails = listOf(galleryBlock.selectFirst("img").attr("abs:src"))

    val title = galleryBlock.selectFirst("b").text()
    val artists = galleryBlock.select("tr:matches(작가) a[href~=artist]").map { it.text() }
    val series = galleryBlock.select("tr:matches(원작) a").map { it.attr("href").substringAfter("series:").replace('_', ' ') }
    val type = galleryBlock.selectFirst("tr:matches(종류) a").attr("href").substringAfter("type:").replace('_', ' ')

    val language = "korean"

    val relatedTags = galleryBlock.select("tr:matches(태그) a").map { it.attr("href").substringAfterLast('/').replace('_', ' ') }

    return GalleryBlock(Code.HIYOBI, galleryID, galleryUrl, thumbnails, title, artists, series, type, language, relatedTags)
}