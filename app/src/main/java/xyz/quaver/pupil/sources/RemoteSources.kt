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

package xyz.quaver.pupil.sources

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import org.kodein.di.compose.localDI
import org.kodein.di.compose.rememberInstance
import org.kodein.di.direct
import org.kodein.di.instance
import xyz.quaver.pupil.util.PupilHttpClient
import xyz.quaver.pupil.util.RemoteSourceInfo

@Composable
fun rememberRemoteSourceList(client: PupilHttpClient = localDI().direct.instance()) = produceState<Map<String, RemoteSourceInfo>?>(null) {
    while (true) {
        value = client.getRemoteSourceList()
        delay(1000)
    }
}