package com.reptar.ebiketrader.ui.messages

import androidx.compose.runtime.Composable
import com.reptar.ebiketrader.data.ConversationSummary
import com.reptar.ebiketrader.ui.components.ReportSheet

/**
 * Reporting the thread rather than one message: a scam is a pattern across a
 * few messages, and picking out the single bad one is work the person being
 * scammed should not have to do.
 */
@Composable
fun ReportConversationSheet(
    conversation: ConversationSummary,
    uid: String,
    onDismiss: () -> Unit,
) {
    val other =
        if (conversation.buyerId == uid) conversation.sellerId else conversation.buyerId

    ReportSheet(
        title = "Report this conversation",
        footnote = "We review every report, and we can see the messages in this " +
            "thread. You can also block this person so they can't message you again.",
        identity = mapOf(
            "kind" to "conversation",
            "conversationId" to conversation.id,
            "reportedUserId" to other,
            // The listing the thread is about. Required by firestore.rules,
            // and it gives the admin queue something to name the report by.
            "listingId" to conversation.listingId,
            "listingTitle" to conversation.listingTitle,
            "listingSellerId" to conversation.sellerId,
            "reporterId" to uid,
        ),
        onDismiss = onDismiss,
    )
}
