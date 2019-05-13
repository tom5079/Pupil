package xyz.quaver.pupil

import android.os.Bundle
import android.view.MenuItem
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import xyz.quaver.pupil.util.Histories
import java.io.File

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

    class SettingsFragment : PreferenceFragmentCompat() {

        private val suffix = listOf(
            "B",
            "kB",
            "MB",
            "GB",
            "TB" //really?
        )

        private fun getCacheSize(dir: File) : String {
            var size = dir.walk().map { it.length() }.sum()
            var suffixIndex = 0

            while (size >= 1024) {
                size /= 1024
                suffixIndex++
            }

            return getString(R.string.settings_clear_cache_summary, size, suffix[suffixIndex])
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            with(findPreference<Preference>("delete_image_cache")) {
                this ?: return@with

                val dir = File(context.cacheDir, "imageCache")

                summary = getCacheSize(dir)

                setOnPreferenceClickListener {
                    AlertDialog.Builder(context).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_clear_cache_alert_message)
                        setPositiveButton(android.R.string.yes) { _, _ ->
                            if (dir.exists())
                                dir.deleteRecursively()

                            summary = getCacheSize(dir)
                        }
                        setNegativeButton(android.R.string.no) { _, _ -> }
                    }.show()

                    true
                }
            }
            with(findPreference<Preference>("clear_history")) {
                this ?: return@with

                val histories = Histories.default

                summary = getString(R.string.settings_clear_history_summary, histories.size)

                setOnPreferenceClickListener {
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
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> onBackPressed()
        }

        return true
    }
}