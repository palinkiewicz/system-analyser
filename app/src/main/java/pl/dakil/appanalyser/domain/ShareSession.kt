package pl.dakil.appanalyser.domain

/** How a share session is protected from others on the same network. */
enum class ShareAccessMode {
    /** Bare `/dakil_share/` — anyone who knows the address gets in. */
    OPEN,

    /** `/dakil_share/<random>/` — the path segment is the secret. */
    TOKEN,

    /** A 4-digit code entered on a login page, kept in a session cookie. */
    PIN,
}

/**
 * Which interface the server is reachable on. Only the *kind* is recorded, never the network name:
 * reading an SSID would cost a location permission this app deliberately does not ask for.
 */
data class ShareNetwork(
    val kind: Kind,
    val ipAddress: String,
) {
    enum class Kind { WIFI, HOTSPOT, OTHER }
}

data class ShareEndpoint(
    val ipAddress: String,
    val port: Int,
    /** Always starts and ends with a slash, e.g. `/dakil_share/` or `/dakil_share/a7f3c9d1/`. */
    val basePath: String,
) {
    /**
     * What the user is shown and asked to type. The trailing slash is dropped for readability; the
     * server redirects the slashless form to [basePath], so both resolve.
     */
    val url: String get() = "http://$ipAddress:$port${basePath.trimEnd('/')}"
}

sealed interface ShareState {
    data object Stopped : ShareState

    data class Running(
        val endpoint: ShareEndpoint,
        val network: ShareNetwork,
        val sharedPath: String,
        val sharedName: String,
        /** Non-null only in [ShareAccessMode.PIN]. */
        val pin: String?,
    ) : ShareState
}
