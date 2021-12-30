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

package xyz.quaver.pupil.sources.manatoki.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil.compose.rememberImagePainter
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.rememberInsetsPaddingValues
import xyz.quaver.pupil.sources.manatoki.MangaListing

private val FabSpacing = 8.dp
private val HeightPercentage = 75 // take 75% of the available space
private enum class MangaListingBottomSheetLayoutContent { Top, Bottom, Fab }

@Composable
fun MangaListingBottomSheetLayout(
    floatingActionButton: @Composable () -> Unit,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit
) {
    SubcomposeLayout { constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight * HeightPercentage / 100

        layout(layoutWidth, layoutHeight) {
            val topPlaceables = subcompose(MangaListingBottomSheetLayoutContent.Top, top).map {
                it.measure(constraints)
            }

            val topPlaceableHeight = topPlaceables.maxOfOrNull { it.height } ?: 0

            val bottomConstraints = constraints.copy(
                maxHeight = layoutHeight - topPlaceableHeight
            )

            val bottomPlaceables = subcompose(MangaListingBottomSheetLayoutContent.Bottom, bottom).map {
                it.measure(bottomConstraints)
            }

            val fabPlaceables = subcompose(MangaListingBottomSheetLayoutContent.Fab, floatingActionButton).mapNotNull {
                it.measure(constraints).takeIf { it.height != 0 && it.width != 0 }
            }

            topPlaceables.forEach { it.place(0, 0) }
            bottomPlaceables.forEach { it.place(0, topPlaceableHeight) }

            if (fabPlaceables.isNotEmpty()) {
                val fabWidth = fabPlaceables.maxOf { it.width }
                val fabHeight = fabPlaceables.maxOf { it.height }

                fabPlaceables.forEach {
                    it.place(
                        layoutWidth - fabWidth - FabSpacing.roundToPx(),
                        topPlaceableHeight - fabHeight / 2
                    )
                }
            }
        }
    }
}

@ExperimentalMaterialApi
@Composable
fun MangaListingBottomSheet(
    mangaListing: MangaListing? = null,
    currentItemID: String? = null,
    onListSize: (Size) -> Unit = { },
    listState: LazyListState = rememberLazyListState(),
    rippleInteractionSource: Map<String, MutableInteractionSource> = emptyMap(),
    recentItem: String? = null,
    nextItem: String? = null,
    onOpenItem: (String) -> Unit = { },
) {
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (mangaListing == null)
            CircularProgressIndicator(
                Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.Center))
        else
            MangaListingBottomSheetLayout(
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        text = { Text(
                            when {
                                mangaListing.entries.any { it.itemID == recentItem } -> "이어보기"
                                mangaListing.entries.any { it.itemID == nextItem } -> "다음화보기"
                                else -> "첫화보기"
                            }
                        ) },
                        onClick = {
                            when {
                                mangaListing.entries.any { it.itemID == recentItem } -> onOpenItem(recentItem!!)
                                mangaListing.entries.any { it.itemID == nextItem } -> onOpenItem(nextItem!!)
                                else -> mangaListing.entries.lastOrNull()?.let { onOpenItem(it.itemID) }
                            }
                        }
                    )
                },
                top = {
                    Row(
                        modifier = Modifier
                            .height(IntrinsicSize.Min)
                            .background(MaterialTheme.colors.primary)
                            .padding(0.dp, 0.dp, 0.dp, 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val painter = rememberImagePainter(mangaListing.thumbnail)

                        Box(Modifier.fillMaxHeight()) {
                            Image(
                                modifier = Modifier
                                    .width(150.dp)
                                    .aspectRatio(
                                        with(painter.intrinsicSize) { if (this == Size.Unspecified) 1f else width / height }
                                    ).align(Alignment.Center),
                                painter = painter,
                                contentDescription = null
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(0.dp, 8.dp)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                mangaListing.title,
                                style = MaterialTheme.typography.h5,
                                modifier = Modifier.weight(1f)
                            )

                            CompositionLocalProvider(LocalContentAlpha provides 0.7f) {
                                Text("작가: ${mangaListing.author}")

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("분류: ")

                                    CompositionLocalProvider(LocalContentAlpha provides 1f) {
                                        FlowRow(
                                            modifier = Modifier.weight(1f),
                                            mainAxisSpacing = 8.dp
                                        ) {
                                            mangaListing.tags.forEach {
                                                Card(
                                                    elevation = 4.dp,
                                                    backgroundColor = Color.White
                                                ) {
                                                    Text(
                                                        it,
                                                        style = MaterialTheme.typography.caption,
                                                        modifier = Modifier.padding(4.dp),
                                                        color = Color.Black
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Text("발행구분: ${mangaListing.type}")
                            }
                        }
                    }
                },
                bottom = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned {
                                onListSize(it.size.toSize())
                            },
                        state = listState,
                        contentPadding = rememberInsetsPaddingValues(LocalWindowInsets.current.navigationBars)
                    ) {
                        itemsIndexed(mangaListing.entries, key = { _, entry -> entry.itemID }) { index, entry ->
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        onOpenItem(entry.itemID)
                                    }
                                    .run {
                                        rippleInteractionSource[entry.itemID]?.let {
                                            indication(it, rememberRipple())
                                        } ?: this
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (entry.itemID == currentItemID)
                                    Icon(
                                        Icons.Default.ArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colors.secondary
                                    )

                                Text(
                                    entry.title,
                                    style = MaterialTheme.typography.h6,
                                    modifier = Modifier.weight(1f)
                                )

                                Text("★ ${entry.starRating}")
                            }
                            Divider()
                        }
                    }
                }
            )
    }
}