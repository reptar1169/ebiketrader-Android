package com.reptar.ebiketrader.push

import android.content.Context
import android.util.Log
import com.reptar.ebiketrader.support.logDebug
import com.google.firebase.messaging.FirebaseMessaging
import com.reptar.ebiketrader.support.await

/** Fetches the current FCM token and files it under the signed-in user.
 *
 * Run on every launch while signed in, not just on first grant: a token can be
 * rotated while the app is closed, and onNewToken can fire when nobody is
 * signed in and there is nowhere to put it. Re-registering is cheap —
 * arrayUnion de-duplicates — and it is what makes a reinstall start working
 * again without the user doing anything. */
object PushRegistrar {
    private const val TAG = "ebt-push"

    /** The (uid, token) pair already written this process. An activity
     * recreation re-runs the LaunchedEffect that calls this, so without it
     * every rotation or theme change costs another Firestore write for no
     * new information. */
    private var lastWritten: Pair<String, String>? = null

    suspend fun register(context: Context, uid: String) {
        PushService.ensureChannel(context)
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            if (lastWritten == uid to token) return
            lastWritten = uid to token
            // The token itself is never logged. It is a bearer credential
            // for pushing to this device, and it used to be printed in full
            // so it could be pasted into Firebase Console's "Send test
            // message" while debugging. Get it from users/{uid}.fcmTokens in
            // the Firestore console instead.
            logDebug(TAG, "registering token for $uid")
            PushService.saveToken(uid, token)
        } catch (e: Exception) {
            Log.e(TAG, "couldn't get FCM token", e)
        }
    }

    suspend fun unregister(uid: String) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            PushService.removeToken(uid, token)
        } catch (_: Exception) {
            // Signing out without pruning just means the Cloud Function will
            // prune it on the next failed send.
        }
    }
}
