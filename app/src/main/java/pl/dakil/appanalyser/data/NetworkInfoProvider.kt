package pl.dakil.appanalyser.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.ShareNetwork
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Works out which local network the share server should advertise itself on.
 *
 * Uses only install-time permissions (`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`). The SSID is the
 * one piece that needs a runtime location permission, so it is resolved separately and degrades to
 * null rather than to Android's `<unknown ssid>` placeholder.
 */
class NetworkInfoProvider(private val context: Context) {

    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    /**
     * The best interface to serve on right now, or null when the device is on neither Wi-Fi nor a
     * hotspot. Preference order: connected Wi-Fi, then an active hotspot, then any other
     * site-local address — Wi-Fi first because clients are already attached to it.
     */
    fun resolve(): ShareNetwork? = wifiNetwork() ?: hotspotNetwork() ?: otherLocalNetwork()

    private fun wifiNetwork(): ShareNetwork? {
        val manager = connectivityManager ?: return null
        val network = manager.activeNetwork ?: return null
        val capabilities = manager.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        val address = manager.getLinkProperties(network)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
            ?: return null

        return ShareNetwork(ShareNetwork.Kind.WIFI, address, ssid())
    }

    /**
     * Tethering exposes no [android.net.Network], so the AP has to be spotted by walking the
     * interface list. Names differ per OEM, hence the 192.168.43.x fallback (the historical
     * Android hotspot subnet).
     */
    private fun hotspotNetwork(): ShareNetwork? {
        val address = localIpv4Addresses()
            .firstOrNull { (name, ip) ->
                name.startsWith("ap") || name.startsWith("swlan") || name == "wlan1" ||
                    ip.startsWith("192.168.43.")
            }
            ?.second
            ?: return null
        // The tethering SSID is not readable through any public API.
        return ShareNetwork(ShareNetwork.Kind.HOTSPOT, address, null)
    }

    private fun otherLocalNetwork(): ShareNetwork? {
        val address = localIpv4Addresses().firstOrNull()?.second ?: return null
        return ShareNetwork(ShareNetwork.Kind.OTHER, address, null)
    }

    /** Non-loopback IPv4 addresses on up interfaces, as (interface name, address) pairs. */
    private fun localIpv4Addresses(): List<Pair<String, String>> = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface ->
                networkInterface.inetAddresses.asSequence()
                    .filter { it is Inet4Address && !it.isLoopbackAddress }
                    .mapNotNull { it.hostAddress?.let { address -> networkInterface.name to address } }
            }
            .toList()
    }.getOrDefault(emptyList())

    /**
     * The connected network's SSID, or null when it cannot be read — which is the normal case
     * until the user grants a location permission, and also when location services are off.
     */
    fun ssid(): String? {
        if (!hasLocationPermission()) return null
        val manager = connectivityManager ?: return null
        val network = manager.activeNetwork ?: return null
        val capabilities = manager.getNetworkCapabilities(network) ?: return null
        val info = capabilities.transportInfo as? WifiInfo ?: return null
        val raw = info.ssid ?: return null
        val cleaned = raw.trim('"')
        return cleaned.takeUnless { it.isBlank() || it == UNKNOWN_SSID }
    }

    fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Display label for a network, falling back to a generic name when the SSID is unavailable. */
    fun label(network: ShareNetwork): String = network.name ?: context.getString(
        when (network.kind) {
            ShareNetwork.Kind.WIFI -> R.string.share_network_wifi
            ShareNetwork.Kind.HOTSPOT -> R.string.share_network_hotspot
            ShareNetwork.Kind.OTHER -> R.string.share_network_other
        }
    )

    private companion object {
        const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
