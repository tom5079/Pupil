package xyz.quaver.pupil.ui.composable

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.DisplayFeature
import com.google.accompanist.adaptive.HorizontalTwoPaneStrategy
import com.google.accompanist.adaptive.TwoPane
import xyz.quaver.pupil.R
import xyz.quaver.pupil.networking.GalleryInfo
import xyz.quaver.pupil.networking.SearchQuery
import xyz.quaver.pupil.ui.viewmodel.MainUIState

@Composable
fun SearchBar(
    contentType: ContentType,
    query: SearchQuery?,
    onQueryChange: (SearchQuery) -> Unit,
    onSearch: () -> Unit,
    topOffset: Int,
    onTopOffsetChange: (Int) -> Unit,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scrimAlpha: Float by animateFloatAsState(if (focused && contentType == ContentType.SINGLE_PANE) 0.3f else 0f, label = "skrim alpha")

    val interactionSource = remember { MutableInteractionSource() }

    if (focused) {
        BackHandler {
            focused = false
        }
    }

    LaunchedEffect(query) {
        focused = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                focused = false
            }
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        val height: Dp by animateDpAsState(if (focused) maxHeight else 60.dp, label = "searchbar height")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    focused = true
                },
            shape = RoundedCornerShape(30.dp),
            elevation = if (focused) CardDefaults.cardElevation(
                defaultElevation = 4.dp
            ) else CardDefaults.cardElevation()
        ) {
            Box {
                androidx.compose.animation.AnimatedVisibility(query == null && !focused, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        modifier = Modifier
                            .height(60.dp)
                            .wrapContentHeight()
                            .padding(horizontal = 16.dp),
                        text = stringResource(id = R.string.search_hint),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                androidx.compose.animation.AnimatedVisibility(focused, enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.fillMaxSize().padding(8.dp)) {
                        IconButton(
                            modifier = Modifier.align(Alignment.TopStart),
                            onClick = {
                                focused = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "close search bar"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    contentType: ContentType,
    displayFeatures: List<DisplayFeature>,
    uiState: MainUIState,
    closeDetailScreen: () -> Unit
) {
    LaunchedEffect(contentType) {
        if (contentType == ContentType.SINGLE_PANE && !uiState.isDetailOnlyOpen) {
            closeDetailScreen()
        }
    }

    val galleryLazyListState = rememberLazyListState()

    if (contentType == ContentType.DUAL_PANE) {
        TwoPane(
            first = {
                GalleryList(
                    contentType = contentType,
                    galleryLazyListState = galleryLazyListState
                )
            },
            second = {

            },
            strategy = HorizontalTwoPaneStrategy(splitFraction = 0.5f, gapWidth = 16.dp),
            displayFeatures = displayFeatures
        )
    } else {
        GalleryList(
            contentType = contentType,
            galleryLazyListState = galleryLazyListState
        )
    }
}

@Composable
fun GalleryList(
    contentType: ContentType,
    galleries: List<GalleryInfo> = emptyList(),
    openedGallery: GalleryInfo? = null,
    query: SearchQuery? = null,
    onQueryChange: (SearchQuery) -> Unit = {},
    onSearch: () -> Unit = { },
    selectedGalleryIds: Set<Int> = emptySet(),
    toggleGallerySelection: (Int) -> Unit = {},
    galleryLazyListState: LazyListState,
    navigateToDetails: (GalleryInfo, ContentType) -> Unit = { gi, ct -> }
) {
    var topOffset by remember { mutableIntStateOf(0) }

    SearchBar(
        contentType = contentType,
        query = query,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        topOffset = topOffset,
        onTopOffsetChange = { topOffset = it },
    ) {

    }
}