package xyz.quaver.pupil.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.ProgressCardViewBinding

class ProgressCard @JvmOverloads constructor(context: Context, attr: AttributeSet? = null, defStyle: Int = R.attr.cardViewStyle) : CardView(context, attr, defStyle) {

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
                DrawableCompat.setTint(binding.progressbar.progressDrawable, color)
            }
        }

    var progress: Int
        get() = binding.progressbar.progress
        set(value) {
            binding.progressbar.progress = value
        }
    var max: Int
        get() = binding.progressbar.max
        set(value) {
            binding.progressbar.max = value

            binding.progressbar.visibility =
                if (value == 0)
                    GONE
                else
                    VISIBLE
        }

    val binding = ProgressCardViewBinding.inflate(LayoutInflater.from(context), this)

    init {
        binding.content.setOnClickListener {
            performClick()
        }

        binding.content.setOnLongClickListener {
            performLongClick()
        }
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        if (childCount == 0)
            super.addView(child, index, params)
        else
            binding.content.addView(child, index, params)
    }

}