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

import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.bindInstance
import org.kodein.di.instance
import java.util.*

private val filesURL = "https://api.github.com/repos/tom5079/Pupil/git/trees/tags"
private val contentURL = "https://raw.githubusercontent.com/tom5079/Pupil/tags/"

private var translations: Map<String, String> = emptyMap()

fun updateTranslations(client: HttpClient) = CoroutineScope(Dispatchers.IO).launch {
    translations = emptyMap()
    kotlin.runCatching {
        translations = client.get<Map<String, String>>("$contentURL${Preferences["hitomi.tag_translation", Locale.getDefault().language]}.json").filterValues { it.isNotEmpty() }
    }
}

fun getAvailableLanguages(client: HttpClient): List<String> {
    val languages = Locale.getISOLanguages()

    val json = runCatching { runBlocking { Json.parseToJsonElement(client.get(filesURL)) } }.getOrNull()

    return listOf("en") + (json?.get("tree")?.jsonArray?.mapNotNull {
        val name = it["path"]?.jsonPrimitive?.content?.takeWhile { c -> c != '.' }

        languages.firstOrNull { code -> code.equals(name, ignoreCase = true) }
    } ?: emptyList())
}