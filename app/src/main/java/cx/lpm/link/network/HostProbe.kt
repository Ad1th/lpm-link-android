package cx.lpm.link.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Races multiple host candidates concurrently and returns the first reachable one.
 * Used during pairing when the QR code provides multiple candidate addresses
 * (e.g., LAN IP + Tailscale IP).
 */
@Singleton
class HostProbe @Inject constructor() {

    companion object {
        private const val PROBE_TIMEOUT_MS = 6_000L
        private const val CONNECT_TIMEOUT_MS = 4_000
    }

    /**
     * Race all [hosts] on [port] and return the first that accepts a TCP connection.
     * Returns null if none are reachable within the timeout.
     */
    suspend fun race(hosts: List<String>, port: Int): String? = coroutineScope {
        if (hosts.isEmpty()) return@coroutineScope null
        if (hosts.size == 1) {
            return@coroutineScope if (probe(hosts[0], port)) hosts[0] else null
        }

        val results = hosts.map { host ->
            async {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                    if (probe(host, port)) host else null
                }
            }
        }

        // Return first non-null result
        for (deferred in results) {
            val result = deferred.await()
            if (result != null) {
                // Cancel remaining probes
                results.forEach { it.cancel() }
                return@coroutineScope result
            }
        }
        null
    }

    /**
     * Probe a single host by attempting a TCP connection.
     */
    private fun probe(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
