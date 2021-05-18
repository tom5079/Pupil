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
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import xyz.quaver.floatingsearchview.FloatingSearchView
import xyz.quaver.floatingsearchview.databinding.SearchSuggestionItemBinding
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.floatingsearchview.util.view.SearchInputView
import xyz.quaver.pupil.R
import xyz.quaver.pupil.sources.Hitomi
import xyz.quaver.pupil.types.FavoriteHistorySwitch
import xyz.quaver.pupil.types.HistorySuggestion
import xyz.quaver.pupil.types.LoadingSuggestion
import xyz.quaver.pupil.types.NoResultSuggestion
import java.util.*

class FloatingSearchView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    xyz.quaver.floatingsearchview.FloatingSearchView(context, attrs),
    FloatingSearchView.OnSearchListener,
    TextWatcher
{
    private val searchInputView = findViewById<SearchInputView>(R.id.search_bar_text)

    var onHistoryDeleteClickedListener: ((String) -> Unit)? = null
    var onFavoriteHistorySwitchClickListener: (() -> Unit)? = null

    var onSuggestionBinding: ((SearchSuggestionItemBinding, SearchSuggestion) -> Unit)? = null

    init {
        searchInputView.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or searchInputView.imeOptions

        searchInputView.addTextChangedListener(this)
        onSearchListener = this
        onBindSuggestionCallback = { binding, item, _ ->
            onBindSuggestion(binding, item)
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

    }

    override fun afterTextChanged(s: Editable?) {
        s ?: return

        if (s.any { it.isUpperCase() })
            s.replace(0, s.length, s.toString().lowercase())
    }

    override fun onSuggestionClicked(searchSuggestion: SearchSuggestion?) {
        when (searchSuggestion) {
            is Hitomi.TagSuggestion -> {
                val tag = "${searchSuggestion.n}:${searchSuggestion.s.replace(Regex("\\s"), "_")}"
                with (searchInputView.text!!) {
                    delete(if (lastIndexOf(' ') == -1) 0 else lastIndexOf(' ') + 1, length)

                    if (!this.contains(tag))
                        append("$tag ")
                }
            }
            is HistorySuggestion -> {
                with (searchInputView.text!!) {
                    clear()
                    append(searchSuggestion.body)
                }
            }
            is FavoriteHistorySwitch -> onFavoriteHistorySwitchClickListener?.invoke()
        }
    }

    override fun onSearchAction(currentQuery: String?) {}

    private fun onBindSuggestion(binding: SearchSuggestionItemBinding, item: SearchSuggestion) {
        when(item) {
            is FavoriteHistorySwitch -> {
                binding.leftIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.swap_horizontal, context.theme))
            }
            is LoadingSuggestion -> {
                binding.leftIcon.setImageDrawable(CircularProgressDrawable(context).also {
                    it.setStyle(CircularProgressDrawable.DEFAULT)
                    it.colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, R.color.colorAccent), PorterDuff.Mode.SRC_IN)
                    it.start()
                })
            }
            is NoResultSuggestion -> {
                binding.leftIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.close, context.theme))
            }
            else -> {
                onSuggestionBinding?.invoke(binding, item)
            }
        }
    }
}
