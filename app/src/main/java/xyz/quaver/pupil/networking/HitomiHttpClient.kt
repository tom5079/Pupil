package xyz.quaver.pupil.networking

import androidx.collection.mutableIntSetOf
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
import java.nio.IntBuffer

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

fun IntBuffer.toSet(): Set<Int> {
    val result = mutableSetOf<Int>()

    while (this.hasRemaining()) {
        result.add(this.get())
    }

    return result
}

class HitomiHttpClient {
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
            getURLAtRange(url, address until (address+max_node_size))
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

    suspend fun getGalleryIDsFromNozomi(
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

    suspend fun getGalleryIDsForQuery(query: SearchQuery.Tag, language: String = "all"): IntBuffer = when (query.namespace) {
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

    suspend fun search(query: SearchQuery?): Set<Int> = when (query) {
        is SearchQuery.Tag -> getGalleryIDsForQuery(query).toSet()
        is SearchQuery.Not -> coroutineScope {
            val allGalleries = async {
                getGalleryIDsFromNozomi(null, "index", "all")
            }

            val queriedGalleries = search(query.query)

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
                    search(query)
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
                    search(query)
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