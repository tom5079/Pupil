package xyz.quaver.pupil.networking

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import xyz.quaver.pupil.hitomi.max_node_size
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.charset.Charset

const val domain = "ltn.hitomi.la"
const val galleryBlockExtension = ".html"
const val galleryBlockDir = "galleryblock"
const val nozomiExtension = ".nozomi"

const val compressedNozomiPrefix = "n"

const val B = 16
const val indexDir = "tagindex"
const val galleriesIndexDir = "galleriesindex"
const val languagesIndexDir = "languagesindex"
const val nozomiURLIndexDir = "nozomiurlindex"

data class Suggestion(
    val tag: SearchQuery.Tag,
    val count: Int
)

fun IntBuffer.toSet(): Set<Int> {
    val result = mutableSetOf<Int>()

    while (this.hasRemaining()) {
        result.add(this.get())
    }

    return result
}

object HitomiHttpClient {
    private val httpClient = HttpClient(OkHttp)

    private var _tagIndexVersion: String? = null
    private suspend fun getTagIndexVersion(): String =
        _tagIndexVersion ?: getIndexVersion("tagindex").also {
            _tagIndexVersion = it
        }

    private var _galleriesIndexVersion: String? = null
    private suspend fun getGalleriesIndexVersion(): String =
        _galleriesIndexVersion ?: getIndexVersion("galleriesindex").also {
            _galleriesIndexVersion = it
        }

    private suspend fun getIndexVersion(name: String): String = withContext(Dispatchers.IO) {
        httpClient.get("https://$domain/$name/version?_=${System.currentTimeMillis()}").bodyAsText()
    }

    private suspend fun getURLAtRange(url: String, range: LongRange): ByteBuffer {
        val response: HttpResponse = withContext(Dispatchers.IO) {
            httpClient.get(url) {
                header("Range", "bytes=${range.first}-${range.last}")
            }
        }

        val result: ByteArray = response.body()

        return ByteBuffer.wrap(result)
    }

    private suspend fun getNodeAtAddress(field: String, address: Long): Node {
        val url = when (field) {
            "galleries" -> "https://$domain/$galleriesIndexDir/galleries.${getGalleriesIndexVersion()}.index"
            "languages" -> "https://$domain/$galleriesIndexDir/languages.${getGalleriesIndexVersion()}.index"
            "nozomiurl" -> "https://$domain/$galleriesIndexDir/nozomiurl.${getGalleriesIndexVersion()}.index"
            else -> "https://$domain/$indexDir/$field.${getTagIndexVersion()}.index"
        }

        return Node.decodeNode(
            getURLAtRange(url, address ..< address+max_node_size)
        )
    }

    private suspend fun bSearch(
        field: String,
        key: Node.Key,
        node: Node
    ): Node.Data? {
        if (node.keys.isEmpty()) {
            return null
        }

        val (matched, index) = node.locateKey(key)

        if (matched) {
            return node.datas[index]
        } else if (node.isLeaf) {
            return null
        }

        val nextNode = getNodeAtAddress(field, node.subNodeAddresses[index])
        return bSearch(field, key, nextNode)
    }

    private suspend fun getGalleryIDsFromData(offset: Long, length: Int): IntBuffer {
        val url = "https://$domain/$galleriesIndexDir/galleries.${getGalleriesIndexVersion()}.data"
        if (length > 100000000 || length <= 0) {
            error("length $length is too long")
        }

        return getURLAtRange(url, offset until (offset+length)).asIntBuffer()
    }

    private suspend fun getSuggestionsFromData(field: String, data: Node.Data): List<Suggestion> {
        val url = "https://$domain/$indexDir/$field.${getTagIndexVersion()}.data"
        val (offset, length) = data

        check(data.length in 1..10000) { "Invalid length ${data.length}" }

        val buffer = getURLAtRange(url, offset..<offset+length).order(ByteOrder.BIG_ENDIAN)

        val numberOfSuggestions = buffer.int

        check(numberOfSuggestions in 1 .. 100) { "Number of suggestions $numberOfSuggestions is too long" }

        return buildList {
            for (i in 0 ..< numberOfSuggestions) {
                val namespaceLen = buffer.int
                val namespace = ByteArray(namespaceLen).apply {
                    buffer.get(this)
                }.toString(charset("UTF-8"))

                val tagLen = buffer.int
                val tag = ByteArray(tagLen).apply {
                    buffer.get(this)
                }.toString(charset("UTF-8"))

                val count = buffer.int

                add(Suggestion(SearchQuery.Tag(namespace, tag), count))
            }
        }
    }

    private suspend fun getGalleryIDsFromNozomi(
        area: String?,
        tag: String,
        language: String
    ): IntBuffer {
        val nozomiAddress = if (area == null) {
            "https://$domain/$compressedNozomiPrefix/$tag-$language$nozomiExtension"
        } else {
            "https://$domain/$compressedNozomiPrefix/$area/$tag-$language$nozomiExtension"
        }

        val response: HttpResponse = withContext(Dispatchers.IO) {
            httpClient.get(nozomiAddress)
        }

        val result: ByteArray = response.body()

        return ByteBuffer.wrap(result).asIntBuffer()
    }

    private suspend fun getGalleryIDsForQuery(query: SearchQuery.Tag, language: String = "all"): IntBuffer = when (query.namespace) {
        "female", "male" -> getGalleryIDsFromNozomi("tag", query.toString(), language)
        "language" -> getGalleryIDsFromNozomi(null, "index", query.tag)
        null -> {
            val key = Node.Key(query.tag)

            val node = getNodeAtAddress("galleries", 0)
            val data = bSearch("galleries", key, node)

            if (data != null) getGalleryIDsFromData(data.offset, data.length) else IntBuffer.allocate(0)
        }
        else -> getGalleryIDsFromNozomi(query.namespace, query.tag, language)
    }

    suspend fun getSuggestionsForQuery(query: SearchQuery.Tag): Result<List<Suggestion>> = runCatching {
        val field = query.namespace ?: "global"
        val key = Node.Key(query.tag)
        val node = getNodeAtAddress(field, 0)
        val data = bSearch(field, key, node)

        data?.let { getSuggestionsFromData(field, data) } ?: emptyList()
    }

    suspend fun search(query: SearchQuery?): Result<Set<Int>> = runCatching {
        when (query) {
            is SearchQuery.Tag -> getGalleryIDsForQuery(query).toSet()
            is SearchQuery.Not -> coroutineScope {
                val allGalleries = async {
                    getGalleryIDsFromNozomi(null, "index", "all")
                }

                val queriedGalleries = search(query.query).getOrThrow()

                val result = mutableSetOf<Int>()

                with (allGalleries.await()) {
                    while (this.hasRemaining()) {
                        val gallery = this.get()

                        if (gallery in queriedGalleries) {
                            result.add(gallery)
                        }
                    }
                }

                result
            }
            is SearchQuery.And -> coroutineScope {
                val queries = query.queries.map { query ->
                    async {
                        search(query).getOrThrow()
                    }
                }

                val result = queries.first().await().toMutableSet()

                queries.drop(1).forEach {
                    val queryResult = it.await()

                    result.retainAll(queryResult)
                }

                result
            }
            is SearchQuery.Or -> coroutineScope {
                val queries = query.queries.map { query ->
                    async {
                        search(query).getOrThrow()
                    }
                }

                val result = mutableSetOf<Int>()

                queries.forEach {
                    val queryResult = it.await()
                    result.addAll(queryResult)
                }

                result
            }
            null -> getGalleryIDsFromNozomi(null, "index", "all").toSet()
        }
    }
}