package xyz.quaver.pupil.types

import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import kotlinx.android.parcel.Parcelize
import xyz.quaver.hitomi.Suggestion

@Parcelize
data class TagSuggestion constructor(val s: String, val t: Int, val u: String, val n: String) : SearchSuggestion {
    constructor(s: Suggestion) : this(s.s, s.t, s.u, s.n)

    override fun getBody(): String {
        return s
    }
}