package cx.lpm.link.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import cx.lpm.link.MainActivity
import cx.lpm.link.R
import cx.lpm.link.model.ClearPayload
import cx.lpm.link.model.PushPayload
import cx.lpm.link.security.KeystoreManager
import cx.lpm.link.security.SealedDecryptor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * FCM service that receives sealed push notifications from the lpm desktop via
 * the stateless push relay. Notifications are end-to-end encrypted with AES-256-GCM
 * and decrypted on-device using the key stored in Android Keystore.
 */
@AndroidEntryPoint
class LpmMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "LpmPushService"
        private const val CHANNEL_ID = "lpm_agent_alerts"
        private const val CHANNEL_NAME = "Agent Alerts & Actions"
    }

    @Inject
    lateinit var keystoreManager: KeystoreManager

    @Inject
    lateinit var decryptor: SealedDecryptor

    @Inject
    lateinit var registrar: PushRegistrar

    @Inject
    lateinit var json: Json

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received")
        registrar.updateToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val blob = message.data["blob"] ?: return

        try {
            val key = keystoreManager.getOrCreatePushKey()
            val plaintext = decryptor.decrypt(blob, key)
            Log.d(TAG, "Decrypted sealed push payload: $plaintext")

            // Check if this is a withdrawal notification
            if (plaintext.contains("\"clear\"")) {
                handleWithdrawal(plaintext)
            } else {
                handleAlert(plaintext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt or handle push notification", e)
        }
    }

    private fun handleAlert(plaintext: String) {
        val payload = try {
            json.decodeFromString<PushPayload>(plaintext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse PushPayload JSON", e)
            return
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannelExists(notificationManager)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("project", payload.project)
            putExtra("terminalId", payload.terminalId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            payload.key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when (payload.status) {
            "Waiting" -> "is waiting for approval"
            "Done" -> "has completed work"
            "Error" -> "encountered an error"
            else -> "updated status (${payload.status})"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(payload.project)
            .setContentText("${payload.terminal ?: "Agent"} $statusText")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(payload.key, 1001, notification)
    }

    private fun handleWithdrawal(plaintext: String) {
        val clearPayload = try {
            json.decodeFromString<ClearPayload>(plaintext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ClearPayload JSON", e)
            return
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        clearPayload.clear.forEach { entry ->
            notificationManager.cancel(entry.key, 1001)
        }
    }

    private fun ensureChannelExists(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications when AI coding agents require approval or finish"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
