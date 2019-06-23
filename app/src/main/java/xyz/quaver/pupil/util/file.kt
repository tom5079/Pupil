package xyz.quaver.pupil.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File

fun getCachedGallery(context: Context, galleryID: Int): File {
    return File(getDownloadDirectory(context), galleryID.toString()).let {
        when {
            it.exists() -> it
            else -> File(context.cacheDir, "imageCache/$galleryID")
        }
    }
}

fun getDownloadDirectory(context: Context): File? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        context.getExternalFilesDir("Pupil")
    else
        File(Environment.getExternalStorageDirectory(), "Pupil")
}