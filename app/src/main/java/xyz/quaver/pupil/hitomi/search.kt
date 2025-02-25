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

import kotlinx.serialization.json.jsonArray
import okhttp3.Request
import xyz.quaver.pupil.client
import xyz.quaver.pupil.util.content
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.min

data class SearchArgs(
    val area: String?,
    val tag: String,
    val language: String,
) {
    companion object {
        fun fromQuery(query: String): SearchArgs? {
            if (!query.contains(':')) {
                return null
            }

            val (left, right) = query.split(':')

            return when (left) {
                "male", "female" -> SearchArgs("tag", query, "all")
                "language" -> SearchArgs(null, "index", right)
                else -> SearchArgs(left, right, "all")
            }
        }
    }
}

enum class SortMode {
    DATE_ADDED,
    DATE_PUBLISHED,
    POPULAR_TODAY,
    POPULAR_WEEK,
    POPULAR_MONTH,
    POPULAR_YEAR,
    RANDOM;

    val orderBy: String
        get() = when (this) {
            DATE_ADDED, DATE_PUBLISHED, RANDOM -> "date"
            POPULAR_TODAY, POPULAR_WEEK, POPULAR_MONTH, POPULAR_YEAR -> "popular"
        }

    val orderByKey: String
        get() = when (this) {
            DATE_ADDED, RANDOM -> "added"
            DATE_PUBLISHED -> "published"
            POPULAR_TODAY -> "today"
            POPULAR_WEEK -> "week"
            POPULAR_MONTH -> "month"
            POPULAR_YEAR -> "year"
        }
}

//searchlib.js
const val separator = "-"
const val extension = ".html"
const val index_dir = "tagindex"
const val galleries_index_dir = "galleriesindex"
const val max_node_size = 464
const val B = 16
const val compressed_nozomi_prefix = "n"

val tag_index_version: String by lazy { getIndexVersion("tagindex") }
val galleries_index_version: String by lazy { getIndexVersion("galleriesindex") }
val tagIndexDomain = "tagindex.hitomi.la"

fun sha256(data: ByteArray): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(data)
}

@OptIn(ExperimentalUnsignedTypes::class)
fun hashTerm(term: String): UByteArray {
    return sha256(term.toByteArray()).toUByteArray().sliceArray(0 until 4)
}

fun sanitize(input: String): String {
    return input.replace(Regex("[/#]"), "")
}

fun getIndexVersion(name: String) =
    URL("$protocol//$domain/$name/version?_=${System.currentTimeMillis()}").readText()

//search.js
fun getGalleryIDsForQuery(query: String, sortMode: SortMode): Set<Int> {
    val sanitizedQuery = query.replace("_", " ")

    val args = SearchArgs.fromQuery(sanitizedQuery)

    return if (args != null) {
        getGalleryIDsFromNozomi(args, sortMode)
    } else {
        val key = hashTerm(sanitizedQuery)
        val field = "galleries"

        val node = getNodeAtAddress(field, 0)

        val data = bSearch(field, key, node)

        if (data != null)
            return getGalleryIDsFromData(data)

        return emptySet()
    }
}

fun encodeSearchQueryForUrl(s: Char) =
    when (s) {
        ' ' -> "_"
        '/' -> "slash"
        '.' -> "dot"
        else -> s.toString()
    }

fun getSuggestionsForQuery(query: String): List<Suggestion> {
    query.replace('_', ' ').let {
        var field = "global"
        var term = it

        if (term.indexOf(':') > -1) {
            val sides = it.split(':')
            field = sides[0]
            term = sides[1]
        }

        val chars = term.map(::encodeSearchQueryForUrl)
        val url =
            "https://$tagIndexDomain/$field${if (chars.isNotEmpty()) "/${chars.joinToString("/")}" else ""}.json"

        val request = Request.Builder()
            .url(url)
            .build()

        val suggestions = json.parseToJsonElement(
            client.newCall(request).execute().body()?.use { body -> body.string() }
                ?: return emptyList())

        return buildList {
            suggestions.jsonArray.forEach { suggestionRaw ->
                val suggestion = suggestionRaw.jsonArray
                if (suggestion.size < 3) {
                    return@forEach
                }
                val ns = suggestion[2].content ?: ""

                val tagname = sanitize(suggestion[0].content ?: return@forEach)
                val url = when (ns) {
                    "female", "male" -> "/tag/$ns:$tagname${separator}1$extension"
                    "language" -> "/index-$tagname${separator}1$extension"
                    else -> "/$ns/$tagname${separator}all${separator}1$extension"
                }

                add(
                    Suggestion(
                        suggestion[0].content ?: "",
                        suggestion[1].content?.toIntOrNull() ?: 0,
                        url,
                        ns
                    )
                )
            }
        }
    }
}

