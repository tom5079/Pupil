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
import android.graphics.drawable.Drawable
import android.util.Log
import dalvik.system.DexClassLoader
import dalvik.system.PathClassLoader
import org.kodein.di.*
import org.kodein.di.bindings.NoArgBindingDI
import org.kodein.di.bindings.NoArgDIBinding
import java.util.*

private const val SOURCES_FEATURE = "pupil.sources"
private const val SOURCES_PACKAGE_PREFIX = "xyz.quaver.pupil.sources"
private const val SOURCES_PATH = "pupil.sources.path"

data class SourceEntry(
    val name: String,
    val source: Source,
    val icon: Drawable
)
typealias SourceEntries = Map<String, SourceEntry>

private val sources = mutableMapOf<String, SourceEntry>()

val PackageInfo.isSourceFeatureEnabled
    get() = this.reqFeatures.orEmpty().any { it.name == SOURCES_FEATURE }

fun loadSource(app: Application, packageInfo: PackageInfo) {
    val packageManager = app.packageManager
    val applicationInfo = packageInfo.applicationInfo

    val classLoader = PathClassLoader(applicationInfo.sourceDir, null, app.classLoader)
    val packageName = packageInfo.packageName

    val sourceName = packageManager.getApplicationLabel(applicationInfo).toString().substringAfter("[Pupil] ")

    val icon = packageManager.getApplicationIcon(applicationInfo)

    packageInfo
        .applicationInfo
        .metaData
        .getString(SOURCES_PATH)
        ?.split(';')
        .orEmpty()
        .forEach { sourcePath ->
            sources[sourceName] = SourceEntry(
                sourceName,
                Class.forName("$packageName$sourcePath", false, classLoader)
                    .getConstructor(Application::class.java)
                    .newInstance(app) as Source,
                icon
            )
        }
}

fun loadSources(app: Application) {
    val packageManager = app.packageManager

    val packages = packageManager.getInstalledPackages(
        PackageManager.GET_CONFIGURATIONS or
                PackageManager.GET_META_DATA
    )

    val sources = packages.filter { it.isSourceFeatureEnabled }

    sources.forEach { loadSource(app, it) }
}

fun sourceModule(app: Application) = DI.Module(name = "source") {
    loadSources(app)
    bindInstance { Collections.unmodifiableMap(sources) }
}