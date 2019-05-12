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

fun getApkUrl(releases: JsonObject, releaseName: String) : Pair<String?, String?>? {
    releases["assets"]?.jsonArray?.forEach {
        if (Regex(releaseName).matches(it.jsonObject["name"]?.content ?: ""))
            return Pair(it.jsonObject["browser_download_url"]?.content, it.jsonObject["name"]?.content)
    }

    return null
}