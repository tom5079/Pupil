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

import android.app.Application
import android.view.LayoutInflater
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.room.*
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.google.accompanist.flowlayout.FlowRow
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import xyz.quaver.floatingsearchview.databinding.SearchSuggestionItemBinding
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.hitomi.*
import xyz.quaver.pupil.R
import xyz.quaver.pupil.db.AppDatabase
import xyz.quaver.pupil.db.Bookmark
import xyz.quaver.pupil.sources.ItemInfo
import xyz.quaver.pupil.sources.SearchResultEvent
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.wordCapitalize
import kotlin.math.max
import kotlin.math.min

@Serializable
@Parcelize
data class HitomiItemInfo(
    override val itemID: String,
    override val title: String,
    val thumbnail: String,
    val artists: List<String>,
    val series: List<String>,
    val type: String,
    val language: String,
    val tags: List<String>,
    private var groups: List<String>? = null,
    private var pageCount: Int? = null,
    val characters: List<String>? = null,
    val preview: List<String>? = null,
    val relatedItem: List<String>? = null
): ItemInfo {

    override val source: String
        get() = "hitomi.la"

    @IgnoredOnParcel
    private val groupMutex = Mutex()
    suspend fun getGroups() = withContext(Dispatchers.IO) {
        if (groups != null) groups
        else groupMutex.withLock { runCatching {
            getGallery(itemID.toInt()).groups
        }.getOrNull() }
    }

    @IgnoredOnParcel
    private val pageCountMutex = Mutex()
    suspend fun getPageCount() = withContext(Dispatchers.IO) {
        if (pageCount != null) pageCount

        else pageCountMutex.withLock { runCatching {
            getGalleryInfo(itemID.toInt()).files.size.also { pageCount = it }
        }.getOrNull() }
    }
}

class Hitomi(app: Application) : Source(), DIAware {

    override val di: DI by closestDI(app)

    private val database: AppDatabase by instance()

    private val bookmarkDao = database.bookmarkDao()

    @Parcelize
    data class TagSuggestion(val s: String, val t: Int, val u: String, val n: String) : SearchSuggestion {
        constructor(s: Suggestion) : this(s.s, s.t, s.u, s.n)

        @IgnoredOnParcel
        override val body = s
        /*
            TODO
            if (translations[s] != null)
                "${translations[s]} ($s)"
            else
                s
         */
    }

    override val name: String = "hitomi.la"
    override val iconResID: Int = R.drawable.hitomi
    override val preferenceID: Int = R.xml.hitomi_preferences
    override val availableSortMode: List<String> = app.resources.getStringArray(R.array.hitomi_sort_mode).toList()

    var cachedQuery: String? = null
    var cachedSortMode: Int = -1
    private val cache = mutableListOf<Int>()

