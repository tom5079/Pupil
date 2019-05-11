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

    val results = ArrayList<Int>()
    val positiveTerms = LinkedList<String>()
    val negativeTerms = LinkedList<String>()

    for (term in terms) {
        if (term.matches(Regex("^-.+")))
            negativeTerms.push(term.replace(Regex("^-"), ""))
        else
            positiveTerms.push(term)
    }

    //first results
    results.addAll(
        if (positiveTerms.isEmpty())
            getGalleryIDsFromNozomi(null, "index", "all")
        else
            getGalleryIDsForQuery(positiveTerms.poll())
    )

    runBlocking {
        @Synchronized fun filterPositive(newResults: List<Int>) {
            results.filter { newResults.binarySearch(it) >= 0 }.let {
                results.clear()
                results.addAll(it)
            }
        }

        @Synchronized fun filterNegative(newResults: List<Int>) {
            results.filterNot { newResults.binarySearch(it) >= 0 }.let {
                results.clear()
                results.addAll(it)
            }
        }

        //positive results
        positiveTerms.map {
            launch(searchDispatcher) {
                filterPositive(getGalleryIDsForQuery(it).reversed())
            }
        }.forEach {
            it.join()
        }

        //negative results
        negativeTerms.map {
            launch(searchDispatcher) {
                filterNegative(getGalleryIDsForQuery(it).reversed())
            }
        }.forEach {
            it.join()
        }
    }

    return results

}