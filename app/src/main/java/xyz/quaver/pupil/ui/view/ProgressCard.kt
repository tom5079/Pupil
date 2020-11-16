package xyz.quaver.pupil.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import kotlinx.android.synthetic.main.view_progress_card.view.*
import xyz.quaver.pupil.R

class ProgressCard @JvmOverloads constructor(context: Context, attr: AttributeSet? = null, defStyle: Int = R.attr.cardViewStyle) : ConstraintLayout(context, attr, defStyle) {

    enum class Type {
        LOADING,
        CACHE,
        DOWNLOAD
    }

    var type: Type = Type.LOADING
        set(value) {
            field = value

            when (field) {
                Type.LOADING -> R.color.colorAccent
                Type.CACHE -> R.color.material_blue_700
                Type.DOWNLOAD -> R.color.material_green_a700
            }.let {
                val color = ContextCompat.getColor(context, it)
                DrawableCompat.setTint(progressbar.progressDrawable, color)
            }
        }

    var progress: Int
        get() = progressbar?.progress ?: 0
        set(value) {
            progressbar?.progress = value
        }
    var max: Int
        get() = progressbar?.max ?: 0
        set(value) {
            progressbar?.max = value

            progressbar.visibility =
                if (value == 0)
                    GONE
                else
                    VISIBLE
        }

    init {
        inflate(context, R.layout.view_progress_card, this)

        content.setOnClickListener {
            performClick()
        }

        content.setOnLongClickListener {
            performLongClick()
        }
    }

    override fun addView(child: View?, params: ViewGroup.LayoutParams?) =
        if (childCount == 0)
            super.addView(child, params)
        else
            content.addView(child, params)

}