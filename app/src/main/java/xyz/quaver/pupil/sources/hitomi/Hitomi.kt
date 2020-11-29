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

package xyz.quaver.pupil.sources.hitomi

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import xyz.quaver.hitomi.*
import xyz.quaver.pupil.R
import xyz.quaver.pupil.sources.SearchResult
import xyz.quaver.pupil.sources.SearchResult.ExtraType
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.util.wordCapitalize
import kotlin.math.max
import kotlin.math.min

class Hitomi : Source<Hitomi.SortMode> {

    enum class SortMode {
        NEWEST,
        POPULAR
    }

    override val name: String = "hitomi.la"
    override val iconResID: Int = R.drawable.hitomi
    override val availableSortMode: Array<SortMode> = SortMode.values()

    var cachedQuery: String? = null
    var cachedSortMode: SortMode? = null
    val cache = mutableListOf<Int>()

    override suspend fun query(query: String, range: IntRange, sortMode: Enum<*>): Pair<Channel<SearchResult>, Int> {
        if (cachedQuery != query || cachedSortMode != sortMode || cache.isEmpty()) {
            cachedQuery = null
            cache.clear()
            yield()
            doSearch(query, sortMode == SortMode.POPULAR).let {
                yield()
                cache.addAll(it)
            }
            cachedQuery = query
        }

        val channel = Channel<SearchResult>()
        val sanitizedRange = max(0, range.first) .. min(range.last, cache.size-1)

        CoroutineScope(Dispatchers.IO).launch {
            cache.slice(sanitizedRange).map {
                async {
                    getGalleryBlock(it)
                }
            }.forEach {
                kotlin.runCatching {
                    yield()
                    channel.send(transform(it.await()))
                }.onFailure {
                    channel.close()
                }
            }

            channel.close()
        }

        return Pair(channel, cache.size)
    }

    companion object {
        val languageMap = mapOf(
            "indonesian" to "Bahasa Indonesia",
            "catalan" to "català",
            "cebuano" to "Cebuano",
            "czech" to "Čeština",
            "danish" to "Dansk",
            "german" to "Deutsch",
            "estonian" to "eesti",
            "english" to "English",
            "spanish" to "Español",
            "esperanto" to "Esperanto",
            "french" to "Français",
            "italian" to "Italiano",
            "latin" to "Latina",
            "hungarian" to "magyar",
            "dutch" to "Nederlands",
            "norwegian" to "norsk",
            "polish" to "polski",
            "portuguese" to "Português",
            "romanian" to "română",
            "albanian" to "shqip",
            "slovak" to "Slovenčina",
            "finnish" to "Suomi",
            "swedish" to "Svenska",
            "tagalog" to "Tagalog",
            "vietnamese" to "tiếng việt",
            "turkish" to "Türkçe",
            "greek" to "Ελληνικά",
            "mongolian" to "Монгол",
            "russian" to "Русский",
            "ukrainian" to "Українська",
            "hebrew" to "עברית",
            "arabic" to "العربية",
            "persian" to "فارسی",
            "thai" to "ไทย",
            "korean" to "한국어",
            "chinese" to "中文",
            "japanese" to "日本語"
        )

        fun transform(galleryBlock: GalleryBlock): SearchResult =
            SearchResult(
                galleryBlock.id.toString(),
                galleryBlock.title,
                galleryBlock.thumbnails.first(),
                galleryBlock.artists.joinToString { it.wordCapitalize() },
                mapOf(
                    ExtraType.GROUP to { getGallery(galleryBlock.id).groups.joinToString { it.wordCapitalize() } },
                    ExtraType.SERIES to { galleryBlock.series.joinToString { it.wordCapitalize() } },
                    ExtraType.TYPE to { galleryBlock.type.wordCapitalize() },
                    ExtraType.LANGUAGE to { languageMap[galleryBlock.language] ?: galleryBlock.language },
                    ExtraType.PAGECOUNT to { getGalleryInfo(galleryBlock.id).files.size.toString() }
                ),
                galleryBlock.relatedTags
            )
    }

}