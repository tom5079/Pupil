package xyz.quaver.pupil.adapters

import android.graphics.BitmapFactory
import android.util.SparseArray
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.chip.Chip
import kotlinx.android.synthetic.main.item_galleryblock.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.list
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.ReaderItem
import xyz.quaver.pupil.R
import xyz.quaver.pupil.types.Tag
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule

class GalleryBlockAdapter(private val galleries: List<Pair<GalleryBlock, Deferred<String>>>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private enum class ViewType {
        VIEW_ITEM,
        VIEW_PROG
    }

    private fun String.wordCapitalize() : String {
        val result = ArrayList<String>()

        for (word in this.split(" "))
            result.add(word.capitalize())

        return result.joinToString(" ")
    }

    var noMore = false
    private val refreshTasks = SparseArray<TimerTask>()
    val completeFlag = SparseBooleanArray()

    val onChipClickedHandler = ArrayList<((Tag) -> Unit)>()

    class ViewHolder(val view: CardView, var galleryID: Int? = null) : RecyclerView.ViewHolder(view)
    class ProgressViewHolder(val view: LinearLayout) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        when(viewType) {
            ViewType.VIEW_ITEM.ordinal -> {
                val view = LayoutInflater.from(parent.context).inflate(
                    R.layout.item_galleryblock, parent, false
                ) as CardView

                return ViewHolder(view)
            }
            ViewType.VIEW_PROG.ordinal -> {
                val view = LayoutInflater.from(parent.context).inflate(
                    R.layout.item_progressbar, parent, false
                ) as LinearLayout

                return ProgressViewHolder(view)
            }
        }

        throw Exception("Unexpected ViewType")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolder) {
            with(holder.view) {
                val resources = context.resources
                val languages = resources.getStringArray(R.array.languages).map {
                    it.split("|").let { split ->
                        Pair(split[0], split[1])
                    }
                }.toMap()
                val (gallery, thumbnail) = galleries[position]

                holder.galleryID = gallery.id

                val artists = gallery.artists
                val series = gallery.series

                CoroutineScope(Dispatchers.Default).launch {
                    val cache = thumbnail.await()

                    if (!File(cache).exists())
                        return@launch

                    val bitmap = BitmapFactory.decodeFile(thumbnail.await())

                    CoroutineScope(Dispatchers.Main).launch {
                        galleryblock_thumbnail.setImageBitmap(bitmap)
                    }
                }

                //Check cache
                val readerCache = File(context.cacheDir, "imageCache/${gallery.id}/reader.json")
                val imageCache = File(context.cacheDir, "imageCache/${gallery.id}/images")

                if (readerCache.exists()) {
                    val reader = Json(JsonConfiguration.Stable)
                        .parse(ReaderItem.serializer().list, readerCache.readText())

                    with(galleryblock_progressbar) {
                        max = reader.size
                        progress = imageCache.list()?.size ?: 0

                        visibility = View.VISIBLE
                    }
                } else {
                    galleryblock_progressbar.visibility = View.GONE
                }

                if (refreshTasks.get(gallery.id) == null) {
                    val refresh = Timer(false).schedule(0, 1000) {
                        post {
                            with(galleryblock_progressbar) {
                                progress = imageCache.list()?.size ?: 0

                                if (!readerCache.exists()) {
                                    visibility = View.GONE
                                    max = 0
                                    progress = 0

                                    holder.view.galleryblock_progress_complete.visibility = View.INVISIBLE
                                } else {
                                    if (visibility == View.GONE) {
                                        val reader = Json(JsonConfiguration.Stable)
                                            .parse(ReaderItem.serializer().list, readerCache.readText())
                                        max = reader.size
                                        visibility = View.VISIBLE
                                    }

                                    if (progress == max) {
                                        if (completeFlag.get(gallery.id, false)) {
                                            with(holder.view.galleryblock_progress_complete) {
                                                setImageResource(R.drawable.ic_progressbar)
                                                visibility = View.VISIBLE
                                            }
                                        } else {
                                            val drawable = AnimatedVectorDrawableCompat.create(context, R.drawable.ic_progressbar_complete)
                                            with(holder.view.galleryblock_progress_complete) {
                                                setImageDrawable(drawable)
                                                visibility = View.VISIBLE
                                            }
                                            drawable?.start()
                                            completeFlag.put(gallery.id, true)
                                        }
                                    } else {
                                        with(holder.view.galleryblock_progress_complete) {
                                            visibility = View.INVISIBLE
                                        }
                                    }

                                    null
                                }
                            }
                        }
                    }

                    refreshTasks.put(gallery.id, refresh)
                }

                galleryblock_title.text = gallery.title
                with(galleryblock_artist) {
                    text = artists.joinToString(", ") { it.wordCapitalize() }
                    visibility = when {
                        artists.isNotEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                }
                with(galleryblock_series) {
                    text =
                        resources.getString(
                            R.string.galleryblock_series,
                            series.joinToString(", ") { it.wordCapitalize() })
                    visibility = when {
                        series.isNotEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                }
                galleryblock_type.text = resources.getString(R.string.galleryblock_type, gallery.type).wordCapitalize()
                with(galleryblock_language) {
                    text =
                        resources.getString(R.string.galleryblock_language, languages[gallery.language])
                    visibility = when {
                        gallery.language.isNotEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                }

                galleryblock_tag_group.removeAllViews()
                gallery.relatedTags.forEach {
                    val tag = Tag.parse(it)

                    val chip = LayoutInflater.from(context)
                            .inflate(R.layout.tag_chip, holder.view, false) as Chip

                    val icon = when(tag.area) {
                        "male" -> {
                            chip.setChipBackgroundColorResource(R.color.material_blue_700)
                            chip.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                            ContextCompat.getDrawable(context, R.drawable.ic_gender_male_white)
                        }
                        "female" -> {
                            chip.setChipBackgroundColorResource(R.color.material_pink_600)
                            chip.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                            ContextCompat.getDrawable(context, R.drawable.ic_gender_female_white)
                        }
                        else -> null
                    }

                    chip.chipIcon = icon
                    chip.text = tag.tag.wordCapitalize()
                    chip.setOnClickListener {
                        for (callback in onChipClickedHandler)
                            callback.invoke(tag)
                    }

                    galleryblock_tag_group.addView(chip)
                }
            }
        }
        if (holder is ProgressViewHolder) {
            holder.view.visibility = when(noMore) {
                true -> View.GONE
                false -> View.VISIBLE
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)

        if (holder is ViewHolder) {
            val galleryID = holder.galleryID ?: return
            val task = refreshTasks.get(galleryID) ?: return

            refreshTasks.remove(galleryID)
            task.cancel()
        }
    }

    override fun getItemCount() = if (galleries.isEmpty()) 0 else galleries.size+1

    override fun getItemViewType(position: Int): Int {
        return when {
            galleries.getOrNull(position) == null -> ViewType.VIEW_PROG.ordinal
            else -> ViewType.VIEW_ITEM.ordinal
        }
    }
}