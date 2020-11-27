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
import com.andrognito.patternlockview.PatternLockView
import com.google.android.material.snackbar.Snackbar
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.LockActivityBinding
import xyz.quaver.pupil.ui.fragment.PINLockFragment
import xyz.quaver.pupil.ui.fragment.PatternLockFragment
import xyz.quaver.pupil.util.Lock
import xyz.quaver.pupil.util.LockManager
import xyz.quaver.pupil.util.Preferences

private var lastUnlocked = 0L
class LockActivity : AppCompatActivity() {

    private lateinit var lockManager: LockManager
    private var mode: String? = null

    private lateinit var binding: LockActivityBinding

    private val patternLockFragment = PatternLockFragment().apply {
        var lastPass = ""
        onPatternDrawn = {
            when(mode) {
                null -> {
                    val result = lockManager.check(it)

                    if (result == true) {
                        lastUnlocked = System.currentTimeMillis()
                        setResult(Activity.RESULT_OK)
                        finish()
                    } else
                        binding.patternLockView.setViewMode(PatternLockView.PatternViewMode.WRONG)
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
                            binding.patternLockView.setViewMode(PatternLockView.PatternViewMode.WRONG)
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
                        lastUnlocked = System.currentTimeMillis()
                        setResult(Activity.RESULT_OK)
                        finish()
                    } else {
                        binding.indicatorDots.startAnimation(AnimationUtils.loadAnimation(context, R.anim.shake).apply {
                            setAnimationListener(object: Animation.AnimationListener {
                                override fun onAnimationEnd(animation: Animation?) {
                                    binding.pinLockView.resetPinLockView()
                                    binding.pinLockView.isEnabled = true
                                }

                                override fun onAnimationStart(animation: Animation?) {
                                    binding.pinLockView.isEnabled = false
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

                        binding.pinLockView.resetPinLockView()
                        Snackbar.make(view!!, R.string.settings_lock_confirm, Snackbar.LENGTH_LONG).show()
                    } else {
                        if (lastPass == it) {
                            LockManager(context!!).add(Lock.generate(Lock.Type.PIN, it))
                            finish()
                        } else {
                            binding.indicatorDots.startAnimation(AnimationUtils.loadAnimation(context, R.anim.shake).apply {
                                setAnimationListener(object: Animation.AnimationListener {
                                    override fun onAnimationEnd(animation: Animation?) {
                                        binding.pinLockView.resetPinLockView()
                                        binding.pinLockView.isEnabled = true
                                    }

                                    override fun onAnimationStart(animation: Animation?) {
                                        binding.pinLockView.isEnabled = false
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
            .setNegativeButtonText(getText(android.R.string.cancel))
            .setConfirmationRequired(false)
            .build()

        val biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    lastUnlocked = System.currentTimeMillis()
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
        binding = LockActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        val force = intent.getBooleanExtra("force", false)

        when(mode) {
            null -> {
                if (lockManager.isEmpty()) {
                    setResult(RESULT_OK)
                    finish()
                    return
                }

                if (System.currentTimeMillis() - lastUnlocked < 5*60*1000 && !force) {
                    lastUnlocked = System.currentTimeMillis()
                    setResult(RESULT_OK)
                    finish()
                    return
                }

                if (
                    Preferences["lock_fingerprint"]
                    && BiometricManager.from(this).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
                ) {
                    binding.fingerprintBtn.apply {
                        isEnabled = true
                        setOnClickListener {
                            showBiometricPrompt()
                        }
                    }
                    showBiometricPrompt()
                }

                binding.patternBtn.apply {
                    isEnabled = lockManager.contains(Lock.Type.PATTERN)
                    setOnClickListener {
                        supportFragmentManager.beginTransaction().replace(
                            R.id.lock_content, patternLockFragment
                        ).commit()
                    }
                }
                binding.pinBtn.apply {
                    isEnabled = lockManager.contains(Lock.Type.PIN)
                    setOnClickListener {
                        supportFragmentManager.beginTransaction().replace(
                            R.id.lock_content, pinLockFragment
                        ).commit()
                    }
                }
                binding.passwordBtn.isEnabled = false

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
                binding.patternBtn.isEnabled = false
                binding.pinBtn.isEnabled = false
                binding.fingerprintBtn.isEnabled = false
                binding.passwordBtn.isEnabled = false

                when(intent.getStringExtra("type")!!) {
                    "pattern" -> {
                        binding.patternBtn.isEnabled = true
                        supportFragmentManager.beginTransaction().add(
                            R.id.lock_content, patternLockFragment
                        ).commit()
                    }
                    "pin" -> {
                        binding.pinBtn.isEnabled = true
                        supportFragmentManager.beginTransaction().add(
                            R.id.lock_content, pinLockFragment
                        ).commit()
                    }
                }
            }
        }
    }

}
