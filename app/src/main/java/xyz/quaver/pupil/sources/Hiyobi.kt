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

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import xyz.quaver.floatingsearchview.databinding.SearchSuggestionItemBinding
import xyz.quaver.hiyobi.*
import xyz.quaver.pupil.R
import xyz.quaver.pupil.client
import xyz.quaver.pupil.util.wordCapitalize
import java.io.IOException
import java.util.*

class Hiyobi : Source<DefaultSortMode, DefaultSearchSuggestion>() {

    override val name: String = "hiyobi.me"
    override val iconResID: Int = R.drawable.ic_hiyobi
    override val availableSortMode: Array<DefaultSortMode> = DefaultSortMode.values()

    override suspend fun search(query: String, range: IntRange, sortMode: Enum<*>): Pair<Channel<ItemInfo>, Int> {
        val channel = Channel<ItemInfo>()

        val (results, total) = if (query.isEmpty())
            list(range)
        else
            search(query.trim(), range)

        CoroutineScope(Dispatchers.Unconfined).launch {
            results.forEach {
                channel.send(transform(name, it))
            }

            channel.close()
        }

        return Pair(channel, total)
    }

    override suspend fun suggestion(query: String): List<DefaultSearchSuggestion> {
        val result = mutableSetOf<String>()

        for (tag in allTags.await()) {
            if (result.size >= 10)
                break

            val lowQuery = query.toLowerCase(Locale.ROOT)

            if (tag.contains(lowQuery, true))
                result.add(tag)
        }

        return result.map { DefaultSearchSuggestion(it) }
    }

    override suspend fun images(itemID: String): List<String> {
        return createImgList(itemID, getGalleryInfo(itemID), false).map {
            it.path
        }
    }

    override suspend fun info(itemID: String): ItemInfo {
        return transform(name, getGalleryBlock(itemID))
    }

    override fun onSuggestionBind(binding: SearchSuggestionItemBinding, item: DefaultSearchSuggestion) {
        val split = item.body.split(':', limit = 2)

        if (split.size != 2)
            return

        binding.leftIcon.setImageResource(
            when(split.first()) {
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

        binding.body.text = split.last()
    }

    companion object {
        private fun downloadAllTagsAsync(): Deferred<List<String>> = CoroutineScope(Dispatchers.IO).async {
            Json.decodeFromString(kotlin.runCatching {
                client.newCall(Request.Builder().url("https://api.hiyobi.me/auto.json").build()).execute().also { if (it.code() != 200) throw IOException() }.body()?.use { it.string() }
            }.getOrNull() ?: "[]")
        }

        private var _allTags: Deferred<List<String>>? = null

        val allTags: Deferred<List<String>>
            get() = if (_allTags == null || (_allTags!!.isCompleted && runBlocking { _allTags!!.await() }.isEmpty())) downloadAllTagsAsync().also {
                _allTags = it
            } else _allTags!!

        suspend fun transform(name: String, galleryBlock: GalleryBlock): ItemInfo = withContext(Dispatchers.IO) {
            ItemInfo(
                name,
                galleryBlock.id,
                galleryBlock.title,
                "https://cdn.$hiyobi/tn/${galleryBlock.id}.jpg",
                galleryBlock.artists.joinToString { it.value.wordCapitalize() },
                mapOf(
                    ItemInfo.ExtraType.CHARACTER to async { galleryBlock.characters.joinToString { it.value.wordCapitalize() } },
                    ItemInfo.ExtraType.SERIES to async { galleryBlock.parodys.joinToString { it.value.wordCapitalize() } },
                    ItemInfo.ExtraType.TYPE to async { galleryBlock.type.name.replace('_', ' ').wordCapitalize() },
                    ItemInfo.ExtraType.PAGECOUNT to async { getGalleryInfo(galleryBlock.id).files.size.toString() },
                    ItemInfo.ExtraType.GROUP to async { galleryBlock.groups.joinToString { it.value.wordCapitalize() } },
                    ItemInfo.ExtraType.TAGS to async { galleryBlock.tags.joinToString() { it.value } }
                )
            )
        }
    }

}