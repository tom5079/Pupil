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
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.DefaultQueryDialogBinding
import xyz.quaver.pupil.sources.hitomi.Hitomi
import xyz.quaver.pupil.types.Tags
import xyz.quaver.pupil.util.Preferences

class DefaultQueryDialogFragment() : DialogFragment() {
    // TODO languageMap
    private val languages = Hitomi.languageMap
    private val reverseLanguages = languages.entries.associate { (k, v) -> v to k }

    private val excludeBL = "-male:yaoi"
    private val excludeGuro = listOf("-female:guro", "-male:guro")
    private val excludeLoli = listOf("-female:loli", "-male:shota")

    var onPositiveButtonClickListener : ((Tags) -> (Unit))? = null

    private var _binding: DefaultQueryDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DefaultQueryDialogBinding.inflate(layoutInflater)

        initView()

        return AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.default_query_dialog_title)
            setView(binding.root)
            setPositiveButton(android.R.string.ok) { _, _ ->
                val newTags = Tags.parse(binding.edittext.text.toString())

                with(binding.languageSelector) {
                    if (selectedItemPosition != 0)
                        newTags.add("language:${reverseLanguages[selectedItem]}")
                }

                if (binding.BLCheckbox.isChecked)
                    newTags.add(excludeBL)

                if (binding.guroCheckbox.isChecked)
                    excludeGuro.forEach { tag ->
                        newTags.add(tag)
                    }

                if (binding.loliCheckbox.isChecked)
                    excludeLoli.forEach { tag ->
                        newTags.add(tag)
                    }

                onPositiveButtonClickListener?.invoke(newTags)
            }
        }.create()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun initView() {
        val tags = Tags.parse(
            Preferences["default_query"]
        )

        with(binding.languageSelector) {
            adapter =
                ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_dropdown_item,
                    arrayListOf(
                        context.getString(R.string.default_query_dialog_language_selector_none)
                    ).apply {
                        addAll(languages.values)
                    }
                )
            if (tags.any { it.area == "language" && !it.isNegative }) {
                val tag = languages[tags.first { it.area == "language" }.tag]
                if (tag != null) {
                    setSelection(
                        @Suppress("UNCHECKED_CAST")
                        (adapter as ArrayAdapter<String>).getPosition(tag)
                    )
                    tags.removeByArea("language", false)
                }
            }
        }

        with(binding.BLCheckbox) {
            isChecked = tags.contains(excludeBL)
            if (tags.contains(excludeBL))
                tags.remove(excludeBL)
        }

        with(binding.guroCheckbox) {
            isChecked = excludeGuro.all { tags.contains(it) }
            if (excludeGuro.all { tags.contains(it) })
                excludeGuro.forEach {
                    tags.remove(it)
                }
        }

        with(binding.loliCheckbox) {
            isChecked = excludeLoli.all { tags.contains(it) }
            if (excludeLoli.all { tags.contains(it) })
                excludeLoli.forEach {
                    tags.remove(it)
                }
        }

        with(binding.edittext) {
            setText(tags.toString(), android.widget.TextView.BufferType.EDITABLE)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    s ?: return

                    if (s.any { it.isUpperCase() })
                        s.replace(
                            0,
                            s.length,
                            s.toString().toLowerCase(java.util.Locale.getDefault())
                        )
                }
            })
        }
    }

}