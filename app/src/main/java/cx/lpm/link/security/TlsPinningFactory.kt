package cx.lpm.link.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Creates SSLSocketFactory instances with custom TrustManagers that pin
 * the lpm desktop's self-signed TLS certificate by SHA-256 fingerprint.
 */
object TlsPinningFactory {

    /**
     * Create an SSLSocketFactory + TrustManager pair.
     *
     * @param pinnedFingerprint If non-null, the TrustManager will reject any
     *   server whose leaf certificate SHA-256 fingerprint doesn't match.
     *   If null, TOFU mode — accepts any cert (caller should save the fingerprint).
     */
    fun create(pinnedFingerprint: String?): Pair<SSLSocketFactory, X509TrustManager> {
        val tm = LpmTrustManager(pinnedFingerprint)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(tm), SecureRandom())
        return Pair(sslContext.socketFactory, tm)
    }

    /**
     * Compute the SHA-256 fingerprint of a certificate's DER encoding.
     * Returns lowercase hex string (64 characters).
     */
    fun fingerprint(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cert.encoded)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Get the observed fingerprint from the last TLS handshake (TOFU mode).
     */
    fun observedFingerprint(trustManager: X509TrustManager): String? {
        return (trustManager as? LpmTrustManager)?.observedFingerprint
    }

    private class LpmTrustManager(private val pinnedFingerprint: String?) : X509TrustManager {

        /** The fingerprint observed during the last handshake (for TOFU). */
        var observedFingerprint: String? = null
            private set

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            // Not used — we're the client
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull()
                ?: throw java.security.cert.CertificateException("No certificate provided")

            val observed = fingerprint(leaf)
            observedFingerprint = observed

            if (pinnedFingerprint != null && observed != pinnedFingerprint) {
                throw java.security.cert.CertificateException(
                    "Certificate fingerprint mismatch: expected $pinnedFingerprint, got $observed"
                )
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
