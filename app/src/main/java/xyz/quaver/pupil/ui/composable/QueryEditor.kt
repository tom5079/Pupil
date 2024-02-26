package xyz.quaver.pupil.ui.composable

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import xyz.quaver.pupil.ui.theme.Gray300
import xyz.quaver.pupil.ui.theme.Red300

@Composable
fun NewQueryChip(currentQuery: SearchQuery) {
    var opened by remember { mutableStateOf(false) }

    @Composable
    fun NewQueryRow(
        icon: ImageVector = Icons.Default.AddCircleOutline,
        text: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
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
        AnimatedContent(targetState = opened, label = "add new query") { targetOpened ->
            if (targetOpened) {
                Column {
                    NewQueryRow(
                        icon = Icons.Default.RemoveCircleOutline,
                        text = stringResource(android.R.string.cancel)
                    ) {
                        opened = false
                    }
                    HorizontalDivider()
                    NewQueryRow(text = stringResource(R.string.search_add_query_item_tag)) {

                    }
                    if (currentQuery !is SearchQuery.And) {
                        HorizontalDivider()
                        NewQueryRow(text = "AND") {

                        }
                    }
                    if (currentQuery !is SearchQuery.Or) {
                        HorizontalDivider()
                        NewQueryRow(text = "OR") {

                        }
                    }
                    if (currentQuery !is SearchQuery.Not) {
                        HorizontalDivider()
                        NewQueryRow(text = "NOT") {

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
    query: SearchQuery?,
    onQueryAdd: (SearchQuery) -> Unit
) {
    when (query) {
        is SearchQuery.Tag -> {
            TagChip(
                query,
                enabled = false,
                rightIcon = {
                    Icon(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(16.dp),
                        imageVector = Icons.Default.RemoveCircleOutline,
                        contentDescription = stringResource(R.string.search_remove_query_item_description)
                    )
                }
            )
        }
        is SearchQuery.Or -> {
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
                            modifier = Modifier.size(16.dp),
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = stringResource(xyz.quaver.pupil.R.string.search_remove_query_item_description)
                        )
                    }
                    query.queries.forEachIndexed { index, subquery ->
                        if (index != 0) { Text("+", modifier = Modifier.padding(horizontal = 8.dp)) }
                        QueryEditorQueryView(subquery, onQueryAdd)
                    }
                    NewQueryChip(query) {

                    }
                }
            }
        }
        is SearchQuery.And -> {
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
                            modifier = Modifier.size(16.dp),
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = stringResource(xyz.quaver.pupil.R.string.search_remove_query_item_description)
                        )
                    }
                    query.queries.forEach { subquery ->
                        QueryEditorQueryView(subquery, onQueryAdd)
                    }
                    NewQueryChip(query) {

                    }
                }
            }
        }
        is SearchQuery.Not -> {
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
                            modifier = Modifier.size(16.dp),
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = stringResource(xyz.quaver.pupil.R.string.search_remove_query_item_description)
                        )
                    }
                    QueryEditorQueryView(query.query, onQueryAdd)
                }
            }
        }
    }
}

@Composable
fun QueryEditor(
    query: SearchQuery?,
    onQueryChange: (SearchQuery) -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        if (query != null) {
            QueryEditorQueryView(query = query) {

            }
        } else {
            NewQueryChip(null) {

            }
        }
    }
}
