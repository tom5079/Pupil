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

import android.annotation.TargetApi
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

@TargetApi(21)
class TreeDocumentFileX(
    private val context: Context,
    private val uri: Uri,
    private var name: String? = null,
    private var documentID: String? = null,
    private var length: Long? = null
) : DocumentFileX() {

    init {
        val projection = mutableListOf<String>()

        if (name == null)
            projection.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        if (documentID == null)
            projection.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        if (length == null)
            projection.add(DocumentsContract.Document.COLUMN_SIZE)

        if (projection.isNotEmpty()) {
            val contentResolver = context.contentResolver

            contentResolver.query(uri, projection.toTypedArray(), null, null, null).use { cursor ->
                while (cursor?.moveToNext() == true) {
                    cursor.columnNames.forEach { column ->
                        val index = cursor.getColumnIndex(column)

                        when (column) {
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME -> this.name = cursor.getString(index)
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID -> this.documentID = cursor.getString(index)
                            DocumentsContract.Document.COLUMN_SIZE -> this.length = cursor.getLong(index)
                        }
                    }
                }
            }
        }
    }

    override fun createFile(displayName: String): DocumentFileX? {
        val uri = kotlin.runCatching {
            DocumentsContract.createDocument(context.contentResolver, uri, "null", displayName)
        }.getOrNull() ?: return null

        return TreeDocumentFileX(context, uri, displayName, DocumentsContract.getDocumentId(uri), 0)
    }

    override fun createDirectory(displayName: String): DocumentFileX? {
        val uri = kotlin.runCatching {
            DocumentsContract.createDocument(context.contentResolver, uri, "null", displayName)
        }.getOrNull() ?: return null
    }

    override fun getUri() = uri

    override fun getName() = name ?: "null"

    override fun getParentFile(): DocumentFileX?

    override fun isDirectory() = name?.contains('.') == false
    override fun isFile() = name?.contains('.') == true

    override fun length() = length ?: -1

    override fun canRead(): Boolean
    override fun canWrite(): Boolean

    override fun delete() =
        kotlin.runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }.getOrElse { false }

    override fun exists() = documentID == null

    override fun listFiles(): List<DocumentFileX>? {
        val contentResolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))

        contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        ).use {

        }
    }

    override fun findFile(displayName: String): DocumentFileX?
    
}