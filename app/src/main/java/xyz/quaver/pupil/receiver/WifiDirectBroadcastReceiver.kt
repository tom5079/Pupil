package xyz.quaver.pupil.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Parcelable
import android.util.Log
import androidx.core.app.ActivityCompat
import xyz.quaver.pupil.ui.ErrorType
import xyz.quaver.pupil.ui.TransferStep
import xyz.quaver.pupil.ui.TransferViewModel

private inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? = when {
    Build.VERSION.SDK_INT >= 33 -> getParcelableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
}

class WifiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val viewModel: TransferViewModel
): BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
        context!!
        when (intent?.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                Log.d("PUPILD", "Wifi P2P state changed: $state")
                viewModel.setWifiP2pEnabled(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                Log.d("PUPILD", "Wifi P2P peers changed")
                manager.requestPeers(channel) { peers ->
                    viewModel.setPeers(peers)
                }
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                // Respond to new connection or disconnections
                val networkInfo = intent.getParcelableExtraCompat<android.net.NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)

                Log.d("PUPILD", "Wifi P2P connection changed: $networkInfo ${networkInfo?.isConnected}")

                if (networkInfo?.isConnected == true) {
                    manager.requestConnectionInfo(channel) { info ->
                        viewModel.setConnectionInfo(info)
                    }
                } else {
                    viewModel.setConnectionInfo(null)
                }
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                // Respond to this device's wifi state changing
                Log.d("PUPILD", "Wifi P2P this device changed")
                viewModel.setThisDevice(intent.getParcelableExtraCompat(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE))
            }
        }
    }
}