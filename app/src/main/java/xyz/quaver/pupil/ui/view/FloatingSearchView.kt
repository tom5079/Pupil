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

package xyz.quaver.pupil.ui.view

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Animatable
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import xyz.quaver.floatingsearchview.FloatingSearchView
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.floatingsearchview.util.view.SearchInputView
import xyz.quaver.pupil.R
import xyz.quaver.pupil.favoriteTags
import xyz.quaver.pupil.types.*
import java.util.*

class FloatingSearchView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FloatingSearchView(context, attrs),
    FloatingSearchView.OnSearchListener,
    TextWatcher
{
    private val searchInputView = findViewById<SearchInputView>(R.id.search_bar_text)

    var onHistoryDeleteClickedListener: ((String) -> Unit)? = null
    var onFavoriteHistorySwitchClickListener: (() -> Unit)? = null

    init {
        searchInputView.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or searchInputView.imeOptions

        searchInputView.addTextChangedListener(this)
        onSearchListener = this
        onBindSuggestionCallback = { binding, item, itemPosition ->
            onBindSuggestion(binding.root, binding.leftIcon, binding.body, item, itemPosition)
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

    }

    override fun afterTextChanged(s: Editable?) {
        s ?: return

        if (s.any { it.isUpperCase() })
            s.replace(0, s.length, s.toString().toLowerCase(Locale.getDefault()))
    }

    override fun onSuggestionClicked(searchSuggestion: SearchSuggestion?) {
        when (searchSuggestion) {
            is TagSuggestion -> {
                val tag = "${searchSuggestion.n}:${searchSuggestion.s.replace(Regex("\\s"), "_")}"
                with(searchInputView.text!!) {
                    delete(if (lastIndexOf(' ') == -1) 0 else lastIndexOf(' ') + 1, length)

                    if (!this.contains(tag))
                        append("$tag ")
                }
            }
            is Suggestion -> {
                with(searchInputView.text!!) {
                    clear()
                    append(searchSuggestion.body)
                }
            }
            is FavoriteHistorySwitch -> onFavoriteHistorySwitchClickListener?.invoke()
        }
    }

    override fun onSearchAction(currentQuery: String?) {}

    fun onBindSuggestion(
        suggestionView: View?,
        leftIcon: ImageView?,
        textView: TextView?,
        item: SearchSuggestion?,
        itemPosition: Int
    ) {
      when(item) {
            is TagSuggestion -> {
                val tag = "${item.n}:${item.s}"

                leftIcon?.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        when(item.n) {
                            "female" -> R.drawable.gender_female
                            "male" -> R.drawable.gender_male
                            "language" -> R.drawable.translate
                            "group" -> R.drawable.account_group
                            "character" -> R.drawable.account_star
                            "series" -> R.drawable.book_open
                            "artist" -> R.drawable.brush
                            else -> R.drawable.tag
                        },
                        context.theme)
                )

                with(suggestionView?.findViewById<ImageView>(R.id.right_icon)) {
                    this ?: return@with

                    if (favoriteTags.contains(Tag.parse(tag)))
                        setImageResource(R.drawable.ic_star_filled)
                    else
                        setImageResource(R.drawable.ic_star_empty)

                    visibility = View.VISIBLE
                    rotation = 0f

                    isEnabled = true
                    isClickable = true

                    setOnClickListener {
                        val tag = Tag.parse(tag)

                        if (favoriteTags.contains(tag)) {
                            setImageResource(R.drawable.ic_star_empty)
                            favoriteTags.remove(tag)
                        }
                        else {
                            setImageDrawable(
                                AnimatedVectorDrawableCompat.create(context,
                                    R.drawable.avd_star
                                ))
                            (drawable as Animatable).start()

                            favoriteTags.add(tag)
                        }
                    }
                }

                if (item.t > 0) {
                    (suggestionView as? LinearLayout)?.let {
                        val count = it.findViewById<TextView>(R.id.count)
                        if (count == null)
                            it.addView(
                                LayoutInflater.from(context).inflate(R.layout.suggestion_count, suggestionView, false)
                                    .apply {
                                        this as TextView

                                        text = item.t.toString()
                                    }, 2
                            )
                        else
                            count.text = item.t.toString()
                    }
                }
            }
            is FavoriteHistorySwitch -> {
                leftIcon?.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.swap_horizontal, context.theme))
            }
            is Suggestion -> {
                leftIcon?.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.history, context.theme))

                with(suggestionView?.findViewById<ImageView>(R.id.right_icon)) {
                    this ?: return@with

                    setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.delete, context.theme))

                    visibility = View.VISIBLE
                    rotation = 0f

                    isEnabled = true
                    isClickable = true

                    setOnClickListener {
                        onHistoryDeleteClickedListener?.invoke(item.body)
                    }
                }
            }
            is LoadingSuggestion -> {
                leftIcon?.setImageDrawable(CircularProgressDrawable(context).also {
                    it.setStyle(CircularProgressDrawable.DEFAULT)
                    it.colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, R.color.colorAccent), PorterDuff.Mode.SRC_IN)
                    it.start()
                })
            }
            is NoResultSuggestion -> {
                leftIcon?.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.close, context.theme))
            }
        }
    }
}
