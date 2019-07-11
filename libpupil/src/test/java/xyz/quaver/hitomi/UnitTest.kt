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

@file:Suppress("UNUSED_VARIABLE")

package xyz.quaver.hitomi

import org.junit.Test

class UnitTest {
    @Test
    fun test() {

    }

    @Test
    fun test_nozomi() {
        val nozomi = getGalleryIDsFromNozomi(null, "popular", "all")

        print(nozomi.size)
    }

    @Test
    fun test_search() {
        val ids = getGalleryIDsForQuery("language:korean").reversed()

        print(ids.size)
    }

    @Test
    fun test_suggestions() {
        val suggestions = getSuggestionsForQuery("language:g")

        print(suggestions)
    }

    @Test
    fun test_doSearch() {
        val r = doSearch("female:loli female:bondage language:korean -male:yaoi -male:guro -female:guro", true)

        print(r.size)
    }

    @Test
    fun test_getBlock() {
        val galleryBlock = getGalleryBlock(1428250)

        print(galleryBlock)
    }

    @Test
    fun test_getGallery() {
        val gallery = getGallery(1405267)

        print(gallery)
    }

    @Test
    fun test_getReader() {
        val reader = getReader(1442740)

        print(reader)
    }

    @Test
    fun test_hiyobi() {

    }
}