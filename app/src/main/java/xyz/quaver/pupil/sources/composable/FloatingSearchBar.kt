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

package xyz.quaver.pupil.sources.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.KeyboardManager

@Preview
@Composable
fun FloatingSearchBar(
    modifier: Modifier = Modifier,
    query: String = "",
    onQueryChange: (String) -> Unit = { },
    navigationIcon: @Composable () -> Unit = { },
    actions: @Composable RowScope.() -> Unit = { },
    onTextFieldFocused: () -> Unit = { },
    onTextFieldUnfocused: () -> Unit = { }
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var isFocused by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val keyboardManager = KeyboardManager(context)
        keyboardManager.attachKeyboardDismissListener {
            focusManager.clearFocus()
        }
        onDispose {
            keyboardManager.release()
        }
    }

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
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp, 0.dp)
                    .onFocusChanged {
                        if (it.isFocused) onTextFieldFocused()
                        else              onTextFieldUnfocused()

                        isFocused = it.isFocused
                    },
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                    }
                ),
                decorationBox = { innerTextField ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty())
                                Text(
                                    stringResource(R.string.search_hint),
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                )
                            innerTextField()
                        }

                        if (query.isNotEmpty() && isFocused)
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { onQueryChange("") }
                            )
                    }
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