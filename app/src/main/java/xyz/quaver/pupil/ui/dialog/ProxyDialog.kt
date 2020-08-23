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

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.dialog_proxy.view.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.quaver.proxy
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.ProxyInfo
import xyz.quaver.pupil.util.getProxyInfo
import java.net.Proxy

class ProxyDialog(context: Context) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        val view = build()

        setTitle(R.string.settings_proxy_title)
        setContentView(view)

        window?.attributes?.width = ViewGroup.LayoutParams.MATCH_PARENT

        super.onCreate(savedInstanceState)
    }

    @SuppressLint("InflateParams")
    private fun build() : View {
        val proxyInfo = getProxyInfo(context)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_proxy, null)

        val enabler = { enable: Boolean ->
            view?.proxy_addr?.isEnabled = enable
            view?.proxy_port?.isEnabled = enable
            view?.proxy_username?.isEnabled = enable
            view?.proxy_password?.isEnabled = enable

            if (!enable) {
                view?.proxy_addr?.text = null
                view?.proxy_port?.text = null
                view?.proxy_username?.text = null
                view?.proxy_password?.text = null
            }
        }

        with(view.proxy_type_selector) {
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

        view.proxy_addr.setText(proxyInfo.host)
        view.proxy_port.setText(proxyInfo.port?.toString())
        view.proxy_username.setText(proxyInfo.username)
        view.proxy_password.setText(proxyInfo.password)

        enabler.invoke(proxyInfo.type != Proxy.Type.DIRECT)

        view.proxy_cancel.setOnClickListener {
            dismiss()
        }

        view.proxy_ok.setOnClickListener {
            val type = Proxy.Type.values()[view.proxy_type_selector.selectedItemPosition]
            val addr = view.proxy_addr.text?.toString()
            val port = view.proxy_port.text?.toString()?.toIntOrNull()
            val username = view.proxy_username.text?.toString()
            val password = view.proxy_password.text?.toString()

            if (type != Proxy.Type.DIRECT) {
                if (addr == null || addr.isEmpty())
                    view.proxy_addr.error = context.getText(R.string.proxy_dialog_error)
                if (port == null)
                    view.proxy_port.error = context.getText(R.string.proxy_dialog_error)

                if (addr == null || addr.isEmpty() || port == null)
                    return@setOnClickListener
            }

            ProxyInfo(type, addr, port, username, password).let {

                PreferenceManager.getDefaultSharedPreferences(context).edit().putString("proxy",
                    Json.encodeToString(it)
                ).apply()

                proxy = it.proxy()
            }

            dismiss()
        }

        return view
    }

}