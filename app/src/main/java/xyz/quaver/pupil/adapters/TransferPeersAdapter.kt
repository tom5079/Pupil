package xyz.quaver.pupil.adapters

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.recyclerview.widget.RecyclerView
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.TransferPeerListItemBinding

class TransferPeersAdapter(
    private val devices: Collection<WifiP2pDevice>,
    private val onDeviceSelected: (WifiP2pDevice) -> Unit
): RecyclerView.Adapter<TransferPeersAdapter.ViewHolder>() {

    class ViewHolder(val binding: TransferPeerListItemBinding): RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = TransferPeerListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices.elementAt(position)

        holder.binding.deviceName.text = device.deviceName
        holder.binding.deviceAddress.text = device.deviceAddress

        holder.binding.root.setOnClickListener {
            onDeviceSelected(device)
        }
    }

    override fun getItemCount(): Int {
        return devices.size
    }
}