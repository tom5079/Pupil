package xyz.quaver.hitomi

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.Executors

val searchDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
fun doSearch(query: String) : List<Int> {
    val terms = query
        .trim()
        .replace(Regex("""^\?"""), "")
        .replace('_', ' ')
        .toLowerCase()
        .split(Regex("\\s+"))

    val positiveTerms = LinkedList<String>()
    val negativeTerms = LinkedList<String>()

    for (term in terms) {
        if (term.matches(Regex("^-.+")))
            negativeTerms.push(term.replace(Regex("^-"), ""))
        else
            positiveTerms.push(term)
    }

    var results = when {
        positiveTerms.isEmpty() -> getGalleryIDsFromNozomi(null, "index", "all")
        else -> getGalleryIDsForQuery(positiveTerms.poll())
    }

    runBlocking {
        @Synchronized fun filterPositive(newResults: List<Int>) {
            results = results.filter { newResults.binarySearch(it) >= 0 }
        }

        @Synchronized fun filterNegative(newResults: List<Int>) {
            results = results.filter { newResults.binarySearch(it) < 0 }
        }

        //positive results
        positiveTerms.map {
            launch(searchDispatcher) {
                filterPositive(getGalleryIDsForQuery(it).sorted())
            }
        }.forEach {
            it.join()
        }

        //negative results
        negativeTerms.map {
            launch(searchDispatcher) {
                filterNegative(getGalleryIDsForQuery(it).sorted())
            }
        }.forEach {
            it.join()
        }
    }

    return results

}