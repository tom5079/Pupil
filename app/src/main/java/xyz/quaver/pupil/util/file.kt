package xyz.quaver.pupil.util

import android.content.Context
import android.os.Environment
import java.io.File

fun getCachedGallery(context: Context, galleryID: Int): File {
    return File(Environment.getExternalStorageDirectory(), "Pupil/$galleryID").let {
        when {
            it.exists() -> it
            else -> File(context.cacheDir, "imageCache/$galleryID")
        }
    }
}