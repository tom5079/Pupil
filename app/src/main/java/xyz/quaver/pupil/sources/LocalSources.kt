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
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dalvik.system.PathClassLoader
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.quaver.pupil.sources.core.Source

@Composable
fun rememberLocalSourceList(context: Context = LocalContext.current): State<List<SourceEntry>> = produceState(emptyList()) {
    while (true) {
        value = loadSourceList(context)
        delay(1000)
    }
}

suspend fun loadSource(context: Context, sourceEntry: SourceEntry): Source = coroutineScope {
    sourceCacheMutex.withLock {
        sourceCache[sourceEntry.packageName] ?: run {
            val classLoader = PathClassLoader(sourceEntry.sourceDir, null, context.classLoader)

            Class.forName("${sourceEntry.packagePath}${sourceEntry.sourcePath}", false, classLoader)
                .getConstructor(Application::class.java)
                .newInstance(context.applicationContext) as Source
        }.also { sourceCache[sourceEntry.packageName] = it }
    }
}

private const val SOURCES_FEATURE = "pupil.sources"
private const val SOURCES_PACKAGE_PREFIX = "xyz.quaver.pupil.sources"
private const val SOURCES_PATH = "pupil.sources.path"

private val PackageInfo.isSourceFeatureEnabled
    get() = this.reqFeatures.orEmpty().any { it.name == SOURCES_FEATURE }

private fun loadSource(context: Context, packageInfo: PackageInfo): List<SourceEntry> {
    val packageManager = context.packageManager

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

private val sourceCacheMutex = Mutex()
private val sourceCache = mutableMapOf<String, Source>()

private fun loadSourceList(context: Context): List<SourceEntry> {
    val packageManager = context.packageManager

    val packages = packageManager.getInstalledPackages(
        PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA
    )

    return packages.flatMap { packageInfo ->
        if (packageInfo.isSourceFeatureEnabled)
            loadSource(context, packageInfo)
        else
            emptyList()
    }
}