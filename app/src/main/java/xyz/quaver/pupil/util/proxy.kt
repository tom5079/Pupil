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

package xyz.quaver.pupil.util

import android.content.Context
import androidx.preference.PreferenceManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Credentials
import java.net.InetSocketAddress
import java.net.Proxy

@Serializable
data class ProxyInfo(
    val type: Proxy.Type,
    val host: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val password: String? = null
) {
    fun proxy() : Proxy {
        return if (host == null || port == null)
            return Proxy.NO_PROXY
        else
            Proxy(type, InetSocketAddress.createUnresolved(host, port))
    }

    fun authenticator() = Authenticator { _, response ->
        val credential = Credentials.basic(username ?: "", password ?: "")

        response.request().newBuilder()
            .header("Proxy-Authorization", credential)
            .build()
    }

}

fun getProxy(context: Context) =
    getProxyInfo(context).proxy()

fun getProxyInfo(context: Context) =
    PreferenceManager.getDefaultSharedPreferences(context).getString("proxy", null).let {
        if (it == null)
            ProxyInfo(Proxy.Type.DIRECT)
        else
            Json.decodeFromString(it)
    }