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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import xyz.quaver.pupil.R
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class TransferClientService : Service() {
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val channel = Channel<Pair<TransferPacket, Continuation<TransferPacket>>>()
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
                TransferPacket.Hello().writeToChannel(writeChannel)
                val handshake = TransferPacket.readFromChannel(readChannel)

                if (handshake !is TransferPacket.Hello || handshake.version != TRANSFER_PROTOCOL_VERSION) {
                    throw IllegalStateException("Invalid handshake")
                }

                while (true) {
                    val (packet, continuation) = channel.receive()

                    Log.d("PUPILD", "Sending packet $packet")

                    packet.writeToChannel(writeChannel)

                    val response = TransferPacket.readFromChannel(readChannel).also {
                        Log.d("PUPILD", "Received packet $it")
                    }

                    continuation.resume(response)
                }
            }.onFailure {
                Log.d("PUPILD", "Connection closed with error $it")
                channel.close()
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


        @OptIn(DelicateCoroutinesApi::class)
        suspend fun sendPacket(packet: TransferPacket): Result<TransferPacket.ListResponse> = runCatching {
            check(job != null) { "Service not running" }
            check(!channel.isClosedForSend) { "Service not running" }

            val response = suspendCoroutine { continuation ->
                check (channel.trySend(packet to continuation).isSuccess) { "Service not running" }
            }

            check (response is TransferPacket.ListResponse) { "Invalid response" }

            response
        }
    }

    private val binder = Binder()

    override fun onBind(intent: Intent?): IBinder = binder
}