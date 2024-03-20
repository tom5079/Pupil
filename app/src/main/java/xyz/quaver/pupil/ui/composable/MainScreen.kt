package xyz.quaver.pupil.ui.composable

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.DisplayFeature
import com.google.accompanist.adaptive.HorizontalTwoPaneStrategy
import com.google.accompanist.adaptive.TwoPane
import xyz.quaver.pupil.R
import xyz.quaver.pupil.networking.GalleryInfo
import xyz.quaver.pupil.networking.SearchQuery
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
    "tag" to Icons.Default.LocalOffer,
)

private val languageMap = mapOf(
    "indonesian" to R.drawable.language_indonesian,
    "javanese" to R.drawable.language_javanese,
    "catalan" to R.drawable.language_catalan,
    "cebuano" to R.drawable.language_philippines,
    "czech" to R.drawable.language_czech,
    "danish" to R.drawable.language_danish,
    "german" to R.drawable.language_german,
    "estonian" to R.drawable.language_estonian,
    "english" to R.drawable.language_english,
    "spanish" to R.drawable.language_spanish,
    "french" to R.drawable.language_french,
    "italian" to R.drawable.language_italian,
    "latin" to R.drawable.language_latin,
    "hungarian" to R.drawable.language_hungarian,
    "dutch" to R.drawable.language_dutch,
    "norwegian" to R.drawable.language_norwegian,
    "polish" to R.drawable.language_polish,
    "portuguese" to R.drawable.language_portuguese,
    "romanian" to R.drawable.language_romanian,
    "albanian" to R.drawable.language_albanian,
    "slovak" to R.drawable.language_slovak,
    "finnish" to R.drawable.language_finnish,
    "swedish" to R.drawable.language_swedish,
    "tagalog" to R.drawable.language_philippines,
    "vietnamese" to R.drawable.language_vietnamese,
    "turkish" to R.drawable.language_turkish,
    "greek" to R.drawable.language_greek,
    "mongolian" to R.drawable.language_mongolian,
    "russian" to R.drawable.language_russian,
    "ukrainian" to R.drawable.language_ukrainian,
    "hebrew" to R.drawable.language_hebrew,
    "persian" to R.drawable.language_persian,
    "thai" to R.drawable.language_thai,
    "korean" to R.drawable.language_korean,
    "chinese" to R.drawable.language_chinese,
    "japanese" to R.drawable.language_japanese,
)

