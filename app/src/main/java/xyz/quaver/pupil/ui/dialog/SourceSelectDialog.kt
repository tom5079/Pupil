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
import android.view.ViewGroup.LayoutParams
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.kodein.di.*
import org.kodein.di.android.x.di
import org.kodein.type.jvmType
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.pupil.adapters.SourceAdapter
import xyz.quaver.pupil.sources.AnySource
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.sources.SourceEntries

class SourceSelectDialog : DialogFragment(), DIAware {

    override val di by di()

    var onSourceSelectedListener: ((String) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext()).apply {
            window?.requestFeature(Window.FEATURE_NO_TITLE)
            window?.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

            setContentView(RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = SourceAdapter(direct.instance()).apply {
                    onSourceSelectedListener = this@SourceSelectDialog.onSourceSelectedListener
                }
            })
        }
    }

}