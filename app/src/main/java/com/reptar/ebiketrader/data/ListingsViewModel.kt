package com.reptar.ebiketrader.data

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ListingsUiState(
    val listings: List<Listing> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val sort: ListingSort = ListingSort.NEWEST,
    val filters: ListingFilters = ListingFilters(),
    val search: String = "",
) {
    /** What the grid actually renders. */
    val visible: List<Listing> get() = listings.applying(filters, search)

    /** Brands present in the current feed, for the filter sheet — derived
     * rather than hardcoded, same as the website's brand row. */
    val brands: List<String>
        get() = listings.map { it.brand }.filter { it.isNotEmpty() }.distinct().sorted()
}

/** Live listings feed — a port of subscribeListings() in src/lib/listings.ts.
 *
 * Firestore's Android callbacks arrive on the main thread, so updating the
 * StateFlow straight from the listener is safe. */
class ListingsViewModel(
    private val status: ListingStatus? = ListingStatus.ACTIVE,
    private val sellerId: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(ListingsUiState())
    val state: StateFlow<ListingsUiState> = _state.asStateFlow()

    private var registration: ListenerRegistration? = null

    fun startIfNeeded() {
        if (registration == null) subscribe()
    }

    /** Sort is applied server-side alongside limit(300), so it decides *which*
     * 300 listings come back, not merely the order they arrive in — which is
     * why changing it has to re-run the query rather than re-sort in memory. */
    fun setSort(sort: ListingSort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        subscribe()
    }

    fun setSearch(search: String) = _state.update { it.copy(search = search) }

    fun setFilters(filters: ListingFilters) = _state.update { it.copy(filters = filters) }

    fun clearFilters() = _state.update { it.copy(filters = ListingFilters()) }

    private fun subscribe() {
        registration?.remove()
        _state.update { it.copy(isLoading = true) }

        var query: Query = FirebaseFirestore.getInstance().collection("listings")
        status?.let { query = query.whereEqualTo("status", it.stored) }
        sellerId?.let { query = query.whereEqualTo("sellerId", it) }

        query = when (_state.value.sort) {
            ListingSort.NEWEST -> query.orderBy("createdAt", Query.Direction.DESCENDING)
            ListingSort.PRICE_ASC -> query.orderBy("price", Query.Direction.ASCENDING)
            ListingSort.PRICE_DESC -> query.orderBy("price", Query.Direction.DESCENDING)
        }

        registration = query.limit(300).addSnapshotListener { snapshot, error ->
            if (error != null) {
                _state.update { it.copy(isLoading = false, errorMessage = error.message) }
                return@addSnapshotListener
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    errorMessage = null,
                    listings = snapshot?.documents?.map(Listing::fromDoc) ?: emptyList(),
                )
            }
        }
    }

    override fun onCleared() {
        registration?.remove()
        registration = null
    }
}
