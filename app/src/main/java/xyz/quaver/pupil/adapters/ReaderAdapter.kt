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
import android.graphics.DiscretePathEffect
import android.graphics.drawable.Animatable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.drawee.interfaces.DraweeController
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.image.ImageInfo
import com.github.piasy.biv.view.BigImageView
import com.github.piasy.biv.view.ImageShownCallback
import com.github.piasy.biv.view.ImageViewFactory
import kotlinx.android.synthetic.main.item_reader.view.*
import kotlinx.coroutines.*
import xyz.quaver.hitomi.Reader
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.ReaderActivity
import xyz.quaver.pupil.util.downloader.Cache
import java.io.File
import kotlin.math.roundToInt

class ReaderAdapter(
    private val activity: ReaderActivity,
    private val galleryID: Int
) : RecyclerView.Adapter<ReaderAdapter.ViewHolder>() {

    var reader: Reader? = null

    var isFullScreen = false

    var onItemClickListener : (() -> (Unit))? = null

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun clear() {
            view.image.ssiv?.recycle()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return LayoutInflater.from(parent.context).inflate(
            R.layout.item_reader, parent, false
        ).let {
            with(it) {
                image.setImageViewFactory(FrescoImageViewFactory().apply {
                    updateView = { imageInfo ->
                        it.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            dimensionRatio = "${imageInfo.width}:${imageInfo.height}"
                        }
                    }
                })
                image.setImageShownCallback(object : ImageShownCallback {
                    override fun onMainImageShown() {
                        it.image.mainView.let { v ->
                            when (v) {
                                is SubsamplingScaleImageView ->
                                    if (!isFullScreen) it.image.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                            }
                        }
                    }

                    override fun onThumbnailShown() {}
                })
                image.setFailureImage(ContextCompat.getDrawable(context, R.drawable.image_broken_variant))
                image.setOnClickListener {
                    this.performClick()
                }
                setOnClickListener {
                    onItemClickListener?.invoke()
                }
            }

            ViewHolder(it)
        }
    }

    private var cache: Cache? = null
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.view as ConstraintLayout

        if (cache == null)
            cache = Cache.getInstance(holder.view.context, galleryID)

        if (!isFullScreen) {
            holder.view.setBackgroundResource(R.drawable.reader_item_boundary)
            holder.view.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height = 0
                dimensionRatio =
                    "${reader!!.galleryInfo.files[position].width}:${reader!!.galleryInfo.files[position].height}"
            }
        } else {
            holder.view.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            holder.view.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height = ConstraintLayout.LayoutParams.MATCH_PARENT
                dimensionRatio = null
            }
            holder.view.background = null
        }

        holder.view.reader_index.text = (position+1).toString()

        val image = cache!!.getImage(position)
        val progress = activity.downloader?.progress?.get(galleryID)?.get(position)

        if (progress?.isInfinite() == true && image != null) {
            holder.view.progress_group.visibility = View.INVISIBLE
            holder.view.image.showImage(image.uri)
        } else {
            holder.view.progress_group.visibility = View.VISIBLE
            holder.view.reader_item_progressbar.progress =
                if (progress?.isInfinite() == true)
                    100
                else
                    progress?.roundToInt() ?: 0

            holder.clear()

            CoroutineScope(Dispatchers.Main).launch {
                delay(1000)
                notifyItemChanged(position)
            }
        }
    }

    override fun getItemCount() = reader?.galleryInfo?.files?.size ?: 0

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clear()
    }

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