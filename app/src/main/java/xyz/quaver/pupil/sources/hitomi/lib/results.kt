package xyz.quaver.pupil.sources.hitomi.lib

import io.ktor.client.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.*

suspend fun doSearch(
    client: HttpClient,
    query: String,
    sortByPopularity: Boolean = false
) : Set<Int> = coroutineScope {
    val terms = query
        .trim()
        .replace(Regex("""^\?"""), "")
        .lowercase()
        .split(Regex("\\s+"))
        .map {
            it.replace('_', ' ')
        }

    val positiveTerms = LinkedList<String>()
    val negativeTerms = LinkedList<String>()

    for (term in terms) {
        if (term.matches(Regex("^-.+")))
            negativeTerms.push(term.replace(Regex("^-"), ""))
        else if (term.isNotBlank())
            positiveTerms.push(term)
    }

    val positiveResults = positiveTerms.map {
        async {
            runCatching {
                getGalleryIDsForQuery(client, it)
            }.getOrElse { emptySet() }
        }
    }

    val negativeResults = negativeTerms.map {
        async {
            runCatching {
                getGalleryIDsForQuery(client, it)
            }.getOrElse { emptySet() }
        }
    }

    var results = when {
        sortByPopularity -> getGalleryIDsFromNozomi(client, null, "popular", "all")
        positiveTerms.isEmpty() -> getGalleryIDsFromNozomi(client, null, "index", "all")
        else -> emptySet()
    }

    fun filterPositive(newResults: Set<Int>) {
        results = when {
            results.isEmpty() -> newResults
            else -> results intersect newResults
        }
    }

    fun filterNegative(newResults: Set<Int>) {
        results = results subtract newResults
    }

    //positive results
    positiveResults.forEach {
        filterPositive(it.await())
    }

    //negative results
    negativeResults.forEach {
        filterNegative(it.await())
    }

    results
}