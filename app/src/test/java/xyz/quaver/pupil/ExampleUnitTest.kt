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

@file:Suppress("UNUSED_VARIABLE", "IncorrectScope")

package xyz.quaver.pupil

import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import xyz.quaver.pupil.sources.manatoki.getItem
import org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

class ExampleUnitTest {

    @Test
    fun test() {
        val itemID = 145360

        val client = HttpClient()

        runBlocking {
            client.getItem(
                itemID.toString(),
                onReader = {
                    assertEquals(
                        listOf(
                            "https://newtoki15.org/data/editor/1901/1077963982_tTXMCveG_fb60ec5a563ca8ab9fd5297aee678f0753001844",
                            "https://newtoki15.org/data/editor/1901/1077963982_gkhHpOSi_49d9003f22e05d7a70b9b877d105a9496cb382b6",
                            "https://newtoki15.org/data/editor/1901/1077963982_1IUNPaSk_fe889333f982b2f85ffdcc8eaf7d1ec435cae4d8",
                            "https://newtoki15.org/data/editor/1901/1077963982_DdnS7iux_a72d128adb7334bd2aa45fa0ebc888405d2d6158",
                            "https://newtoki15.org/data/editor/1901/1077963982_vLQun9me_76a6914ff06c6b9df3ed69d43524e5b7eb7d063c",
                            "https://newtoki15.org/data/editor/1901/1077963982_hRnmqsBz_47695027ed3c1e039bd61a4bd059bd977218dceb",
                            "https://newtoki15.org/data/editor/1901/1077963982_aQB3cyXP_bbc55416bed60989c57150ce8d1dd7476e7e4573",
                            "https://newtoki15.org/data/editor/1901/1077963982_4ulxFtgy_50d903e92bc4e4e610c65d2f67d46ec679abb3b1",
                            "https://newtoki15.org/data/editor/1901/1077963982_Jf8cvnm6_9fe96de962feaffe72bc96be1e1255bb50d1ec6a",
                            "https://newtoki15.org/data/editor/1901/1077963982_C7rUVlhL_f968ff4e44e850bc38e9e18f0ef25b5cc3de376f",
                            "https://newtoki15.org/data/editor/1901/1077963982_pEJB2Fl7_095351ba621239059891f3f41476de529014b17e",
                            "https://newtoki15.org/data/editor/1901/1077963982_6PN0QgvO_ed11c8baeb3565d86b3563d7760beee10d302364",
                            "https://newtoki15.org/data/editor/1901/1077963982_s73REfCc_c373538bf02c14dc7d9f195b2893c50380cfd74f",
                            "https://newtoki15.org/data/editor/1901/1077963982_UxiyBoPO_4cb9f733c9bab6552f380ce49f7ec9fc5a35e0a7",
                            "https://newtoki15.org/data/editor/1901/1077963982_VHa1wSsp_94be272f1947be8731b474aaf966d2395ed32a4e",
                            "https://newtoki15.org/data/editor/1901/1077963982_hXrkCWlN_ed908b9d1febbb875ce79d04fd52bdb9132ff980",
                            "https://newtoki15.org/data/editor/1901/1077963982_voBeugVP_a491b8fdf4dc033a7cb0393fbafd8d505eb776b9",
                            "https://newtoki15.org/data/editor/1901/1077963982_ZVCMAjoJ_29eca6af62de41b228590e61748208572c78db61"
                        ),
                        it.urls
                    )
                }
            )
        }
    }

}
