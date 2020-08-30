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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.item_dl_location.view.*
import net.rdrei.android.dirchooser.DirectoryChooserActivity
import net.rdrei.android.dirchooser.DirectoryChooserConfig
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.*
import java.io.File

@SuppressLint("InflateParams")
class DownloadLocationDialog(val activity: Activity) : AlertDialog(activity) {
    private val buttons = mutableListOf<Pair<RadioButton, File?>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTitle(R.string.settings_dl_location)

        setView(build())

        setButton(Dialog.BUTTON_POSITIVE, context.getText(android.R.string.ok)) { _, _ -> }

        super.onCreate(savedInstanceState)
    }

    private fun build() : View {
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
                    Preferences["dl_location"] = dir.canonicalPath
                }
                buttons.add(button to dir)
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

                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), R.id.request_write_permission_and_saf.normalizeID())
                    else {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            putExtra("android.content.extra.SHOW_ADVANCED", true)
                        }

                        activity.startActivityForResult(intent, R.id.request_download_folder.normalizeID())
                    }

                    dismiss()
                } else {    // Can't use SAF on old Androids!
                    val config = DirectoryChooserConfig.builder()
                        .newDirectoryName("Pupil")
                        .allowNewDirectoryNameModification(true)
                        .build()

                    val intent = Intent(context, DirectoryChooserActivity::class.java).apply {
                        putExtra(DirectoryChooserActivity.EXTRA_CONFIG, config)
                    }

                    activity.startActivityForResult(intent, R.id.request_download_folder_old.normalizeID())
                    dismiss()
                }
            }
            buttons.add(button to null)
        })

        externalFilesDirs.indexOfFirst {
            it.canonicalPath == getDownloadDirectory(context).canonicalPath
        }.let { index ->
            if (index < 0)
                buttons.first().first.isChecked = true
            else
                buttons[index].first.isChecked = true
        }

        return view
    }

}