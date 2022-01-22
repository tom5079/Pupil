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

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
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
import com.google.accompanist.insets.ui.BottomNavigation
import com.google.accompanist.insets.ui.Scaffold
import com.google.accompanist.insets.ui.TopAppBar
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.kodein.di.compose.rememberInstance
import xyz.quaver.pupil.sources.SourceEntries
import xyz.quaver.pupil.sources.SourceEntry
import xyz.quaver.pupil.sources.rememberSources
import xyz.quaver.pupil.util.ApkDownloadManager

private sealed class SourceSelectorScreen(val route: String, val icon: ImageVector) {
    object Local: SourceSelectorScreen("local", Icons.Default.DownloadDone)
    object Explore: SourceSelectorScreen("explore", Icons.Default.Explore)
}

private val sourceSelectorScreens = listOf(
    SourceSelectorScreen.Local,
    SourceSelectorScreen.Explore
)

@Composable
fun SourceListItem(icon: Painter, name: String, version: String, actions: @Composable () -> Unit = { }) {
    Card(
        modifier = Modifier.padding(8.dp),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                icon,
                contentDescription = "source icon",
                modifier = Modifier.size(48.dp)
            )

            Column(
                Modifier.weight(1f)
            ) {
                Text(
                    name.capitalize(Locale.current)
                )

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
    val sources by rememberSources()

    if (sources.isEmpty()) {
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
            items(sources) { source ->
                SourceListItem(
                    rememberDrawablePainter(source.icon),
                    source.sourceName,
                    source.version
                ) {
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

@Serializable
private data class RemoteSourceInfo(
    val projectName: String,
    val name: String,
    val version: String
)

@Composable
fun Explore() {
    val sources by rememberSources()
    val localSources by derivedStateOf {
        sources.map {
            it.packageName to it
        }.toMap()
    }

    val client: HttpClient by rememberInstance()

    val downloadManager: ApkDownloadManager by rememberInstance()
    val progresses = remember { mutableStateMapOf<String, Float>() }

    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    val remoteSources by produceState<Map<String, RemoteSourceInfo>?>(null) {
        while (true) {
            delay(1000)
            value = withContext(Dispatchers.IO) {
                client.get<Map<String, RemoteSourceInfo>>("https://raw.githubusercontent.com/tom5079/PupilSources/master/versions.json")
            }
        }
    }

    Box(
        Modifier.fillMaxSize()
    ) {
        if (remoteSources == null)
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        else
            LazyColumn {
                items(remoteSources?.values?.toList() ?: emptyList()) { source ->
                    SourceListItem(
                        rememberImagePainter("https://raw.githubusercontent.com/tom5079/PupilSources/master/${source.projectName}/src/main/res/mipmap-xxxhdpi/ic_launcher.png"),
                        source.name,
                        source.version
                    ) {
                        if (source.name !in progresses)
                            IconButton(onClick = {
                                if (source.name in localSources) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", localSources[source.name]!!.packagePath, null)
                                        )
                                    )
                                } else coroutineScope.launch {
                                    progresses[source.name] = 0f
                                    downloadManager.download(source.projectName, source.name, source.version)
                                        .onCompletion {
                                            progresses.remove(source.name)
                                        }.collectLatest {
                                            progresses[source.name] = it
                                        }
                                }
                            }) {
                                Icon(
                                    if (source.name !in localSources) Icons.Default.Download
                                    else                              Icons.Outlined.Info,
                                    contentDescription = "download"
                                )
                            }
                        else {
                            val progress = progresses[source.name]

                            Box(
                                Modifier.padding(12.dp, 0.dp)
                            ) {
                                when {
                                    progress?.isFinite() == true ->
                                        CircularProgressIndicator(progress, modifier = Modifier.size(24.dp))
                                    else ->
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
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
                    Text("Sources")
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
        NavHost(bottomNavController, startDestination = "local", modifier = Modifier.padding(contentPadding)) {
            composable(SourceSelectorScreen.Local.route) { Local(onSource) }
            composable(SourceSelectorScreen.Explore.route) { Explore() }
        }
    }

}