package xyz.quaver.pupil.adapters

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import kotlinx.android.synthetic.main.item_galleryblock.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.pupil.R
import xyz.quaver.pupil.types.Tag

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

    class ViewHolder(val view: CardView) : RecyclerView.ViewHolder(view)
    class ProgressViewHolder(view: LinearLayout) : RecyclerView.ViewHolder(view)

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

                val artists = gallery.artists
                val series = gallery.series

                CoroutineScope(Dispatchers.Default).launch {
                    val bitmap = BitmapFactory.decodeFile(thumbnail.await())

                    CoroutineScope(Dispatchers.Main).launch {
                        galleryblock_thumbnail.setImageBitmap(bitmap)
                    }
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
                    val chip = LayoutInflater
                            .from(context)
                            .inflate(R.layout.tag_chip, holder.view, false) as Chip

                    val icon = when(tag.area) {
                        "male" -> {
                            chip.setChipBackgroundColorResource(R.color.material_blue_100)
                            chip.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                            ContextCompat.getDrawable(context, R.drawable.ic_gender_male_white)
                        }
                        "female" -> {
                            chip.setChipBackgroundColorResource(R.color.material_pink_100)
                            chip.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                            ContextCompat.getDrawable(context, R.drawable.ic_gender_female_white)
                        }
                        else -> null
                    }

                    chip.chipIcon = icon
                    chip.text = Tag.parse(it).tag.wordCapitalize()

                    galleryblock_tag_group.addView(chip)
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