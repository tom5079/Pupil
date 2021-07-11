/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI
import org.kodein.di.instance
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.ProxyDialogBinding
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.ProxyInfo
import xyz.quaver.pupil.util.getProxyInfo
import java.net.Proxy

class ProxyDialogFragment : DialogFragment(), DIAware {

    override val di: DI by closestDI()

    private var _binding: ProxyDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = ProxyDialogBinding.inflate(layoutInflater)

        initView()

        return AlertDialog.Builder(requireContext()).apply {
            setView(binding.root)
        }.create()
    }

    private fun initView() {
        val proxyInfo = getProxyInfo()

        val enabler = { enable: Boolean ->
            binding.addr.isEnabled = enable
            binding.port.isEnabled = enable
            binding.username.isEnabled = enable
            binding.password.isEnabled = enable

            if (!enable) {
                binding.addr.text = null
                binding.port.text = null
                binding.username.text = null
                binding.password.text = null
            }
        }

        with(binding.typeSelector) {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                context.resources.getStringArray(R.array.proxy_type)
            )

            setSelection(proxyInfo.type.ordinal)

            onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    enabler.invoke(position != 0)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        binding.addr.setText(proxyInfo.host)
        binding.port.setText(proxyInfo.port?.toString())
        binding.username.setText(proxyInfo.username)
        binding.password.setText(proxyInfo.password)

        enabler.invoke(proxyInfo.type != Proxy.Type.DIRECT)

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.okButton.setOnClickListener {
            val type = Proxy.Type.values()[binding.typeSelector.selectedItemPosition]
            val addr = binding.addr.text?.toString()
            val port = binding.port.text?.toString()?.toIntOrNull()
            val username = binding.username.text?.toString()
            val password = binding.password.text?.toString()

            if (type != Proxy.Type.DIRECT) {
                if (addr == null || addr.isEmpty())
                    binding.addr.error = requireContext().getText(R.string.proxy_dialog_error)
                if (port == null)
                    binding.port.error = requireContext().getText(R.string.proxy_dialog_error)

                if (addr == null || addr.isEmpty() || port == null)
                    return@setOnClickListener
            }

            ProxyInfo(type, addr, port, username, password).let {
                Preferences["proxy"] = Json.encodeToString(it)
                // TODO
            }

            dismiss()
        }
    }

}