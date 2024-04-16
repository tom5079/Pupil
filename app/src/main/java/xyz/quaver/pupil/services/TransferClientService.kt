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
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import xyz.quaver.pupil.R

class TransferClientService : Service() {
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val channel = Channel<String>()
    private var job: Job? = null

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
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra("address") ?: run {
            stopSelf(startId)
            return START_STICKY
        }

        startForeground()

        Log.d("PUPILD", "Starting service with address $address")

        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            Log.d("PUPILD", "Connecting to $address")

            val socket = aSocket(selectorManager).tcp().connect(address, 12221)

            Log.d("PUPILD", "Connected to $address")

            val readChannel = socket.openReadChannel()
            val writeChannel = socket.openWriteChannel(autoFlush = true)

            runCatching {
                while (true) {
                    val message = channel.receive()
                    Log.d("PUPILD", "Sending message $message!")
                    writeChannel.writeStringUtf8(message)
                    Log.d("PUPILD", readChannel.readUTF8Line(4).toString())
                }
            }.onFailure {
                socket.close()
                stopSelf(startId)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }

    inner class Binder: android.os.Binder() {
        fun sendMessage(message: String) {
            Log.d("PUPILD", "Sending message $message")
            channel.trySendBlocking(message + '\n')
        }
    }

    private val binder = Binder()

    override fun onBind(intent: Intent?): IBinder = binder
}