package xyz.quaver.pupil.util

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

fun getCachedGallery(context: Context, galleryID: Int): File {
    return File(context.getExternalFilesDir("Pupil"), galleryID.toString()).let {
        when {
            it.exists() -> it
            else -> File(context.cacheDir, "imageCache/$galleryID")
        }
    }
}