    override suspend fun search(query: String, range: IntRange, sortMode: Int): Pair<Channel<ItemInfo>, Int> {
        if (cachedQuery != query || cachedSortMode != sortMode || cache.isEmpty()) {
            cachedQuery = null
            cache.clear()
            yield()
            doSearch("$query ${Preferences["hitomi.default_query", ""]}", sortMode == 1).let {
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
                channel.send(transform(it.await()))
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
            imageUrlFromImage(galleryID, it, false)
        }
    }

    override suspend fun info(itemID: String): HitomiItemInfo = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            getGallery(itemID.toInt()).let {
                HitomiItemInfo(
                    itemID,
                    it.title,
                    it.cover,
                    it.artists,
                    it.series,
                    it.type,
                    it.language,
                    it.tags,
                    it.groups,
                    it.thumbnails.size,
                    it.characters,
                    it.thumbnails,
                    it.related.map { it.toString() }
                )
            }
        }.getOrElse {
            transform(getGalleryBlock(itemID.toInt()))
        }
    }

    @Composable
    override fun SearchResult(itemInfo: ItemInfo, onEvent: ((SearchResultEvent) -> Unit)?) {
        itemInfo as HitomiItemInfo

        FullSearchResult(itemInfo = itemInfo)
    }

    override fun getHeadersBuilderForImage(itemID: String, url: String): HeadersBuilder.() -> Unit = {
        append("Referer", getReferer(itemID.toInt()))
    }

    override fun onSuggestionBind(binding: SearchSuggestionItemBinding, item: SearchSuggestion) {
        item as TagSuggestion

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

        fun transform(galleryBlock: GalleryBlock) =
            HitomiItemInfo(
                galleryBlock.id.toString(),
                galleryBlock.title,
                galleryBlock.thumbnails.first(),
                galleryBlock.artists,
                galleryBlock.series,
                galleryBlock.type,
                galleryBlock.language,
                galleryBlock.relatedTags
            )
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun TagChip(tag: String, isFavorite: Boolean, onClick: ((String) -> Unit)? = null, onFavoriteClick: ((String) -> Unit)? = null) {
        val tagParts = tag.split(":", limit = 2).let {
            if (it.size == 1) listOf("", it.first())
            else it
        }

        val icon = when (tagParts[0]) {
            "male" -> Icons.Filled.Male
            "female" -> Icons.Filled.Female
            else -> null
        }

        val (surfaceColor, textTint) = when {
            isFavorite -> Pair(colorResource(id = R.color.material_orange_500), Color.White)
            else -> when (tagParts[0]) {
                "male" -> Pair(colorResource(id = R.color.material_blue_700), Color.White)
                "female" -> Pair(colorResource(id = R.color.material_pink_600), Color.White)
                else -> Pair(MaterialTheme.colors.background, MaterialTheme.colors.onBackground)
            }
        }

        val starIcon = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline

        Surface(
            modifier = Modifier.padding(2.dp),
            onClick = { onClick?.invoke(tag) },
            shape = RoundedCornerShape(16.dp),
            color = surfaceColor,
            elevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null)
                    Icon(
                        icon,
                        contentDescription = "Icon",
                        modifier = Modifier
                            .padding(4.dp)
                            .size(24.dp),
                        tint = Color.White
                    )
                else
                    Box(Modifier.size(16.dp))

                Text(
                    tagParts[1],
                    color = textTint,
                    style = MaterialTheme.typography.body2
                )

                Icon(
                    starIcon,
                    contentDescription = "Favorites",
                    modifier = Modifier
                        .padding(8.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .clickable { onFavoriteClick?.invoke(tag) },
                    tint = textTint
                )
            }
        }
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun TagGroup(tags: List<String>) {
        var isFolded by remember { mutableStateOf(true) }
        val bookmarkedTags by bookmarkDao.getAll(name).observeAsState(emptyList())

        val bookmarkedTagsInList = bookmarkedTags.toSet() intersect tags

        FlowRow(Modifier.padding(0.dp, 16.dp)) {
            tags.sortedBy { if (bookmarkedTagsInList.contains(it)) 0 else 1 }.let { (if (isFolded) it.take(10) else it) }.forEach { tag ->
                TagChip(
                    tag = tag,
                    isFavorite = bookmarkedTagsInList.contains(tag),
                    onFavoriteClick = { tag ->
                        val bookmarkTag = Bookmark(name, tag)

                        CoroutineScope(Dispatchers.IO).launch {
                            if (bookmarkedTagsInList.contains(tag))
                                bookmarkDao.delete(bookmarkTag)
                            else
                                bookmarkDao.insert(bookmarkTag)
                        }
                   }
                )
            }

            if (isFolded && tags.size > 10)
                Surface(
                    modifier = Modifier.padding(2.dp),
                    color = MaterialTheme.colors.background,
                    shape = RoundedCornerShape(16.dp),
                    elevation = 2.dp,
                    onClick = { isFolded = false }
                ) {
                    Text(
                        "…",
                        modifier = Modifier.padding(16.dp, 8.dp),
                        color = MaterialTheme.colors.onBackground,
                        style = MaterialTheme.typography.body2
                    )
                }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    @Composable
    fun FullSearchResult(itemInfo: HitomiItemInfo) {
        var group by remember { mutableStateOf(emptyList<String>()) }
        var pageCount by remember { mutableStateOf("-") }

        LaunchedEffect(itemInfo) {
            launch {
                itemInfo.getPageCount()?.run {
                    pageCount = "${this}P"
                }
            }

            launch {
                itemInfo.getGroups()?.run {
                    group = this
                }
            }
        }

        val painter = rememberImagePainter(itemInfo.thumbnail)

        Column {
            Row {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier
                        .requiredWidth(150.dp)
                        .aspectRatio(
                            with(painter.intrinsicSize) { if (this == Size.Companion.Unspecified) 1f else width / height },
                            true
                        )
                        .padding(0.dp, 0.dp, 8.dp, 0.dp)
                        .align(Alignment.CenterVertically),
                    contentScale = ContentScale.FillWidth
                )
                Column {
                    Text(
                        itemInfo.title,
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.onSurface
                    )

                    val artistStringBuilder = StringBuilder()

                    with (itemInfo.artists) {
                        if (this.isNotEmpty())
                            artistStringBuilder.append(this.joinToString(", ") { it.wordCapitalize() })
                    }

                    if (group.isNotEmpty()) {
                        if (artistStringBuilder.isNotEmpty()) artistStringBuilder.append(" ")

                        artistStringBuilder.append("(")
                        artistStringBuilder.append(group.joinToString(", ") { it.wordCapitalize() })
                        artistStringBuilder.append(")")
                    }

                    if (artistStringBuilder.isNotEmpty())
                        Text(
                            artistStringBuilder.toString(),
                            style = MaterialTheme.typography.subtitle1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6F)
                        )

                    if (itemInfo.series.isNotEmpty())
                        Text(
                            stringResource(
                                id = R.string.galleryblock_series,
                                itemInfo.series.joinToString { it.wordCapitalize() }
                            ),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6F)
                        )

                    Text(
                        stringResource(id = R.string.galleryblock_type, itemInfo.type),
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6F)
                    )

                    languageMap[itemInfo.language]?.run {
                        Text(
                            stringResource(id = R.string.galleryblock_language, this),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6F)
                        )
                    }

                    TagGroup(tags = itemInfo.tags)
                }
            }

            Divider(
                thickness = 1.dp,
                modifier = Modifier.padding(0.dp, 8.dp)
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Text(
                    itemInfo.itemID,
                    color = MaterialTheme.colors.onSurface,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.CenterStart)
                )

                Text(
                    pageCount,
                    color = MaterialTheme.colors.onSurface,
                    modifier = Modifier.align(Alignment.Center)
                )

                Image(
                    painterResource(id = R.drawable.ic_star_empty),
                    contentDescription = "Favorite",
                    modifier = Modifier
                        .size(32.dp)
                        .padding(4.dp)
                        .align(Alignment.CenterEnd)
                )
            }
        }
    }

}