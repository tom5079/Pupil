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

package xyz.quaver.pupil.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.quaver.pupil.R

@Preview
@Composable
fun FloatingSearchBar(
    modifier: Modifier = Modifier,
    query: String = "",
    onQueryChange: (String) -> Unit = { },
    navigationIcon: @Composable () -> Unit = {
        Icon(
            Icons.Default.Menu,
            modifier = Modifier.size(24.dp),
            contentDescription = null,
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
        )
    },
    actions: @Composable RowScope.() -> Unit = { }
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(8.dp, 8.dp)
            .background(Color.Transparent),
        elevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp, 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigationIcon()

            BasicTextField(
                modifier = Modifier.weight(1f).padding(16.dp, 0.dp),
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colors.primary),
                decorationBox = { innerTextField ->
                    if (query.isEmpty())
                        Text(
                            stringResource(R.string.search_hint),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )

                    innerTextField()
                }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                content = actions
            )
        }
    }
}