package xyz.quaver.pupil

import android.os.Bundle
import android.preference.PreferenceManager
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
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.android.synthetic.main.dialog_default_query.view.*
import xyz.quaver.pupil.types.Tags
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
            with(findPreference<Preference>("delete_downloads")) {
                this ?: return@with

                val dir = File(ContextCompat.getDataDir(context), "images")

                summary = getCacheSize(dir)

                setOnPreferenceClickListener {
                    AlertDialog.Builder(context).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_clear_downloads_alert_message)
                        setPositiveButton(android.R.string.yes) { _, _ ->
                            if (dir.exists())
                                dir.deleteRecursively()

                            val downloads = (activity!!.application as Pupil).downloads

                            downloads.clear()

                            summary = getCacheSize(dir)
                        }
                        setNegativeButton(android.R.string.no) { _, _ -> }
                    }.show()

                    true
                }
            }
            with(findPreference<Preference>("clear_history")) {
                this ?: return@with

                val histories = (activity!!.application as Pupil).histories

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

            with(findPreference<Preference>("default_query")) {
                this ?: return@with

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

                setOnPreferenceClickListener {
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
                            ArrayAdapter<String>(
                                context,
                                android.R.layout.simple_spinner_dropdown_item,
                                arrayListOf(
                                    getString(R.string.default_query_dialog_language_selector_none)
                                ).apply {
                                    addAll(languages.values)
                                }
                            )
                        if (tags.any { it.area == "language" }) {
                            val tag = languages[tags.first { it.area == "language" }.tag]
                            if (tag != null) {
                                setSelection(
                                    @Suppress("UNCHECKED_CAST")
                                    (adapter as ArrayAdapter<String>).getPosition(tag)
                                )
                                tags.removeByArea("language")
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
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> onBackPressed()
        }

        return true
    }
}