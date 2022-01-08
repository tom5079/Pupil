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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import xyz.quaver.pupil.webView

//searchlib.js
const val extension = ".html"

@OptIn(ExperimentalSerializationApi::class)
suspend fun getGalleryIDsForQuery(query: String) : Set<Int> {
    val result = webView.evaluatePromise("get_galleryids_for_query('$query')")

    return Json.decodeFromString(result)
}

@Serializable
data class Suggestion(val s: String, val t: Int, val u: String, val n: String)

@OptIn(ExperimentalSerializationApi::class)
suspend fun getSuggestionsForQuery(query: String) : List<Suggestion> {
    val result = webView.evaluatePromise("get_suggestions_for_query('$query')")

    return Json.decodeFromString<List<List<Suggestion>?>>(result)[0] ?: return emptyList()
}

@OptIn(ExperimentalSerializationApi::class)
suspend fun getGalleryIDsFromNozomi(area: String?, tag: String, language: String) : Set<Int> {
    val jsArea = if (area == null) "null" else "'$area'"

    val json = webView.evaluatePromise("""get_galleryids_from_nozomi($jsArea, '$tag', '$language')""")

    return Json.decodeFromString(json)
}