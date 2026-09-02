package cx.lpm.link.push

import android.util.Base64
import android.util.Log
import cx.lpm.link.model.NotifyPrefs
import cx.lpm.link.network.LpmClient
import cx.lpm.link.security.KeystoreManager
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers the device's FCM push token and shared AES-256 key with the lpm desktop.
 */
@Singleton
class PushRegistrar @Inject constructor(
    private val client: LpmClient,
    private val keystoreManager: KeystoreManager,
) {
    companion object {
        private const val TAG = "PushRegistrar"
    }

    private var cachedToken: String? = null

    fun updateToken(token: String) {
        cachedToken = token
        registerWithDesktop()
    }

    /**
     * Send push registration frame to currently connected Mac.
     */
    fun registerWithDesktop(prefs: NotifyPrefs = NotifyPrefs()) {
        val token = cachedToken ?: return
        val rawKey = keystoreManager.getOrCreatePushKey()
        val keyBase64 = Base64.encodeToString(rawKey, Base64.NO_WRAP)

        val notifyObj = buildJsonObject {
            put("waiting", prefs.waiting)
            put("done", prefs.done)
            put("error", prefs.error)
            put("automationStarted", prefs.automationStarted)
            put("automationDone", prefs.automationDone)
            put("automationError", prefs.automationError)
        }

        val payload = buildJsonObject {
            put("token", token)
            put("env", "production")
            put("key", keyBase64)
            put("notify", notifyObj)
        }

        Log.d(TAG, "Registering push token with desktop")
        client.send("apnsToken", payload)
    }
}
