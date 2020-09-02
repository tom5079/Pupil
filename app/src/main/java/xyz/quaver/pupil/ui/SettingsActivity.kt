/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2019  tom5079
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

package xyz.quaver.pupil.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.settings_activity.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.rdrei.android.dirchooser.DirectoryChooserActivity
import xyz.quaver.io.FileX
import xyz.quaver.pupil.R
import xyz.quaver.pupil.favorites
import xyz.quaver.pupil.ui.fragment.LockSettingsFragment
import xyz.quaver.pupil.ui.fragment.SettingsFragment
import xyz.quaver.pupil.util.*
import java.io.File
import java.nio.charset.Charset

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        if (Preferences["security_mode"])
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE)
        else
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onResume()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> onBackPressed()
        }

        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            R.id.request_lock.normalizeID() -> {
                if (resultCode == Activity.RESULT_OK) {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.settings, LockSettingsFragment())
                        .addToBackStack("Lock")
                        .commitAllowingStateLoss()
                }
            }
            R.id.request_restore.normalizeID() -> {
                if (resultCode == Activity.RESULT_OK) {
                    val uri = data?.data ?: return

                    try {
                        val str = contentResolver.openInputStream(uri).use { inputStream ->
                            inputStream!!

                            inputStream.readBytes().toString(Charset.defaultCharset())
                        }

                        favorites.addAll(Json.decodeFromString<List<Int>>(str).also {
                            Snackbar.make(
                                window.decorView,
                                getString(R.string.settings_restore_success, it.size),
                                Snackbar.LENGTH_LONG
                            ).show()
                        })
                    } catch (e: Exception) {
                        Snackbar.make(
                            window.decorView,
                            R.string.settings_restore_failed,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    @SuppressLint("InlinedApi")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            R.id.request_write_permission_and_saf.normalizeID() -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        putExtra("android.content.extra.SHOW_ADVANCED", true)
                    }

                    startActivityForResult(intent, R.id.request_download_folder.normalizeID())
                }
            }
        }
    }
}