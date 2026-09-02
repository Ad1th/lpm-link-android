package cx.lpm.link.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decrypts AES-256-GCM sealed push notification payloads.
 *
 * The sealed box format (matching Apple CryptoKit AES.GCM.SealedBox):
 *   nonce (12 bytes) || ciphertext (variable) || tag (16 bytes)
 *
 * This combined blob is Base64-encoded as the "blob" field in push payloads.
 */
@Singleton
class SealedDecryptor @Inject constructor() {

    companion object {
        private const val NONCE_SIZE = 12
        private const val TAG_BITS = 128 // 16 bytes
        private const val ALGORITHM = "AES/GCM/NoPadding"
    }

    /**
     * Decrypt a Base64-encoded sealed box.
     *
     * @param blob Base64 string containing nonce + ciphertext + tag
     * @param key 32-byte AES-256 key
     * @return Decrypted plaintext as UTF-8 string
     * @throws Exception on decryption failure
     */
    fun decrypt(blob: String, key: ByteArray): String {
        val combined = Base64.decode(blob, Base64.DEFAULT)
        require(combined.size > NONCE_SIZE) { "Sealed box too short" }

        val nonce = combined.sliceArray(0 until NONCE_SIZE)
        val ciphertextAndTag = combined.sliceArray(NONCE_SIZE until combined.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_BITS, nonce)
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val plaintext = cipher.doFinal(ciphertextAndTag)
        return String(plaintext, Charsets.UTF_8)
    }
}
