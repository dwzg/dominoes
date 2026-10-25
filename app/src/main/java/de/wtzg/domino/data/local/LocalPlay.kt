package de.wtzg.domino.data.local

import de.wtzg.domino.data.online.OnlineException
import de.wtzg.domino.protocol.Limits
import de.wtzg.domino.server.GameService
import de.wtzg.domino.server.InMemoryStore
import de.wtzg.domino.server.LiveHub
import de.wtzg.domino.server.dominoServer
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI

/*
 * Games on the same Wi-Fi, with no server to set up: the host's phone runs the same game
 * server as online play, and everyone (the host included) talks to it over the local network.
 *
 * That traffic is plain HTTP, because there's no certificate for a phone's local address.
 * The app's network security config keeps HTTPS for everything else, so local games use
 * Ktor's CIO client, which talks straight to the socket, and only ever for addresses on the
 * local network (see [isLocalNetworkAddress]).
 */

/** The port a hosted game tries first, so the address stays the same between games. */
const val LOCAL_PORT = 8077

/** True for http:// addresses on a private network (10.x, 172.16-31.x, 192.168.x): the same Wi-Fi. */
fun isLocalNetworkAddress(url: String): Boolean {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
    if (uri.scheme != "http") return false
    val parts = uri.host?.split('.') ?: return false
    if (parts.size != 4) return false
    val n = parts.map { it.toIntOrNull()?.takeIf { v -> v in 0..255 } ?: return false }
    return n[0] == 10 || (n[0] == 172 && n[1] in 16..31) || (n[0] == 192 && n[1] == 168)
}

/** A game hosted on someone's phone: where to reach it, and its code. */
data class LocalInvite(val server: String, val code: String)

private val LOCAL_INVITE = Regex("""^(http://[0-9.]+(?::\d+)?)/join/([A-Za-z0-9]+)/?(?:[?#].*)?$""")

/** The hosted game in a scanned QR code or a typed address, or null if it isn't one. */
fun localInviteFrom(text: String): LocalInvite? {
    val m = LOCAL_INVITE.find(text.trim()) ?: return null
    val server = m.groupValues[1]
    val code = m.groupValues[2].uppercase()
    if (code.length != Limits.CODE_LENGTH || !isLocalNetworkAddress(server)) return null
    return LocalInvite(server, code)
}

/**
 * The host's address as people type it ("192.168.1.5:8077" or "192.168.1.5"), turned into
 * a server address, or null if it isn't one on the local network.
 */
fun localServerFrom(typed: String): String? {
    val t = typed.trim().removePrefix("http://").trimEnd('/')
    if (t.isEmpty()) return null
    val url = if (':' in t) "http://$t" else "http://$t:$LOCAL_PORT"
    return url.takeIf(::isLocalNetworkAddress)
}

/**
 * This phone's address on the Wi-Fi, or on the hotspot it's providing; null when it's on
 * neither. Wi-Fi and hotspot interfaces come first, so mobile data addresses aren't picked.
 */
fun localNetworkAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .sortedBy { i -> if (listOf("wlan", "swlan", "ap", "softap").any { i.name.startsWith(it) }) 0 else 1 }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .mapNotNull { it.hostAddress }
        .firstOrNull { isLocalNetworkAddress("http://$it") }
}.getOrNull()

/**
 * The game server this phone hosts for players on the same Wi-Fi. Games live only in memory:
 * they end when hosting stops or the app is closed.
 */
class LocalHost(private val address: () -> String? = ::localNetworkAddress) {
    private val lock = Mutex()
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val _url = MutableStateFlow<String?>(null)

    /** Where others reach the hosted games ("http://192.168.1.5:8077"), or null when not hosting. */
    val url: StateFlow<String?> = _url.asStateFlow()

    /** Starts hosting (once) and returns the address to share. */
    suspend fun start(): String = lock.withLock {
        _url.value?.let { return it }
        val ip = address() ?: throw OnlineException("Connect to Wi-Fi first, or turn on your phone's hotspot.")
        val s = withContext(Dispatchers.IO) { listen(LOCAL_PORT) ?: listen(0) }
            ?: throw OnlineException("Couldn't start hosting on this phone.")
        val port = s.engine.resolvedConnectors().first().port
        server = s
        "http://$ip:$port".also { _url.value = it }
    }

    /** Stops hosting; games on this phone end. */
    suspend fun stop() = lock.withLock {
        val s = server ?: return@withLock
        server = null
        _url.value = null
        withContext(Dispatchers.IO) { s.stop(200, 1_000) }
    }

    private fun listen(port: Int) = runCatching {
        embeddedServer(CIO, port = port, host = "0.0.0.0") {
            dominoServer(GameService(InMemoryStore()), LiveHub())
        }.start(wait = false)
    }.getOrNull()
}
