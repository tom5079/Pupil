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

package xyz.quaver.pupil.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.LockActivity
import xyz.quaver.pupil.util.Lock
import xyz.quaver.pupil.util.LockManager

class LockSettingsFragment :
    PreferenceFragmentCompat() {

    override fun onResume() {
        super.onResume()

        val lockManager = LockManager(requireContext())

        findPreference<Preference>("lock_pattern")?.summary =
            if (lockManager.contains(Lock.Type.PATTERN))
                getString(R.string.settings_lock_enabled)
            else
                ""

        findPreference<Preference>("lock_pin")?.summary =
            if (lockManager.contains(Lock.Type.PIN))
                getString(R.string.settings_lock_enabled)
            else
                ""

        if (lockManager.isEmpty()) {
            (findPreference<Preference>("lock_fingerprint") as SwitchPreferenceCompat).isChecked = false

            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("lock_fingerprint", false).apply()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.lock_preferences, rootKey)

        with(findPreference<Preference>("lock_pattern")) {
            this!!

            if (LockManager(requireContext()).contains(Lock.Type.PATTERN))
                summary = getString(R.string.settings_lock_enabled)

            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val lockManager = LockManager(requireContext())

                if (lockManager.contains(Lock.Type.PATTERN)) {
                    AlertDialog.Builder(requireContext()).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_lock_remove_message)

                        setPositiveButton(android.R.string.yes) { _, _ ->
                            lockManager.remove(Lock.Type.PATTERN)
                            onResume()
                        }
                        setNegativeButton(android.R.string.no) { _, _ -> }
                    }.show()
                } else {
                    val intent = Intent(requireContext(), LockActivity::class.java).apply {
                        putExtra("mode", "add_lock")
                        putExtra("type", "pattern")
                    }

                    startActivity(intent)
                }

                true
            }
        }

        with(findPreference<Preference>("lock_pin")) {
            this!!

            if (LockManager(requireContext()).contains(Lock.Type.PIN))
                summary = getString(R.string.settings_lock_enabled)

            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val lockManager = LockManager(requireContext())

                if (lockManager.contains(Lock.Type.PIN)) {
                    AlertDialog.Builder(requireContext()).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_lock_remove_message)

                        setPositiveButton(android.R.string.yes) { _, _ ->
                            lockManager.remove(Lock.Type.PIN)
                            onResume()
                        }
                        setNegativeButton(android.R.string.no) { _, _ -> }
                    }.show()
                } else {
                    val intent = Intent(requireContext(), LockActivity::class.java).apply {
                        putExtra("mode", "add_lock")
                        putExtra("type", "pin")
                    }

                    startActivity(intent)
                }

                true
            }
        }

        with(findPreference<Preference>("lock_fingerprint")) {
            this!!

            setOnPreferenceChangeListener { _, newValue ->
                this as SwitchPreferenceCompat

                if (newValue == true && LockManager(requireContext()).isEmpty()) {
                    isChecked = false

                    Toast.makeText(requireContext(), R.string.settings_lock_fingerprint_without_lock, Toast.LENGTH_SHORT).show()
                } else
                    isChecked = newValue as Boolean

                false
            }
        }
    }
}