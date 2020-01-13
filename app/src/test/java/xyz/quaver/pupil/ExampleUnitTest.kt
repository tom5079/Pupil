/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2019  tom5079
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

@file:Suppress("UNUSED_VARIABLE")

package xyz.quaver.pupil

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

import org.junit.Test
import xyz.quaver.pupil.util.download
import java.io.File
import java.net.URL

class ExampleUnitTest {

    @Test
    fun test() {
        URL("https://github.om/tom5079/Pupil/releases/download/4.2-beta2-hotfix2/Pupil-v4.2-beta2-hotfix2.apk").download(
            File(System.getenv("USERPROFILE"), "Pupil.apk")
        ) { downloaded, fileSize ->
            println("%.1f%%".format(downloaded*100.0/fileSize))
        }
    }

}
