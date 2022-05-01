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

package xyz.quaver.pupil.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberImagePainter
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.insets.systemBarsPadding
import com.google.accompanist.insets.ui.BottomNavigation
import com.google.accompanist.insets.ui.Scaffold
import com.google.accompanist.insets.ui.TopAppBar
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.kodein.di.*
import org.kodein.di.compose.localDI
import org.kodein.di.compose.rememberInstance
import xyz.quaver.pupil.sources.SourceEntry
import xyz.quaver.pupil.sources.rememberLocalSourceList
import xyz.quaver.pupil.sources.rememberRemoteSourceList
import xyz.quaver.pupil.util.PupilHttpClient
import xyz.quaver.pupil.util.RemoteSourceInfo
import xyz.quaver.pupil.util.launchApkInstaller
import java.io.File
import kotlin.collections.associateBy
import kotlin.collections.contains
import kotlin.collections.forEach
import kotlin.collections.listOf
import kotlin.collections.orEmpty

private sealed class SourceSelectorScreen(val route: String, val icon: ImageVector) {
    object Local: SourceSelectorScreen("local", Icons.Default.DownloadDone)
    object Explore: SourceSelectorScreen("explore", Icons.Default.Explore)
}

private val sourceSelectorScreens = listOf(
    SourceSelectorScreen.Local,
    SourceSelectorScreen.Explore
)

class DownloadApkActionState(override val di: DI) : DIAware {
    private val app: Application by instance()
    private val client: PupilHttpClient by instance()

    var progress by mutableStateOf<Float?>(null)
        private set

    suspend fun download(sourceInfo: RemoteSourceInfo): File {
        progress = 0f

        val file = File(app.cacheDir, "apks/${sourceInfo.name}-${sourceInfo.version}.apk").also {
            it.parentFile?.mkdirs()
        }

        client.downloadApk(sourceInfo, file).collect { progress = it }

        require(progress == Float.POSITIVE_INFINITY)

        progress = null
        return file
    }
}

@Composable
fun rememberDownloadApkActionState(di: DI = localDI()) = remember { DownloadApkActionState(di) }

@Composable
fun DownloadApkAction(
    state: DownloadApkActionState = rememberDownloadApkActionState(),
    content: @Composable () -> Unit
) {
    state.progress?.let { progress ->
        Box(
            Modifier.padding(12.dp, 0.dp)
        ) {
            when {
                progress.isFinite() && progress > 0f ->
                    CircularProgressIndicator(progress, modifier = Modifier.size(24.dp))
                else ->
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }

        true
    } ?: content()
}

@Composable
fun SourceListItem(icon: @Composable (Modifier) -> Unit = { }, name: String, version: String, actions: @Composable () -> Unit = { }) {
    Card(
        modifier = Modifier.padding(8.dp),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon(Modifier.size(48.dp))

            Column(
                Modifier.weight(1f)
            ) {
                Text(name.capitalize(Locale.current))

                CompositionLocalProvider(LocalContentAlpha provides 0.5f) {
                    Text(
                        "v$version",
                        style = MaterialTheme.typography.caption
                    )
                }
            }

            actions()
        }
    }
}

@Composable
fun Local(onSource: (SourceEntry) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val localSourceList by rememberLocalSourceList()
    val remoteSourceList by rememberRemoteSourceList()

    if (localSourceList.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CompositionLocalProvider(LocalContentAlpha provides 0.5f) {
                    Text("(´∇｀)", style = MaterialTheme.typography.h2)
                }
                Text("No sources found!\nLet's go download one.", textAlign = TextAlign.Center)
            }
        }
    } else {
        LazyColumn {
            items(localSourceList) { source ->
                val actionState = rememberDownloadApkActionState()

                SourceListItem(
                    icon = { modifier ->
                        Image(
                            rememberDrawablePainter(source.icon),
                            contentDescription = "source icon",
                            modifier = modifier
                        )
                    },
                    source.sourceName,
                    source.version
                ) {
                    DownloadApkAction(actionState) {
                        val remoteSource = remoteSourceList?.get(source.packageName)
                        if (remoteSource != null && remoteSource.version != source.version) {
                            TextButton(onClick = {
                                coroutineScope.launch {
                                    val file = actionState.download(remoteSource)
                                    context.launchApkInstaller(file)
                                }
                            }) {
                                Text("UPDATE")
                            }
                        } else {
                            TextButton(
                                onClick = { onSource(source) }
                            ) {
                                Text("GO")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Explore() {
    val localSourceList by rememberLocalSourceList()
    val localSources by derivedStateOf {
        localSourceList.associateBy {
            it.packageName
        }
    }

    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    val remoteSources by rememberRemoteSourceList()

    Box(
        Modifier.fillMaxSize()
    ) {
        if (remoteSources == null)
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        else
            LazyColumn {
                items(remoteSources?.values?.toList().orEmpty()) { sourceInfo ->
                    val actionState = rememberDownloadApkActionState()

                    SourceListItem(
                        icon = { modifier ->
                            Image(
                                rememberImagePainter("https://raw.githubusercontent.com/tom5079/PupilSources/master/${sourceInfo.projectName}/src/main/res/mipmap-xxxhdpi/ic_launcher.png"),
                                contentDescription = "source icon",
                                modifier = modifier
                            )
                        },
                        sourceInfo.name,
                        sourceInfo.version
                    ) {
                        DownloadApkAction(actionState) {
                            IconButton(onClick = {
                                if (sourceInfo.name in localSources) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", localSources[sourceInfo.name]!!.packagePath, null)
                                        )
                                    )
                                } else coroutineScope.launch {
                                    val file = actionState.download(sourceInfo)
                                    context.launchApkInstaller(file)
                                }
                            }) {
                                Icon(
                                    if (sourceInfo.name !in localSources) Icons.Default.Download
                                    else                                  Icons.Outlined.Info,
                                    contentDescription = "download"
                                )
                            }
                        }
                    }
                }
            }
    }
}

@Composable
fun SourceSelector(onSource: (SourceEntry) -> Unit) {
    val bottomNavController = rememberNavController()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Pupil")
                },
                contentPadding = rememberInsetsPaddingValues(LocalWindowInsets.current.statusBars)
            )
        },
        bottomBar = {
            BottomNavigation(
                contentPadding = rememberInsetsPaddingValues(LocalWindowInsets.current.navigationBars)
            ) {
                val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                sourceSelectorScreens.forEach { screen ->
                    BottomNavigationItem(
                        icon = { Icon(screen.icon, contentDescription = screen.route) },
                        label = { Text(screen.route) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            bottomNavController.navigate(screen.route) {
                                popUpTo(bottomNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { contentPadding ->
        NavHost(bottomNavController, startDestination = "local", modifier = Modifier
            .systemBarsPadding(top = false, bottom = false)
            .padding(contentPadding)) {
            composable(SourceSelectorScreen.Local.route) { Local(onSource) }
            composable(SourceSelectorScreen.Explore.route) { Explore() }
        }
    }

}