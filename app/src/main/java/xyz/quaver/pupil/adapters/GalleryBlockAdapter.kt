package xyz.quaver.pupil.adapters

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import kotlinx.android.synthetic.main.item_galleryblock.view.*
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.toTag
import xyz.quaver.pupil.R

class GalleryBlockAdapter(private val galleries: List<Pair<GalleryBlock, Bitmap?>>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

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

    class ViewHolder(val view: CardView) : RecyclerView.ViewHolder(view)
    class ProgressViewHolder(view: LinearLayout) : RecyclerView.ViewHolder(view)

    private var callback: ((Int) -> Unit)? = null
    fun setClickListener(callback: ((Int) -> Unit)?) {
        this.callback = callback
    }

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

                val artists = gallery.artists.ifEmpty { listOf("N/A") }
                val series = gallery.series.ifEmpty { listOf("N/A") }

                setOnClickListener {
                    callback?.invoke(gallery.id)
                }

                galleryblock_thumbnail.setImageBitmap(thumbnail)
                galleryblock_title.text = gallery.title
                galleryblock_artist.text = artists.joinToString(", ") { it.wordCapitalize() }
                galleryblock_series.text =
                    resources.getString(R.string.galleryblock_series, series.joinToString(", ") { it.wordCapitalize() })
                galleryblock_type.text = resources.getString(R.string.galleryblock_type, gallery.type).wordCapitalize()
                galleryblock_language.text =
                    resources.getString(R.string.galleryblock_language, languages[gallery.language])

                galleryblock_tag_group.removeAllViews()
                gallery.relatedTags.forEach {
                    galleryblock_tag_group.addView(
                        Chip(context).apply {
                            text = it.toTag().wordCapitalize()
                        }
                    )
                }
            }
        }
    }

    override fun getItemCount() = if (galleries.isEmpty()) 0 else galleries.size+(if (noMore) 0 else 1)

    override fun getItemViewType(position: Int): Int {
        return when {
            galleries.getOrNull(position) == null -> ViewType.VIEW_PROG.ordinal
            else -> ViewType.VIEW_ITEM.ordinal
        }
    }
}