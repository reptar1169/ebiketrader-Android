package com.reptar.ebiketrader.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.reptar.ebiketrader.support.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SavedListingsUiState(
    val listings: List<Listing> = emptyList(),
    val isLoading: Boolean = true,
)

/** The bikes you've saved.
 *
 * Fetched by id rather than subscribed to: a saved listing can be sold, so it
 * can't come from the active feed, and one live listener per favourite would
 * scale with however many someone saves. The favourite ids themselves are
 * live (UserDataViewModel), so this reloads whenever that set changes — which
 * is the part that actually needs to feel instant. */
class SavedListingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(SavedListingsUiState())
    val state: StateFlow<SavedListingsUiState> = _state.asStateFlow()

    private var lastIds: Set<String>? = null

    fun load(ids: Set<String>) {
        if (ids == lastIds) return
        lastIds = ids

        if (ids.isEmpty()) {
            _state.value = SavedListingsUiState(isLoading = false)
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val db = FirebaseFirestore.getInstance()
            val found = mutableListOf<Listing>()

            // whereIn is capped server-side, and the cap has moved between
            // Firestore versions — chunking at 10 is the value every version
            // accepts.
            ids.chunked(10).forEach { chunk ->
                runCatching {
                    db.collection("listings")
                        .whereIn(FieldPath.documentId(), chunk)
                        .get()
                        .await()
                }.onSuccess { snapshot ->
                    found += snapshot.documents.map(Listing::fromDoc)
                }
            }

            // Newest first, same default as browse.
            _state.value = SavedListingsUiState(
                listings = found.sortedByDescending { it.createdAt ?: 0L },
                isLoading = false,
            )
        }
    }
}
