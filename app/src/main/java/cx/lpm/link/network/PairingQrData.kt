package cx.lpm.link.network

import java.net.URI

/**
 * Parses lpm://pair QR code URIs.
 *
 * Format: lpm://pair?p=<port>&c=<code>&h=<host>&h=<host>&f=<fingerprint>
 */
data class PairingQrData(
    val port: Int,
    val code: String,
    val hosts: List<String>,
    val fingerprint: String?,
) {
    companion object {
        /**
         * Parse a QR code string into PairingQrData.
         * Returns null if the string is not a valid lpm://pair URI.
         */
        fun parse(raw: String): PairingQrData? {
            val uri = try { URI(raw.trim()) } catch (_: Exception) { return null }
            if (uri.scheme != "lpm" || uri.host != "pair") return null

            val params = parseQuery(uri.rawQuery ?: return null)

            val port = params["p"]?.firstOrNull()?.toIntOrNull() ?: 8765
            val code = params["c"]?.firstOrNull() ?: return null
            val hosts = params["h"] ?: return null
            val fingerprint = params["f"]?.firstOrNull()

            if (hosts.isEmpty()) return null

            return PairingQrData(
                port = port,
                code = code,
                hosts = hosts,
                fingerprint = fingerprint,
            )
        }

        private fun parseQuery(query: String): Map<String, List<String>> {
            val result = mutableMapOf<String, MutableList<String>>()
            for (pair in query.split("&")) {
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = java.net.URLDecoder.decode(parts[0], "UTF-8")
                    val value = java.net.URLDecoder.decode(parts[1], "UTF-8")
                    result.getOrPut(key) { mutableListOf() }.add(value)
                }
            }
            return result
        }
    }
}
