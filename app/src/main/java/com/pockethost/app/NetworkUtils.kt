package com.pockethost.app

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Collections

object NetworkUtils {

    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in Collections.list(iface.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("192.168.43.") || host.startsWith("192.168.42.")) {
                            return host
                        }
                    }
                }
            }
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in Collections.list(iface.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: SocketException) {}
        return "127.0.0.1"
    }

    fun isHotspotOn(context: Context): Boolean {
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return try {
            val m = wifi.javaClass.getDeclaredMethod("isWifiApEnabled")
            m.isAccessible = true
            m.invoke(wifi) as? Boolean ?: false
        } catch (_: Exception) {
            val ip = getLocalIpAddress()
            ip.startsWith("192.168.43.") || ip.startsWith("192.168.42.")
        }
    }
}
