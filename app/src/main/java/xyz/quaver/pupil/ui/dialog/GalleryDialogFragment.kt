/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
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

package xyz.quaver.pupil.ui.dialog

import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout.LayoutParams
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.forEach
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.imagepipeline.image.ImageInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kodein.di.DIAware
import org.kodein.di.android.x.di
import org.kodein.di.instance
import xyz.quaver.pupil.R
import xyz.quaver.pupil.adapters.SearchResultsAdapter
import xyz.quaver.pupil.adapters.ThumbnailPageAdapter
import xyz.quaver.pupil.databinding.*
import xyz.quaver.pupil.sources.ItemInfo
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.ui.ReaderActivity
import xyz.quaver.pupil.ui.view.TagChip
import xyz.quaver.pupil.ui.viewmodel.GalleryDialogViewModel
import xyz.quaver.pupil.util.ItemClickSupport
import xyz.quaver.pupil.util.SavedSourceSet
import xyz.quaver.pupil.util.wordCapitalize
import java.util.*
import kotlin.collections.ArrayList

class GalleryDialogFragment(private val source: String, private val itemID: String) : DialogFragment(), DIAware {

    override val di by di()

    private val favoriteTags: SavedSourceSet by instance(tag = "favoriteTags")

    val onChipClickedHandler = ArrayList<((Tag) -> (Unit))>()

    private var _binding: GalleryDialogBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GalleryDialogViewModel by viewModels()

    private val controllerListener = object: BaseControllerListener<ImageInfo>() {
        override fun onIntermediateImageSet(id: String?, imageInfo: ImageInfo?) {
            imageInfo?.let {
                binding.cover.aspectRatio = it.width / it.height.toFloat()
            }
        }

        override fun onFinalImageSet(id: String?, imageInfo: ImageInfo?, animatable: Animatable?) {
            imageInfo?.let {
                binding.cover.aspectRatio = it.width / it.height.toFloat()
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = GalleryDialogBinding.inflate(layoutInflater)

        with (binding.fab) {
            setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.arrow_right))
            setOnClickListener {
                context?.startActivity(Intent(requireContext(), ReaderActivity::class.java).apply {
                    putExtra("source", source)
                    putExtra("id", itemID)
                })
            }
        }

        val lilMutex = Mutex(true)
        viewModel.info.observe(this) {
            binding.progressbar.visibility = View.GONE
            binding.title.text = it.title
            binding.artist.text = it.artists

            binding.cover.controller = Fresco.newDraweeControllerBuilder()
                .setUri(it.thumbnail)
                .setControllerListener(controllerListener)
                .setOldController(binding.cover.controller)
                .build()

            MainScope().launch {
                binding.type.text = it.extra[ItemInfo.ExtraType.TYPE]?.await()?.wordCapitalize()
                addDetails(it)
                addPreviews(it)

                lilMutex.unlock()
            }
        }

        viewModel.related.observe(this) {
            if (it != null) {
                MainScope().launch {
                    lilMutex.withLock {
                        addRelated(it)
                    }
                }
            }
        }

        viewModel.load(source, itemID)

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
    }

    private suspend fun addDetails(info: ItemInfo) {
        GalleryDialogDetailsBinding.inflate(layoutInflater, binding.contents, true).apply {
            type.setText(R.string.gallery_details)

            listOf(
                R.string.gallery_artists,
                R.string.gallery_groups,
                R.string.gallery_language,
                R.string.gallery_series,
                R.string.gallery_characters,
                R.string.gallery_tags
            ).zip(
                listOf(
                    info.artists.split(", ").map { Tag("artist", it) },
                    info.extra[ItemInfo.ExtraType.GROUP]?.await()?.split(", ")?.filterNot { it.isEmpty() }?.map { Tag("group", it) },
                    info.extra[ItemInfo.ExtraType.LANGUAGE]?.await()?.split(", ")?.filterNot { it.isEmpty() }?.map { Tag("language", it) },
                    info.extra[ItemInfo.ExtraType.SERIES]?.await()?.split(", ")?.filterNot { it.isEmpty() }?.map { Tag("series", it) },
                    info.extra[ItemInfo.ExtraType.CHARACTER]?.await()?.split(", ")?.filterNot { it.isEmpty() }?.map { Tag("character", it) },
                    info.extra[ItemInfo.ExtraType.TAGS]?.await()?.split(", ")?.filterNot { it.isEmpty() }?.sortedBy {
                        val tag = Tag.parse(it)

                        if (favoriteTags.map[source]?.contains(tag.toString()) == true)
                            -1
                        else
                            when(Tag.parse(it).area) {
                                "female" -> 0
                                "male" -> 1
                                else -> 2
                            }
                    }?.map {
                        Tag.parse(it).let { tag ->
                            when {
                                tag.area != null -> tag
                                else -> Tag("tag", it)
                            }
                        }
                    }
                )
            ).filterNot { (_, content) ->
                content.isNullOrEmpty()
            }.forEach { (title, content) ->
                GalleryDialogTagsBinding.inflate(layoutInflater, contents, true).apply {
                    type.setText(title)

                    content!!.forEach { tag ->
                        tags.addView(
                            TagChip(requireContext(), source, tag).apply {
                                setOnClickListener {
                                    onChipClickedHandler.forEach { handler ->
                                        handler.invoke(tag)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private suspend fun addPreviews(info: ItemInfo) {
        val previews = info.extra[ItemInfo.ExtraType.PREVIEW]?.await()?.split(", ") ?: return

        GalleryDialogDetailsBinding.inflate(layoutInflater, binding.contents, true).apply {
            type.setText(R.string.gallery_thumbnails)

            val pager = ViewPager2(requireContext()).apply {
                adapter = ThumbnailPageAdapter(previews)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }

            contents.addView(pager)

            // TODO: Change to direct allocation
            GalleryDialogDotindicatorBinding.inflate(layoutInflater, contents, true).apply {
                dotindicator.setViewPager2(pager)
            }
        }
    }

    private fun addRelated(relatedItems: List<ItemInfo>) {
        val adapter = SearchResultsAdapter(MutableLiveData(relatedItems)).apply {
            onChipClickedHandler = { tag ->
                this@GalleryDialogFragment.onChipClickedHandler.forEach { handler ->
                    handler.invoke(tag)
                }
            }
        }

        GalleryDialogDetailsBinding.inflate(layoutInflater, binding.contents, true).apply {
            type.setText(R.string.gallery_related)

            contents.addView(RecyclerView(requireContext()).apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter

                ItemClickSupport.addTo(this).apply {
                    onItemClickListener = { _, position, _ ->
                        requireContext().startActivity(Intent(requireContext(), ReaderActivity::class.java).apply {
                            putExtra("source", source)
                            putExtra("id", relatedItems[position].id)
                        })
                    }
                    onItemLongClickListener = { _, position, _ ->
                        GalleryDialogFragment(source, relatedItems[position].id).apply {
                            onChipClickedHandler.add { tag ->
                                this@GalleryDialogFragment.onChipClickedHandler.forEach { it.invoke(tag) }
                            }
                        }.show(parentFragmentManager, "")

                        true
                    }
                }
            })
        }
    }

    override fun onDestroyView() {
        binding.contents.forEach { if (it is RecyclerView) ItemClickSupport.removeFrom(it) }
        super.onDestroyView()
    }

}