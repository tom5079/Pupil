package xyz.quaver.pupil.util

import kotlinx.serialization.json.*
import java.net.URL

fun getReleases(url: String) : JsonArray {
    return URL(url).readText().let {
        Json(JsonConfiguration.Stable).parse(JsonArray.serializer(), it)
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