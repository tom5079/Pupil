package xyz.quaver.pupil.adapters

import android.graphics.BitmapFactory
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.quaver.pupil.R

class GalleryAdapter(private val images: List<String>) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(val view: ImageView) : RecyclerView.ViewHolder(view)

    private var onClick: (() -> Unit)? = null
    fun setOnClick(callback: (() -> Unit)?) {
        this.onClick = callback
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        LayoutInflater.from(parent.context).inflate(
            R.layout.item_gallery, parent, false
        ).let {
            return ViewHolder(it as ImageView)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder.view) {
            setOnClickListener {
                onClick?.invoke()
            }

            CoroutineScope(Dispatchers.Default).launch {
                val options = BitmapFactory.Options()

                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(images[position], options)

                options.inSampleSize = options.outWidth /
                        context.resources.displayMetrics.widthPixels

                options.inJustDecodeBounds = false

                val image = BitmapFactory.decodeFile(images[position], options)

                launch(Dispatchers.Main) {
                    setImageBitmap(image)
                }
            }
        }
    }

    override fun getItemCount() = images.size

}