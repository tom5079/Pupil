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
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import xyz.quaver.pupil.R

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

    private suspend fun handleConnection(socket: Socket) {
        val readChannel = socket.openReadChannel()
        val writeChannel = socket.openWriteChannel(autoFlush = true)

        runCatching {
            while (true) {
                if (readChannel.readUTF8Line(8) == "ping") {
                    writeChannel.writeStringUtf8("pong\n")
                }
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
        fun getService() = this@TransferServerService
    }

    private val binder = Binder()

    override fun onBind(intent: Intent?): IBinder = binder
}