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

package xyz.quaver.pupil.util

import android.annotation.SuppressLint
import java.util.*
import kotlin.collections.ArrayList

@OptIn(ExperimentalStdlibApi::class)
fun String.wordCapitalize() : String {
    val result = ArrayList<String>()

    @SuppressLint("DefaultLocale")
    for (word in this.split(" "))
        result.add(word.capitalize(Locale.US))

    return result.joinToString(" ")
}

fun byteToString(byte: Long, precision : Int = 1) : String {

    val suffix = listOf(
        "B",
        "kB",
        "MB",
        "GB",
        "TB" //really?
    )
    var size = byte.toDouble(); var suffixIndex = 0

    while (size >= 1024) {
        size /= 1024
        suffixIndex++
    }

    return "%.${precision}f ${suffix[suffixIndex]}".format(size)

}

/*
 * Convert android generated ID to requestCode
 * to prevent java.lang.IllegalArgumentException: Can only use lower 16 bits for requestCode
 *
 * https://stackoverflow.com/questions/38072322/generate-16-bit-unique-ids-in-android-for-startactivityforresult
 */
fun Int.normalizeID() = this.and(0xFFFF)