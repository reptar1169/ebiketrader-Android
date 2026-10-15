package com.reptar.ebiketrader.data

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.reptar.ebiketrader.support.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BlockedUser(
    /** The blocked user's uid — also the document id. */
    val id: String,
    val displayName: String,
    val createdAt: Long?,
)

data class UserDataUiState(
    val favoriteIds: Set<String> = emptySet(),
    val blocked: List<BlockedUser> = emptyList(),
) {
    val blockedIds: Set<String> get() = blocked.map { it.id }.toSet()
    fun isFavorite(listingId: String) = listingId in favoriteIds
    fun isBlocked(userId: String) = userId in blockedIds
}

/** Saved listings and the block list — both private per-user subcollections
 * under users/{uid}, the same ones the website writes, so a bike saved on the
 * phone shows up saved on the web and a block made on either side holds
 * everywhere.
 *
 * Kept together in one ViewModel because they share a lifecycle: both are
 * bound to whoever is signed in, and both have to be torn down on sign-out. */
class UserDataViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(UserDataUiState())
    val state: StateFlow<UserDataUiState> = _state.asStateFlow()

    private var favoritesReg: ListenerRegistration? = null
    private var blockedReg: ListenerRegistration? = null
    private var uid: String? = null

    /** Re-points at a different signed-in user, or clears on sign-out. */
    fun bind(newUid: String?) {
        if (newUid == uid) return
        uid = newUid

        favoritesReg?.remove()
        blockedReg?.remove()
        favoritesReg = null
        blockedReg = null
        _state.value = UserDataUiState()

        val boundUid = newUid ?: return

        favoritesReg = favorites(boundUid).addSnapshotListener { snapshot, _ ->
            val ids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
            _state.update { it.copy(favoriteIds = ids) }
        }

        blockedReg = blocked(boundUid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.map { doc ->
                    BlockedUser(
                        id = doc.id,
                        displayName = FirestoreValue.string(
                            doc.data?.get("displayName"),
                            fallback = "This user",
                        ),
                        createdAt = FirestoreValue.millis(doc.data?.get("createdAt")),
                    )
                } ?: emptyList()
                _state.update { it.copy(blocked = list) }
            }
    }

    /** Optimistic: the heart fills instantly and the snapshot listener
     * confirms a moment later. On failure the listener's next emission puts it
     * back, since Firestore stays the source of truth.
     *
     * The favoriteCount on the listing itself is maintained by Cloud Function
     * triggers, never here — firestore.rules blocks clients from touching it. */
    fun toggleFavorite(listingId: String) {
        val boundUid = uid ?: return
        val document = favorites(boundUid).document(listingId)

        if (listingId in _state.value.favoriteIds) {
            _state.update { it.copy(favoriteIds = it.favoriteIds - listingId) }
            document.delete()
        } else {
            _state.update { it.copy(favoriteIds = it.favoriteIds + listingId) }
            document.set(
                mapOf(
                    "listingId" to listingId,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
        }
    }

    /** The display name is denormalised in so the Blocked list can name people
     * without reading their profiles, which the rules wouldn't allow anyway —
     * users/{uid} is readable only by its owner. */
    suspend fun block(userId: String, displayName: String) {
        val boundUid = uid ?: return
        if (userId == boundUid) return
        blocked(boundUid).document(userId).set(
            mapOf(
                "displayName" to displayName,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun unblock(userId: String) {
        val boundUid = uid ?: return
        blocked(boundUid).document(userId).delete().await()
    }

    override fun onCleared() {
        favoritesReg?.remove()
        blockedReg?.remove()
    }

    private fun favorites(forUid: String): CollectionReference =
        db.collection("users").document(forUid).collection("favorites")

    private fun blocked(forUid: String): CollectionReference =
        db.collection("users").document(forUid).collection("blocked")
}
