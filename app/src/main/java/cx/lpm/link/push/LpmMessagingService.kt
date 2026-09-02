package cx.lpm.link.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint

/**
 * FCM service that receives sealed push notifications from the lpm desktop via
 * the stateless push relay. Notifications are end-to-end encrypted with AES-256-GCM
 * and decrypted on-device using the key stored in Android Keystore.
 */
@AndroidEntryPoint
class LpmMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO: Send new FCM token to all paired Macs via apnsToken message
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val blob = message.data["blob"] ?: return
        // TODO: Decrypt blob with AES-256-GCM key from Keystore
        // TODO: Parse payload and display/dismiss notification
    }
}
