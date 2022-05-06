/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2021 tom5079
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
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

const val GROUP_ID = "xyz.quaver"
const val VERSION = "6.0.0-alpha02"

object Versions {
    const val KOTLIN = "1.6.10"
    const val COROUTINE = "1.6.1"
    const val SERIALIZATION = "1.3.2"

    const val JETPACK_COMPOSE = "1.2.0-alpha07"
    const val ACCOMPANIST = "0.23.1"

    const val KTOR = "2.0.0"
}

object Kotlin {
    const val SERIALIZATION = "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.SERIALIZATION}"
    const val COROUTINE = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.COROUTINE}"
    const val COROUTINE_TEST = "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.COROUTINE}"
}

object JetpackCompose {
    const val UI = "androidx.compose.ui:ui:${Versions.JETPACK_COMPOSE}"
    const val UI_TOOLING = "androidx.compose.ui:ui-tooling:${Versions.JETPACK_COMPOSE}"
    const val FOUNDATION = "androidx.compose.foundation:foundation:${Versions.JETPACK_COMPOSE}"
    const val MATERIAL = "androidx.compose.material:material:${Versions.JETPACK_COMPOSE}"
    const val MATERIAL_ICONS = "androidx.compose.material:material-icons-extended:${Versions.JETPACK_COMPOSE}"
    const val RUNTIME_LIVEDATA = "androidx.compose.runtime:runtime-livedata:${Versions.JETPACK_COMPOSE}"
    const val UI_UTIL = "androidx.compose.ui:ui-util:${Versions.JETPACK_COMPOSE}"
    const val ANIMATION = "androidx.compose.animation:animation:${Versions.JETPACK_COMPOSE}"

    const val MARKDOWN = "com.github.jeziellago:compose-markdown:0.2.9"
}

object Accompanist {
    const val FLOW_LAYOUT = "com.google.accompanist:accompanist-flowlayout:${Versions.ACCOMPANIST}"
    const val APPCOMPAT_THEME = "com.google.accompanist:accompanist-appcompat-theme:${Versions.ACCOMPANIST}"
    const val INSETS = "com.google.accompanist:accompanist-insets:${Versions.ACCOMPANIST}"
    const val INSETS_UI = "com.google.accompanist:accompanist-insets-ui:${Versions.ACCOMPANIST}"
    const val DRAWABLE_PAINTER = "com.google.accompanist:accompanist-drawablepainter:${Versions.ACCOMPANIST}"
    const val SYSTEM_UI_CONTROLLER = "com.google.accompanist:accompanist-systemuicontroller:${Versions.ACCOMPANIST}"
}

object KtorClient {
    const val CORE = "io.ktor:ktor-client-core:${Versions.KTOR}"
    const val OKHTTP = "io.ktor:ktor-client-okhttp:${Versions.KTOR}"
    const val CONTENT_NEGOTIATION = "io.ktor:ktor-client-content-negotiation:${Versions.KTOR}"
    const val SERIALIZATION = "io.ktor:ktor-serialization-kotlinx-json:${Versions.KTOR}"

    const val TEST = "io.ktor:ktor-client-mock:${Versions.KTOR}"
}