package com.reptar.ebiketrader.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxUiState(
    val conversations: List<ConversationSummary> = emptyList(),
    val isLoading: Boolean = true,
)

/** The inbox. Firestore has no OR across fields, so this runs the same two
 * subscriptions the web app does — one where you're the buyer, one where
 * you're the seller — and merges them. */
class InboxViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private var buyerReg: ListenerRegistration? = null
    private var sellerReg: ListenerRegistration? = null
    private var asBuyer: List<ConversationSummary> = emptyList()
    private var asSeller: List<ConversationSummary> = emptyList()
    private var uid: String? = null

    fun bind(newUid: String?) {
        if (newUid == uid) return
        uid = newUid

        buyerReg?.remove()
        sellerReg?.remove()
        buyerReg = null
        sellerReg = null
        asBuyer = emptyList()
        asSeller = emptyList()
        _state.value = InboxUiState(isLoading = newUid != null)

        val boundUid = newUid ?: return

        buyerReg = db.collection(ChatService.COLLECTION)
            .whereEqualTo("buyerId", boundUid)
            .addSnapshotListener { snapshot, _ ->
                asBuyer = snapshot?.documents?.map(ConversationSummary::fromDoc) ?: emptyList()
                emit()
            }

        sellerReg = db.collection(ChatService.COLLECTION)
            .whereEqualTo("sellerId", boundUid)
            .addSnapshotListener { snapshot, _ ->
                asSeller = snapshot?.documents?.map(ConversationSummary::fromDoc) ?: emptyList()
                emit()
            }
    }

    private fun emit() {
        val merged = (asBuyer + asSeller).sortedByDescending { it.lastMessageAt ?: 0L }
        _state.value = InboxUiState(conversations = merged, isLoading = false)
    }

    override fun onCleared() {
        buyerReg?.remove()
        sellerReg?.remove()
    }
}

data class ThreadUiState(
    val summary: ConversationSummary? = null,
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val draft: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null,
)

/** One conversation: the live message list plus the send/mark-read actions. */
class ThreadViewModel(
    private val conversationId: String,
    private val uid: String,
) : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(ThreadUiState())
    val state: StateFlow<ThreadUiState> = _state.asStateFlow()

    private var summaryReg: ListenerRegistration? = null
    private var messagesReg: ListenerRegistration? = null

    fun startIfNeeded() {
        if (messagesReg != null) return

        summaryReg = db.collection(ChatService.COLLECTION).document(conversationId)
            .addSnapshotListener { snapshot, _ ->
                val summary = snapshot?.takeIf { it.exists() }
                    ?.let(ConversationSummary::fromDoc) ?: return@addSnapshotListener
                _state.update { it.copy(summary = summary) }
                // Opening the thread is what marks it read, and every new
                // message that arrives while it's open counts as read too.
                viewModelScope.launch {
                    runCatching { ChatService.markRead(conversationId, uid, summary.buyerId) }
                }
            }

        messagesReg = db.collection(ChatService.COLLECTION).document(conversationId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _state.update { it.copy(isLoading = false, errorMessage = error.message) }
                    return@addSnapshotListener
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        messages = snapshot?.documents?.map(Message::fromDoc) ?: emptyList(),
                    )
                }
            }
    }

    fun setDraft(text: String) = _state.update { it.copy(draft = text) }

    fun send() {
        val text = _state.value.draft.trim()
        if (text.isEmpty() || _state.value.isSending) return

        viewModelScope.launch {
            // The draft is cleared optimistically but restored on failure —
            // silently eating someone's typed message is the worst outcome
            // here, and it's exactly the bug the iOS thread view shipped with
            // until the error was surfaced.
            _state.update { it.copy(draft = "", isSending = true, errorMessage = null) }
            try {
                ChatService.send(conversationId, uid, text)
                _state.update { it.copy(isSending = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        draft = text,
                        isSending = false,
                        errorMessage = e.message ?: "Couldn't send that message.",
                    )
                }
            }
        }
    }

    fun delete(messageId: String) {
        viewModelScope.launch {
            try {
                ChatService.delete(conversationId, messageId)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Couldn't delete that.") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    override fun onCleared() {
        summaryReg?.remove()
        messagesReg?.remove()
    }
}
