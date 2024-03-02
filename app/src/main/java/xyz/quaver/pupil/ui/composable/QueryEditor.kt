package xyz.quaver.pupil.ui.composable

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.quaver.pupil.R
import xyz.quaver.pupil.networking.SearchQuery
import xyz.quaver.pupil.ui.theme.Blue300
import xyz.quaver.pupil.ui.theme.Blue600
import xyz.quaver.pupil.ui.theme.Gray300
import xyz.quaver.pupil.ui.theme.Pink600
import xyz.quaver.pupil.ui.theme.Red300
import xyz.quaver.pupil.ui.theme.Yellow400

private fun SearchQuery.toEditableStateInternal(): EditableSearchQueryState = when (this) {
    is SearchQuery.Tag -> EditableSearchQueryState.Tag(namespace, tag)
    is SearchQuery.And -> EditableSearchQueryState.And(queries.map { it.toEditableStateInternal() })
    is SearchQuery.Or -> EditableSearchQueryState.Or(queries.map { it.toEditableStateInternal() })
    is SearchQuery.Not -> EditableSearchQueryState.Not(query.toEditableStateInternal())
}

fun SearchQuery?.toEditableState(): EditableSearchQueryState.Root
    = EditableSearchQueryState.Root(this?.toEditableStateInternal())

private fun EditableSearchQueryState.toSearchQueryInternal(): SearchQuery? = when (this) {
    is EditableSearchQueryState.Tag -> SearchQuery.Tag(namespace.value, tag.value)
    is EditableSearchQueryState.And -> SearchQuery.And(queries.mapNotNull { it.toSearchQueryInternal() })
    is EditableSearchQueryState.Or -> SearchQuery.Or(queries.mapNotNull { it.toSearchQueryInternal() })
    is EditableSearchQueryState.Not -> query.value?.toSearchQueryInternal()?.let { SearchQuery.Not(it) }
}

fun EditableSearchQueryState.Root.toSearchQuery(): SearchQuery?
    = query.value?.toSearchQueryInternal()

fun coalesceTags(oldTag: EditableSearchQueryState.Tag?, newTag: EditableSearchQueryState?): EditableSearchQueryState?
    = if (oldTag != null) {
        when (newTag) {
            is EditableSearchQueryState.Tag,
            is EditableSearchQueryState.Not -> EditableSearchQueryState.And(listOf(oldTag, newTag))
            is EditableSearchQueryState.And -> newTag.apply { queries.add(oldTag) }
            is EditableSearchQueryState.Or -> newTag.apply { queries.add(oldTag) }
            null -> oldTag
        }
    } else newTag

sealed interface EditableSearchQueryState {
    class Tag(
        namespace: String? = null,
        tag: String = ""
    ): EditableSearchQueryState {
        val namespace = mutableStateOf(namespace)
        val tag = mutableStateOf(tag)
    }

    class And(
        queries: List<EditableSearchQueryState> = emptyList()
    ): EditableSearchQueryState {
        val queries = queries.toMutableStateList()
    }

    class Or(
        queries: List<EditableSearchQueryState> = emptyList()
    ): EditableSearchQueryState {
        val queries = queries.toMutableStateList()
    }

    class Not(
        query: EditableSearchQueryState? = null
    ): EditableSearchQueryState {
        val query = mutableStateOf(query)
    }

    class Root(
        query: EditableSearchQueryState? = null
    ) {
        val query = mutableStateOf(query)
    }

}

