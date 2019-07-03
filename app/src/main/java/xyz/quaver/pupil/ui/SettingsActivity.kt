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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.dialog_default_query.view.*
import xyz.quaver.pupil.Pupil
import xyz.quaver.pupil.R
import xyz.quaver.pupil.types.Tags
import xyz.quaver.pupil.util.Lock
import xyz.quaver.pupil.util.LockManager
import xyz.quaver.pupil.util.getDownloadDirectory
import java.io.File

class SettingsActivity : AppCompatActivity() {

    val REQUEST_LOCK = 38238

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
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        if (preferences.getBoolean("security_mode", false))
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE)
        else
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onResume()
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val suffix = listOf(
            "B",
            "kB",
            "MB",
            "GB",
            "TB" //really?
        )

        override fun onResume() {
            super.onResume()

            val lockManager = LockManager(context!!)

            findPreference<Preference>("app_lock")?.summary = if (lockManager.locks.isNullOrEmpty()) {
                getString(R.string.settings_lock_none)
            } else {
                lockManager.locks?.joinToString(", ") {
                    when(it.type) {
                        Lock.Type.PATTERN -> getString(R.string.settings_lock_pattern)
                        Lock.Type.PIN -> getString(R.string.settings_lock_pin)
                        Lock.Type.PASSWORD -> getString(R.string.settings_lock_password)
                    }
                }
            }
        }

        private fun getDirSize(dir: File) : String {
            var size = dir.walk().map { it.length() }.sum()
            var suffixIndex = 0

            while (size >= 1024) {
                size /= 1024
                suffixIndex++
            }

            return getString(R.string.settings_clear_summary, size, suffix[suffixIndex])
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            with(findPreference<Preference>("app_version")) {
                this!!

                val manager = context.packageManager
                val info = manager.getPackageInfo(context.packageName, 0)

                summary = info.versionName
            }

            with(findPreference<Preference>("delete_cache")) {
                this!!

                val dir = File(context.cacheDir, "imageCache")

                summary = getDirSize(dir)

                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    AlertDialog.Builder(context).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_clear_cache_alert_message)
                        setPositiveButton(android.R.string.yes) { _, _ ->
                            if (dir.exists())
                                dir.deleteRecursively()

                            summary = getDirSize(dir)
                        }
                        setNegativeButton(android.R.string.no) { _, _ -> }
                    }.show()

                    true
                }
            }

            with(findPreference<Preference>("delete_downloads")) {
                this!!

                val dir = getDownloadDirectory(context)!!

                summary = getDirSize(dir)

                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    AlertDialog.Builder(context).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_clear_downloads_alert_message)
                        setPositiveButton(android.R.string.yes) { _, _ ->
                            if (dir.exists())
                                dir.deleteRecursively()

                            val downloads = (activity!!.application as Pupil).downloads

                            downloads.clear()

                            summary = getDirSize(dir)
                        }
                        setNegativeButton(android.R.string.no) { _, _ -> }
                    }.show()

                    true
                }
            }
            with(findPreference<Preference>("clear_history")) {
                this!!

                val histories = (activity!!.application as Pupil).histories

                summary = getString(R.string.settings_clear_history_summary, histories.size)

                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    AlertDialog.Builder(context).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_clear_history_alert_message)
                        setPositiveButton(android.R.string.yes) { _, _ ->
                            histories.clear()
                            summary = getString(R.string.settings_clear_history_summary, histories.size)
                        }
                        setNegativeButton(android.R.string.no) { _, _ -> }
                    }.show()

                    true
                }
            }

            with(findPreference<Preference>("default_query")) {
                this!!

                val preferences = PreferenceManager.getDefaultSharedPreferences(context)

                summary = preferences.getString("default_query", "") ?: ""

                val languages = resources.getStringArray(R.array.languages).map {
                    it.split("|").let { split ->
                        Pair(split[0], split[1])
                    }
                }.toMap()
                val reverseLanguages = languages.entries.associate { (k, v) -> v to k }

                val excludeBL = "-male:yaoi"
                val excludeGuro = listOf("-female:guro", "-male:guro")

                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    val dialogView = LayoutInflater.from(context).inflate(
                        R.layout.dialog_default_query,
                        LinearLayout(context),
                        false
                    )

                    val tags = Tags.parse(
                        preferences.getString("default_query", "") ?: ""
                    )

                    summary = tags.toString()

                    with(dialogView.default_query_dialog_language_selector) {
                        adapter =
                            ArrayAdapter(
                                context,
                                android.R.layout.simple_spinner_dropdown_item,
                                arrayListOf(
                                    getString(R.string.default_query_dialog_language_selector_none)
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

                    with(dialogView.default_query_dialog_BL_checkbox) {
                        isChecked = tags.contains(excludeBL)
                        if (tags.contains(excludeBL))
                            tags.remove(excludeBL)
                    }

                    with(dialogView.default_query_dialog_guro_checkbox) {
                        isChecked = excludeGuro.all { tags.contains(it) }
                        if (excludeGuro.all { tags.contains(it) })
                            excludeGuro.forEach {
                                tags.remove(it)
                            }
                    }

                    with(dialogView.default_query_dialog_edittext) {
                        setText(tags.toString(), TextView.BufferType.EDITABLE)
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                            override fun afterTextChanged(s: Editable?) {
                                s ?: return

                                if (s.any { it.isUpperCase() })
                                    s.replace(0, s.length, s.toString().toLowerCase())
                            }
                        })
                    }

                    val dialog = AlertDialog.Builder(context!!).apply {
                        setView(dialogView)
                    }.create()

                    dialogView.default_query_dialog_ok.setOnClickListener {
                        val newTags = Tags.parse(dialogView.default_query_dialog_edittext.text.toString())

                        with(dialogView.default_query_dialog_language_selector) {
                            if (selectedItemPosition != 0)
                                newTags.add("language:${reverseLanguages[selectedItem]}")
                        }

                        if (dialogView.default_query_dialog_BL_checkbox.isChecked)
                            newTags.add(excludeBL)

                        if (dialogView.default_query_dialog_guro_checkbox.isChecked)
                            excludeGuro.forEach { tag ->
                                newTags.add(tag)
                            }

                        preferenceManager.sharedPreferences.edit().putString("default_query", newTags.toString()).apply()
                        summary = preferences.getString("default_query", "") ?: ""
                        tags.clear()
                        tags.addAll(newTags)
                        dialog.dismiss()
                    }

                    dialog.show()

                    true
                }
            }
            with(findPreference<Preference>("app_lock")) {
                this!!

                val lockManager = LockManager(context)

                summary = if (lockManager.locks.isNullOrEmpty()) {
                    getString(R.string.settings_lock_none)
                } else {
                    lockManager.locks?.joinToString(", ") {
                        when(it.type) {
                            Lock.Type.PATTERN -> getString(R.string.settings_lock_pattern)
                            Lock.Type.PIN -> getString(R.string.settings_lock_pin)
                            Lock.Type.PASSWORD -> getString(R.string.settings_lock_password)
                        }
                    }
                }

                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    val intent = Intent(context, LockActivity::class.java)
                    activity?.startActivityForResult(intent, (activity as SettingsActivity).REQUEST_LOCK)

                    true
                }
            }
        }
    }

    class LockFragment : PreferenceFragmentCompat() {

        override fun onResume() {
            super.onResume()

            val lockManager = LockManager(context!!)

            findPreference<Preference>("lock_pattern")?.summary =
                if (lockManager.contains(Lock.Type.PATTERN))
                    getString(R.string.settings_lock_enabled)
                else
                    ""
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.lock_preferences, rootKey)

            with(findPreference<Preference>("lock_pattern")) {
                this!!

                if (LockManager(context!!).contains(Lock.Type.PATTERN))
                    summary = getString(R.string.settings_lock_enabled)

                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    val lockManager = LockManager(context!!)

                    if (lockManager.contains(Lock.Type.PATTERN)) {
                        AlertDialog.Builder(context).apply {
                            setTitle(R.string.warning)
                            setMessage(R.string.settings_lock_remove_message)

                            setPositiveButton(android.R.string.yes) { _, _ ->
                                lockManager.remove(Lock.Type.PATTERN)
                                onResume()
                            }
                            setNegativeButton(android.R.string.no) { _, _ -> }
                        }.show()
                    } else {
                        val intent = Intent(context, LockActivity::class.java).apply {
                            putExtra("mode", "add_lock")
                            putExtra("type", "pattern")
                        }

                        startActivity(intent)
                    }

                    true
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> onBackPressed()
        }

        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            REQUEST_LOCK -> {
                if (resultCode == Activity.RESULT_OK) {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.settings, LockFragment())
                        .addToBackStack("Lock")
                        .commitAllowingStateLoss()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }
}