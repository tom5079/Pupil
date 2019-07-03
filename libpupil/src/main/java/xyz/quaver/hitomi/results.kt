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