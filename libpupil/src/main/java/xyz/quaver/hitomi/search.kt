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

import xyz.quaver.proxy
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

//searchlib.js
const val separator = "-"
const val extension = ".html"
const val index_dir = "tagindex"
const val galleries_index_dir = "galleriesindex"
const val max_node_size = 464
const val B = 16
const val compressed_nozomi_prefix = "n"

var tag_index_version = getIndexVersion("tagindex")
var galleries_index_version = getIndexVersion("galleriesindex")

fun sha256(data: ByteArray) : ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(data)
}

@OptIn(ExperimentalUnsignedTypes::class)
fun hashTerm(term: String) : UByteArray {
    return sha256(term.toByteArray()).toUByteArray().sliceArray(0 until 4)
}

fun sanitize(input: String) : String {
    return input.replace(Regex("[/#]"), "")
}

fun getIndexVersion(name: String) : String {
    return try {
        URL("$protocol//$domain/$name/version?_=${System.currentTimeMillis()}").openConnection(proxy).getInputStream().use {
            it.reader().readText()
        }
    } catch (e: Exception) {
        ""
    }
}

//search.js
fun getGalleryIDsForQuery(query: String) : List<Int> {
    query.replace("_", " ").let {
        if (it.indexOf(':') > -1) {
            val sides = it.split(":")
            val ns = sides[0]
            var tag = sides[1]

            var area : String? = ns
            var language = "all"
            when (ns) {
                "female", "male" -> {
                    area = "tag"
                    tag = it
                }
                "language" -> {
                    area = null
                    language = tag
                    tag = "index"
                }
            }

            return getGalleryIDsFromNozomi(area, tag, language)
        }

        val key = hashTerm(it)
        val field = "galleries"

        val node = getNodeAtAddress(field, 0) ?: return emptyList()

        val data = bSearch(field, key, node)

        if (data != null)
            return getGalleryIDsFromData(data)

        return emptyList()
    }
}

fun getSuggestionsForQuery(query: String) : List<Suggestion> {
    query.replace('_', ' ').let {
        var field = "global"
        var term = it

        if (term.indexOf(':') > -1) {
            val sides = it.split(':')
            field = sides[0]
            term = sides[1]
        }

        val key = hashTerm(term)
        val node = getNodeAtAddress(field, 0) ?: return emptyList()
        val data = bSearch(field, key, node)

        if (data != null)
            return getSuggestionsFromData(field, data)

        return emptyList()
    }
}

data class Suggestion(val s: String, val t: Int, val u: String, val n: String)
fun getSuggestionsFromData(field: String, data: Pair<Long, Int>) : List<Suggestion> {
    if (tag_index_version.isEmpty())
        tag_index_version = getIndexVersion("tagindex")

    val url = "$protocol//$domain/$index_dir/$field.$tag_index_version.data"
    val (offset, length) = data
    if (length > 10000 || length <= 0)
        throw Exception("length $length is too long")

    val inbuf = getURLAtRange(url, offset.until(offset+length)) ?: return emptyList()

    val suggestions = ArrayList<Suggestion>()

    val buffer = ByteBuffer
        .wrap(inbuf)
        .order(ByteOrder.BIG_ENDIAN)
    val numberOfSuggestions = buffer.int

    if (numberOfSuggestions > 100 || numberOfSuggestions <= 0)
        throw Exception("number of suggestions $numberOfSuggestions is too long")

    for (i in 0.until(numberOfSuggestions)) {
        var top = buffer.int

        val ns = inbuf.sliceArray(buffer.position().until(buffer.position()+top)).toString(charset("UTF-8"))
        buffer.position(buffer.position()+top)

        top = buffer.int

        val tag = inbuf.sliceArray(buffer.position().until(buffer.position()+top)).toString(charset("UTF-8"))
        buffer.position(buffer.position()+top)

        val count = buffer.int

        val tagname = sanitize(tag)
        val u =
                when(ns) {
                    "female", "male" -> "/tag/$ns:$tagname${separator}1$extension"
                    "language" -> "/index-$tagname${separator}1$extension"
                    else -> "/$ns/$tagname${separator}all${separator}1$extension"
                }

        suggestions.add(Suggestion(tag, count, u, ns))
    }

    return suggestions
}

