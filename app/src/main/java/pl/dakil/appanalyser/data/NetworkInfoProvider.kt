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
     * The best interface to serve on right now, or null when nothing is reachable from another
     * device. Preference order: connected Wi-Fi, then an active hotspot, then any other local
     * address — Wi-Fi first because clients are already attached to it.
     *
     * Everything is driven off the interface list rather than [ConnectivityManager.activeNetwork]:
     * tethering has no [android.net.Network] at all, and the active network is often mobile data
     * even while Wi-Fi is connected. ConnectivityManager is used only to *label* which interface is
     * the Wi-Fi client.
     */
    fun resolve(): ShareNetwork? {
        val candidates = reachableIpv4Addresses()
        if (candidates.isEmpty()) return null

        val wifiNames = wifiInterfaceNames()
        candidates.firstOrNull { it.first in wifiNames }?.let { (_, address) ->
            return ShareNetwork(ShareNetwork.Kind.WIFI, address, ssid())
        }
        candidates.firstOrNull { looksLikeHotspot(it.first, it.second) }?.let { (_, address) ->
            // The tethering SSID is not readable through any public API.
            return ShareNetwork(ShareNetwork.Kind.HOTSPOT, address, null)
        }
        return candidates.first().let { ShareNetwork(ShareNetwork.Kind.OTHER, it.second, null) }
    }

    /** Interfaces the platform reports as carrying a Wi-Fi client connection. */
    private fun wifiInterfaceNames(): Set<String> {
        val manager = connectivityManager ?: return emptySet()
        return runCatching {
            @Suppress("DEPRECATION") // The callback API cannot answer a one-shot question.
            manager.allNetworks
                .filter {
                    manager.getNetworkCapabilities(it)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                }
                .mapNotNull { manager.getLinkProperties(it)?.interfaceName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * Names differ per OEM, so this also accepts the historical 192.168.43.x subnet and, more
     * generally, holding the `.1` of a private subnet — which is what being the access point means.
     */
    private fun looksLikeHotspot(name: String, address: String): Boolean =
        name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan") ||
            name == "wlan1" || address.startsWith("192.168.43.") ||
            (isPrivate(address) && address.endsWith(".1"))

    private fun isPrivate(address: String): Boolean =
        address.startsWith("192.168.") || address.startsWith("10.") ||
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(address)

    /**
     * IPv4 addresses another device on the same network could actually connect to, as
     * (interface name, address) pairs. Cellular and virtual interfaces are dropped: a carrier-NAT
     * address like `10.184.x.x` on `rmnet` is unreachable, and advertising it is worse than saying
     * nothing. Each interface is probed independently so one that refuses to answer cannot wipe out
     * the whole list.
     */
    private fun reachableIpv4Addresses(): List<Pair<String, String>> {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }
            .getOrNull()
            .orEmpty()

        return interfaces.flatMap { networkInterface ->
            runCatching {
                val name = networkInterface.name.orEmpty()
                if (!networkInterface.isUp || networkInterface.isLoopback) return@runCatching emptyList()
                if (EXCLUDED_PREFIXES.any { name.startsWith(it) }) return@runCatching emptyList()
                networkInterface.inetAddresses.asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }
                    .mapNotNull { address -> address.hostAddress?.let { name to it } }
                    .toList()
            }.getOrDefault(emptyList())
        }
    }

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

        /** Cellular and virtual interfaces: never reachable from another device on the LAN. */
        val EXCLUDED_PREFIXES = listOf(
            "rmnet", "ccmni", "pdp", "clat", "v4-", "dummy", "sit", "tun", "ppp", "rndis",
        )
    }
}
