/*
 *    Copyright 2019 tom5079
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package xyz.quaver.pupil.hitomi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.util.*

fun doSearch(query: String, sortByPopularity: Boolean = false) : Set<Int> {
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
        CoroutineScope(Dispatchers.IO).async {
            kotlin.runCatching {
                getGalleryIDsForQuery(it)
            }.getOrElse { emptySet() }
        }
    }

    val negativeResults = negativeTerms.map {
        CoroutineScope(Dispatchers.IO).async {
            kotlin.runCatching {
                getGalleryIDsForQuery(it)
            }.getOrElse { emptySet() }
        }
    }

    var results = when {
        sortByPopularity -> getGalleryIDsFromNozomi(null, "popular", "all")
        positiveTerms.isEmpty() -> getGalleryIDsFromNozomi(null, "index", "all")
        else -> emptySet()
    }

    runBlocking {
        @Synchronized fun filterPositive(newResults: Set<Int>) {
            results = when {
                results.isEmpty() -> newResults
                else -> results intersect newResults
            }
        }

        @Synchronized fun filterNegative(newResults: Set<Int>) {
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
    }

    return results
}