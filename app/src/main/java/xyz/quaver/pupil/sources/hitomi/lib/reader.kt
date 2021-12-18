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

import kotlinx.serialization.Serializable

fun getReferer(galleryID: Int) = "https://hitomi.la/reader/$galleryID.html"

@Serializable
data class Tag(
    val male: String? = null,
    val female: String? = null,
    val url: String,
    val tag: String
)

@Serializable
data class GalleryInfo(
    val id: Int? = null,
    val language_localname: String? = null,
    val tags: List<Tag> = emptyList(),
    val title: String? = null,
    val files: List<GalleryFiles>,
    val date: String? = null,
    val type: String? = null,
    val language: String? = null,
    val japanese_title: String? = null
)

@Serializable
data class GalleryFiles(
    val width: Int,
    val hash: String? = null,
    val haswebp: Int = 0,
    val name: String,
    val height: Int,
    val hasavif: Int = 0,
    val hasavifsmalltn: Int? = 0
)