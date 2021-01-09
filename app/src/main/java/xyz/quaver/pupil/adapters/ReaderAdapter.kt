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

package xyz.quaver.pupil.adapters

import android.content.Context
import android.graphics.drawable.Animatable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.drawee.interfaces.DraweeController
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.image.ImageInfo
import com.github.piasy.biv.loader.ImageLoader
import com.github.piasy.biv.view.BigImageView
import com.github.piasy.biv.view.ImageShownCallback
import com.github.piasy.biv.view.ImageViewFactory
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.ReaderItemBinding
import java.io.File
import java.lang.Exception
import kotlin.math.roundToInt

data class ReaderItem(
    val progress: Float,
    val image: File?
)

class ReaderAdapter : ListAdapter<ReaderItem, ReaderAdapter.ViewHolder>(ReaderItemDiffCallback()) {
    var onItemClickListener : (() -> (Unit))? = null
    var fullscreen = false

    inner class ViewHolder(private val binding: ReaderItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            with (binding.image) {
                setImageViewFactory(FrescoImageViewFactory().apply {
                    updateView = { imageInfo ->
                        layoutParams.height = imageInfo.height
                        (mainView as? SimpleDraweeView)?.aspectRatio = imageInfo.width / imageInfo.height.toFloat()
                    }
                })
                setImageShownCallback(object: ImageShownCallback {
                    override fun onMainImageShown() {
                        binding.progressGroup.visibility = View.INVISIBLE

                        binding.root.layoutParams.height = if (fullscreen)
                            MATCH_PARENT
                        else
                            WRAP_CONTENT
                    }

                    override fun onThumbnailShown() {}
                })

                setFailureImage(ContextCompat.getDrawable(itemView.context, R.drawable.image_broken_variant))
                setOnClickListener {
                    onItemClickListener?.invoke()
                }
            }

            binding.root.setOnClickListener {
                onItemClickListener?.invoke()
            }

            binding.readerItemProgressbar.max = 100
        }

        fun bind(position: Int) {
            recycle()

            binding.root.layoutParams.height = MATCH_PARENT

            binding.readerIndex.text = (position+1).toString()

            val (progress, image) = getItem(position)

            binding.progressGroup.visibility = View.VISIBLE

            if (image != null) {
                binding.root.background = null
                binding.image.showImage(Uri.fromFile(image))
            } else {
                binding.root.setBackgroundResource(R.drawable.reader_item_boundary)

                if (progress == Float.NEGATIVE_INFINITY)
                    binding.image.showImage(Uri.EMPTY)
                else
                    binding.readerItemProgressbar.progress = progress.roundToInt()
            }
        }

        fun recycle() {
            binding.image.mainView.run {
                when (this) {
                    is SubsamplingScaleImageView -> recycle()
                    is SimpleDraweeView -> recycle()
                    is ImageView -> setImageBitmap(null)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ReaderItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

}

class ReaderItemDiffCallback : DiffUtil.ItemCallback<ReaderItem>() {
    override fun areItemsTheSame(oldItem: ReaderItem, newItem: ReaderItem) =
        true

    override fun areContentsTheSame(oldItem: ReaderItem, newItem: ReaderItem) =
        oldItem == newItem
}

class FrescoImageViewFactory : ImageViewFactory() {
    var updateView: ((ImageInfo) -> Unit)? = null

    override fun createAnimatedImageView(
        context: Context, imageType: Int,
        initScaleType: Int
    ): View {
        val view = SimpleDraweeView(context)
        view.hierarchy.actualImageScaleType = scaleType(initScaleType)
        return view
    }

    override fun loadAnimatedContent(
        view: View, imageType: Int,
        imageFile: File
    ) {
        if (view is SimpleDraweeView) {
            val controller: DraweeController = Fresco.newDraweeControllerBuilder()
                .setOldController(view.controller)
                .setUri(Uri.parse("file://" + imageFile.absolutePath))
                .setAutoPlayAnimations(true)
                .setControllerListener(object: BaseControllerListener<ImageInfo>() {
                    override fun onIntermediateImageSet(id: String?, imageInfo: ImageInfo?) {
                        imageInfo?.let { updateView?.invoke(it) }
                    }

                    override fun onFinalImageSet(id: String?, imageInfo: ImageInfo?, animatable: Animatable?) {
                        imageInfo?.let { updateView?.invoke(it) }
                    }
                })
                .build()
            view.controller = controller
        }
    }

    override fun createThumbnailView(
        context: Context,
        scaleType: ImageView.ScaleType, willLoadFromNetwork: Boolean
    ): View {
        return if (willLoadFromNetwork) {
            val thumbnailView = SimpleDraweeView(context)
            thumbnailView.hierarchy.actualImageScaleType = scaleType(scaleType)
            thumbnailView
        } else {
            super.createThumbnailView(context, scaleType, false)
        }
    }

    override fun loadThumbnailContent(view: View, thumbnail: Uri) {
        if (view is SimpleDraweeView) {
            val controller: DraweeController = Fresco.newDraweeControllerBuilder()
                .setOldController(view.controller)
                .setUri(thumbnail)
                .build()
            view.controller = controller
        }
    }

    private fun scaleType(value: Int): ScalingUtils.ScaleType {
        return when (value) {
            BigImageView.INIT_SCALE_TYPE_CENTER -> ScalingUtils.ScaleType.CENTER
            BigImageView.INIT_SCALE_TYPE_CENTER_CROP -> ScalingUtils.ScaleType.CENTER_CROP
            BigImageView.INIT_SCALE_TYPE_CENTER_INSIDE -> ScalingUtils.ScaleType.CENTER_INSIDE
            BigImageView.INIT_SCALE_TYPE_FIT_END -> ScalingUtils.ScaleType.FIT_END
            BigImageView.INIT_SCALE_TYPE_FIT_START -> ScalingUtils.ScaleType.FIT_START
            BigImageView.INIT_SCALE_TYPE_FIT_XY -> ScalingUtils.ScaleType.FIT_XY
            BigImageView.INIT_SCALE_TYPE_FIT_CENTER -> ScalingUtils.ScaleType.FIT_CENTER
            else -> ScalingUtils.ScaleType.FIT_CENTER
        }
    }

    private fun scaleType(scaleType: ImageView.ScaleType): ScalingUtils.ScaleType {
        return when (scaleType) {
            ImageView.ScaleType.CENTER -> ScalingUtils.ScaleType.CENTER
            ImageView.ScaleType.CENTER_CROP -> ScalingUtils.ScaleType.CENTER_CROP
            ImageView.ScaleType.CENTER_INSIDE -> ScalingUtils.ScaleType.CENTER_INSIDE
            ImageView.ScaleType.FIT_END -> ScalingUtils.ScaleType.FIT_END
            ImageView.ScaleType.FIT_START -> ScalingUtils.ScaleType.FIT_START
            ImageView.ScaleType.FIT_XY -> ScalingUtils.ScaleType.FIT_XY
            ImageView.ScaleType.FIT_CENTER -> ScalingUtils.ScaleType.FIT_CENTER
            else -> ScalingUtils.ScaleType.FIT_CENTER
        }
    }
}