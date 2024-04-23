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

    var numberFoundAction: ((galleryID: Int) -> Unit)? = null

    fun checkClipboardOnFocus() {

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
                        numberFoundAction?.let {
                            it(galleryID)
                            clearClipboard()
                        }
                    }
                }
            }
    }

    fun clearClipboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboardManager.clearPrimaryClip()
        } else {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

}