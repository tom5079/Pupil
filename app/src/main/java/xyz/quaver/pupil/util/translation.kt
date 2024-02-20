/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import xyz.quaver.pupil.client
import java.io.IOException
import java.util.*

private val filesURL = "https://api.github.com/repos/tom5079/Pupil/git/trees/tags"
private val contentURL = "https://raw.githubusercontent.com/tom5079/Pupil/tags/"

var translations: Map<String, String> = run {
    updateTranslations()
    emptyMap()
}
    private set

@Suppress("BlockingMethodInNonBlockingContext")
fun updateTranslations() = CoroutineScope(Dispatchers.IO).launch {
    translations = emptyMap()
    kotlin.runCatching {
        translations = Json.decodeFromString<Map<String, String>>(client.newCall(
                Request.Builder()
                    .url(contentURL + "${Preferences["tag_translation", ""].let { if (it.isEmpty()) Locale.getDefault().language else it }}.json")
                    .build()
            ).execute().also { if (it.code != 200) return@launch }.body?.use { it.string() } ?: return@launch).filterValues { it.isNotEmpty() }
    }
}

fun getAvailableLanguages(): List<String> {
    val languages = Locale.getISOLanguages()

    val json = Json.parseToJsonElement(client.newCall(
        Request.Builder()
            .url(filesURL)
            .build()
    ).execute().also { if (it.code != 200) throw IOException() }.body?.use { it.string() } ?: return emptyList())

    return listOf("en") + (json["tree"]?.jsonArray?.mapNotNull {
        val name = it["path"]?.jsonPrimitive?.content?.takeWhile { c -> c != '.' }

        languages.firstOrNull { code -> code.equals(name, ignoreCase = true) }
    } ?: emptyList())
}