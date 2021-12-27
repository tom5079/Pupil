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

package xyz.quaver.pupil.sources.hitomi.composable

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
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.google.accompanist.flowlayout.FlowRow
import xyz.quaver.pupil.R
import xyz.quaver.pupil.sources.hitomi.HitomiSearchResult
import xyz.quaver.pupil.ui.theme.Blue700
import xyz.quaver.pupil.ui.theme.Orange500
import xyz.quaver.pupil.ui.theme.Pink600

private val languageMap = mapOf(
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

private fun String.wordCapitalize() : String {
    val result = ArrayList<String>()

    for (word in this.split(" "))
        result.add(word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })

    return result.joinToString(" ")
}

@ExperimentalMaterialApi
@Composable
fun DetailedSearchResult(
    result: HitomiSearchResult,
    favorites: Set<String>,
    onFavoriteToggle: (String) -> Unit = { },
    onClick: (HitomiSearchResult) -> Unit = { }
) {
    val painter = rememberImagePainter(result.thumbnail)

    Card(
        modifier = Modifier
            .padding(8.dp, 4.dp)
            .fillMaxWidth()
            .clickable { onClick(result) },
        elevation = 4.dp
    ) {
        Column {
            Row {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier
                        .width(150.dp)
                        .aspectRatio(
                            with(painter.intrinsicSize) { if (this == Size.Unspecified) 1f else width / height },
                            true
                        )
                        .padding(0.dp, 0.dp, 8.dp, 0.dp)
                        .align(Alignment.CenterVertically),
                    contentScale = ContentScale.FillWidth
                )
                Column {
                    Text(
                        result.title,
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.onSurface
                    )

                    Text(
                        result.artists.joinToString { it.wordCapitalize() },
                        style = MaterialTheme.typography.subtitle1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )

                    if (result.series.isNotEmpty())
                        Text(
                            stringResource(
                                id = R.string.galleryblock_series,
                                result.series.joinToString { it.wordCapitalize() }
                            ),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )

                    Text(
                        stringResource(id = R.string.galleryblock_type, result.type),
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )

                    languageMap[result.language]?.run {
                        Text(
                            stringResource(id = R.string.galleryblock_language, this),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    key(result.tags) {
                        TagGroup(
                            tags = result.tags,
                            favorites,
                            onFavoriteToggle = onFavoriteToggle
                        )
                    }
                }
            }

            Divider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    result.itemID,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )

                Icon(
                    if (result.itemID in favorites) Icons.Default.Star else Icons.Default.StarOutline,
                    contentDescription = null,
                    tint = Orange500,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            onFavoriteToggle(result.itemID)
                        }
                )
            }
        }
    }
}

@ExperimentalMaterialApi
@Composable
fun TagGroup(
    tags: List<String>,
    favorites: Set<String>,
    onFavoriteToggle: (String) -> Unit = { }
) {
    var isFolded by remember { mutableStateOf(true) }

    val favoriteTagsInList = favorites intersect tags.toSet()

    FlowRow(Modifier.padding(0.dp, 16.dp)) {
        tags.sortedBy { if (favoriteTagsInList.contains(it)) 0 else 1 }
            .let { (if (isFolded) it.take(10) else it) }.forEach { tag ->
            TagChip(
                tag = tag,
                isFavorite = favoriteTagsInList.contains(tag),
                onFavoriteClick = onFavoriteToggle
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TagChip(
    tag: String,
    isFavorite: Boolean,
    onClick: (String) -> Unit = { },
    onFavoriteClick: (String) -> Unit = { }
) {
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
        isFavorite -> Pair(Orange500, Color.White)
        else -> when (tagParts[0]) {
            "male" -> Pair(Blue700, Color.White)
            "female" -> Pair(Pink600, Color.White)
            else -> Pair(MaterialTheme.colors.background, MaterialTheme.colors.onBackground)
        }
    }

    val starIcon = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline

    Surface(
        modifier = Modifier.padding(2.dp),
        onClick = { onClick(tag) },
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
                    .clickable { onFavoriteClick(tag) },
                tint = textTint
            )
        }
    }
}
