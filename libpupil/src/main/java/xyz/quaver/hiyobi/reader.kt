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

package xyz.quaver.hiyobi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.content
import org.jsoup.Jsoup
import xyz.quaver.hitomi.Reader
import xyz.quaver.hitomi.ReaderItem
import java.net.URL
import javax.net.ssl.HttpsURLConnection

const val hiyobi = "xn--9w3b15m8vo.asia"
const val user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.131 Safari/537.36"

class HiyobiReader(title: String, readerItems: List<ReaderItem>) : Reader(title, readerItems)

var cookie: String = ""
    get() {
        if (field.isEmpty())
            field  = renewCookie()

        return field
    }

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

fun getReader(galleryID: Int) : Reader {
    val reader = "https://$hiyobi/reader/$galleryID"
    val url = "https://$hiyobi/data/json/${galleryID}_list.json"

    val title = Jsoup.connect(reader).get().title()

    val json = Json(JsonConfiguration.Stable).parseJson(
        with(URL(url).openConnection() as HttpsURLConnection) {
            setRequestProperty("User-Agent", user_agent)
            setRequestProperty("Cookie", cookie)
            connectTimeout = 2000
            connect()

            inputStream.bufferedReader().use { it.readText() }
        }
    )

    return Reader(title, json.jsonArray.map {
        val name = it.jsonObject["name"]!!.content
        ReaderItem("https://$hiyobi/data/$galleryID/$name", null)
    })
}