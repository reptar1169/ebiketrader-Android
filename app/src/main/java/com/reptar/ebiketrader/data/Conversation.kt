package com.reptar.ebiketrader.data

import com.google.firebase.firestore.DocumentSnapshot

/** Mirrors ConversationSummary in the website's src/lib/types.ts. The doc id
 * is always "{listingId}_{buyerId}" (see ChatService.conversationId), which is
 * what keeps one buyer + one listing to a single thread on all three clients. */
data class ConversationSummary(
    val id: String,
    val listingId: String,
    val listingTitle: String,
    val listingPhoto: String?,
    val sellerId: String,
    val sellerName: String,
    val buyerId: String,
    val buyerName: String,
    val lastMessage: String,
    val lastMessageAt: Long?,
    val lastSenderId: String,
    /** When each side last opened the thread — null means never. */
    val buyerLastReadAt: Long?,
    val sellerLastReadAt: Long?,
) {
    /** Port of isUnreadForUser() in src/lib/messages.ts: there's a message you
     * didn't send, and you haven't opened the thread since it arrived. */
    fun isUnread(uid: String): Boolean {
        val arrived = lastMessageAt ?: return false
        if (lastSenderId == uid) return false
        val lastReadAt = if (uid == buyerId) buyerLastReadAt else sellerLastReadAt
        return lastReadAt == null || arrived > lastReadAt
    }

    /** The name to show in the inbox — whoever isn't you. */
    fun otherPartyName(uid: String): String = if (uid == buyerId) sellerName else buyerName

    companion object {
        /** Port of summaryFromDoc() in src/lib/messages.ts, fallbacks included. */
        fun fromDoc(doc: DocumentSnapshot): ConversationSummary {
            val d = doc.data ?: emptyMap<String, Any>()
            return ConversationSummary(
                id = doc.id,
                listingId = FirestoreValue.string(d["listingId"]),
                listingTitle = FirestoreValue.string(d["listingTitle"]),
                listingPhoto = d["listingPhoto"] as? String,
                sellerId = FirestoreValue.string(d["sellerId"]),
                sellerName = FirestoreValue.string(d["sellerName"], fallback = "Seller"),
                buyerId = FirestoreValue.string(d["buyerId"]),
                buyerName = FirestoreValue.string(d["buyerName"], fallback = "Buyer"),
                lastMessage = FirestoreValue.string(d["lastMessage"]),
                lastMessageAt = FirestoreValue.millis(d["lastMessageAt"]),
                lastSenderId = FirestoreValue.string(d["lastSenderId"]),
                buyerLastReadAt = FirestoreValue.millis(d["buyerLastReadAt"]),
                sellerLastReadAt = FirestoreValue.millis(d["sellerLastReadAt"]),
            )
        }
    }
}

data class Message(
    val id: String,
    val senderId: String,
    val text: String,
    val createdAt: Long?,
) {
    companion object {
        fun fromDoc(doc: DocumentSnapshot): Message {
            val d = doc.data ?: emptyMap<String, Any>()
            return Message(
                id = doc.id,
                senderId = FirestoreValue.string(d["senderId"]),
                text = FirestoreValue.string(d["text"]),
                createdAt = FirestoreValue.millis(d["createdAt"]),
            )
        }
    }
}
