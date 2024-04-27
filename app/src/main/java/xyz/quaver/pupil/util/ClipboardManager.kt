package xyz.quaver.pupil.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

class ClipboardHelper(
    context: Context
) {
    private val clipboardManager: ClipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private var isFocus: Boolean = false

    // Focus를 체크하는 이유는 안드로이드10 이상에서 background에서 접근하려하면 에러나기때문
    fun updateFocus(focus: Boolean) {
        this.isFocus = focus
    }

    fun checkClipboardOnFocus(completion: (Int) -> Unit) {
        if (isFocus) {
            clipboardManager
                .primaryClip
                ?.getItemAt(0)
                ?.text
                ?.toString()
                ?.let { clipData ->
                    if (clipData.length == 7) {
                        runCatching {
                            clipData.toInt()
                        }.onSuccess { galleryID ->
                            completion(galleryID)
                        }
                    }
                }
        }
    }
}