package pl.dakil.appanalyser.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.ShareNetwork
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Works out which local network the share server should advertise itself on.
 *
 * Uses only install-time permissions (`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`) and asks for no
 * runtime permission at all. The network *name* is deliberately out of reach: Android gates the SSID
 * behind a location permission, so the UI reports the connection kind instead.
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
            return ShareNetwork(ShareNetwork.Kind.WIFI, address)
        }
        candidates.firstOrNull { looksLikeHotspot(it.first, it.second) }?.let { (_, address) ->
            return ShareNetwork(ShareNetwork.Kind.HOTSPOT, address)
        }
        return candidates.first().let { ShareNetwork(ShareNetwork.Kind.OTHER, it.second) }
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

    /** How to describe the connection in the UI — a kind, never a network name. */
    fun label(network: ShareNetwork): String = context.getString(
        when (network.kind) {
            ShareNetwork.Kind.WIFI -> R.string.share_network_wifi
            ShareNetwork.Kind.HOTSPOT -> R.string.share_network_hotspot
            ShareNetwork.Kind.OTHER -> R.string.share_network_other
        }
    )

    private companion object {
        /** Cellular and virtual interfaces: never reachable from another device on the LAN. */
        val EXCLUDED_PREFIXES = listOf(
            "rmnet", "ccmni", "pdp", "clat", "v4-", "dummy", "sit", "tun", "ppp", "rndis",
        )
    }
}
