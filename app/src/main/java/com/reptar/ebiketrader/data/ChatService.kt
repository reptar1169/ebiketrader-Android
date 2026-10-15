package com.reptar.ebiketrader.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.reptar.ebiketrader.support.await

/** Shared pieces of the messaging data model, ported from src/lib/messages.ts.
 * Kept in one place so the id scheme can't drift between the clients — a
 * mismatch would silently start a second thread for a buyer who already had
 * one.
 *
 * Named ChatService rather than Messaging to match the iOS port, where that
 * name would shadow FirebaseMessaging's own Messaging class. Android has no
 * such conflict, but keeping the names aligned makes the two easier to read
 * side by side. */
object ChatService {
    const val COLLECTION = "conversations"

    private val db get() = FirebaseFirestore.getInstance()

    /** Deterministic id: one thread per listing per buyer, on every client. */
    fun conversationId(listingId: String, buyerId: String) = "${listingId}_$buyerId"

    /** Port of getOrCreateConversation(). Safe to call every time someone opens
     * "message seller" — it only writes when the thread is new. */
    suspend fun getOrCreateConversation(
        listing: Listing,
        buyerId: String,
        buyerName: String,
    ): String {
        val id = conversationId(listing.id, buyerId)
        val reference = db.collection(COLLECTION).document(id)

        if (reference.get().await().exists()) return id

        reference.set(
            mapOf(
                "listingId" to listing.id,
                "listingTitle" to listing.title,
                // Kotlin maps carry a real null happily, so no NSNull dance
                // the way the iOS port needed.
                "listingPhoto" to listing.photos.firstOrNull(),
                "sellerId" to listing.sellerId,
                "sellerName" to listing.sellerName,
                "buyerId" to buyerId,
                "buyerName" to buyerName,
                "lastMessage" to "",
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastSenderId" to "",
                // The buyer is the one creating this thread, so it starts read
                // for them and unread for the seller.
                "buyerLastReadAt" to FieldValue.serverTimestamp(),
                "sellerLastReadAt" to null,
            )
        ).await()

        return id
    }

    /** Port of sendMessage(): append to the subcollection, then refresh the
     * denormalized summary the inbox reads. */
    suspend fun send(conversationId: String, senderId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val conversation = db.collection(COLLECTION).document(conversationId)

        conversation.collection("messages").add(
            mapOf(
                "senderId" to senderId,
                "text" to trimmed,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()

        conversation.update(
            mapOf(
                "lastMessage" to trimmed,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastSenderId" to senderId,
            )
        ).await()
    }

    /** Port of markConversationRead(). */
    suspend fun markRead(conversationId: String, uid: String, buyerId: String) {
        val field = if (uid == buyerId) "buyerLastReadAt" else "sellerLastReadAt"
        db.collection(COLLECTION).document(conversationId)
            .update(field, FieldValue.serverTimestamp())
            .await()
    }

    /** Port of deleteMessage(). firestore.rules only permits deleting your own
     * messages; if the deleted one was the latest, the conversation's summary
     * fields are rebuilt so the inbox doesn't keep previewing a message that no
     * longer exists. */
    suspend fun delete(conversationId: String, messageId: String) {
        val conversation = db.collection(COLLECTION).document(conversationId)
        conversation.collection("messages").document(messageId).delete().await()

        val latest = conversation.collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()

        val newest = latest.documents.firstOrNull()
        if (newest == null) {
            conversation.update(mapOf("lastMessage" to "", "lastSenderId" to "")).await()
            return
        }

        val update = mutableMapOf<String, Any>(
            "lastMessage" to FirestoreValue.string(newest.data?.get("text")),
            "lastSenderId" to FirestoreValue.string(newest.data?.get("senderId")),
        )
        // Carry the surviving message's own timestamp across, so the inbox
        // keeps sorting by when that message was actually sent. A message still
        // awaiting its serverTimestamp has none yet.
        newest.getTimestamp("createdAt")?.let { update["lastMessageAt"] = it }
        conversation.update(update).await()
    }
}
