package xyz.quaver.pupil

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val suffix = listOf(
            "B",
            "kB",
            "MB",
            "GB",
            "TB" //really?
        )

        private fun getCacheSize() : String {
            var size = context!!.cacheDir.walk().map { it.length() }.sum()
            var suffixIndex = 0

            while (size >= 1024) {
                size /= 1024
                suffixIndex++
            }

            return getString(R.string.settings_delete_cache_summary, size, suffix[suffixIndex])
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            with(findPreference<Preference>("delete_cache")) {
                this ?: return@with

                summary = getCacheSize()

                setOnPreferenceClickListener {
                    AlertDialog.Builder(context).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_delete_cache_alert_message)
                        setPositiveButton(android.R.string.yes) { _, _ ->
                            with(context.cacheDir) {
                                if (exists())
                                    deleteRecursively()
                            }

                            summary = getCacheSize()
                        }
                        setNegativeButton(android.R.string.no) { _, _ -> }
                    }.show()

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
}