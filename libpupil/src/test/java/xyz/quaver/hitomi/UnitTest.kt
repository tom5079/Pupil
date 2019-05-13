package xyz.quaver.hitomi

import org.junit.Test
import java.net.URL

class UnitTest {
    @Test
    fun test() {
        val url = URL("https://ltn.hitomi.la/galleries/1411672.js")

        print(url.path.substring(url.path.lastIndexOf('/')+1))
    }

    @Test
    fun test_nozomi() {
        val nozomi = fetchNozomi(start = 0, count = 5)

        for (n in nozomi)
            println(n)
    }

    @Test
    fun test_search() {
        val ids = getGalleryIDsForQuery("female:loli").reversed()

        for (i in 0..100)
            println(ids[i])
    }

    @Test
    fun test_suggestions() {
        val suggestions = getSuggestionsForQuery("language:g")

        print(suggestions)
    }

    @Test
    fun test_doSearch() {
        val r = doSearch("type:artistcg language:korean female:loli female:mind_break -female:anal")

        print(r.size)
    }

    @Test
    fun test_getBlock() {
        val galleryBlock = getGalleryBlock(1405716)

        print(galleryBlock)
    }

    @Test
    fun test_getGallery() {
        val gallery = getGallery(1405267)

        print(gallery)
    }

    @Test
    fun test_getReader() {
        val reader = getReader(1404693)

        print(reader)
    }

    @Test
    fun test_hiyobi() {
        xyz.quaver.hiyobi.getReader(1414061)
    }
}