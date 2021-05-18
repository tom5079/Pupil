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
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.kodein.di.*
import xyz.quaver.floatingsearchview.databinding.SearchSuggestionItemBinding
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.pupil.R

@Serializable(with = ItemInfo.SearchResultSerializer::class)
data class ItemInfo(
    val source: String,
    val id: String,
    val title: String,
    val thumbnail: String,
    val artists: String,
    val extra: Map<ExtraType, Deferred<String?>> = emptyMap()
) {
    enum class ExtraType {
        GROUP,
        CHARACTER,
        SERIES,
        TYPE,
        TAGS,
        LANGUAGE,
        PAGECOUNT,
        PREVIEW,
        RELATED_ITEM,
    }

    @Serializable
    @SerialName("SearchResult")
    data class ItemInfoSurrogate(
        val source: String,
        val id: String,
        val title: String,
        val thumbnail: String,
        val artists: String,
        val extra: Map<ExtraType, String?> = emptyMap()
    )

    object SearchResultSerializer : KSerializer<ItemInfo> {
        override val descriptor = ItemInfoSurrogate.serializer().descriptor

        override fun serialize(encoder: Encoder, value: ItemInfo) {
            val surrogate = ItemInfoSurrogate(
                value.source,
                value.id,
                value.title,
                value.thumbnail,
                value.artists,
                value.extra.mapValues { runBlocking { it.value.await() } }
            )
            encoder.encodeSerializableValue(ItemInfoSurrogate.serializer(), surrogate)
        }

        override fun deserialize(decoder: Decoder): ItemInfo {
            val surrogate = decoder.decodeSerializableValue(ItemInfoSurrogate.serializer())
            return ItemInfo(
                surrogate.source,
                surrogate.id,
                surrogate.title,
                surrogate.thumbnail,
                surrogate.artists,
                surrogate.extra.mapValues { CoroutineScope(Dispatchers.Unconfined).async { it.value } }
            )
        }
    }

    val isReady: Boolean
        get() = extra.all { it.value.isCompleted }

    suspend fun awaitAll() = extra.values.awaitAll()

    companion object {
        val extraTypeMap = mapOf(
            ExtraType.SERIES to R.string.galleryblock_series,
            ExtraType.TYPE to R.string.galleryblock_type,
            ExtraType.LANGUAGE to R.string.galleryblock_language,
            ExtraType.PAGECOUNT to R.string.galleryblock_pagecount
        )
    }
}

enum class DefaultSortMode {
    DEFAULT
}

@Parcelize
class DefaultSearchSuggestion(override val body: String) : SearchSuggestion

typealias AnySource = Source<*, SearchSuggestion>
abstract class Source<Query_SortMode: Enum<Query_SortMode>, Suggestion: SearchSuggestion> {
    abstract val name: String
    abstract val iconResID: Int
    abstract val preferenceID: Int
    abstract val availableSortMode: Array<Query_SortMode>

    abstract suspend fun search(query: String, range: IntRange, sortMode: Enum<*>) : Pair<Channel<ItemInfo>, Int>
    abstract suspend fun suggestion(query: String) : List<Suggestion>
    abstract suspend fun images(itemID: String) : List<String>
    abstract suspend fun info(itemID: String) : ItemInfo

    open fun getHeadersForImage(itemID: String, url: String): Map<String, String> {
        return emptyMap()
    }
    
    open fun onSuggestionBind(binding: SearchSuggestionItemBinding, item: Suggestion) {
        binding.leftIcon.setImageResource(R.drawable.tag)
    }
}

typealias SourceEntry = Pair<String, AnySource>
typealias SourceEntries = Set<SourceEntry>
typealias PreferenceID = Pair<String, Int>
typealias PreferenceIDs = Set<PreferenceID>
@Suppress("UNCHECKED_CAST")
val sourceModule = DI.Module(name = "source") {
    bindSet<SourceEntry>()
    bindSet<PreferenceID>()

    listOf(
        Hitomi()
    ).forEach { source ->
        inSet { multiton { _: Unit -> source.name to (source as AnySource) } }
        inSet { singleton { source.name to source.preferenceID } }
    }

    bind { factory { source: String -> History(di, source) } }
}