@Composable
fun TagChipIcon(tag: SearchQuery.Tag) {
    val icon = iconMap[tag.namespace]

    if (icon != null) {
        if (tag.namespace == "language" && languageMap.contains(tag.tag)) {
            Icon(
                painter = painterResource(languageMap[tag.tag]!!),
                contentDescription = "icon",
                modifier = Modifier
                    .padding(4.dp)
                    .size(24.dp),
                tint = Color.Unspecified
            )
        } else {
            Icon(
                icon,
                contentDescription = "icon",
                modifier = Modifier
                    .padding(4.dp)
                    .size(24.dp)
            )
        }
    } else {
        Spacer(Modifier.width(16.dp))
    }
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

@Composable
fun QueryView(
    query: SearchQuery?,
    topLevel: Boolean = true
) {
    val modifier = if (topLevel) {
        Modifier
    } else {
        Modifier.border(width = 0.5.dp, color = LocalContentColor.current, shape = CardDefaults.shape)
    }

    when (query) {
        null -> {
            Text(
                modifier = Modifier
                    .height(60.dp)
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp),
                text = stringResource(id = R.string.search_hint),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
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
                query.queries.forEachIndexed { index, subQuery ->
                    if (index != 0) { Text("+") }
                    QueryView(subQuery, topLevel = false)
                }
            }
        }
        is SearchQuery.And -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                query.queries.forEach { subQuery ->
                    QueryView(subQuery, topLevel = false)
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
    onQueryChange: (SearchQuery?) -> Unit,
    onSearch: () -> Unit,
    topOffset: Int,
    onTopOffsetChange: (Int) -> Unit,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scrimAlpha: Float by animateFloatAsState(if (focused && contentType == ContentType.SINGLE_PANE) 0.3f else 0f, label = "scrim alpha")

    val interactionSource = remember { MutableInteractionSource() }

    val state = remember(query) { query.toEditableState() }

    LaunchedEffect(focused) {
        if (!focused) {
            onQueryChange(state.toSearchQuery())
        }
    }

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
    ) {
        val height: Dp by animateDpAsState(if (focused) maxHeight else 60.dp, label = "searchbar height")

        content()

        Card(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(16.dp)
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
                androidx.compose.animation.AnimatedVisibility(!focused, enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 60.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp))
                        QueryView(query)
                        Box(Modifier.size(8.dp))
                    }
                }
                androidx.compose.animation.AnimatedVisibility(focused, enter = fadeIn(), exit = fadeOut()) {
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
    closeDetailScreen: () -> Unit,
    onQueryChange: (SearchQuery?) -> Unit,
    loadSearchResult: (IntRange) -> Unit
) {
    LaunchedEffect(contentType) {
        if (contentType == ContentType.SINGLE_PANE && !uiState.isDetailOnlyOpen) {
            closeDetailScreen()
        }
    }

    val galleryLazyListState = rememberLazyListState()

    val itemsPerPage by remember { mutableIntStateOf(20) }

    val pageToRange: (Int) -> IntRange = remember(itemsPerPage) {{ page ->
        page * itemsPerPage ..< (page+1) * itemsPerPage
    }}

    val currentPage = remember(uiState) {
        if (uiState.currentRange != IntRange.EMPTY) {
            uiState.currentRange.first / itemsPerPage
        } else 0
    }

    val maxPage = remember(itemsPerPage, uiState) {
        if (uiState.galleryCount != null) {
            uiState.galleryCount / itemsPerPage + if (uiState.galleryCount % itemsPerPage != 0) 1 else 0
        } else 0
    }

    val loadResult: (Int) -> Unit = remember(loadSearchResult) {{ page ->
        loadSearchResult(pageToRange(page))
    }}

    LaunchedEffect(Unit) { loadSearchResult(pageToRange(0)) }

    if (contentType == ContentType.DUAL_PANE) {
        TwoPane(
            first = {
                GalleryList(
                    contentType = contentType,
                    galleries = uiState.galleries,
                    query = uiState.query,
                    currentPage = currentPage,
                    maxPage = maxPage,
                    loading = uiState.loading,
                    error = uiState.error,
                    galleryLazyListState = galleryLazyListState,
                    onQueryChange = onQueryChange,
                    onPageChange = loadResult
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
            galleries = uiState.galleries,
            query = uiState.query,
            currentPage = currentPage,
            maxPage = maxPage,
            loading = uiState.loading,
            error = uiState.error,
            galleryLazyListState = galleryLazyListState,
            onQueryChange = onQueryChange,
            onPageChange = loadResult
        )
    }
}

@Composable
fun GalleryList(
    contentType: ContentType,
    galleries: List<GalleryInfo>,
    query: SearchQuery?,
    currentPage: Int,
    maxPage: Int,
    loading: Boolean = false,
    error: Boolean = false,
    openedGallery: GalleryInfo? = null,
    onPageChange: (Int) -> Unit,
    onQueryChange: (SearchQuery?) -> Unit = {},
    search: () -> Unit = {},
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
        onSearch = search,
        topOffset = topOffset,
        onTopOffsetChange = { topOffset = it },
    ) {
        AnimatedVisibility (loading, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
        AnimatedVisibility(error, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("(´∇｀)", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text("No sources found!\nLet's go download one.", textAlign = TextAlign.Center)
                }
            }
        }
        AnimatedVisibility(!loading && !error, enter = fadeIn(), exit = fadeOut()) {
            OverscrollPager(
                prevPage = if (currentPage != 0) currentPage else null,
                nextPage = if (currentPage < maxPage) currentPage + 2 else null,
                onPageTurn = { onPageChange(it-1) }
            ) {
                LazyColumn(
                    contentPadding = WindowInsets.systemBars.asPaddingValues().let { systemBarPaddingValues ->
                        val layoutDirection = LocalLayoutDirection.current
                        PaddingValues(
                            top = systemBarPaddingValues.calculateTopPadding() + 96.dp,
                            bottom = systemBarPaddingValues.calculateBottomPadding(),
                            start = systemBarPaddingValues.calculateStartPadding(layoutDirection),
                            end = systemBarPaddingValues.calculateEndPadding(layoutDirection),
                        )
                    }
                ) {
                    items(galleries) {galleryInfo ->
                        Text(galleryInfo.title)
                    }
                }
            }
        }
    }
}