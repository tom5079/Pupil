package xyz.quaver.pupil.ui.composable

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.DisplayFeature
import com.google.accompanist.adaptive.HorizontalTwoPaneStrategy
import com.google.accompanist.adaptive.TwoPane
import xyz.quaver.pupil.R
import xyz.quaver.pupil.networking.GalleryInfo
import xyz.quaver.pupil.networking.SearchQuery
import xyz.quaver.pupil.networking.SearchQueryPreviewParameterProvider
import xyz.quaver.pupil.ui.theme.Blue600
import xyz.quaver.pupil.ui.theme.Pink600
import xyz.quaver.pupil.ui.theme.Yellow400
import xyz.quaver.pupil.ui.viewmodel.MainUIState

private val iconMap = mapOf(
    "female" to Icons.Default.Female,
    "male" to Icons.Default.Male,
    "artist" to Icons.Default.Brush,
    "group" to Icons.Default.Group,
    "character" to Icons.Default.Face,
    "series" to Icons.Default.Book,
    "type" to Icons.Default.Folder,
    "language" to Icons.Default.Translate,
    "tag" to Icons.Default.LocalOffer
)

@Composable
fun TagChipIcon(tag: SearchQuery.Tag) {
    val icon = iconMap[tag.namespace]

    if (icon != null)
        Icon(
            icon,
            contentDescription = "icon",
            modifier = Modifier
                .padding(4.dp)
                .size(24.dp)
        )
    else
        Spacer(Modifier.width(16.dp))
}

@Composable
fun TagChip(
    tag: SearchQuery.Tag,
    isFavorite: Boolean = false,
    enabled: Boolean = true,
    onClick: (SearchQuery.Tag) -> Unit = { },
    leftIcon: @Composable (SearchQuery.Tag) -> Unit = { TagChipIcon(it) },
    rightIcon: @Composable (SearchQuery.Tag) -> Unit = { Spacer(Modifier.width(16.dp)) },
    content: @Composable (SearchQuery.Tag) -> Unit = { Text(it.tag) },
) {
    val surfaceColor = if (isFavorite) Yellow400 else when (tag.namespace) {
        "male" -> Blue600
        "female" -> Pink600
        else -> MaterialTheme.colorScheme.surface
    }

    val contentColor =
        if (surfaceColor == MaterialTheme.colorScheme.surface)
            MaterialTheme.colorScheme.onSurface
        else
            Color.White

    val inner = @Composable {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalTextStyle provides MaterialTheme.typography.bodyMedium
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                leftIcon(tag)
                content(tag)
                rightIcon(tag)
            }
        }
    }

    val modifier = Modifier.height(32.dp)
    val shape = RoundedCornerShape(16.dp)

    if (enabled)
        Surface(
            modifier = modifier,
            shape = shape,
            color = surfaceColor,
            onClick = { onClick(tag) },
            content = inner
        )
    else
        Surface(
            modifier,
            shape = shape,
            color = surfaceColor,
            content = inner
        )
}

@Preview
@Composable
fun QueryView(
    @PreviewParameter(SearchQueryPreviewParameterProvider::class) query: SearchQuery,
    topLevel: Boolean = true
) {
    val modifier = if (topLevel) {
        Modifier
    } else {
        Modifier.border(width = 0.5.dp, color = LocalContentColor.current, shape = CardDefaults.shape)
    }

    when (query) {
        is SearchQuery.Tag -> {
            TagChip(
                query,
                enabled = false
            )
        }
        is SearchQuery.Or -> {
            Row(
                modifier = modifier.padding(vertical=2.dp, horizontal=4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                query.queries.forEachIndexed { index, subquery ->
                    if (index != 0) { Text("+") }
                    QueryView(subquery, topLevel = false)
                }
            }
        }
        is SearchQuery.And -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                query.queries.forEach { subquery ->
                    QueryView(subquery, topLevel = false)
                }
            }
        }
        is SearchQuery.Not -> {
            Row(
                modifier = modifier.padding(vertical=2.dp, horizontal=4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("-")
                QueryView(query.query, topLevel = false)
            }
        }
    }
}

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
    var focused by remember { mutableStateOf(true) }
    val scrimAlpha: Float by animateFloatAsState(if (focused && contentType == ContentType.SINGLE_PANE) 0.3f else 0f, label = "skrim alpha")

    val interactionSource = remember { MutableInteractionSource() }

    if (focused) {
        BackHandler {
            focused = false
        }
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
                }
                .clip(RoundedCornerShape(30.dp)),
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
                androidx.compose.animation.AnimatedVisibility(query != null && !focused, enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 60.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QueryView(query!!)
                    }
                }
                androidx.compose.animation.AnimatedVisibility(focused, enter = fadeIn(), exit = fadeOut()) {
                    val state = remember(query) { query.toEditableState() }

                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp, start = 8.dp, end = 8.dp)) {
                        IconButton(
                            onClick = {
                                focused = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "close search bar"
                            )
                        }
                        QueryEditor(state = state)
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
    query: SearchQuery? = SearchQueryPreviewParameterProvider().values.first(),
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