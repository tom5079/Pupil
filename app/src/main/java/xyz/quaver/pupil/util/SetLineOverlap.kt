package xyz.quaver.pupil.util

import android.graphics.Paint
import android.text.style.LineHeightSpan

class SetLineOverlap(private val overlap: Boolean) : LineHeightSpan {
    companion object {
        private var originalBottom = 15
        private var originalDescent = 13
        private var overlapSaved = false
    }

    override fun chooseHeight(
        text: CharSequence?,
        start: Int,
        end: Int,
        spanstartv: Int,
        lineHeight: Int,
        fm: Paint.FontMetricsInt?
    ) {
        fm ?: return

        if (overlap) {
            if (overlapSaved) {
                originalBottom = fm.bottom
                originalDescent = fm.descent
                overlapSaved = true
            }
            fm.bottom += fm.top
            fm.descent += fm.top
        } else {
            fm.bottom = originalBottom
            fm.descent = originalDescent
            overlapSaved = false
        }
    }
}