package xyz.quaver.pupil.types

import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import kotlinx.android.parcel.Parcelize
import xyz.quaver.hitomi.Suggestion

@Parcelize
class SelectorSuggestion : SearchSuggestion {

    override fun getBody(): String {
        return ""
    }
}