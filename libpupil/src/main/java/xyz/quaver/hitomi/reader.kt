package xyz.quaver.hitomi

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.list
import org.jsoup.Jsoup
import xyz.quaver.hiyobi.HiyobiReader
import java.net.URL

fun getReferer(galleryID: Int) = "https://hitomi.la/reader/$galleryID.html"
fun webpUrlFromUrl(url: String) = url.replace("/galleries/", "/webp/") + ".webp"

fun webpReaderFromReader(reader: Reader) : Reader {
    if (reader is HiyobiReader)
        return reader

    return Reader(reader.title, reader.readerItems.map {
        ReaderItem(
            if (it.galleryInfo?.haswebp == 1) webpUrlFromUrl(it.url) else it.url,
            it.galleryInfo
        )
    })
}

@Serializable
data class GalleryInfo(
    val width: Int,
    val haswebp: Int,
    val name: String,
    val height: Int
)
@Serializable
data class ReaderItem(
    val url: String,
    val galleryInfo: GalleryInfo?
)

@Serializable
open class Reader(val title: String, val readerItems: List<ReaderItem>)

//Set header `Referer` to reader url to avoid 403 error
fun getReader(galleryID: Int) : Reader {
    val readerUrl = "https://hitomi.la/reader/$galleryID.html"
    val galleryInfoUrl = "https://ltn.hitomi.la/galleries/$galleryID.js"

    val doc = Jsoup.connect(readerUrl).get()

    val title = doc.title()

    val images = doc.select(".img-url").map {
        protocol + urlFromURL(it.text())
    }

    val galleryInfo = ArrayList<GalleryInfo?>()

    galleryInfo.addAll(
        Json(JsonConfiguration.Stable).parse(
            GalleryInfo.serializer().list,
            Regex("""\[.+]""").find(
                URL(galleryInfoUrl).readText()
            )?.value ?: "[]"
        )
    )

    if (images.size > galleryInfo.size)
        galleryInfo.addAll(arrayOfNulls(images.size - galleryInfo.size))

    return Reader(title, (images zip galleryInfo).map {
        ReaderItem(it.first, it.second)
    })
}