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

package xyz.quaver

import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import java.net.Proxy

var proxy : Proxy = Proxy.NO_PROXY

@OptIn(UnstableDefault::class)
var json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    serializeSpecialFloatingPointValues = true
    useArrayPolymorphism = true
}

fun availableInHiyobi(galleryID: Int) : Boolean {
    return try {
        xyz.quaver.hiyobi.getReader(galleryID)
        true
    } catch (e: Exception) {
        false
    }
}