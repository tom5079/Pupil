package xyz.quaver.pupil.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.IntentFilter
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
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xyz.quaver.pupil.R
import xyz.quaver.pupil.receiver.WifiDirectBroadcastReceiver
import xyz.quaver.pupil.ui.fragment.TransferDirectionFragment
import xyz.quaver.pupil.ui.fragment.TransferPermissionFragment
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
                override fun onSuccess() {
                    Log.d("PUPILD", "Connection successful")
                }

                override fun onFailure(reason: Int) {
                    Log.d("PUPILD", "Connection failed: $reason")
                    viewModel.setPeers(null)
                }
            })
        }

        viewModel.connectionInfo.observe(this) { info ->
            if (info == null) { return@observe }

            if (info.groupFormed && info.isGroupOwner) {
                // Do something
                Log.d("PUPILD", "Group formed and is group owner")
                Log.d("PUPILD", "Group owner IP: ${info.groupOwnerAddress.hostAddress}")
            } else if (info.groupFormed) {
                // Do something
                Log.d("PUPILD", "Group formed")
                Log.d("PUPILD", "Group owner IP: ${info.groupOwnerAddress.hostAddress}")
                Log.d("PUPILD", "Local IP: ${info.groupOwnerAddress.hostAddress}")
                Log.d("PUPILD", "Is group owner: ${info.isGroupOwner}")
            }
        }

        lifecycleScope.launch {
            viewModel.step.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { step ->
                when (step) {
                    TransferStep.TARGET,
                    TransferStep.TARGET_FORCE -> {
                        if (!checkPermission(step == TransferStep.TARGET_FORCE)) {
                            return@collect
                        }

                        manager.discoverPeers(channel, object: WifiP2pManager.ActionListener {
                            override fun onSuccess() { }

                            override fun onFailure(reason: Int) {
                            }
                        })

                        supportFragmentManager.commit {
                            replace(R.id.fragment_container_view, TransferTargetFragment())
                        }
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
                        if (!checkPermission()) { return@collect }

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
                        }.onFailure {
                            Log.e("PUPILD", "Failed to create group", it)
                        }

                        supportFragmentManager.commit {
                            replace(R.id.fragment_container_view, TransferWaitForConnectionFragment())
                        }
                    }
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        WifiDirectBroadcastReceiver(manager, channel, viewModel).also {
            receiver = it
            registerReceiver(it, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        receiver?.let { unregisterReceiver(it) }
        receiver = null
    }
}

enum class TransferStep {
    TARGET, TARGET_FORCE, DIRECTION, PERMISSION, WAIT_FOR_CONNECTION
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

    private val _connectionInfo: MutableLiveData<WifiP2pInfo?> = MutableLiveData(null)
    val connectionInfo: LiveData<WifiP2pInfo?> = _connectionInfo

    private val _peerToConnect: MutableLiveData<WifiP2pDevice?> = MutableLiveData(null)
    val peerToConnect: LiveData<WifiP2pDevice?> = _peerToConnect

    fun setStep(step: TransferStep) {
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

    fun connect(device: WifiP2pDevice) {
        _peerToConnect.value = device
    }
}