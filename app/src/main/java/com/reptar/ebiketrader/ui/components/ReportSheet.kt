package com.reptar.ebiketrader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.reptar.ebiketrader.support.await
import kotlinx.coroutines.launch

/** Mirrors ReportReason in the iOS sheet. The stored values are what the
 * admin page at ebiketrader.net/admin/reports reads, so they can't drift. */
enum class ReportReason(val stored: String, val label: String) {
    SPAM("spam", "Spam"),
    SCAM("scam", "Scam or fraud"),
    SOLD_ELSEWHERE("sold-elsewhere", "Already sold elsewhere"),
    INAPPROPRIATE("inappropriate", "Inappropriate content"),
    OTHER("other", "Other"),
}

/**
 * The reason chips, the free-text box and the write to /reports, shared by
 * every kind of report.
 *
 * `identity` is whatever names the thing being reported. Every report carries
 * a non-empty listingId whatever its kind, because firestore.rules requires
 * one on create — a conversation is always about a listing, so that costs
 * nothing and means reporting a message needed no rules change.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportSheet(
    title: String,
    footnote: String,
    identity: Map<String, Any>,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf(ReportReason.SPAM) }
    var details by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)

            Text("Why are you reporting it?", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportReason.entries.forEach { option ->
                    FilterChip(
                        selected = reason == option,
                        onClick = { reason = option },
                        label = { Text(option.label) },
                    )
                }
            }

            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                label = { Text("Anything else we should know?") },
                placeholder = { Text("Optional") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            errorMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    isSending = true
                    errorMessage = null
                    scope.launch {
                        try {
                            FirebaseFirestore.getInstance().collection("reports").add(
                                identity + mapOf(
                                    "reason" to reason.stored,
                                    "details" to details.trim(),
                                    // firestore.rules requires exactly "open".
                                    "status" to "open",
                                    "createdAt" to FieldValue.serverTimestamp(),
                                )
                            ).await()
                            sent = true
                            isSending = false
                            onDismiss()
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Couldn't send that report."
                            isSending = false
                        }
                    }
                },
                enabled = !isSending && !sent,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (sent) "Report sent" else "Send report") }
        }
    }
}
