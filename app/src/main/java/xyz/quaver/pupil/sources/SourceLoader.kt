/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
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

package xyz.quaver.pupil.sources

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import dalvik.system.PathClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.bindFactory
import org.kodein.di.bindInstance
import org.kodein.di.bindProvider
import org.kodein.di.compose.rememberInstance
import xyz.quaver.pupil.sources.core.Source
import java.util.concurrent.ConcurrentHashMap

private const val SOURCES_FEATURE = "pupil.sources"
private const val SOURCES_PACKAGE_PREFIX = "xyz.quaver.pupil.sources"
private const val SOURCES_PATH = "pupil.sources.path"

data class SourceEntry(
    val packageName: String,
    val packagePath: String,
    val sourceName: String,
    val sourcePath: String,
    val sourceDir: String,
    val icon: Drawable,
    val version: String
)

val PackageInfo.isSourceFeatureEnabled
    get() = this.reqFeatures.orEmpty().any { it.name == SOURCES_FEATURE }

fun loadSource(app: Application, packageInfo: PackageInfo): List<SourceEntry> {
    val packageManager = app.packageManager

    val applicationInfo = packageInfo.applicationInfo

    val packageName = packageManager.getApplicationLabel(applicationInfo).toString().substringAfter("[Pupil] ")
    val packagePath = packageInfo.packageName

    val icon = packageManager.getApplicationIcon(applicationInfo)

    val version = packageInfo.versionName

    return packageInfo
        .applicationInfo
        .metaData
        ?.getString(SOURCES_PATH)
        ?.split(';')
        ?.map { source ->
            val (sourceName, sourcePath) = source.split(':', limit = 2)
            SourceEntry(
                packageName,
                packagePath,
                sourceName,
                sourcePath,
                applicationInfo.sourceDir,
                icon,
                version
            )
        }.orEmpty()
}

fun loadSource(app: Application, sourceEntry: SourceEntry): Source {
    val classLoader = PathClassLoader(sourceEntry.sourceDir, null, app.classLoader)

    return Class.forName("${sourceEntry.packagePath}${sourceEntry.sourcePath}", false, classLoader)
        .getConstructor(Application::class.java)
        .newInstance(app) as Source
}

fun updateSources(app: Application): List<SourceEntry> {
    val packageManager = app.packageManager

    val packages = packageManager.getInstalledPackages(
        PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA
    )

    return packages.flatMap { packageInfo ->
        if (packageInfo.isSourceFeatureEnabled)
            loadSource(app, packageInfo)
        else
            emptyList()
    }
}

@Composable
fun rememberSources(): State<List<SourceEntry>> {
    val app: Application by rememberInstance()
    val sources = remember { mutableStateOf<List<SourceEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            sources.value = updateSources(app)
            delay(1000)
        }
    }

    return sources
}