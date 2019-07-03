package xyz.quaver.hitomi

import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.Executors

fun doSearch(query: String) : List<Int> {
    val time = System.currentTimeMillis()

    val terms = query
        .trim()
        .replace(Regex("""^\?"""), "")
        .toLowerCase()
        .split(Regex("\\s+"))
        .map {
            it.replace('_', ' ')
        }

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
            async(Dispatchers.IO) {
                Pair(getGalleryIDsForQuery(it), true)
            }
        }+negativeTerms.map {
            async(Dispatchers.IO) {
                Pair(getGalleryIDsForQuery(it), false)
            }
        }.forEach {
            val (result, isPositive) = it.await()

            when {
                isPositive -> filterPositive(result.sorted())
                else -> filterNegative(result.sorted())
            }
        }
    }

    println("PUPIL/SEARCH TIME ${System.currentTimeMillis() - time}ms")

    return results
}