package xyz.quaver.pupil.networking

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

const val domain = "ltn.hitomi.la"
const val galleryBlockExtension = ".html"
const val galleryBlockDir = "galleryblock"
const val nozomiExtension = ".nozomi"

const val compressedNozomiPrefix = "n"

const val B = 16
const val indexDir = "tagindex"
const val maxNodeSize = 464
const val galleriesIndexDir = "galleriesindex"
const val languagesIndexDir = "languagesindex"
const val nozomiURLIndexDir = "nozomiurlindex"

data class Suggestion(
    val tag: SearchQuery.Tag,
    val count: Int
)

fun IntBuffer.toSet(): Set<Int> {
    val result = LinkedHashSet<Int>()

    while (this.hasRemaining()) {
        result.add(this.get())
    }

    return result
}

private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

class ImagePathResolver(ggjs: String) {
    private val defaultPrefix: Int = Regex("var o = (\\d)").find(ggjs)!!.groupValues[1].toInt()
    private val prefixMap: Map<Int, Int> = buildMap {
        val o = Regex("o = (\\d); break;").find(ggjs)!!.groupValues[1].toInt()

        Regex("case (\\d+):").findAll(ggjs).forEach {
            val case = it.groupValues[1].toInt()
            put(case, o)
        }
    }

    private val imageBaseDir: String = Regex("b: '(.+)'").find(ggjs)!!.groupValues[1]

    fun decodeSubdomain(hash: String, thumbnail: Boolean): String {
        val key = (hash.last() + hash.dropLast(1).takeLast(2)).toInt(16)
        val base = if (thumbnail) "tn" else "a"

        return "${'a' + (prefixMap[key] ?: defaultPrefix)}$base"
    }

    fun decodeImagePath(hash: String, thumbnail: Boolean): String {
        val key = hash.last() to hash.dropLast(1).takeLast(2)

        return if (thumbnail) {
            "${key.first}/${key.second}/$hash"
        } else {
            "$imageBaseDir/${(key.first + key.second).toInt(16)}/$hash"
        }
    }
}

class ExpirableEntry<T>(
    private val expiryDuration: Duration,
    private val action: suspend () -> T
) {
    private var value: T? = null
    private var expiresAt: Instant = now()

    private val mutex = Mutex()

    suspend fun getValue(): T = mutex.withLock {
        value?.let { if (expiresAt > now()) value else null } ?: action().also {
            expiresAt = now() + expiryDuration
            value = it
        }
    }
}

object HitomiHttpClient {
    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                sslSocketFactory(SSLSettings.sslContext!!.socketFactory, SSLSettings.trustManager!!)
            }
        }
    }

    private var imagePathResolver = ExpirableEntry(1.minutes) {
        ImagePathResolver(httpClient.get("https://ltn.hitomi.la/gg.js").bodyAsText())
    }

    private val tagIndexVersion = ExpirableEntry(1.minutes) { getIndexVersion("tagindex") }
    private val galleriesIndexVersion = ExpirableEntry(1.minutes) { getIndexVersion("galleriesindex") }

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
            "galleries" -> "https://$domain/$galleriesIndexDir/galleries.${galleriesIndexVersion.getValue()}.index"
            "languages" -> "https://$domain/$galleriesIndexDir/languages.${galleriesIndexVersion.getValue()}.index"
            "nozomiurl" -> "https://$domain/$galleriesIndexDir/nozomiurl.${galleriesIndexVersion.getValue()}.index"
            else -> "https://$domain/$indexDir/$field.${HitomiHttpClient.tagIndexVersion.getValue()}.index"
        }

        return Node.decodeNode(
            getURLAtRange(url, address ..< address + maxNodeSize)
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
        val url = "https://$domain/$galleriesIndexDir/galleries.${galleriesIndexVersion.getValue()}.data"
        if (length > 100000000 || length <= 0) {
            error("length $length is too long")
        }

        return getURLAtRange(url, offset until (offset+length)).asIntBuffer()
    }

    private suspend fun getSuggestionsFromData(field: String, data: Node.Data): List<Suggestion> {
        val url = "https://$domain/$indexDir/$field.${tagIndexVersion.getValue()}.data"
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

    suspend fun getGalleryInfo(galleryID: Int) = runCatching {
        withContext(Dispatchers.IO) {
            json.decodeFromString<GalleryInfo>(
                httpClient.get("https://$domain/galleries/$galleryID.js").bodyAsText()
                    .replace("var galleryinfo = ", "")
            )
        }
    }

    suspend fun search(query: SearchQuery?): Result<Set<Int>> = runCatching {
        when (query) {
            is SearchQuery.Tag -> getGalleryIDsForQuery(query).toSet()
            is SearchQuery.Not -> coroutineScope {
                val allGalleries = async {
                    getGalleryIDsFromNozomi(null, "index", "all")
                }

                val queriedGalleries = search(query.query).getOrThrow()

                val result = LinkedHashSet<Int>()

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

                val result = LinkedHashSet<Int>()

                queries.forEach {
                    val queryResult = it.await()
                    result.addAll(queryResult)
                }

                result
            }
            null -> getGalleryIDsFromNozomi(null, "index", "all").toSet()
        }
    }

    suspend fun getImageURL(galleryFile: GalleryFile, thumbnail: Boolean = false): List<String> = buildList {
        val imagePathResolver = imagePathResolver.getValue()

        listOf("webp", "avif", "jxl").forEach { type ->
            val available = when {
                thumbnail && type != "jxl" -> true
                type == "webp" -> galleryFile.hasWebP != 0
                type == "avif" -> galleryFile.hasAVIF != 0
                !thumbnail && type == "jxl" -> galleryFile.hasJXL != 0
                else -> false
            }

            if (!available) return@forEach

            val url = buildString {
                append("https://")
                append(imagePathResolver.decodeSubdomain(galleryFile.hash, thumbnail))
                append(".hitomi.la/")
                append(type)
                if (thumbnail) append("bigtn")
                append('/')
                append(imagePathResolver.decodeImagePath(galleryFile.hash, thumbnail))
                append('.')
                append(type)
            }

            add(url)
        }
    }

    suspend fun loadImage(
        galleryFile: GalleryFile,
        thumbnail: Boolean = false,
        acceptImage: (String) -> Boolean = { true },
        onDownload: (bytesSentTotal: Long, contentLength: Long) -> Unit = { _, _ -> }
    ): Result<Pair<ByteReadChannel, String>> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val url = getImageURL(galleryFile, thumbnail).firstOrNull(acceptImage) ?: error("No available image")
                val channel: ByteReadChannel = httpClient.get(url) { onDownload(onDownload) }.body()
                Pair(channel, url)
            }
        }
    }

}