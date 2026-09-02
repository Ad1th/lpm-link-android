package cx.lpm.link.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "keystore_manager_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getOrCreatePushKey(): ByteArray {
        return getPushKey() ?: ByteArray(32).apply {
            SecureRandom().nextBytes(this)
            storePushKey(this)
        }
    }

    fun storePushKey(key: ByteArray) {
        sharedPrefs.edit().putString("lpm_push_key", Base64.encodeToString(key, Base64.NO_WRAP)).apply()
    }

    fun getPushKey(): ByteArray? {
        return sharedPrefs.getString("lpm_push_key", null)?.let {
            Base64.decode(it, Base64.NO_WRAP)
        }
    }
    
    fun storeEncryptedString(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }
    
    fun getEncryptedString(key: String): String? {
        return sharedPrefs.getString(key, null)
    }
}
