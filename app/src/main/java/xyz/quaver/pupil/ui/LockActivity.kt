package xyz.quaver.pupil.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_lock.*
import xyz.quaver.pupil.R

class LockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        supportFragmentManager.beginTransaction().add(
            R.id.lock_content,
            PatternLockFragment().apply {
                onPatternDrawn = {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
            }
        ).commit()

        lock_pattern.isEnabled = false
        lock_fingerprint.isEnabled = false
        lock_password.isEnabled = false
    }

}
