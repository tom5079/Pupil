package xyz.quaver.pupil.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.andrognito.patternlockview.PatternLockView
import com.andrognito.patternlockview.listener.PatternLockViewListener
import com.andrognito.patternlockview.utils.PatternLockUtils
import kotlinx.android.synthetic.main.fragment_pattern_lock.*
import kotlinx.android.synthetic.main.fragment_pattern_lock.view.*
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.hash
import xyz.quaver.pupil.util.hashWithSalt

class PatternLockFragment : Fragment(), PatternLockViewListener {

    var onPatternDrawn: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pattern_lock, container, false).apply {
            lock_pattern_view.addPatternLockListener(this@PatternLockFragment)
        }
    }

    override fun onCleared() {

    }

    override fun onComplete(pattern: MutableList<PatternLockView.Dot>?) {
        val password = PatternLockUtils.patternToMD5(lock_pattern_view, pattern)
        onPatternDrawn?.invoke(password)
    }

    override fun onProgress(progressPattern: MutableList<PatternLockView.Dot>?) {

    }

    override fun onStarted() {

    }

}
