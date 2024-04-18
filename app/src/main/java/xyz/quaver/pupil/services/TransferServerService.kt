package xyz.quaver.pupil.services

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import xyz.quaver.pupil.R
import xyz.quaver.pupil.favorites
import xyz.quaver.pupil.histories
import xyz.quaver.pupil.util.downloader.DownloadManager

class TransferServerService : Service() {
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private val job = Job()

    private fun startForeground() = runCatching {
        val notification = NotificationCompat.Builder(this, "transfer")
            .setContentTitle("Pupil")
            .setContentText("Transfer server is running")
            .setSmallIcon(R.drawable.ic_notification)
            .build()

        ServiceCompat.startForeground(
            this,
            1,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
        )
    }

    private fun generateListResponse(): TransferPacket.ListResponse {
        val favoritesCount = favorites.size
        val historyCount = histories.size
        val downloadsCount = DownloadManager.getInstance(this).downloadFolderMap.size
        return TransferPacket.ListResponse(favoritesCount, historyCount, downloadsCount)
    }

    private suspend fun handleConnection(socket: Socket) {
        val readChannel = socket.openReadChannel()
        val writeChannel = socket.openWriteChannel(autoFlush = true)

        runCatching {
            while (true) {
                val packet = TransferPacket.readFromChannel(readChannel)

                Log.d("PUPILD", "Received packet $packet")

                binder.channel.trySend(packet)

                val response = when (packet) {
                    is TransferPacket.Hello -> TransferPacket.Hello()
                    is TransferPacket.Ping -> TransferPacket.Pong
                    is TransferPacket.ListRequest -> generateListResponse()
                    else -> TransferPacket.Invalid
                }

                response.writeToChannel(writeChannel)
            }
        }.onFailure {
            socket.close()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra("address") ?: run {
            stopSelf(startId)
            return START_STICKY
        }

        if (serverSocket != null) {
            return START_STICKY
        }

        startForeground()

        val serverSocket = aSocket(selectorManager).tcp().bind(address, 12221).also {
            this@TransferServerService.serverSocket = it
        }

        CoroutineScope(Dispatchers.IO + job).launch {
            while (true) {
                Log.d("PUPILD", "Waiting for connection")
                val socket = serverSocket.accept()
                Log.d("PUPILD", "Accepted connection from ${socket.remoteAddress}")
                launch { handleConnection(socket) }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        serverSocket?.close()
    }

    inner class Binder: android.os.Binder() {
        val channel = Channel<TransferPacket>()
    }

    private val binder = Binder()

    override fun onBind(intent: Intent?): IBinder = binder
}