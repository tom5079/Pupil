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

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.quaver.availableInHiyobi

class UnitTest {
    @Test
    fun test() {
        assertEquals(
            "6/2d/c26014fc6153ef717932d85f4d26c75195560fb2ce1da60b431ef376501642d6",
            fullPathFromHash("c26014fc6153ef717932d85f4d26c75195560fb2ce1da60b431ef376501642d6")
        )
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
        val gallery = getGallery(1552751)

        print(gallery)
    }

    @Test
    fun test_getReader() {
        val reader = getReader(1574736)

        print(reader)
    }

    @Test
    fun test_getImages() {
        val reader = getReader(1702001)

        print(urlFromUrlFromHash(1702001, reader.galleryInfo.files[15], "webp"))
    }

    @Test
    fun test_hiyobi() {
        val reader = xyz.quaver.hiyobi.getReader(1664762)

        print(reader)
    }

    @Test
    fun test_urlFromUrlFromHash() {
        val url = urlFromUrlFromHash(1531795, GalleryFiles(
            212, "719d46a7556be0d0021c5105878507129b5b3308b02cf67f18901b69dbb3b5ef", 1, "00.jpg", 300
        ), "webp")

        print(url)
    }

    @Test
    fun test_availableInHiyobi() {
        val result = availableInHiyobi(1272781)

        print(result)
    }

    @Test
    fun test_hiyobi_galleryBlock() {
        val galleryBlock = xyz.quaver.hiyobi.getGalleryBlock(10000027)

        print(galleryBlock)
    }
}