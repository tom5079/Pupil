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

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import com.google.android.material.snackbar.Snackbar
import xyz.quaver.io.FileX
import xyz.quaver.io.util.toFile
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.DownloadLocationDialogBinding
import xyz.quaver.pupil.databinding.DownloadLocationItemBinding
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.byteToString
import xyz.quaver.pupil.util.downloader.DownloadManager
import java.io.File

class DownloadLocationDialogFragment : DialogFragment() {

    private var _binding: DownloadLocationDialogBinding? = null
    private val binding get() = _binding!!

    private val entries = mutableMapOf<File?, DownloadLocationItemBinding>()

    private val requestDownloadFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            val context = context ?: return@registerForActivityResult
            val dialog = dialog ?: return@registerForActivityResult

            it.data?.data?.also { uri ->
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                if (kotlin.runCatching { FileX(context, uri).canWrite() }.getOrDefault(false)) {
                    entries[null]?.locationAvailable?.text = uri.toFile(context)?.canonicalPath
                    Preferences["download_folder"] = uri.toString()
                } else {
                    Snackbar.make(
                        dialog.window!!.decorView.rootView,
                        R.string.settings_download_folder_not_writable,
                        Snackbar.LENGTH_LONG
                    ).show()

                    val downloadFolder = DownloadManager.getInstance(context).downloadFolder.canonicalPath
                    val key = entries.keys.firstOrNull { it?.canonicalPath == downloadFolder }
                    entries[key]!!.button.isChecked = true
                    if (key == null) entries[null]!!.locationAvailable.text = downloadFolder
                }
            }
        } else {
            val downloadFolder = DownloadManager.getInstance(context ?: return@registerForActivityResult).downloadFolder.canonicalPath
            val key = entries.keys.firstOrNull { it?.canonicalPath == downloadFolder }
            if (key == null)
                entries[null]!!.locationAvailable.text = downloadFolder
            else {
                entries[null]!!.button.isChecked = false
                entries[key]!!.button.isChecked = true
            }
        }
    }

    private fun initView() {
        val externalFilesDirs = ContextCompat.getExternalFilesDirs(requireContext(), null)

        externalFilesDirs.forEachIndexed { index, dir ->
            dir ?: return@forEachIndexed

            DownloadLocationItemBinding.inflate(layoutInflater, binding.root, true).apply {
                locationType.text = requireContext().getString(when (index) {
                    0 -> R.string.settings_download_folder_internal
                    else -> R.string.settings_download_folder_removable
                })
                locationAvailable.text = requireContext().getString(
                    R.string.settings_download_folder_available,
                    byteToString(dir.freeSpace)
                )
                root.setOnClickListener {
                    entries.values.forEach { entry ->
                        entry.button.isChecked = false
                    }
                    button.performClick()
                    Preferences["download_folder"] = dir.toUri().toString()
                }
                entries[dir] = this
            }
        }

        DownloadLocationItemBinding.inflate(layoutInflater, binding.root, true).apply {
            locationType.text = requireContext().getString(R.string.settings_download_folder_custom)
            root.setOnClickListener {
                entries.values.forEach { entry ->
                    entry.button.isChecked = false
                }
                button.performClick()

                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    putExtra("android.content.extra.SHOW_ADVANCED", true)
                }

                requestDownloadFolderLauncher.launch(intent)
            }
            entries[null] = this
        }

        val downloadFolder = DownloadManager.getInstance(requireContext()).downloadFolder.canonicalPath
        val key = entries.keys.firstOrNull { it?.canonicalPath == downloadFolder }
        entries[key]!!.button.isChecked = true
        if (key == null) entries[key]!!.locationAvailable.text = downloadFolder
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DownloadLocationDialogBinding.inflate(layoutInflater)

        initView()

        return AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.settings_download_folder)
            setView(binding.root)
            setPositiveButton(requireContext().getText(android.R.string.ok)) { _, _ ->
                if (Preferences["download_folder", ""].isEmpty())
                    Preferences["download_folder"] = context.getExternalFilesDir(null)?.toUri()?.toString() ?: ""
            }

            isCancelable = false
        }.create()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}