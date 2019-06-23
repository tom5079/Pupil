package xyz.quaver.pupil.util

import android.util.Log
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

    val latestVersion = releases[0].jsonObject["tag_name"]?.content

    return when {
        currentVersion.split('-').size == 1 -> {
            when {
                currentVersion != latestVersion -> releases[0].jsonObject
                else -> null
            }
        }
        else -> {
            when {
                (currentVersion.split('-')[0] == latestVersion) -> releases[0].jsonObject
                else -> null
            }
        }
    }
}