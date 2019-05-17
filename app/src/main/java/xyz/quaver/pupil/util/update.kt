package xyz.quaver.pupil.util

import kotlinx.io.IOException
import kotlinx.serialization.json.*
import java.net.URL

fun getReleases(url: String) : JsonArray {
    return try {
        URL(url).readText().let {
            Json(JsonConfiguration.Stable).parse(JsonArray.serializer(), it)
        }
    } catch (e: Exception) {
        JsonArray(emptyList())
    }
}

fun checkUpdate(url: String, currentVersion: String) : JsonObject? {
    val releases = getReleases(url)

    if (releases.isEmpty())
        return null

    if (currentVersion != releases[0].jsonObject["tag_name"]?.content)
        return releases[0].jsonObject

    return null
}