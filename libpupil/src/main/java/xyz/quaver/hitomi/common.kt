package xyz.quaver.hitomi

const val protocol = "https:"

//common.js
var adapose = false
const val numberOfFrontends = 2
const val domain = "ltn.hitomi.la"
const val galleryblockdir = "galleryblock"
const val nozomiextension = ".nozomi"

fun subdomainFromGalleryID(g: Int) : String {
    if (adapose)
        return "0"

    val o = g % numberOfFrontends

    return (97+o).toChar().toString()
}

fun subdomainFromURL(url: String, base: String? = null) : String {
    var retval = "a"

    if (base != null)
        retval = base

    val r = Regex("""/\d*(\d)/""")
    val m = r.find(url)

    m ?: return retval

    var g = m.groups[1]!!.value.toIntOrNull()

    g ?: return retval

    if (g == 1)
        g = 0

    retval = subdomainFromGalleryID(g) + retval

    return retval
}

fun urlFromURL(url: String, base: String? = null) : String {
    return url.replace(Regex("//..?\\.hitomi\\.la/"), "//${subdomainFromURL(url, base)}.hitomi.la/")
}