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

import kotlinx.serialization.Serializable

@Serializable
data class Gallery(
    val related: List<Int>,
    val langList: List<Pair<String, String>>,
    val cover: String,
    val title: String,
    val artists: List<String>,
    val groups: List<String>,
    val type: String,
    val language: String,
    val series: List<String>,
    val characters: List<String>,
    val tags: List<String>,
    val thumbnails: List<String>
)

suspend fun getGallery(galleryID: Int) : Gallery {
    val info = getGalleryInfo(galleryID)

    return Gallery(
        info.related,
        info.languages.map { it.name to it.galleryid },
        urlFromUrlFromHash(galleryID, info.files.first(), "webpbigtn", "webp", "tn"),
        info.title,
        info.artists?.map { it.artist }.orEmpty(),
        info.groups?.map { it.group }.orEmpty(),
        info.type,
        info.language.orEmpty(),
        info.parodys?.map { it.parody }.orEmpty(),
        info.characters?.map { it.character }.orEmpty(),
        info.tags?.map { "${if (it.female.isNullOrEmpty()) "" else "female:"}${if (it.male.isNullOrEmpty()) "" else "male:"}${it.tag}" }.orEmpty(),
        info.files.map { urlFromUrlFromHash(galleryID, it, "webpsmalltn", "webp", "tn") }
    )
}