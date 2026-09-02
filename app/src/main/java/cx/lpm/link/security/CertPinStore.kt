package cx.lpm.link.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores and verifies TLS certificate fingerprints for paired Macs.
 * Uses EncryptedSharedPreferences for secure storage.
 */
@Singleton
class CertPinStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "lpm_cert_pins",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /**
     * Store a certificate fingerprint for a paired Mac.
     */
    fun pinFingerprint(localId: String, fingerprint: String) {
        prefs.edit().putString("pin_$localId", fingerprint.lowercase()).apply()
    }

    /**
     * Get the stored fingerprint for a Mac, or null if none.
     */
    fun getFingerprint(localId: String): String? {
        return prefs.getString("pin_$localId", null)
    }

    /**
     * Verify an observed fingerprint against the stored pin.
     * Returns true if they match or if no pin is stored (TOFU).
     */
    fun verifyFingerprint(localId: String, observed: String): Boolean {
        val stored = getFingerprint(localId) ?: return true // TOFU
        return stored == observed.lowercase()
    }

    /**
     * Remove a stored fingerprint.
     */
    fun removeFingerprint(localId: String) {
        prefs.edit().remove("pin_$localId").apply()
    }
}
