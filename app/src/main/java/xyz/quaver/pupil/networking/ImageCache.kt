package xyz.quaver.pupil.networking

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed class ImageLoadProgress {
    data object NotStarted : ImageLoadProgress()
    data class Progress(val bytesSent: Long, val contentLength: Long) : ImageLoadProgress()
    data class Finished(val file: File) : ImageLoadProgress()
    data class Error(val exception: Throwable) : ImageLoadProgress()
}

interface ImageCache {
    suspend fun load(galleryFile: GalleryFile, forceDownload: Boolean = false): StateFlow<ImageLoadProgress>
    suspend fun free(vararg files: GalleryFile)
    suspend fun clear()
}

class FileImageCache(
    private val cacheDir: File,
    private val cacheLimit: Long = 128 * 1024 * 1024 // 128MB
) : ImageCache {
    private val mutex = Mutex()

    private val requests = mutableMapOf<String, Pair<Job, StateFlow<ImageLoadProgress>>>()
    private val activeFiles = mutableMapOf<String, File>()

    private suspend fun cleanup() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val size = cacheDir.listFiles()?.sumOf { it.length() } ?: 0

            if (size > cacheLimit) {
                cacheDir.listFiles { file ->
                    file.nameWithoutExtension !in activeFiles
                }?.forEach { file ->
                    file.delete()
                }
            }
        }
    }

    override suspend fun free(vararg files: GalleryFile) = withContext(Dispatchers.IO) {
        mutex.withLock {
            files.forEach { file ->
                val hash = file.hash

                requests[hash]?.let {  (job, _) ->
                    job.cancel()
                }

                requests.remove(hash)
                activeFiles.remove(hash)
            }
        }
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            requests.forEach { _, (job, _) -> job.cancel() }
            activeFiles.clear()
            cacheDir.deleteRecursively()
        }
    }

    override suspend fun load(galleryFile: GalleryFile, forceDownload: Boolean): StateFlow<ImageLoadProgress> {
        val hash = galleryFile.hash

        mutex.withLock {
            val file = activeFiles[hash]
            if (!forceDownload && file != null) {
                return MutableStateFlow(ImageLoadProgress.Finished(file))
            }
        }

        cleanup()

        mutex.withLock {
            requests[hash]?.first?.cancelAndJoin()
            activeFiles[hash]?.delete()

            val flow = MutableStateFlow<ImageLoadProgress>(ImageLoadProgress.NotStarted)
            val job = coroutineScope {
                launch {
                    runCatching {
                        val (channel, url) = HitomiHttpClient.loadImage(galleryFile) { sent, total ->
                            flow.value = ImageLoadProgress.Progress(sent, total)
                        }.onFailure {
                            FirebaseCrashlytics.getInstance().recordException(it)
                            flow.value = ImageLoadProgress.Error(it)
                        }.getOrThrow()

                        val file = File(cacheDir, "$hash.${url.substringAfterLast('.')}")

                        mutex.withLock {
                            activeFiles.put(hash, file)
                        }

                        channel.copyAndClose(file.writeChannel())

                        file
                    }.onSuccess { file ->
                        flow.value = ImageLoadProgress.Finished(file)
                    }.onFailure {
                        activeFiles.remove(hash)
                        FirebaseCrashlytics.getInstance().recordException(it)
                        flow.value = ImageLoadProgress.Error(it)
                    }
                }
            }

            requests[hash] = job to flow

            return flow
        }
    }
}