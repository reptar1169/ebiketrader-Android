package com.reptar.ebiketrader.data

import java.util.Locale

enum class ListingSort(val label: String) {
    NEWEST("Newest first"),
    PRICE_ASC("Price: low to high"),
    PRICE_DESC("Price: high to low"),
}

/** The client-side half of the web app's filtering. Only `status` and the sort
 * order go to Firestore (see ListingsViewModel) — everything here is applied to
 * the page that comes back, which is what lets the whole feed run on the
 * composite indexes the site already has rather than needing one per filter
 * combination. */
data class ListingFilters(
    val condition: Condition? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val brand: String? = null,
    val state: String? = null,
) {
    val isActive: Boolean
        get() = condition != null || minPrice != null || maxPrice != null ||
            brand != null || state != null
}

/** Client-side filtering from subscribeListings() plus the homepage's text
 * search (HomeClient.tsx) — same fields, same case-insensitive contains. */
fun List<Listing>.applying(filters: ListingFilters, search: String): List<Listing> {
    var result = this

    filters.brand?.let { brand -> result = result.filter { it.brand == brand } }
    filters.condition?.let { condition -> result = result.filter { it.condition == condition } }
    filters.minPrice?.let { min -> result = result.filter { it.price >= min } }
    filters.maxPrice?.let { max -> result = result.filter { it.price <= max } }
    filters.state?.let { state -> result = result.filter { it.state.equals(state, ignoreCase = true) } }

    val query = search.trim().lowercase(Locale.US)
    if (query.isNotEmpty()) {
        result = result.filter { listing ->
            listOf(listing.title, listing.brand, listing.model, listing.city, listing.state)
                .joinToString(" ")
                .lowercase(Locale.US)
                .contains(query)
        }
    }

    return result
}
