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

package xyz.quaver.pupil.services

import android.annotation.SuppressLint
import android.app.job.JobParameters
import android.app.job.JobService
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.util.cio.writeChannel
import io.ktor.util.collections.ConcurrentMap
import io.ktor.util.collections.ConcurrentSet
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import xyz.quaver.pupil.Pupil
import xyz.quaver.pupil.networking.GalleryFile
import xyz.quaver.pupil.networking.HitomiHttpClient
import java.io.File

@SuppressLint("SpecifyJobSchedulerIdRange")
@AndroidEntryPoint
class ImageCacheService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }
}