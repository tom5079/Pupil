/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2019  tom5079
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

@file:Suppress("UNUSED_VARIABLE")

package xyz.quaver.pupil

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xyz.quaver.pupil.hitomi.*
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
//    @Before
//    fun init() {
//        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
//    }

    @Before
    fun init() {
        clientBuilder = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(0, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .connectTimeout(0, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Referer", "https://hitomi.la/")
                    .build()

                chain.proceed(request)
            }
    }

    @Test
    fun test_empty() {
        print(
            "".trim()
                .replace(Regex("""^\?"""), "")
                .lowercase(Locale.getDefault())
                .split(Regex("\\s+"))
                .map {
                    it.replace('_', ' ')
                })
    }
    @Test
    fun test_nozomi() {
        val nozomi = getGalleryIDsFromNozomi(null, "index", "all")

        Log.d("PUPILD", nozomi.size.toString())
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
        val r = runBlocking {
            doSearch("language:korean")
        }

        Log.d("PUPILD", r.take(10).toString())
    }

//    @Test
//    fun test_getBlock() {
//        val galleryBlock = getGalleryBlock(2097576)
//
//        print(galleryBlock)
//    }
//
//    @Test
//    fun test_getGallery() {
//        val gallery = getGallery(2097751)
//
//        print(gallery)
//    }

    @Test
    fun test_getGalleryInfo() {
        val info = getGalleryInfo(1469394)

        print(info)
    }

    @Test
    fun test_getReader() {
        val reader = getGalleryInfo(2128654)

        Log.d("PUPILD", reader.toString())
    }

    @Test
    fun test_getImages() { runBlocking {
        val galleryID = 2128654

        val images = getGalleryInfo(galleryID).files.map {
            imageUrlFromImage(galleryID, it,false)
        }

        Log.d("PUPILD", images.toString())

//        images.forEachIndexed { index, image ->
//            println("Testing $index/${images.size}: $image")
//            val response = client.newCall(
//                Request.Builder()
//                    .url(image)
//                    .header("Referer", "https://hitomi.la/")
//                    .build()
//            ).execute()
//
//            assertEquals(200, response.code())
//
//            println("$index/${images.size} Passed")
//        }
    } }

//    @Test
//    fun test_urlFromUrlFromHash() {
//        val url = urlFromUrlFromHash(1531795, GalleryFiles(
//            212, "719d46a7556be0d0021c5105878507129b5b3308b02cf67f18901b69dbb3b5ef", 1, "00.jpg", 300
//        ), "webp")
//
//        print(url)
//    }

//    @Test
//    suspend fun test_doSearch_extreme() {
//        val query = "language:korean -tag:sample -female:humiliation -female:diaper -female:strap-on -female:squirting -female:lizard_girl -female:voyeurism -type:artistcg -female:blood -female:ryona -male:blood -male:ryona -female:crotch_tattoo -male:urethra_insertion -female:living_clothes -male:tentacles -female:slave -female:gag -male:gag -female:wooden_horse -male:exhibitionism -male:miniguy -female:mind_break -male:mind_break -male:unbirth -tag:scanmark -tag:no_penetration -tag:nudity_only -female:enema -female:brain_fuck -female:navel_fuck -tag:novel -tag:mosaic_censorship -tag:webtoon -male:rape -female:rape -female:yuri -male:anal -female:anal -female:futanari -female:huge_breasts -female:big_areolae -male:torture -male:stuck_in_wall -female:stuck_in_wall -female:torture -female:birth -female:pregnant -female:drugs -female:bdsm -female:body_writing -female:cbt -male:dark_skin -male:insect -female:insect -male:vore -female:vore -female:vomit -female:urination -female:urethra_insertion -tag:mmf_threesome -female:sex_toys -female:double_penetration -female:eggs -female:prolapse -male:smell -male:bestiality -female:bestiality -female:big_ass -female:milf -female:mother -male:dilf -male:netorare -female:netorare -female:cosplaying -female:filming -female:armpit_sex -female:armpit_licking -female:tickling -female:lactation -male:skinsuit -female:skinsuit -male:bbm -female:prostitution -female:double_penetration -female:females_only -male:males_only -female:tentacles -female:tentacles -female:stomach_deformation -female:hairy_armpits -female:large_insertions -female:mind_control -male:orc -female:dark_skin -male:yandere -female:yandere -female:scat -female:toddlercon -female:bbw -female:hairy -male:cuntboy -male:lactation -male:drugs -female:body_modification -female:monoeye -female:chikan -female:long_tongue -female:harness -female:fisting -female:glory_hole -female:latex -male:latex -female:unbirth -female:giantess -female:sole_dickgirl -female:robot -female:doll_joints -female:machine -tag:artbook -male:cbt -female:farting -male:farting -male:midget -female:midget -female:exhibitionism  -male:monster -female:big_nipples -female:big_clit -female:gyaru -female:piercing -female:necrophilia -female:snuff -female:smell -male:cheating -female:cheating -male:snuff -female:harem -male:harem"
//        print(doSearch(query).size)
//    }

//    @Test
//    suspend fun test_parse() {
//        print(doSearch("-male:yaoi -female:yaoi -female:loli").size)
//    }

//    @Test
//    fun test_subdomainFromUrl() {
//        val galleryInfo = getGalleryInfo(1929109).files[2]
//        print(urlFromUrlFromHash(1929109, galleryInfo, "webp", null, "a"))
//    }
}