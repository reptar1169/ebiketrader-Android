package com.reptar.ebiketrader.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Row
import com.reptar.ebiketrader.data.AuthViewModel
import com.reptar.ebiketrader.data.UserDataViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    authViewModel: AuthViewModel,
    userDataViewModel: UserDataViewModel,
    onBack: (() -> Unit)? = null,
    onSignIn: () -> Unit,
    onMyListings: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by authViewModel.state.collectAsStateWithLifecycle()
    val userData by userDataViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isSignedIn) {
                Text("Signed in as", style = MaterialTheme.typography.bodySmall)
                Text(state.displayName, style = MaterialTheme.typography.titleMedium)
                state.user?.email?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                OutlinedButton(onClick = onMyListings, modifier = Modifier.fillMaxWidth()) {
                    Text("My listings")
                }
                OutlinedButton(onClick = onSaved, modifier = Modifier.fillMaxWidth()) {
                    Text("Saved bikes (${userData.favoriteIds.size})")
                }

                if (userData.blocked.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("Blocked users", style = MaterialTheme.typography.titleSmall)
                    userData.blocked.forEach { person ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                person.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                scope.launch { userDataViewModel.unblock(person.id) }
                            }) { Text("Unblock") }
                        }
                    }
                }

                OutlinedButton(
                    onClick = authViewModel::signOut,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("Sign out") }

                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete account", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Text(
                    "Sign in to save bikes, message sellers and list your own.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in or create an account")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Legal", style = MaterialTheme.typography.titleSmall)
            listOf(
                "Help & support" to "https://ebiketrader.net/support",
                "Privacy Policy" to "https://ebiketrader.net/privacy",
                "Terms of Use" to "https://ebiketrader.net/terms",
            ).forEach { (label, url) ->
                TextButton(
                    onClick = { uriHandler.openUri(url) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(label, modifier = Modifier.fillMaxWidth()) }
            }
            Text(
                "City suggestions use place-name data from GeoNames, licensed " +
                    "under CC BY 4.0.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.errorMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = {
                Text(
                    "This removes your listings, saved bikes and messages, and " +
                        "can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    // Account is a tab now, so there is nothing to pop to:
                    // deleting signs you out and this same screen re-renders
                    // in its signed-out state, which is the right landing.
                    authViewModel.deleteAccount(onDeleted = { onBack?.invoke() })
                }) { Text("Delete account") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
