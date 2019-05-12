package xyz.quaver.pupil

import org.junit.Test
import xyz.quaver.pupil.util.checkUpdate

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

class ExampleUnitTest {

    @Test
    fun test() {
        print(checkUpdate("https://api.github.com/repos/tom5079/Pupil-issue/releases", "0.0.1"))
    }

}
