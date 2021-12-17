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

package xyz.quaver.pupil.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import xyz.quaver.pupil.R

@Composable
fun OpenWithItemIDDialog(onDismissRequest: (String?) -> Unit = { }) {
    var itemID by remember { mutableStateOf("") }

    Dialog(onDismissRequest = { onDismissRequest(null) }) {
        Card(
            elevation = 8.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.main_open_gallery_by_id),
                    style = MaterialTheme.typography.h6
                )
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = itemID,
                    onValueChange = {
                        itemID = it
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = { onDismissRequest(itemID) }
                    )
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onDismissRequest(itemID) }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}