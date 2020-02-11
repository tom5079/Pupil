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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.item_dl_location.view.*
import net.rdrei.android.dirchooser.DirectoryChooserActivity
import net.rdrei.android.dirchooser.DirectoryChooserConfig
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.REQUEST_DOWNLOAD_FOLDER
import xyz.quaver.pupil.util.REQUEST_DOWNLOAD_FOLDER_OLD
import xyz.quaver.pupil.util.byteToString
import xyz.quaver.pupil.util.getDownloadDirectory

@SuppressLint("InflateParams")
class DownloadLocationDialog(val activity: Activity) : AlertDialog(activity) {

    private val preference = PreferenceManager.getDefaultSharedPreferences(context)
    private val buttons = mutableListOf<Pair<RadioButton, Uri?>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val view = layoutInflater.inflate(R.layout.dialog_dl_location, null) as LinearLayout

        val externalFilesDirs = ContextCompat.getExternalFilesDirs(context, null)

        externalFilesDirs.forEachIndexed { index, dir ->

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
                    buttons.forEach { pair ->
                        pair.first.isChecked = false
                    }
                    button.performClick()
                    preference.edit().putString("dl_location", Uri.fromFile(dir).toString()).apply()
                }
                buttons.add(button to Uri.fromFile(dir))
            })
        }

        view.addView(layoutInflater.inflate(R.layout.item_dl_location, view, false).apply {
            location_type.text = context.getString(R.string.settings_dl_location_custom)
            setOnClickListener {
                buttons.forEach { pair ->
                    pair.first.isChecked = false
                }
                button.performClick()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        putExtra("android.content.extra.SHOW_ADVANCED", true)
                    }

                    activity.startActivityForResult(intent, REQUEST_DOWNLOAD_FOLDER)

                    dismiss()
                } else {    // Can't use SAF on old Androids!
                    val config = DirectoryChooserConfig.builder()
                        .newDirectoryName("Pupil")
                        .allowNewDirectoryNameModification(true)
                        .build()

                    val intent = Intent(context, DirectoryChooserActivity::class.java).apply {
                        putExtra(DirectoryChooserActivity.EXTRA_CONFIG, config)
                    }

                    activity.startActivityForResult(intent, REQUEST_DOWNLOAD_FOLDER_OLD)
                    dismiss()
                }
            }
            buttons.add(button to null)
        })

        val pref = getDownloadDirectory(context)
        val index = externalFilesDirs.indexOfFirst {
            Uri.fromFile(it).toString() == pref.uri.toString()
        }

        if (index < 0)
            buttons.last().first.isChecked = true
        else
            buttons[index].first.isChecked = true

        setTitle(R.string.settings_dl_location)

        setView(view)

        setButton(Dialog.BUTTON_POSITIVE, context.getText(android.R.string.ok)) { _, _ ->
            dismiss()
        }

        super.onCreate(savedInstanceState)
    }

}