data class Suggestion(val s: String, val t: Int, val u: String, val n: String)

fun getSuggestionsFromData(field: String, data: Pair<Long, Int>): List<Suggestion> {
    val url = "$protocol//$domain/$index_dir/$field.$tag_index_version.data"
    val (offset, length) = data
    if (length > 10000 || length <= 0)
        throw Exception("length $length is too long")

    val inbuf = getURLAtRange(url, offset.until(offset + length))

    val suggestions = ArrayList<Suggestion>()

    val buffer = ByteBuffer
        .wrap(inbuf)
        .order(ByteOrder.BIG_ENDIAN)
    val numberOfSuggestions = buffer.int

    if (numberOfSuggestions > 100 || numberOfSuggestions <= 0)
        throw Exception("number of suggestions $numberOfSuggestions is too long")

    for (i in 0.until(numberOfSuggestions)) {
        var top = buffer.int

        val ns = inbuf.sliceArray(buffer.position().until(buffer.position() + top))
            .toString(charset("UTF-8"))
        buffer.position(buffer.position() + top)

        top = buffer.int

        val tag = inbuf.sliceArray(buffer.position().until(buffer.position() + top))
            .toString(charset("UTF-8"))
        buffer.position(buffer.position() + top)

        val count = buffer.int

        val tagname = sanitize(tag)
        val u =
            when (ns) {
                "female", "male" -> "/tag/$ns:$tagname${separator}1$extension"
                "language" -> "/index-$tagname${separator}1$extension"
                else -> "/$ns/$tagname${separator}all${separator}1$extension"
            }

        suggestions.add(Suggestion(tag, count, u, ns))
    }

    return suggestions
}

fun nozomiAddressFromArgs(args: SearchArgs, sortMode: SortMode) = when {
    sortMode != SortMode.DATE_ADDED && sortMode != SortMode.RANDOM ->
        if (args.area == "all") "$protocol//$domain/$compressed_nozomi_prefix/${sortMode.orderBy}/${sortMode.orderByKey}-${args.language}$nozomiextension"
        else "$protocol//$domain/$compressed_nozomi_prefix/${args.area}/${sortMode.orderBy}/${sortMode.orderByKey}/${args.tag}-${args.language}$nozomiextension"

    args.area == "all" -> "$protocol//$domain/$compressed_nozomi_prefix/${args.tag}-${args.language}$nozomiextension"
    else -> "$protocol//$domain/$compressed_nozomi_prefix/${args.area}/${args.tag}-${args.language}$nozomiextension"
}

fun getGalleryIDsFromNozomi(args: SearchArgs, sortMode: SortMode): Set<Int> {
    val nozomiAddress = nozomiAddressFromArgs(args, sortMode)

    val bytes = URL(nozomiAddress).readBytes()

    val nozomi = mutableSetOf<Int>()

    val arrayBuffer = ByteBuffer
        .wrap(bytes)
        .order(ByteOrder.BIG_ENDIAN)

    while (arrayBuffer.hasRemaining())
        nozomi.add(arrayBuffer.int)

    return nozomi
}

fun getGalleryIDsFromData(data: Pair<Long, Int>): Set<Int> {
    val url = "$protocol//$domain/$galleries_index_dir/galleries.$galleries_index_version.data"
    val (offset, length) = data
    if (length > 100000000 || length <= 0)
        throw Exception("length $length is too long")

    val inbuf = getURLAtRange(url, offset.until(offset + length))

    val galleryIDs = mutableSetOf<Int>()

    val buffer = ByteBuffer
        .wrap(inbuf)
        .order(ByteOrder.BIG_ENDIAN)

    val numberOfGalleryIDs = buffer.int

    val expectedLength = numberOfGalleryIDs * 4 + 4

    if (numberOfGalleryIDs > 10000000 || numberOfGalleryIDs <= 0)
        throw Exception("number_of_galleryids $numberOfGalleryIDs is too long")
    else if (inbuf.size != expectedLength)
        throw Exception("inbuf.byteLength ${inbuf.size} != expected_length $expectedLength")

    for (i in 0.until(numberOfGalleryIDs))
        galleryIDs.add(buffer.int)

    return galleryIDs
}

