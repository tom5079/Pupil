package xyz.quaver.pupil.adapters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
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

        fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            // Raw height and width of image
            val (height: Int, width: Int) = options.run { outHeight to outWidth }
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {

                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            return inSampleSize
        }

        with(holder.view as ImageView) {
            val options = BitmapFactory.Options()

            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(images[position], options)

            val (reqWidth, reqHeight) = context.resources.displayMetrics.let {
                Pair(it.widthPixels, it.heightPixels)
            }

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            options.inPreferredConfig = Bitmap.Config.RGB_565

            options.inJustDecodeBounds = false

            val image = BitmapFactory.decodeFile(images[position], options)

            Log.d("Pupil", image.byteCount.toString())
            Log.d("Pupil", "${image.width}x${image.height}")
            Log.d("Pupil", "deviceWidth ${context.resources.displayMetrics.widthPixels}")

            setImageBitmap(image)
        }
    }

    override fun getItemCount() = images.size

}