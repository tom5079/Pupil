package xyz.quaver.hiyobi

import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.content
import xyz.quaver.hitomi.Reader
import xyz.quaver.hitomi.ReaderItem
import java.net.URL
import javax.net.ssl.HttpsURLConnection

const val hiyobi = "xn--9w3b15m8vo.asia"
const val user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.131 Safari/537.36"

var cookie: String = ""

fun renewCookie() : String {
    val url = "https://$hiyobi/"

    try {
        with(URL(url).openConnection() as HttpsURLConnection) {
            setRequestProperty("User-Agent", user_agent)
            connectTimeout = 2000
            connect()
            return headerFields["Set-Cookie"]!![0]
        }
    } catch (e: Exception) {
        return ""
    }
}

fun getReader(galleryId: Int) : Reader {
    val url = "https://$hiyobi/data/json/${galleryId}_list.json"

    if (cookie.isEmpty())
        cookie = renewCookie()

    try {
        val json = Json(JsonConfiguration.Stable).parseJson(
            with(URL(url).openConnection() as HttpsURLConnection) {
                setRequestProperty("User-Agent", user_agent)
                setRequestProperty("Cookie", cookie)
                connectTimeout = 2000
                connect()

                inputStream.bufferedReader().use { it.readText() }
            }
        )

        return json.jsonArray.map {
            val name = it.jsonObject["name"]!!.content
            ReaderItem("https://$hiyobi/data/$galleryId/$name", null)
        }
    } catch (e: Exception) {
        return emptyList()
    }
}