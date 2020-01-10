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
import android.content.Context
import android.widget.LinearLayout
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.item_dl_location.view.*
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.byteToString

@SuppressLint("InflateParams")
class DownloadLocationDialog(context: Context) : AlertDialog(context) {

    private val preference = PreferenceManager.getDefaultSharedPreferences(context)
    private val buttons = mutableListOf<RadioButton>()
    var onDownloadLocationChangedListener : ((Int) -> (Unit))? = null

    init {
        val view = layoutInflater.inflate(R.layout.dialog_dl_location, null) as LinearLayout

        ContextCompat.getExternalFilesDirs(context, null).forEachIndexed { index, dir ->

            dir ?: return@forEachIndexed

            view.addView(layoutInflater.inflate(R.layout.item_dl_location, view, false).apply {
                location_type.text = context.getString(when (index) {
                    0 -> R.string.settings_dl_location_internal
                    else -> R.string.settings_dl_location_removable
                })
                location_available.text = context.getString(
                    R.string.settings_dl_location_available,
                    byteToString(dir.freeSpace)
                )
                setOnClickListener {
                    buttons.forEach { button ->
                        button.isChecked = false
                    }
                    button.performClick()
                    onDownloadLocationChangedListener?.invoke(index)
                }
                buttons.add(button)
            })
        }

        buttons[preference.getInt("dl_location", 0)].isChecked = true

        setTitle(R.string.settings_dl_location)

        setView(view)

        setButton(Dialog.BUTTON_POSITIVE, context.getText(android.R.string.ok)) { _, _ ->
            dismiss()
        }
    }

}