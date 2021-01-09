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

package xyz.quaver.pupil.sources

import android.view.LayoutInflater
import android.widget.TextView
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.quaver.floatingsearchview.databinding.SearchSuggestionItemBinding
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.hitomi.*
import xyz.quaver.pupil.R
import xyz.quaver.pupil.sources.ItemInfo.ExtraType
import xyz.quaver.pupil.util.translations
import xyz.quaver.pupil.util.wordCapitalize
import kotlin.math.max
import kotlin.math.min

class Hitomi : Source<Hitomi.SortMode, Hitomi.TagSuggestion>() {

    enum class SortMode {
        NEWEST,
        POPULAR
    }

    @Parcelize
    data class TagSuggestion(val s: String, val t: Int, val u: String, val n: String) :
        SearchSuggestion {
        constructor(s: Suggestion) : this(s.s, s.t, s.u, s.n)

        @IgnoredOnParcel
        override val body =
            if (translations[s] != null)
                "${translations[s]} ($s)"
            else
                s
    }

    override val name: String = "hitomi.la"
    override val iconResID: Int = R.drawable.hitomi
    override val availableSortMode: Array<SortMode> = SortMode.values()

    var cachedQuery: String? = null
    var cachedSortMode: SortMode? = null
    val cache = mutableListOf<Int>()

    override suspend fun search(query: String, range: IntRange, sortMode: Enum<*>): Pair<Channel<ItemInfo>, Int> {
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

        val channel = Channel<ItemInfo>()
        val sanitizedRange = max(0, range.first) .. min(range.last, cache.size-1)

        CoroutineScope(Dispatchers.IO).launch {
            cache.slice(sanitizedRange).map {
                async {
                    getGalleryBlock(it)
                }
            }.forEach {
                channel.send(transform(name, it.await()))
            }

            channel.close()
        }

        return Pair(channel, cache.size)
    }

    override suspend fun suggestion(query: String) : List<TagSuggestion> {
        return getSuggestionsForQuery(query.takeLastWhile { !it.isWhitespace() }).map {
            TagSuggestion(it)
        }
    }

    override suspend fun images(itemID: String): List<String> {
        val galleryID = itemID.toInt()

        val reader = getGalleryInfo(galleryID)

        return reader.files.map {
            imageUrlFromImage(galleryID, it, true)
        }
    }

    override suspend fun info(itemID: String): ItemInfo = coroutineScope {
        kotlin.runCatching {
            getGallery(itemID.toInt()).let {
                ItemInfo(
                    name,
                    itemID,
                    it.title,
                    it.cover,
                    it.artists.joinToString { it.wordCapitalize() },
                    mapOf(
                        ExtraType.TYPE to async { it.type.wordCapitalize() },
                        ExtraType.GROUP to async { it.groups.joinToString { it.wordCapitalize() } },
                        ExtraType.LANGUAGE to async { languageMap[it.language] ?: it.language },
                        ExtraType.SERIES to async { it.series.joinToString { it.wordCapitalize() } },
                        ExtraType.CHARACTER to async { it.characters.joinToString { it.wordCapitalize() } },
                        ExtraType.TAGS to async { it.tags.joinToString() },
                        ExtraType.PREVIEW to async { it.thumbnails.joinToString() },
                        ExtraType.RELATED_ITEM to async { it.related.joinToString() },
                        ExtraType.PAGECOUNT to async { it.thumbnails.size.toString() },
                    )
                )
            }
        }.getOrElse {
            transform(name, getGalleryBlock(itemID.toInt()))
        }
    }

    override fun getHeadersForImage(itemID: String, url: String): Map<String, String> {
        return mapOf(
            "Referer" to getReferer(itemID.toInt())
        )
    }

    override fun onSuggestionBind(binding: SearchSuggestionItemBinding, item: TagSuggestion) {
        binding.leftIcon.setImageResource(
            when(item.n) {
                "female" -> R.drawable.gender_female
                "male" -> R.drawable.gender_male
                "language" -> R.drawable.translate
                "group" -> R.drawable.account_group
                "character" -> R.drawable.account_star
                "series" -> R.drawable.book_open
                "artist" -> R.drawable.brush
                else -> R.drawable.tag
            }
        )

        if (item.t > 0) {
            with (binding.root) {
                val count = findViewById<TextView>(R.id.count)
                if (count == null)
                    addView(
                        LayoutInflater.from(context).inflate(R.layout.suggestion_count, binding.root, false)
                            .apply {
                                this as TextView

                                text = item.t.toString()
                            }, 2
                    )
                else
                    count.text = item.t.toString()
            }
        }
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

        fun transform(name: String, galleryBlock: GalleryBlock) =
            ItemInfo(
                name,
                galleryBlock.id.toString(),
                galleryBlock.title,
                galleryBlock.thumbnails.first(),
                galleryBlock.artists.joinToString { it.wordCapitalize() },
                mapOf(
                    ExtraType.GROUP to CoroutineScope(Dispatchers.IO).async { kotlin.runCatching {
                        getGallery(galleryBlock.id).groups.joinToString { it.wordCapitalize() }
                    }.getOrDefault("") },
                    ExtraType.SERIES to CoroutineScope(Dispatchers.Unconfined).async { galleryBlock.series.joinToString { it.wordCapitalize() } },
                    ExtraType.TYPE to CoroutineScope(Dispatchers.Unconfined).async { galleryBlock.type.wordCapitalize() },
                    ExtraType.LANGUAGE to CoroutineScope(Dispatchers.Unconfined).async { languageMap[galleryBlock.language] ?: galleryBlock.language },
                    ExtraType.PAGECOUNT to CoroutineScope(Dispatchers.IO).async { kotlin.runCatching {
                        getGalleryInfo(galleryBlock.id).files.size.toString()
                    }.getOrNull() },
                    ExtraType.TAGS to CoroutineScope(Dispatchers.Unconfined).async { galleryBlock.relatedTags.joinToString() }
                )
            )
    }

}