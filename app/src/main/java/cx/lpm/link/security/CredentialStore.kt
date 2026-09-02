package cx.lpm.link.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class DeviceCredential(val deviceId: String, val token: String)

@Serializable
data class MacServer(
    val localId: String,
    val serverId: String,
    val serverName: String,
    val hosts: List<String>,
    val port: Int,
    val certFingerprint: String?
)

@Singleton
class CredentialStore @Inject constructor(@ApplicationContext context: Context) {
    private val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "credential_store_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun saveCredential(localId: String, cred: DeviceCredential) {
        prefs.edit().putString("cred_$localId", json.encodeToString(cred)).apply()
    }

    fun getCredential(localId: String): DeviceCredential? {
        return prefs.getString("cred_$localId", null)?.let {
            json.decodeFromString<DeviceCredential>(it)
        }
    }

    fun deleteCredential(localId: String) {
        prefs.edit().remove("cred_$localId").apply()
    }

    fun saveServer(server: MacServer) {
        prefs.edit().putString("server_${server.localId}", json.encodeToString(server)).apply()
    }

    fun getServer(localId: String): MacServer? {
        return prefs.getString("server_$localId", null)?.let {
            json.decodeFromString<MacServer>(it)
        }
    }

    fun getAllServers(): List<MacServer> {
        val servers = mutableListOf<MacServer>()
        for ((key, value) in prefs.all) {
            if (key.startsWith("server_") && value is String) {
                try {
                    servers.add(json.decodeFromString(value))
                } catch (e: Exception) {
                    // Ignore decoding errors
                }
            }
        }
        return servers
    }

    fun deleteServer(localId: String) {
        prefs.edit().remove("server_$localId").apply()
    }

    fun getActiveServerId(): String? {
        return prefs.getString("active_server_id", null)
    }

    fun setActiveServerId(localId: String) {
        prefs.edit().putString("active_server_id", localId).apply()
    }
}
