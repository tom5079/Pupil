/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2022  tom5079
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
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import org.kodein.di.compose.onDIContext
import xyz.quaver.pupil.util.Release
import xyz.quaver.pupil.util.launchApkInstaller
import java.util.*

@Composable
fun UpdateAlertDialog(
    show: Boolean,
    release: Release,
    onDismiss: () -> Unit
) {
    val state = rememberDownloadApkActionState()

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    if (show) {
        Dialog(onDismissRequest = { if (state.progress == null) onDismiss() }) {
            Card {
                val progress = state.progress

                if (progress != null) {
                    if (progress.isFinite() && progress > 0)
                        LinearProgressIndicator(progress)
                    else
                        LinearProgressIndicator()
                }

                Column(
                    Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Update Available",
                        style = MaterialTheme.typography.h6
                    )

                    MarkdownText(release.releaseNotes.getOrElse(Locale.getDefault()) { release.releaseNotes[Locale.ENGLISH]!! })

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss, enabled = progress == null) {
                            Text("DISMISS")
                        }

                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    val file = state.download(release.apkUrl)!! // TODO("Handle exception")
                                    context.launchApkInstaller(file)
                                    state.reset()
                                    onDismiss()
                                }
                            },
                            enabled = progress == null
                        ) {
                            Text("UPDATE")
                        }
                    }
                }
            }
        }
    }
}