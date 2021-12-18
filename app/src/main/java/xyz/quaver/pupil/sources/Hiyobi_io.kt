///*
// *     Pupil, Hitomi.la viewer for Android
// *     Copyright (C) 2021 tom5079
// *
// *     This program is free software: you can redistribute it and/or modify
// *     it under the terms of the GNU General Public License as published by
// *     the Free Software Foundation, either version 3 of the License, or
// *     (at your option) any later version.
// *
// *     This program is distributed in the hope that it will be useful,
// *     but WITHOUT ANY WARRANTY; without even the implied warranty of
// *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// *     GNU General Public License for more details.
// *
// *     You should have received a copy of the GNU General Public License
// *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
// */
//
//package xyz.quaver.pupil.sources
//
//import android.app.Application
//import android.os.Parcelable
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.*
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Female
//import androidx.compose.material.icons.filled.Male
//import androidx.compose.material.icons.filled.Star
//import androidx.compose.material.icons.filled.StarOutline
//import androidx.compose.material.icons.outlined.StarOutline
//import androidx.compose.runtime.*
//import androidx.compose.runtime.livedata.observeAsState
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.geometry.Size
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.res.stringResource
//import androidx.compose.ui.unit.dp
//import coil.annotation.ExperimentalCoilApi
//import coil.compose.rememberImagePainter
//import com.google.accompanist.flowlayout.FlowRow
//import io.ktor.client.*
//import io.ktor.client.request.*
//import io.ktor.http.*
//import kotlinx.coroutines.*
//import kotlinx.coroutines.channels.Channel
//import kotlinx.parcelize.Parcelize
//import kotlinx.serialization.Serializable
//import kotlinx.serialization.json.JsonObject
//import kotlinx.serialization.json.int
//import kotlinx.serialization.json.jsonArray
//import kotlinx.serialization.json.jsonPrimitive
//import org.kodein.di.DIAware
//import org.kodein.di.android.closestDI
//import org.kodein.di.instance
//import org.kodein.log.LoggerFactory
//import org.kodein.log.newLogger
//import xyz.quaver.pupil.R
//import xyz.quaver.pupil.db.AppDatabase
//import xyz.quaver.pupil.db.Bookmark
//import xyz.quaver.pupil.ui.theme.Blue700
//import xyz.quaver.pupil.ui.theme.Orange500
//import xyz.quaver.pupil.ui.theme.Pink600
//import xyz.quaver.pupil.util.content
//import xyz.quaver.pupil.util.get
//import xyz.quaver.pupil.util.wordCapitalize
//
//@Serializable
//@Parcelize
//data class Tag(
//    val male: Int?,
//    val female: Int?,
//    val tag: String
//) : Parcelable {
//    override fun toString(): String {
//        val stringBuilder = StringBuilder()
//
//        stringBuilder.append(when {
//            male != null -> "male"
//            female != null -> "female"
//            else -> "tag"
//        })
//        stringBuilder.append(':')
//        stringBuilder.append(tag)
//
//        return stringBuilder.toString()
//    }
//}
//
//@Serializable
//@Parcelize
//data class HiyobiItemInfo(
//    override val itemID: String,
//    override val title: String,
//    val thumbnail: String,
//    val artists: List<String>,
//    val series: List<String>,
//    val type: String,
//    val date: String,
//    val bookmark: Unit?,
//    val tags: List<Tag>,
//    val commentCount: Int,
//    val pageCount: Int
//): ItemInfo {
//    override val source: String
//        get() = "hiyobi.io"
//}
//
//@Serializable
//data class Manga(
//    val mangaId: Int,
//    val title: String,
//    val artist: List<String>,
//    val thumbnail: String,
//    val series: List<String>,
//    val type: String,
//    val date: String,
//    val bookmark: Unit?,
//    val tags: List<Tag>,
//    val commentCount: Int,
//    val pageCount: Int
//)
//
//@Serializable
//data class QueryManga(
//    val nowPage: Int,
//    val maxPage: Int,
//    val manga: List<Manga>
//)
//
//@Serializable
//data class SearchResultData(
//    val queryManga: QueryManga
//)
//
//@Serializable
//data class SearchResult(
//    val data: SearchResultData
//)
//
//class Hiyobi_io(app: Application): Source(), DIAware {
//    override val di by closestDI(app)
//
//    private val logger = newLogger(LoggerFactory.default)
//
//    private val database: AppDatabase by instance()
//    private val bookmarkDao = database.bookmarkDao()
//
//    override val name = "hiyobi.io"
//    override val iconResID = R.drawable.hitomi
//    override val availableSortMode = emptyList<String>()
//
//    private val client: HttpClient by instance()
//
//    private suspend fun query(page: Int, tags: String): SearchResult {
//        val query = "{queryManga(page:$page,tags:$tags){nowPage maxPage manga{mangaId title artist thumbnail series type date bookmark tags{male female tag} commentCount pageCount}}}"
//
//        return client.get("https://api.hiyobi.io/api?query=$query")
//    }
//
//    private suspend fun totalCount(tags: String): Int {
//        val firstPageQuery = "{queryManga(page:1,tags:$tags){maxPage}}"
//        val maxPage = client.get<JsonObject>(
//            "https://api.hiyobi.io/api?query=$firstPageQuery"
//        )["data"]!!["queryManga"]!!["maxPage"]!!.jsonPrimitive.int
//
//        val lastPageQuery = "{queryManga(page:$maxPage,tags:$tags){manga{mangaId}}}"
//        val lastPageCount = client.get<JsonObject>(
//            "https://api.hiyobi.io/api?query=$lastPageQuery"
//        )["data"]!!["queryManga"]!!["manga"]!!.jsonArray.size
//
//        return (maxPage-1)*25+lastPageCount
//    }
//
//    override suspend fun search(query: String, page: Int, sortMode: Int): Pair<Channel<ItemInfo>, Int> = withContext(Dispatchers.IO) {
//        val channel = Channel<ItemInfo>()
//
//        val tags = parseQuery(query)
//
//        logger.info {
//            tags
//        }
//
//        CoroutineScope(Dispatchers.IO).launch {
//            (range.first/25+1 .. range.last/25+1).map { page ->
//                page to async { query(page, tags) }
//            }.forEach { (page, result) ->
//                result.await().data.queryManga.manga.forEachIndexed { index, manga ->
//                    if ((page-1)*25+index in range) channel.send(transform(manga))
//                }
//            }
//
//            channel.close()
//        }
//
//        channel to totalCount(tags)
//    }
//
//    override suspend fun images(itemID: String): List<String> = withContext(Dispatchers.IO) {
//        val query = "{getManga(mangaId:$itemID){urls}}"
//
//        client.post<JsonObject>("https://api.hiyobi.io/api") {
//            contentType(ContentType.Application.Json)
//            body = mapOf("query" to query)
//        }["data"]!!["getManga"]!!["urls"]!!.jsonArray.map { "https://api.hiyobi.io/${it.content!!}" }
//    }
//
//    override suspend fun info(itemID: String): ItemInfo {
//        TODO("Not yet implemented")
//    }
//
//    @OptIn(ExperimentalMaterialApi::class)
//    @Composable
//    fun TagChip(tag: Tag, isFavorite: Boolean, onClick: ((Tag) -> Unit)? = null, onFavoriteClick: ((Tag) -> Unit)? = null) {
//        val icon = when {
//            tag.male != null -> Icons.Filled.Male
//            tag.female != null -> Icons.Filled.Female
//            else -> null
//        }
//
//        val (surfaceColor, textTint) = when {
//            isFavorite -> Pair(Orange500, Color.White)
//            else -> when {
//                tag.male != null -> Pair(Blue700, Color.White)
//                tag.female != null -> Pair(Pink600, Color.White)
//                else -> Pair(MaterialTheme.colors.background, MaterialTheme.colors.onBackground)
//            }
//        }
//
//        val starIcon = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline
//
//        Surface(
//            modifier = Modifier.padding(2.dp),
//            onClick = { onClick?.invoke(tag) },
//            shape = RoundedCornerShape(16.dp),
//            color = surfaceColor,
//            elevation = 2.dp
//        ) {
//            Row(
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                if (icon != null)
//                    Icon(
//                        icon,
//                        contentDescription = "Icon",
//                        modifier = Modifier
//                            .padding(4.dp)
//                            .size(24.dp),
//                        tint = Color.White
//                    )
//                else
//                    Box(Modifier.size(16.dp))
//
//                Text(
//                    tag.tag,
//                    color = textTint,
//                    style = MaterialTheme.typography.body2
//                )
//
//                Icon(
//                    starIcon,
//                    contentDescription = "Favorites",
//                    modifier = Modifier
//                        .padding(8.dp)
//                        .size(16.dp)
//                        .clip(CircleShape)
//                        .clickable { onFavoriteClick?.invoke(tag) },
//                    tint = textTint
//                )
//            }
//        }
//    }
//
//    @OptIn(ExperimentalMaterialApi::class)
//    @Composable
//    fun TagGroup(tags: List<Tag>) {
//        var isFolded by remember { mutableStateOf(true) }
//        val bookmarkedTags by bookmarkDao.getAll(name).observeAsState(emptyList())
//
//        val bookmarkedTagsInList = tags.filter { it.toString() in bookmarkedTags }
//
//        FlowRow(Modifier.padding(0.dp, 16.dp)) {
//            tags.sortedBy { if (bookmarkedTagsInList.contains(it)) 0 else 1 }.let { (if (isFolded) it.take(10) else it) }.forEach { tag ->
//                TagChip(
//                    tag = tag,
//                    isFavorite = bookmarkedTagsInList.contains(tag),
//                    onFavoriteClick = {
//                        val bookmarkTag = Bookmark(name, it.toString())
//
//                        CoroutineScope(Dispatchers.IO).launch {
//                            if (bookmarkedTagsInList.contains(it))
//                                bookmarkDao.delete(bookmarkTag)
//                            else
//                                bookmarkDao.insert(bookmarkTag)
//                        }
//                    }
//                )
//            }
//
//            if (isFolded && tags.size > 10)
//                Surface(
//                    modifier = Modifier.padding(2.dp),
//                    color = MaterialTheme.colors.background,
//                    shape = RoundedCornerShape(16.dp),
//                    elevation = 2.dp,
//                    onClick = { isFolded = false }
//                ) {
//                    Text(
//                        "…",
//                        modifier = Modifier.padding(16.dp, 8.dp),
//                        color = MaterialTheme.colors.onBackground,
//                        style = MaterialTheme.typography.body2
//                    )
//                }
//        }
//    }
//
//    @OptIn(ExperimentalCoilApi::class)
//    @Composable
//    override fun SearchResult(itemInfo: ItemInfo, onEvent: (SearchResultEvent) -> Unit) {
//        itemInfo as HiyobiItemInfo
//
//        val bookmark by bookmarkDao.contains(itemInfo).observeAsState(false)
//
//        val painter = rememberImagePainter(itemInfo.thumbnail)
//
//        Column(
//            modifier = Modifier.clickable {
//                onEvent(SearchResultEvent(SearchResultEvent.Type.OPEN_READER, itemInfo.itemID, itemInfo))
//            }
//        ) {
//            Row {
//                Image(
//                    painter = painter,
//                    contentDescription = null,
//                    modifier = Modifier
//                        .requiredWidth(150.dp)
//                        .aspectRatio(
//                            with(painter.intrinsicSize) { if (this == Size.Unspecified) 1f else width / height },
//                            true
//                        )
//                        .padding(0.dp, 0.dp, 8.dp, 0.dp)
//                        .align(Alignment.CenterVertically),
//                    contentScale = ContentScale.FillWidth
//                )
//
//                Column {
//                    Text(
//                        itemInfo.title,
//                        style = MaterialTheme.typography.h6,
//                        color = MaterialTheme.colors.onSurface
//                    )
//
//                    val artistStringBuilder = StringBuilder()
//
//                    with(itemInfo.artists) {
//                        if (this.isNotEmpty())
//                            artistStringBuilder.append(this.joinToString(", ") { it.wordCapitalize() })
//                    }
//
//                    if (artistStringBuilder.isNotEmpty())
//                        Text(
//                            artistStringBuilder.toString(),
//                            style = MaterialTheme.typography.subtitle1,
//                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6F)
//                        )
//
//                    if (itemInfo.series.isNotEmpty())
//                        Text(
//                            stringResource(
//                                id = R.string.galleryblock_series,
//                                itemInfo.series.joinToString { it.wordCapitalize() }
//                            ),
//                            style = MaterialTheme.typography.body2,
//                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6F)
//                        )
//
//                    Text(
//                        stringResource(id = R.string.galleryblock_type, itemInfo.type),
//                        style = MaterialTheme.typography.body2,
//                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6F)
//                    )
//
//                    key(itemInfo.tags) {
//                        TagGroup(tags = itemInfo.tags)
//                    }
//                }
//            }
//
//            Divider(
//                thickness = 1.dp,
//                modifier = Modifier.padding(0.dp, 8.dp, 0.dp, 0.dp)
//            )
//
//            Row(
//                modifier = Modifier.padding(8.dp).fillMaxWidth(),
//                verticalAlignment = Alignment.CenterVertically,
//                horizontalArrangement = Arrangement.SpaceBetween
//            ) {
//                Text(itemInfo.itemID)
//
//                Text("${itemInfo.pageCount}P")
//
//                Icon(
//                    if (bookmark) Icons.Default.Star else Icons.Default.StarOutline,
//                    contentDescription = null,
//                    tint = Orange500,
//                    modifier = Modifier
//                        .size(32.dp)
//                        .clickable {
//                            CoroutineScope(Dispatchers.IO).launch {
//                                if (bookmark) bookmarkDao.delete(itemInfo)
//                                else          bookmarkDao.insert(itemInfo)
//                            }
//                        }
//                )
//            }
//        }
//    }
//
//    companion object {
//        private fun transform(manga: Manga) = HiyobiItemInfo(
//            manga.mangaId.toString(),
//            manga.title,
//            "https://api.hiyobi.io/${manga.thumbnail}",
//            manga.artist,
//            manga.series,
//            manga.type,
//            manga.date,
//            manga.bookmark,
//            manga.tags,
//            manga.commentCount,
//            manga.pageCount
//        )
//
//        fun parseQuery(query: String): String {
//            val queryBuilder = StringBuilder("[")
//
//            if (query.isNotBlank())
//                query.split(' ').filter { it.isNotBlank() }.forEach {
//                    val tags = it.replace('_', ' ').split(':', limit = 2)
//
//                    if (queryBuilder.length != 1) queryBuilder.append(',')
//
//                    queryBuilder.append(
//                        when {
//                            tags.size == 1 -> "{tag:\"${tags[0]}\"}"
//                            tags[0] == "male" -> "{male:1,tag:\"${tags[1]}\"}"
//                            tags[0] == "female" -> "{female:1,tag:\"${tags[1]}\"}"
//                            else -> "{tag:\"${tags[1]}\"}"
//                        }
//                    )
//                }
//
//            return queryBuilder.append(']').toString()
//        }
//    }
//
//}