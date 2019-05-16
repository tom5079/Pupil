package xyz.quaver.hitomi

import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.net.URL
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import javax.net.ssl.HttpsURLConnection

//galleryblock.js
fun fetchNozomi(area: String? = null, tag: String = "index", language: String = "all", start: Int = -1, count: Int = -1) : List<Int> {
    val url =
            when(area) {
                null -> "$protocol//$domain/$tag-$language$nozomiextension"
                else -> "$protocol//$domain/$area/$tag-$language$nozomiextension"
            }

    try {
        with(URL(url).openConnection() as HttpsURLConnection) {
            requestMethod = "GET"

            if (start != -1 && count != -1) {
                val startByte = start*4
                val endByte = (start+count)*4-1

                setRequestProperty("Range", "bytes=$startByte-$endByte")
            }

            val nozomi = ArrayList<Int>()

            val arrayBuffer = ByteBuffer
                .wrap(inputStream.readBytes())
                .order(ByteOrder.BIG_ENDIAN)

            while (arrayBuffer.hasRemaining())
                nozomi.add(arrayBuffer.int)

            return nozomi
        }
    } catch (e: Exception) {
        return emptyList()
    }
}

@Serializable
data class GalleryBlock(
    val id: Int,
    val thumbnails: List<String>,
    val title: String,
    val artists: List<String>,
    val series: List<String>,
    val type: String,
    val language: String,
    val relatedTags: List<String>
)
fun getGalleryBlock(galleryID: Int) : GalleryBlock? {
    val url = "$protocol//$domain/$galleryblockdir/$galleryID$extension"

    try {
        val doc = Jsoup.connect(url).get()

        val thumbnails = doc.select("img").map { protocol + it.attr("data-src") }

        val title = doc.selectFirst("h1.lillie > a").text()
        val artists = doc.select("div.artist-list a").map{ it.text() }
        val series = doc.select("a[href~=^/series/]").map { it.text() }
        val type = doc.selectFirst("a[href~=^/type/]").text()

        val language = {
            val href = doc.select("a[href~=^/index-.+-1.html]").attr("href")
            href.slice(7 until href.indexOf("-1"))
        }.invoke()

        val relatedTags = doc.select(".relatedtags a").map {
            val href = URLDecoder.decode(it.attr("href"), "UTF-8")
            href.slice(5 until href.indexOf('-'))
        }

        return GalleryBlock(galleryID, thumbnails, title, artists, series, type, language, relatedTags)
    } catch (e: Exception) {
        return null
    }
}