@Composable
fun EditableTagChip(
    state: EditableSearchQueryState.Tag,
    isFavorite: Boolean = false,
    enabled: Boolean = true,
    leftIcon: @Composable (SearchQuery.Tag) -> Unit = { tag -> TagChipIcon(tag) },
    rightIcon: @Composable (SearchQuery.Tag) -> Unit = { _ -> Spacer(Modifier.width(16.dp)) },
    content: @Composable (SearchQuery.Tag) -> Unit = { tag -> Text(tag.tag) },
) {
    val namespace by state.namespace
    val tag by state.tag

    val surfaceColor = if (isFavorite) Yellow400 else when (namespace) {
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
            val queryTag = SearchQuery.Tag(namespace, tag)

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                leftIcon(queryTag)
                content(queryTag)
                rightIcon(queryTag)
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
fun NewQueryChip(
    currentQuery: EditableSearchQueryState?,
    onNewQuery: (EditableSearchQueryState) -> Unit
) {
    var opened by remember { mutableStateOf(false) }

    @Composable
    fun NewQueryRow(
        modifier: Modifier = Modifier,
        icon: ImageVector = Icons.Default.AddCircleOutline,
        text: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = modifier
                .height(32.dp)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier
                    .padding(8.dp)
                    .size(16.dp),
                imageVector = icon,
                contentDescription = text
            )
            Text(
                modifier = Modifier.padding(end = 16.dp),
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    Surface(shape = RoundedCornerShape(16.dp)) {
        AnimatedContent(targetState = opened, label = "add new query" ) { targetOpened ->
            if (targetOpened) {
                Column {
                    NewQueryRow(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.RemoveCircleOutline,
                        text = stringResource(android.R.string.cancel)
                    ) {
                        opened = false
                    }
                    HorizontalDivider()
                    if (currentQuery !is EditableSearchQueryState.Tag) {
                        NewQueryRow(modifier = Modifier.fillMaxWidth(), text = stringResource(R.string.search_add_query_item_tag)) {
                            opened = false
                            onNewQuery(EditableSearchQueryState.Tag())
                        }
                    }
                    if (currentQuery !is EditableSearchQueryState.And) {
                        HorizontalDivider()
                        NewQueryRow(modifier = Modifier.fillMaxWidth(), text = "AND") {
                            opened = false
                            onNewQuery(EditableSearchQueryState.And())
                        }
                    }
                    if (currentQuery !is EditableSearchQueryState.Or) {
                        HorizontalDivider()
                        NewQueryRow(modifier = Modifier.fillMaxWidth(), text = "OR") {
                            opened = false
                            onNewQuery(EditableSearchQueryState.Or())
                        }
                    }
                    if (currentQuery !is EditableSearchQueryState.Not || currentQuery.query.value != null) {
                        HorizontalDivider()
                        NewQueryRow(modifier = Modifier.fillMaxWidth(), text = "NOT") {
                            opened = false
                            onNewQuery(EditableSearchQueryState.Not())
                        }
                    }
                }
            } else {
                NewQueryRow(text = stringResource(R.string.search_add_query_item)) {
                    opened = true
                }
            }
        }
    }
}

@Composable
fun QueryEditorQueryView(
    state: EditableSearchQueryState,
    onQueryRemove: (EditableSearchQueryState) -> Unit,
) {
    when (state) {
        is EditableSearchQueryState.Tag -> {
            EditableTagChip(
                state,
                enabled = false,
                rightIcon = {
                    Icon(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(16.dp)
                            .clickable {
                                onQueryRemove(state)
                            },
                        imageVector = Icons.Default.RemoveCircleOutline,
                        contentDescription = stringResource(R.string.search_remove_query_item_description)
                    )
                }
            )
        }
        is EditableSearchQueryState.Or -> {
            Card(
                colors = CardColors(
                    containerColor = Blue300,
                    contentColor = Color.Black,
                    disabledContainerColor = Blue300,
                    disabledContentColor = Color.Black
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("OR", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelMedium)
                        Icon(
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onQueryRemove(state) },
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = stringResource(R.string.search_remove_query_item_description)
                        )
                    }
                    state.queries.forEachIndexed { index, subQueryState ->
                        if (index != 0) { Text("+", modifier = Modifier.padding(horizontal = 8.dp)) }
                        QueryEditorQueryView(
                            subQueryState,
                            onQueryRemove = { state.queries.remove(it) }
                        )
                    }
                    NewQueryChip(state) { newQueryState ->
                        state.queries.add(newQueryState)
                    }
                }
            }
        }
        is EditableSearchQueryState.And -> {
            Card(
                colors = CardColors(
                    containerColor = Gray300,
                    contentColor = Color.Black,
                    disabledContainerColor = Gray300,
                    disabledContentColor = Color.Black
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("AND", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelMedium)
                        Icon(
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onQueryRemove(state) },
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = stringResource(R.string.search_remove_query_item_description)
                        )
                    }
                    state.queries.forEach { subQuery ->
                        QueryEditorQueryView(
                            subQuery,
                            onQueryRemove = { state.queries.remove(it) }
                        )
                    }
                    NewQueryChip(state) { newQueryState ->
                        state.queries.add(newQueryState)
                    }
                }
            }
        }
        is EditableSearchQueryState.Not -> {
            var subQueryState by state.query

            Card(
                colors = CardColors(
                    containerColor = Red300,
                    contentColor = Color.Black,
                    disabledContainerColor = Red300,
                    disabledContentColor = Color.Black
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("-", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelMedium)
                        Icon(
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onQueryRemove(state) },
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = stringResource(R.string.search_remove_query_item_description)
                        )
                    }
                    val subQueryStateSnapshot = subQueryState
                    if (subQueryStateSnapshot != null) {
                        QueryEditorQueryView(
                            subQueryStateSnapshot,
                            onQueryRemove = { subQueryState = null }
                        )
                    }

                    if (subQueryStateSnapshot == null) {
                        NewQueryChip(state) { newQueryState ->
                            subQueryState = newQueryState
                        }
                    }

                    if (subQueryStateSnapshot is EditableSearchQueryState.Tag) {
                        NewQueryChip(state) { newQueryState ->
                            subQueryState = coalesceTags(subQueryStateSnapshot, newQueryState)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QueryEditor(
    state: EditableSearchQueryState.Root
) {
    var rootQuery by state.query

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val rootQuerySnapshot = rootQuery
        if (rootQuerySnapshot != null) {
            QueryEditorQueryView(
                state = rootQuerySnapshot,
                onQueryRemove = { rootQuery = null }
            )
        }

        if (rootQuerySnapshot is EditableSearchQueryState.Tag?) {
            NewQueryChip(rootQuerySnapshot) { newState ->
                rootQuery = coalesceTags(rootQuerySnapshot, newState)
            }
        }
    }
}