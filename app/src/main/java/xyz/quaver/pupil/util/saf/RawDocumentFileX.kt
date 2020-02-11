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

import android.net.Uri
import java.io.File

class RawDocumentFileX(private var file: File) : DocumentFileX() {

    override fun createFile(displayName: String) =
        File(file, displayName).let {
            try {
                it.createNewFile()
                RawDocumentFileX(it)
            } catch (e: Exception) {
                null
            }
        }

    override fun createDirectory(displayName: String) =
        File(file, displayName).let {
            if (it.isDirectory || it.mkdir())
                RawDocumentFileX(it)
            else
                null
        }

    override fun getUri() = Uri.fromFile(file)!!

    override fun getName() = file.name

    fun getParentFile() =
        file.parentFile.let {
            if (it == null)
                null
            else
                RawDocumentFileX(it)
        }

    override fun isDirectory() = file.isDirectory

    override fun isFile() = file.isFile

    override fun length() = file.length()

    override fun canRead() = file.canRead()

    override fun canWrite() = file.canWrite()

    override fun delete() =
        if (file.isDirectory)
            file.deleteRecursively()
        else
            file.delete()

    override fun exists() = file.exists()

    override fun listFiles() = file.listFiles()?.map { RawDocumentFileX(it) }

    override fun findFile(displayName: String) =
        File(file, displayName).let {
            if (it.exists())
                RawDocumentFileX(it)
            else
                null
        }

}