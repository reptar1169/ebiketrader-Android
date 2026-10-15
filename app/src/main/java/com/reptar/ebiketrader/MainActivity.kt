package com.reptar.ebiketrader

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.reptar.ebiketrader.push.PushService
import com.reptar.ebiketrader.ui.AppNavigation
import com.reptar.ebiketrader.ui.theme.EBikeTraderTheme

class MainActivity : ComponentActivity() {

    /** Backed by state so a notification tapped while the activity is already
     * running (launchMode is standard, but FLAG_ACTIVITY_SINGLE_TOP routes it
     * to onNewIntent) still reaches the nav graph. */
    private var pendingConversationId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingConversationId = intent?.getStringExtra(PushService.EXTRA_CONVERSATION_ID)

        setContent {
            EBikeTraderTheme {
                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* Declined just means no pushes; nothing else changes. */ }

                LaunchedEffect(Unit) {
                    // Runtime-requested from API 33. Below that the manifest
                    // declaration is enough.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                AppNavigation(
                    deepLinkConversationId = pendingConversationId,
                    onDeepLinkHandled = { pendingConversationId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingConversationId = intent.getStringExtra(PushService.EXTRA_CONVERSATION_ID)
    }
}
