package com.reptar.ebiketrader.data

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ListingDetailUiState(
    val listing: Listing? = null,
    val isLoading: Boolean = true,
    /** Set when the listing is deleted out from under the screen. */
    val wasRemoved: Boolean = false,
    val errorMessage: String? = null,
)

/** Live view of a single listing, so the detail screen picks up a price edit or
 * a "mark as sold" while it is open. Mirrors subscribeListing() on the web and
 * ListingDetailStore on iOS. */
class ListingDetailViewModel(private val listingId: String) : ViewModel() {

    private val _state = MutableStateFlow(ListingDetailUiState())
    val state: StateFlow<ListingDetailUiState> = _state.asStateFlow()

    private var registration: ListenerRegistration? = null

    fun startIfNeeded() {
        if (registration != null) return
        registration = FirebaseFirestore.getInstance()
            .collection("listings")
            .document(listingId)
            .addSnapshotListener { snapshot, error ->
                _state.value = when {
                    error != null ->
                        _state.value.copy(isLoading = false, errorMessage = error.message)
                    snapshot == null || !snapshot.exists() ->
                        _state.value.copy(isLoading = false, wasRemoved = true)
                    else ->
                        ListingDetailUiState(listing = Listing.fromDoc(snapshot), isLoading = false)
                }
            }
    }

    override fun onCleared() {
        registration?.remove()
        registration = null
    }
}
