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

package xyz.quaver.pupil.sources

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.kodein.di.compose.rememberInstance
import xyz.quaver.pupil.sources.core.Source

@Composable
fun SourceSelectDialog(navController: NavController, currentSource: String? = null, onDismissRequest: () -> Unit = { }) {
    SourceSelectDialog(currentSource = currentSource, onDismissRequest = onDismissRequest) {
        onDismissRequest()
        navController.navigate(it.name) {
            currentSource?.let { popUpTo("main") }
        }
    }
}

@Composable
fun SourceSelectDialogItem(sourceEntry: SourceEntry, isSelected: Boolean, onSelected: (Source) -> Unit = { }) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = rememberDrawablePainter(sourceEntry.icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )

        Text(
            sourceEntry.name,
            modifier = Modifier.weight(1f)
        )

        Icon(
            Icons.Default.Settings,
            contentDescription = null,
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
        )

        Button(
            enabled = !isSelected,
            onClick = {
                onSelected(sourceEntry.source)
            }
        ) {
            Text("GO")
        }

    }
}

@Composable
fun SourceSelectDialog(currentSource: String? = null, onDismissRequest: () -> Unit = { }, onSelected: (Source) -> Unit = { }) {
    val sourceEntries: SourceEntries by rememberInstance()

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            elevation = 8.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column() {
                sourceEntries.values.forEach { SourceSelectDialogItem(it, it.name == currentSource, onSelected) }
            }
        }
    }
}