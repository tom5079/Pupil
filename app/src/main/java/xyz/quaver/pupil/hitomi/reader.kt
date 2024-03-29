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
import xyz.quaver.pupil.hitomi.GalleryInfo
import xyz.quaver.pupil.hitomi.getGalleryInfo

@Serializable
data class GalleryFiles(
    val width: Int,
    val hash: String,
    val haswebp: Int = 0,
    val name: String,
    val height: Int,
    val hasavif: Int = 0,
    val hasavifsmalltn: Int? = 0
)

//Set header `Referer` to reader url to avoid 403 error
@Deprecated("", replaceWith = ReplaceWith("getGalleryInfo"))
fun getReader(galleryID: Int) : GalleryInfo {
   return getGalleryInfo(galleryID)
}