fun getGalleryIDsFromNozomi(area: String?, tag: String, language: String) : List<Int> {
    val nozomiAddress =
            when(area) {
                null -> "$protocol//$domain/$compressed_nozomi_prefix/$tag-$language$nozomiextension"
                else -> "$protocol//$domain/$compressed_nozomi_prefix/$area/$tag-$language$nozomiextension"
            }

    val bytes = try {
        URL(nozomiAddress).openConnection(proxy).getInputStream().use {
            it.readBytes()
        }
    } catch (e: Exception) {
        return emptyList()
    }

    val nozomi = ArrayList<Int>()

    val arrayBuffer = ByteBuffer
        .wrap(bytes)
        .order(ByteOrder.BIG_ENDIAN)

    while (arrayBuffer.hasRemaining())
        nozomi.add(arrayBuffer.int)

    return nozomi
}

fun getGalleryIDsFromData(data: Pair<Long, Int>) : List<Int> {
    if (galleries_index_version.isEmpty())
        galleries_index_version = getIndexVersion("galleriesindex")

    val url = "$protocol//$domain/$galleries_index_dir/galleries.$galleries_index_version.data"
    val (offset, length) = data
    if (length > 100000000 || length <= 0)
        throw Exception("length $length is too long")

    val inbuf = getURLAtRange(url, offset.until(offset+length)) ?: return emptyList()

    val galleryIDs = ArrayList<Int>()

    val buffer = ByteBuffer
        .wrap(inbuf)
        .order(ByteOrder.BIG_ENDIAN)

    val numberOfGalleryIDs = buffer.int

    val expectedLength = numberOfGalleryIDs*4+4

    if (numberOfGalleryIDs > 10000000 || numberOfGalleryIDs <= 0)
        throw Exception("number_of_galleryids $numberOfGalleryIDs is too long")
    else if (inbuf.size != expectedLength)
        throw Exception("inbuf.byteLength ${inbuf.size} != expected_length $expectedLength")

    for (i in 0.until(numberOfGalleryIDs))
        galleryIDs.add(buffer.int)

    return galleryIDs
}

fun getNodeAtAddress(field: String, address: Long) : Node? {
    if (tag_index_version.isEmpty())
        tag_index_version = getIndexVersion("tagindex")
    if (galleries_index_version.isEmpty())
        galleries_index_version = getIndexVersion("galleriesindex")

    val url =
            when(field) {
                "galleries" -> "$protocol//$domain/$galleries_index_dir/galleries.$galleries_index_version.index"
                else -> "$protocol//$domain/$index_dir/$field.$tag_index_version.index"
            }

    val nodedata = getURLAtRange(url, address.until(address+max_node_size)) ?: return null

    return decodeNode(nodedata)
}

fun getURLAtRange(url: String, range: LongRange) : ByteArray? {
    try {
        with (URL(url).openConnection(proxy) as HttpsURLConnection) {
            requestMethod = "GET"

            setRequestProperty("Range", "bytes=${range.first}-${range.last}")

            return inputStream.readBytes()
        }
    } catch (e: Exception) {
        return null
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
data class Node(val keys: List<UByteArray>, val datas: List<Pair<Long, Int>>, val subNodeAddresses: List<Long>)
@OptIn(ExperimentalUnsignedTypes::class)
fun decodeNode(data: ByteArray) : Node {
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

        keys.add(uData.sliceArray(buffer.position().until(buffer.position()+keySize)))
        buffer.position(buffer.position()+keySize)
    }

    val numberOfDatas = buffer.int
    val datas = ArrayList<Pair<Long, Int>>()

    for (i in 0.until(numberOfDatas)) {
        val offset = buffer.long
        val length = buffer.int

        datas.add(Pair(offset, length))
    }

    val numberOfSubNodeAddresses = B+1
    val subNodeAddresses = ArrayList<Long>()

    for (i in 0.until(numberOfSubNodeAddresses)) {
        val subNodeAddress = buffer.long
        subNodeAddresses.add(subNodeAddress)
    }

    return Node(keys, datas, subNodeAddresses)
}

@OptIn(ExperimentalUnsignedTypes::class)
fun bSearch(field: String, key: UByteArray, node: Node) : Pair<Long, Int>? {
    fun compareArrayBuffers(dv1: UByteArray, dv2: UByteArray) : Int {
        val top = Math.min(dv1.size, dv2.size)

        for (i in 0.until(top)) {
            if (dv1[i] < dv2[i])
                return -1
            else if (dv1[i] > dv2[i])
                return 1
        }

        return 0
    }

    fun locateKey(key: UByteArray, node: Node) : Pair<Boolean, Int> {
        for (i in 0 until node.keys.size) {
            val cmpResult = compareArrayBuffers(key, node.keys[i])

            if (cmpResult <= 0)
                return Pair(cmpResult==0, i)
        }

        return Pair(false, node.keys.size)
    }

    fun isLeaf(node: Node) : Boolean {
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

    val nextNode = getNodeAtAddress(field, node.subNodeAddresses[where]) ?: return null
    return bSearch(field, key, nextNode)
}