/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
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
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.util.download

import kotlinx.serialization.Serializable
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.Reader

@Suppress("DEPRECATION")
@Deprecated("Use downloader.Cache.Metadata instead")
@Serializable
data class Metadata(
    var thumbnail: String? = null,
    var galleryBlock: GalleryBlock? = null,
    var reader: Reader? = null,
    var isDownloading: Boolean? = null
) {
    constructor(
        metadata: Metadata?,
        thumbnail: String? = null,
        galleryBlock: GalleryBlock? = null,
        readers: Reader? = null,
        isDownloading: Boolean? = null
    ) : this(
        thumbnail ?: metadata?.thumbnail,
        galleryBlock ?: metadata?.galleryBlock,
        readers ?: metadata?.reader,
        isDownloading ?: metadata?.isDownloading
    )
}