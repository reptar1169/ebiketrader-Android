package com.reptar.ebiketrader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.reptar.ebiketrader.data.AuthViewModel
import kotlinx.coroutines.delay

/** Marks the signed-in user active every 60 seconds — but only while the app
 * is actually on screen.
 *
 * repeatOnLifecycle(RESUMED) is the whole point: it cancels the loop when the
 * app goes to the background and restarts it on return, which is the Android
 * equivalent of the web's document.visibilityState check.
 *
 * Getting this wrong is worse than it sounds. onNewMessage skips BOTH the push
 * and the email when lastActiveAt is under five minutes old, on the theory that
 * someone with the app open is already watching the thread. A heartbeat that
 * keeps ticking in the background therefore makes the user permanently
 * un-notifiable. */
@Composable
fun PresenceHeartbeat(authViewModel: AuthViewModel, uid: String?) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(uid, lifecycleOwner) {
        if (uid == null) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                authViewModel.touchPresence()
                delay(60_000)
            }
        }
    }
}