fun getNodeAtAddress(field: String, address: Long): Node {
    val url =
        when (field) {
            "galleries" -> "$protocol//$domain/$galleries_index_dir/galleries.$galleries_index_version.index"
            "languages" -> "$protocol//$domain/$galleries_index_dir/languages.$galleries_index_version.index"
            "nozomiurl" -> "$protocol//$domain/$galleries_index_dir/nozomiurl.$galleries_index_version.index"
            else -> "$protocol//$domain/$index_dir/$field.$tag_index_version.index"
        }

    val nodedata = getURLAtRange(url, address.until(address + max_node_size))

    return decodeNode(nodedata)
}

fun getURLAtRange(url: String, range: LongRange): ByteArray {
    val request = Request.Builder()
        .url(url)
        .header("Range", "bytes=${range.first}-${range.last}")
        .build()

    return client.newCall(request).execute().body()?.use { it.bytes() } ?: byteArrayOf()
}

@OptIn(ExperimentalUnsignedTypes::class)
data class Node(
    val keys: List<UByteArray>,
    val datas: List<Pair<Long, Int>>,
    val subNodeAddresses: List<Long>
)

@OptIn(ExperimentalUnsignedTypes::class)
fun decodeNode(data: ByteArray): Node {
    val buffer = ByteBuffer
        .wrap(data)
        .order(ByteOrder.BIG_ENDIAN)

    val uData = data.toUByteArray()

    val numberOfKeys = buffer.int
    val keys = ArrayList<UByteArray>()

    for (i in 0.until(numberOfKeys)) {
        val keySize = buffer.int

        if (keySize == 0 || keySize > 32)
            throw Exception("fatal: !keySize || keySize > 32")

        keys.add(uData.sliceArray(buffer.position().until(buffer.position() + keySize)))
        buffer.position(buffer.position() + keySize)
    }

    val numberOfDatas = buffer.int
    val datas = ArrayList<Pair<Long, Int>>()

    for (i in 0.until(numberOfDatas)) {
        val offset = buffer.long
        val length = buffer.int

        datas.add(Pair(offset, length))
    }

    val numberOfSubNodeAddresses = B + 1
    val subNodeAddresses = ArrayList<Long>()

    for (i in 0.until(numberOfSubNodeAddresses)) {
        val subNodeAddress = buffer.long
        subNodeAddresses.add(subNodeAddress)
    }

    return Node(keys, datas, subNodeAddresses)
}

@OptIn(ExperimentalUnsignedTypes::class)
fun bSearch(field: String, key: UByteArray, node: Node): Pair<Long, Int>? {
    fun compareArrayBuffers(dv1: UByteArray, dv2: UByteArray): Int {
        val top = min(dv1.size, dv2.size)

        for (i in 0.until(top)) {
            if (dv1[i] < dv2[i])
                return -1
            else if (dv1[i] > dv2[i])
                return 1
        }

        return 0
    }

    fun locateKey(key: UByteArray, node: Node): Pair<Boolean, Int> {
        for (i in node.keys.indices) {
            val cmpResult = compareArrayBuffers(key, node.keys[i])

            if (cmpResult <= 0)
                return Pair(cmpResult == 0, i)
        }

        return Pair(false, node.keys.size)
    }

    fun isLeaf(node: Node): Boolean {
        for (subnode in node.subNodeAddresses)
            if (subnode != 0L)
                return false

        return true
    }

    if (node.keys.isEmpty())
        return null

    val (there, where) = locateKey(key, node)
    if (there)
        return node.datas[where]
    else if (isLeaf(node))
        return null

    val nextNode = getNodeAtAddress(field, node.subNodeAddresses[where])
    return bSearch(field, key, nextNode)
}