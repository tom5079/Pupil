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

package xyz.quaver.pupil

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import xyz.quaver.pupil.sources.isSourceFeatureEnabled
import xyz.quaver.pupil.sources.loadSource

@RunWith(AndroidJUnit4::class)
class SourceLoaderInstrumentedTest {

    @Test
    fun getPackages() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val application: Application = appContext.applicationContext as Application

        val packageManager = appContext.packageManager

        val packages = packageManager.getInstalledPackages(
            PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA
        )

        val sources = packages.filter { it.isSourceFeatureEnabled }

        assertEquals(1, sources.size)
    }

}