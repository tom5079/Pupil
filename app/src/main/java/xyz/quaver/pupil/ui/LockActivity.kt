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
import android.app.AlertDialog
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.andrognito.patternlockview.PatternLockView
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_lock.*
import kotlinx.android.synthetic.main.fragment_pattern_lock.*
import kotlinx.android.synthetic.main.fragment_pin_lock.*
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.fragment.PINLockFragment
import xyz.quaver.pupil.ui.fragment.PatternLockFragment
import xyz.quaver.pupil.util.Lock
import xyz.quaver.pupil.util.LockManager

class LockActivity : AppCompatActivity() {

    private lateinit var lockManager: LockManager
    private var mode: String? = null

    private val patternLockFragment = PatternLockFragment().apply {
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

    private val pinLockFragment = PINLockFragment().apply {
        var lastPass = ""
        onPINEntered = {
            when(mode) {
                null -> {
                    val result = lockManager.check(it)

                    if (result == true) {
                        setResult(Activity.RESULT_OK)
                        finish()
                    } else {
                        indicator_dots.startAnimation(AnimationUtils.loadAnimation(context, R.anim.shake).apply {
                            setAnimationListener(object: Animation.AnimationListener {
                                override fun onAnimationEnd(animation: Animation?) {
                                    pin_lock_view.resetPinLockView()
                                    pin_lock_view.isEnabled = true
                                }

                                override fun onAnimationStart(animation: Animation?) {
                                    pin_lock_view.isEnabled = false
                                }

                                override fun onAnimationRepeat(animation: Animation?) {
                                    // Do Nothing
                                }
                            })
                        })
                    }
                }
                "add_lock" -> {
                    if (lastPass.isEmpty()) {
                        lastPass = it

                        pin_lock_view.resetPinLockView()
                        Snackbar.make(view!!, R.string.settings_lock_confirm, Snackbar.LENGTH_LONG).show()
                    } else {
                        if (lastPass == it) {
                            LockManager(context!!).add(Lock.generate(Lock.Type.PIN, it))
                            finish()
                        } else {
                            indicator_dots.startAnimation(AnimationUtils.loadAnimation(context, R.anim.shake).apply {
                                setAnimationListener(object: Animation.AnimationListener {
                                    override fun onAnimationEnd(animation: Animation?) {
                                        pin_lock_view.resetPinLockView()
                                        pin_lock_view.isEnabled = true
                                    }

                                    override fun onAnimationStart(animation: Animation?) {
                                        pin_lock_view.isEnabled = false
                                    }

                                    override fun onAnimationRepeat(animation: Animation?) {
                                        // Do Nothing
                                    }
                                })
                            })
                            lastPass = ""

                            Snackbar.make(view!!, R.string.settings_lock_wrong_confirm, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun showBiometricPrompt() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getText(R.string.settings_lock_fingerprint_prompt))
            .setSubtitle(getText(R.string.settings_lock_fingerprint_prompt_subtitle))
            .setNegativeButtonText("Cancel")
            .setConfirmationRequired(false)
            .build()

        val biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    setResult(RESULT_OK)
                    finish()
                    return
                }
            })

        // Displays the "log in" prompt.
        biometricPrompt.authenticate(promptInfo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        lockManager = try {
            LockManager(this)
        } catch (e: Exception) {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.warning)
                setMessage(R.string.lock_corrupted)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    finish()
                }
            }.show()
            return
        }

        mode = intent.getStringExtra("mode")

        when(mode) {
            null -> {
                if (lockManager.isEmpty()) {
                    setResult(RESULT_OK)
                    finish()
                    return
                }

                if (
                    PreferenceManager.getDefaultSharedPreferences(this).getBoolean("lock_fingerprint", false)
                    && BiometricManager.from(this).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
                ) {
                    lock_fingerprint.apply {
                        isEnabled = true
                        setOnClickListener {
                            showBiometricPrompt()
                        }
                    }
                    showBiometricPrompt()
                }

                lock_pattern.apply {
                    isEnabled = lockManager.contains(Lock.Type.PATTERN)
                    setOnClickListener {
                        supportFragmentManager.beginTransaction().replace(
                            R.id.lock_content, patternLockFragment
                        ).commit()
                    }
                }
                lock_pin.apply {
                    isEnabled = lockManager.contains(Lock.Type.PIN)
                    setOnClickListener {
                        supportFragmentManager.beginTransaction().replace(
                            R.id.lock_content, pinLockFragment
                        ).commit()
                    }
                }
                lock_password.isEnabled = false

                when (lockManager.locks!!.first().type) {
                    Lock.Type.PIN -> {

                        supportFragmentManager.beginTransaction().add(
                            R.id.lock_content, pinLockFragment
                        ).commit()
                    }
                    Lock.Type.PATTERN -> {
                        supportFragmentManager.beginTransaction().add(
                            R.id.lock_content, patternLockFragment
                        ).commit()
                    }
                    else -> return
                }
            }
            "add_lock" -> {
                lock_pattern.isEnabled = false
                lock_pin.isEnabled = false
                lock_fingerprint.isEnabled = false
                lock_password.isEnabled = false

                when(intent.getStringExtra("type")!!) {
                    "pattern" -> {
                        lock_pattern.isEnabled = true
                        supportFragmentManager.beginTransaction().add(
                            R.id.lock_content, patternLockFragment
                        ).commit()
                    }
                    "pin" -> {
                        lock_pin.isEnabled = true
                        supportFragmentManager.beginTransaction().add(
                            R.id.lock_content, pinLockFragment
                        ).commit()
                    }
                }
            }
        }
    }

}
