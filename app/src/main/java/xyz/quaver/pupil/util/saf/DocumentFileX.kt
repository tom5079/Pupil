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

package xyz.quaver.pupil.util.saf

import android.content.Context
import android.net.Uri
import java.io.File

abstract class DocumentFileX {

    abstract fun createFile(displayName: String): DocumentFileX?
    abstract fun createDirectory(displayName: String): DocumentFileX?
    abstract fun getUri(): Uri
    abstract fun getName(): String
    abstract fun isDirectory(): Boolean
    abstract fun isFile(): Boolean
    abstract fun length(): Long
    abstract fun canRead(): Boolean
    abstract fun canWrite(): Boolean
    abstract fun delete(): Boolean
    abstract fun exists(): Boolean
    abstract fun listFiles(): List<DocumentFileX>?
    abstract fun findFile(displayName: String): DocumentFileX?

    companion object {
        fun fromFile(file: File): DocumentFileX {
            return RawDocumentFileX(file)
        }

        fun fromTreeUri(context: Context, uri: Uri): DocumentFileX {
            return TreeDocumentFileX(context, uri)
        }
    }

}