package xyz.quaver.pupil.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.quaver.pupil.R
import xyz.quaver.pupil.receiver.WifiDirectBroadcastReceiver
import xyz.quaver.pupil.services.TransferClientService
import xyz.quaver.pupil.services.TransferPacket
import xyz.quaver.pupil.services.TransferServerService
import xyz.quaver.pupil.ui.fragment.TransferConnectedFragment
import xyz.quaver.pupil.ui.fragment.TransferDirectionFragment
import xyz.quaver.pupil.ui.fragment.TransferPermissionFragment
import xyz.quaver.pupil.ui.fragment.TransferSelectDataFragment
import xyz.quaver.pupil.ui.fragment.TransferTargetFragment
import xyz.quaver.pupil.ui.fragment.TransferWaitForConnectionFragment
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class TransferActivity : AppCompatActivity(R.layout.transfer_activity) {

    private val viewModel: TransferViewModel by viewModels()

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel

    private var receiver: BroadcastReceiver? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setStep(TransferStep.TARGET)
        } else {
            viewModel.setStep(TransferStep.PERMISSION)
        }
    }

    private var clientServiceBinder: TransferClientService.Binder? = null

    private val clientServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            clientServiceBinder = service as TransferClientService.Binder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            clientServiceBinder = null
        }
    }

    private fun checkPermission(force: Boolean = false): Boolean {
        val permissionRequired = if (VERSION.SDK_INT < VERSION_CODES.TIRAMISU) {
            Manifest.permission.ACCESS_FINE_LOCATION
        } else {
            Manifest.permission.NEARBY_WIFI_DEVICES
        }

        val permissionGranted =
            ActivityCompat.checkSelfPermission(this, permissionRequired) == PackageManager.PERMISSION_GRANTED

        val shouldShowRationale =
            ActivityCompat.shouldShowRequestPermissionRationale(this, permissionRequired)

        if (!permissionGranted) {
            if (shouldShowRationale && force) {
                viewModel.setStep(TransferStep.PERMISSION)
            } else {
                requestPermissionLauncher.launch(permissionRequired)
            }
            return false
        }

        return true
    }

    private fun handleServerResponse(response: TransferPacket?) {
        when (response) {
            is TransferPacket.ListResponse -> {
                Log.d("PUPILD", "Received list response $response")
            }
            else -> {
                Log.d("PUPILD", "Received invalid response $response")
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        manager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        viewModel.peerToConnect.observe(this) { peer ->
            if (peer == null) { return@observe }
            if (!checkPermission()) { return@observe }

            val config = WifiP2pConfig().apply {
                deviceAddress = peer.deviceAddress
                wps.setup = WpsInfo.PBC
            }

            manager.connect(channel, config, object: WifiP2pManager.ActionListener {
                override fun onSuccess() { }

                override fun onFailure(reason: Int) {
                    viewModel.connect(null)
                }
            })
        }
        lifecycleScope.launch {
            viewModel.messageQueue.consumeEach {
                clientServiceBinder?.sendPacket(it)?.getOrNull()?.let(::handleServerResponse)
            }
        }

        lifecycleScope.launch {
            viewModel.step.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collectLatest step@{ step ->
                when (step) {
                    TransferStep.TARGET,
                    TransferStep.TARGET_FORCE -> {
                        if (!checkPermission(step == TransferStep.TARGET_FORCE)) {
                            return@step
                        }

                        manager.discoverPeers(channel, object: WifiP2pManager.ActionListener {
                            override fun onSuccess() { }

                            override fun onFailure(reason: Int) {
                            }
                        })

                        supportFragmentManager.commit {
                            replace(R.id.fragment_container_view, TransferTargetFragment())
                        }

                        val hostAddress = viewModel.connectionInfo.filterNotNull().first {
                            it.groupFormed
                        }.groupOwnerAddress.hostAddress

                        val intent = Intent(this@TransferActivity, TransferClientService::class.java).also {
                            it.putExtra("address", hostAddress)
                        }
                        ContextCompat.startForegroundService(this@TransferActivity, intent)
                        bindService(intent, clientServiceConnection, BIND_AUTO_CREATE)

                        viewModel.setStep(TransferStep.SELECT_DATA)
                    }
                    TransferStep.DIRECTION -> {
                        supportFragmentManager.commit {
                            replace(R.id.fragment_container_view, TransferDirectionFragment())
                        }
                    }
                    TransferStep.PERMISSION -> {
                        supportFragmentManager.commit {
                            replace(R.id.fragment_container_view, TransferPermissionFragment())
                        }
                    }
                    TransferStep.WAIT_FOR_CONNECTION -> {
                        Log.d("PUPILD", "wait for connection")
                        if (!checkPermission()) { return@step }

                        runCatching {
                            suspendCoroutine { continuation ->
                                manager.removeGroup(channel, object: WifiP2pManager.ActionListener {
                                    override fun onSuccess() {
                                        continuation.resume(Unit)
                                    }

                                    override fun onFailure(reason: Int) {
                                        continuation.resume(Unit)
                                    }
                                })
                            }

                            suspendCoroutine { continuation ->
                                manager.cancelConnect(channel, object: WifiP2pManager.ActionListener {
                                    override fun onSuccess() {
                                        continuation.resume(Unit)
                                    }

                                    override fun onFailure(reason: Int) {
                                        continuation.resume(Unit)
                                    }
                                })
                            }

                            suspendCoroutine { continuation ->
                                manager.createGroup(channel, object: WifiP2pManager.ActionListener {
                                    override fun onSuccess() {
                                        continuation.resume(Unit)
                                    }

                                    override fun onFailure(reason: Int) {
                                        continuation.resumeWithException(Exception("Failed to create group $reason"))
                                    }
                                })
                            }

                            supportFragmentManager.commit {
                                replace(R.id.fragment_container_view, TransferWaitForConnectionFragment())
                            }

                            val address = viewModel.connectionInfo.filterNotNull().first {
                                it.groupFormed && it.isGroupOwner
                            }.groupOwnerAddress.hostAddress

                            val intent = Intent(this@TransferActivity, TransferServerService::class.java).also {
                                it.putExtra("address", address)
                            }
                            ContextCompat.startForegroundService(this@TransferActivity, intent)
                            val binder: TransferServerService.Binder = suspendCoroutine { continuation ->
                                bindService(intent, object: ServiceConnection {
                                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                                        continuation.resume(service as TransferServerService.Binder)
                                    }

                                    override fun onServiceDisconnected(name: ComponentName?) { }
                                }, BIND_AUTO_CREATE)
                            }
                            binder.channel.receive()

                            viewModel.setStep(TransferStep.CONNECTED)
                        }.onFailure {
                            Log.e("PUPILD", "Failed to create group", it)
                        }

                        supportFragmentManager.commit {
                            replace(R.id.fragment_container_view, TransferWaitForConnectionFragment())
                        }
                    }
                    TransferStep.CONNECTED -> {
                        supportFragmentManager.commit {
                            replace(R.id.fragment_container_view, TransferConnectedFragment())
                        }
                    }
                    TransferStep.SELECT_DATA -> {
                        supportFragmentManager.commit {
                            replace(R.id.fragment_container_view, TransferSelectDataFragment())
                        }
                    }
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        bindService(Intent(this, TransferClientService::class.java), clientServiceConnection, BIND_AUTO_CREATE)
        WifiDirectBroadcastReceiver(manager, channel, viewModel).also {
            receiver = it
            registerReceiver(it, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        unbindService(clientServiceConnection)
        receiver?.let { unregisterReceiver(it) }
        receiver = null
    }
}

enum class TransferStep {
    TARGET, TARGET_FORCE, DIRECTION, PERMISSION, WAIT_FOR_CONNECTION, CONNECTED, SELECT_DATA
}

enum class ErrorType {
}

class TransferViewModel : ViewModel() {
    private val _step: MutableStateFlow<TransferStep> = MutableStateFlow(TransferStep.DIRECTION)
    val step: StateFlow<TransferStep> = _step

    private val _error = MutableLiveData<ErrorType?>(null)
    val error: LiveData<ErrorType?> = _error

    private val _wifiP2pEnabled: MutableLiveData<Boolean> = MutableLiveData(false)
    val wifiP2pEnabled: LiveData<Boolean> = _wifiP2pEnabled

    private val _thisDevice: MutableLiveData<WifiP2pDevice?> = MutableLiveData(null)
    val thisDevice: LiveData<WifiP2pDevice?> = _thisDevice

    private val _peers: MutableLiveData<WifiP2pDeviceList?> = MutableLiveData(null)
    val peers: LiveData<WifiP2pDeviceList?> = _peers

    private val _connectionInfo: MutableStateFlow<WifiP2pInfo?> = MutableStateFlow(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo

    private val _peerToConnect: MutableLiveData<WifiP2pDevice?> = MutableLiveData(null)
    val peerToConnect: LiveData<WifiP2pDevice?> = _peerToConnect

    val messageQueue: Channel<TransferPacket> = Channel()

    fun setStep(step: TransferStep) {
        Log.d("PUPILD", "Set step: $step")
        _step.value = step
    }

    fun setWifiP2pEnabled(enabled: Boolean) {
        _wifiP2pEnabled.value = enabled
    }

    fun setThisDevice(device: WifiP2pDevice?) {
        _thisDevice.value = device
    }

    fun setPeers(peers: WifiP2pDeviceList?) {
        _peers.value = peers
    }

    fun setConnectionInfo(info: WifiP2pInfo?) {
        _connectionInfo.value = info
    }

    fun setError(error: ErrorType?) {
        _error.value = error
    }

    fun connect(device: WifiP2pDevice?) {
        _peerToConnect.value = device
    }

    fun ping() {
        messageQueue.trySend(TransferPacket.Ping)
    }

    fun list() {
        messageQueue.trySend(TransferPacket.ListRequest)
    }
}