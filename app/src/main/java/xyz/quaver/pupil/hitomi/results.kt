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

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.LinkedList

suspend fun doSearch(query: String, sortMode: SortMode): List<Int> = coroutineScope {
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
        if (term.startsWith("-"))
            negativeTerms.push(term.substring(1))
        else if (term.isNotBlank())
            positiveTerms.push(term)
    }

    val positiveResults = positiveTerms.map {
        async {
            runCatching {
                getGalleryIDsForQuery(it, sortMode)
            }.getOrElse { emptySet() }
        }
    }

    val negativeResults = negativeTerms.map {
        async {
            runCatching {
                getGalleryIDsForQuery(it, sortMode)
            }.getOrElse { emptySet() }
        }
    }

    val results = when {
        positiveTerms.isEmpty() -> getGalleryIDsFromNozomi(
            SearchArgs("all", "index", "all"),
            sortMode
        )

        else -> emptySet()
    }.toMutableSet()

    fun filterPositive(newResults: Set<Int>) {
        when {
            results.isEmpty() -> results.addAll(newResults)
            else -> results.retainAll(newResults)
        }
    }

    fun filterNegative(newResults: Set<Int>) {
        results.removeAll(newResults)
    }

    //positive results
    positiveResults.forEach {
        filterPositive(it.await())
    }

    //negative results
    negativeResults.forEach {
        filterNegative(it.await())
    }

    return@coroutineScope if (sortMode != SortMode.RANDOM) {
        results.toList()
    } else {
        results.shuffled()
    }
}