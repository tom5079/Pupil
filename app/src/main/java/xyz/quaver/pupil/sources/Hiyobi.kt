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

import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import xyz.quaver.floatingsearchview.databinding.SearchSuggestionItemBinding
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
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

    override suspend fun search(query: String, range: IntRange, sortMode: Enum<*>): Pair<Channel<SearchResult>, Int> {
        val channel = Channel<SearchResult>()

        val (results, total) = if (query.isEmpty())
            list(range)
        else
            search(query.trim(), range)

        CoroutineScope(Dispatchers.Unconfined).launch {
            results.forEach {
                channel.send(transform(it))
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
        private fun downloadAllTags(): Deferred<List<String>> = CoroutineScope(Dispatchers.IO).async {
            Json.decodeFromString(kotlin.runCatching {
                client.newCall(Request.Builder().url("https://api.hiyobi.me/auto.json").build()).execute().also { if (it.code() != 200) throw IOException() }.body()?.use { it.string() }
            }.getOrNull() ?: "[]")
        }

        private var _allTags: Deferred<List<String>>? = null

        val allTags: Deferred<List<String>>
            get() = if (_allTags == null || (_allTags!!.isCompleted && runBlocking { _allTags!!.await() }.isEmpty())) downloadAllTags().also {
                _allTags = it
            } else _allTags!!

        fun transform(galleryBlock: GalleryBlock): SearchResult =
            SearchResult(
                galleryBlock.id,
                galleryBlock.title,
                "https://cdn.$hiyobi/tn/${galleryBlock.id}.jpg",
                galleryBlock.artists.joinToString { it.value.wordCapitalize() },
                mapOf(
                    SearchResult.ExtraType.CHARACTER to { galleryBlock.characters.joinToString { it.value.wordCapitalize() } },
                    SearchResult.ExtraType.SERIES to { galleryBlock.parodys.joinToString { it.value.wordCapitalize() } },
                    SearchResult.ExtraType.TYPE to { galleryBlock.type.name.replace('_', ' ').wordCapitalize() },
                    SearchResult.ExtraType.PAGECOUNT to { getGalleryInfo(galleryBlock.id).files.size.toString() },
                    SearchResult.ExtraType.GROUP to { galleryBlock.groups.joinToString { it.value.wordCapitalize() } }
                ),
                galleryBlock.tags.map { it.value }
            )
    }

}