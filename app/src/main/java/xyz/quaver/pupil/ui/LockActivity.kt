package xyz.quaver.pupil.ui

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.andrognito.patternlockview.PatternLockView
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_lock.*
import kotlinx.android.synthetic.main.fragment_pattern_lock.*
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.Lock
import xyz.quaver.pupil.util.LockManager

class LockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        val lockManager = LockManager(this)

        val mode = intent.getStringExtra("mode")

        lock_pattern.isEnabled = false
        lock_pin.isEnabled = false
        lock_fingerprint.isEnabled = false
        lock_password.isEnabled = false

        when(mode) {
            null -> {
                if (lockManager.empty()) {
                    setResult(RESULT_OK)
                    finish()
                }
            }
            "add_lock" -> {
                when(intent.getStringExtra("type")!!) {
                    "pattern" -> {

                    }
                }
            }
        }

        supportFragmentManager.beginTransaction().add(
            R.id.lock_content,
            PatternLockFragment().apply {
                var lastPass = ""
                onPatternDrawn = {
                    when(mode) {
                        null -> {
                            val result = lockManager.check(it)

                            if (result == true) {
                                setResult(Activity.RESULT_OK)
                                finish()
                            } else
                                lock_pattern_view.setViewMode(PatternLockView.PatternViewMode.WRONG)
                        }
                        "add_lock" -> {
                            if (lastPass.isEmpty()) {
                                lastPass = it

                                Snackbar.make(view!!, R.string.settings_lock_confirm, Snackbar.LENGTH_LONG).show()
                            } else {
                                if (lastPass == it) {
                                    LockManager(context!!).add(Lock.generate(Lock.Type.PATTERN, it))
                                    finish()
                                } else {
                                    lock_pattern_view.setViewMode(PatternLockView.PatternViewMode.WRONG)
                                    lastPass = ""

                                    Snackbar.make(view!!, R.string.settings_lock_wrong_confirm, Snackbar.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            }
        ).commit()
    }

}
