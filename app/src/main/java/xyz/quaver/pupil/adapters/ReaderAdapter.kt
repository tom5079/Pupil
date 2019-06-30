package xyz.quaver.pupil.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import xyz.quaver.pupil.R

class ReaderAdapter(private val images: List<String>) : RecyclerView.Adapter<ReaderAdapter.ViewHolder>() {

    var isFullScreen = false

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        LayoutInflater.from(parent.context).inflate(
            R.layout.item_reader, parent, false
        ).let {
            return ViewHolder(it)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val progressDrawable = CircularProgressDrawable(holder.view.context).apply {
            strokeWidth = 10f
            centerRadius = 100f
            start()
        }

        Glide.with(holder.view)
            .load(images[position])
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .placeholder(progressDrawable)
            .error(R.drawable.image_broken_variant)
            .into(holder.view as ImageView)
    }

    override fun getItemCount() = images.size

}