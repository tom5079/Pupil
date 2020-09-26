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
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.dialog_download_folder_name.view.*
import kotlinx.coroutines.runBlocking
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.formatDownloadFolder
import xyz.quaver.pupil.util.formatDownloadFolderTest
import xyz.quaver.pupil.util.formatMap

class DownloadFolderNameDialogFragment : DialogFragment() {

    @SuppressLint("InflateParams")
    private fun build(): View {
        val galleryID = Cache.instances.let { if (it.size == 0) 1199708 else it.keys.elementAt((0 until it.size).random()) }
        val galleryBlock = runBlocking {
            Cache.getInstance(requireContext(), galleryID).getGalleryBlock()
        }

        return layoutInflater.inflate(R.layout.dialog_download_folder_name, null).apply {
            message.text = getString(R.string.settings_download_folder_name_message, formatMap.keys.toString(), galleryBlock?.formatDownloadFolder() ?: "")
            edittext.setText(Preferences["download_folder_name", "[-id-] -title-"])
            edittext.addTextChangedListener {
                message.text = getString(R.string.settings_download_folder_name_message, formatMap.keys.toString(), galleryBlock?.formatDownloadFolderTest(it.toString()) ?: "")
            }
            ok_button.setOnClickListener {
                val newValue = edittext.text.toString()

                if ((newValue as? String)?.contains("/") != false) {
                    Snackbar.make(this, R.string.settings_invalid_download_folder_name, Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                Preferences["download_folder_name"] = edittext.text.toString()

                dismiss()
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
       Dialog(requireContext()).apply {
           setContentView(build())
           window?.attributes?.width = ViewGroup.LayoutParams.MATCH_PARENT
       }

}