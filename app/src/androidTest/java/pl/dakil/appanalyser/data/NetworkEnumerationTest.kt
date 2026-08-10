package pl.dakil.appanalyser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Diagnostic: prints what the app process can actually see of the device's interfaces, which is the
 * only way the hotspot is detectable (tethering exposes no `Network` object).
 */
@RunWith(AndroidJUnit4::class)
class NetworkEnumerationTest {

    @Test
    fun dumpInterfaces() {
        val whole = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }
        println("NETDUMP wholeEnumeration success=${whole.isSuccess} error=${whole.exceptionOrNull()}")
        val interfaces = whole.getOrDefault(emptyList())
        println("NETDUMP interfaceCount=${interfaces.size}")

        for (candidate in interfaces) {
            val up = runCatching { candidate.isUp }
            val loopback = runCatching { candidate.isLoopback }
            val addresses = runCatching {
                candidate.inetAddresses.toList().filterIsInstance<Inet4Address>()
                    .joinToString { it.hostAddress ?: "?" }
            }
            println(
                "NETDUMP name=${candidate.name} up=${up.getOrNull()} upErr=${up.exceptionOrNull()} " +
                    "loopback=${loopback.getOrNull()} v4=${addresses.getOrNull()} " +
                    "addrErr=${addresses.exceptionOrNull()}"
            )
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = NetworkInfoProvider(context)
        val resolved = provider.resolve()
        println("NETDUMP resolve=$resolved")

        // The invariant that actually matters: a carrier-NAT address on a cellular interface is
        // unreachable from another device, so it must never be what we advertise.
        val cellular = interfaces
            .filter { candidate ->
                runCatching { candidate.name.startsWith("rmnet") || candidate.name.startsWith("ccmni") }
                    .getOrDefault(false)
            }
            .flatMap { candidate ->
                runCatching {
                    candidate.inetAddresses.toList().filterIsInstance<Inet4Address>()
                        .mapNotNull { it.hostAddress }
                }.getOrDefault(emptyList())
            }
        if (resolved != null) {
            assertFalse(
                "resolved a cellular address: ${resolved.ipAddress}",
                resolved.ipAddress in cellular,
            )
        }
    }
}
