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

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.runBlocking
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.DownloadFolderNameDialogBinding
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.formatDownloadFolder
import xyz.quaver.pupil.util.formatDownloadFolderTest
import xyz.quaver.pupil.util.formatMap

class DownloadFolderNameDialogFragment : DialogFragment() {

    private var _binding: DownloadFolderNameDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DownloadFolderNameDialogBinding.inflate(layoutInflater)

        initView()

        return Dialog(requireContext()).apply {
            setContentView(binding.root)
            window?.attributes?.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun initView() {
        val galleryID = Cache.instances.let { if (it.size == 0) "1199708" else it.keys.elementAt((0 until it.size).random()) }
        val galleryBlock = runBlocking {
            Cache.getInstance(requireContext(), galleryID).getGalleryBlock()
        }

        binding.message.text = getString(R.string.settings_download_folder_name_message, formatMap.keys.toString(), galleryBlock?.formatDownloadFolder() ?: "")
        binding.edittext.setText(Preferences["download_folder_name", "[-id-] -title-"])
        binding.edittext.addTextChangedListener {
            binding.message.text = requireContext().getString(R.string.settings_download_folder_name_message, formatMap.keys.toString(), galleryBlock?.formatDownloadFolderTest(it.toString()) ?: "")
        }
        binding.okButton.setOnClickListener {
            val newValue = binding.edittext.text.toString()

            if ((newValue as? String)?.contains("/") != false) {
                Snackbar.make(binding.root, R.string.settings_invalid_download_folder_name, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Preferences["download_folder_name"] = binding.edittext.text.toString()

            dismiss()
        }
    }

}