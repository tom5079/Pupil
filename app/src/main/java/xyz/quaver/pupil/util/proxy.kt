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

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        return if (host.isNullOrBlank() || port == null)
            return Proxy.NO_PROXY
        else
            Proxy(type, InetSocketAddress.createUnresolved(host, port))
    }

    // TODO: Migrate to ktor-client and implement proxy authentication
}

fun getProxyInfo(): ProxyInfo =
    Json.decodeFromString(Preferences["proxy", Json.encodeToString(ProxyInfo(Proxy.Type.DIRECT))])