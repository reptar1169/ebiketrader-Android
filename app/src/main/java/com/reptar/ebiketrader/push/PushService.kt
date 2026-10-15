package com.reptar.ebiketrader.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.reptar.ebiketrader.support.logDebug
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.reptar.ebiketrader.MainActivity
import com.reptar.ebiketrader.R

/** Receives message pushes from the onNewMessage Cloud Function.
 *
 * No backend change was needed for Android: that function already sends a
 * top-level `notification` block plus a `data` payload carrying conversationId,
 * and its `apns` block is simply ignored off Apple platforms. */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Fired when FCM rotates the token, which can happen with the app in
        // any state — including signed out, in which case there's nowhere to
        // put it and the next sign-in will re-register.
        logDebug(TAG, "token refreshed")
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            saveToken(uid, token)
        }
    }

    /** Only called when the app is in the foreground. Backgrounded, FCM builds
     * the tray notification itself from the `notification` block — which is
     * why the manifest has to name an icon and channel for it to use. */
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        val conversationId = message.data["conversationId"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            conversationId?.let { putExtra(EXTRA_CONVERSATION_ID, it) }
        }
        val pending = PendingIntent.getActivity(
            this,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        ensureChannel(this)

        val built = NotificationCompat.Builder(this, getString(R.string.message_channel_id))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.brand))
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        // One notification per conversation, so a chatty thread replaces its
        // own notification rather than stacking five.
        NotificationManagerCompat.from(this)
            .notify(conversationId?.hashCode() ?: 0, built)
    }

    companion object {
        private const val TAG = "ebt-push"
        const val EXTRA_CONVERSATION_ID = "conversationId"

        fun ensureChannel(context: Context) {
            // minSdk is 26, so channels always exist — no version check, which
            // is exactly why minSdk 26 was chosen.
            val channel = NotificationChannel(
                context.getString(R.string.message_channel_id),
                context.getString(R.string.message_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.message_channel_description)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        /** Tokens live in an `fcmTokens` array on users/{uid} — the same field
         * the iOS app writes and the Cloud Function reads, so one account can
         * have a phone, an iPad and a laptop all registered at once. */
        fun saveToken(uid: String, token: String) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .set(
                    mapOf("fcmTokens" to FieldValue.arrayUnion(token)),
                    com.google.firebase.firestore.SetOptions.merge(),
                )
                .addOnSuccessListener { logDebug(TAG, "token saved to users/$uid") }
                .addOnFailureListener { Log.e(TAG, "token save failed", it) }
        }

        fun removeToken(uid: String, token: String) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("fcmTokens", FieldValue.arrayRemove(token))
        }
    }
}
