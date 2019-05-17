package xyz.quaver.pupil

import android.content.Intent
import android.os.Process
import com.finotes.android.finotescore.Fn
import com.finotes.android.finotescore.ObservableApplication
import com.finotes.android.finotescore.Severity

class Pupil : ObservableApplication() {

    override fun onCreate() {
        super.onCreate()
        Fn.init(this)

        Fn.enableFrameDetection()

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Fn.reportException(t, Exception(e), Severity.FATAL)
        }
    }

}