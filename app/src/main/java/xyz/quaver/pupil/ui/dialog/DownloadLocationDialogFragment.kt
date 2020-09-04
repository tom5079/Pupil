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

package xyz.quaver.pupil.ui.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.item_download_folder.view.*
import net.rdrei.android.dirchooser.DirectoryChooserActivity
import net.rdrei.android.dirchooser.DirectoryChooserConfig
import xyz.quaver.io.FileX
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.byteToString
import xyz.quaver.pupil.util.downloader.DownloadManager
import xyz.quaver.pupil.util.migrate
import xyz.quaver.pupil.util.normalizeID
import java.io.File

class DownloadLocationDialogFragment : DialogFragment() {
    private val entries = mutableMapOf<File?, View>()

    @SuppressLint("InflateParams")
    private fun build() : View? {
        val context = context ?: return null

        val view = layoutInflater.inflate(R.layout.dialog_download_folder, null) as LinearLayout

        val externalFilesDirs = ContextCompat.getExternalFilesDirs(context, null)

        externalFilesDirs.forEachIndexed { index, dir ->
            dir ?: return@forEachIndexed

            view.addView(layoutInflater.inflate(R.layout.item_download_folder, view, false).apply {
                location_type.text = context.getString(when (index) {
                    0 -> R.string.settings_download_folder_internal
                    else -> R.string.settings_download_folder_removable
                })
                location_available.text = context.getString(
                    R.string.settings_download_folder_available,
                    byteToString(dir.freeSpace)
                )
                setOnClickListener {
                    entries.values.forEach {
                        it.button.isChecked = false
                    }
                    button.performClick()
                    Preferences["download_folder"] = dir.toUri().toString()
                }
                entries[dir] = this
            })
        }

        view.addView(layoutInflater.inflate(R.layout.item_download_folder, view, false).apply {
            location_type.text = context.getString(R.string.settings_download_folder_custom)
            setOnClickListener {
                entries.values.forEach {
                    it.button.isChecked = false
                }
                button.performClick()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        putExtra("android.content.extra.SHOW_ADVANCED", true)
                    }

                    startActivityForResult(intent, R.id.request_download_folder.normalizeID())
                } else {    // Can't use SAF on old Androids!
                    val config = DirectoryChooserConfig.builder()
                        .newDirectoryName("Pupil")
                        .allowNewDirectoryNameModification(true)
                        .build()

                    val intent = Intent(context, DirectoryChooserActivity::class.java).apply {
                        putExtra(DirectoryChooserActivity.EXTRA_CONFIG, config)
                    }

                    startActivityForResult(intent, R.id.request_download_folder_old.normalizeID())
                }
            }
            entries[null] = this
        })

        val downloadFolder = DownloadManager.getInstance(context).downloadFolder.canonicalPath
        val key = entries.keys.firstOrNull { it?.canonicalPath == downloadFolder }
        entries[key]!!.button.isChecked = true
        if (key == null) entries[key]!!.location_available.text = downloadFolder

        return view
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())

        builder
            .setTitle(R.string.settings_download_folder)
            .setView(build())
            .setPositiveButton(requireContext().getText(android.R.string.ok)) { _, _ ->
                DownloadManager.getInstance(requireContext()).migrate()
            }

        isCancelable = false

        return builder.create()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            R.id.request_download_folder.normalizeID() -> {
                if (resultCode == Activity.RESULT_OK) {
                    val activity = activity ?: return
                    val context = context ?: return
                    val dialog = dialog ?: return

                    data?.data?.also { uri ->
                        val takeFlags: Int =
                            activity.intent.flags and
                                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                            context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                        if (FileX(context, uri).canWrite())
                            Preferences["download_folder"] = uri.toString()
                        else {
                            Snackbar.make(
                                dialog.window!!.decorView.rootView,
                                R.string.settings_download_folder_not_writable,
                                Snackbar.LENGTH_LONG
                            ).show()

                            val downloadFolder = DownloadManager.getInstance(context).downloadFolder.canonicalPath
                            val key = entries.keys.firstOrNull { it?.canonicalPath == downloadFolder }
                            entries[key]!!.button.isChecked = true
                            if (key == null) entries[key]!!.location_available.text = downloadFolder
                        }
                    }
                }
            }
            R.id.request_download_folder_old.normalizeID() -> {
                val context = context ?: return
                val dialog = dialog ?: return

                if (resultCode == DirectoryChooserActivity.RESULT_CODE_DIR_SELECTED) {
                    val directory = data?.getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR)!!

                    if (!File(directory).canWrite()) {
                        Snackbar.make(
                            dialog.window!!.decorView.rootView,
                            R.string.settings_download_folder_not_writable,
                            Snackbar.LENGTH_LONG
                        ).show()

                        val downloadFolder = DownloadManager.getInstance(context).downloadFolder.canonicalPath
                        val key = entries.keys.firstOrNull { it?.canonicalPath == downloadFolder }
                        entries[key]!!.button.isChecked = true
                        if (key == null) entries[key]!!.location_available.text = downloadFolder
                    }
                    else
                        Preferences["download_folder"] = File(directory).canonicalPath
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

}