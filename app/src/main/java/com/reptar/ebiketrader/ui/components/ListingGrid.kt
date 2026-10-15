package com.reptar.ebiketrader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reptar.ebiketrader.data.Listing

/** The listing grid, shared by Browse, My listings and Saved.
 *
 * Extracted once there were three callers rather than copied — the card sizing
 * and the adaptive column count are the sort of thing that drifts apart if each
 * screen owns its own copy. */
@Composable
fun ListingGrid(
    listings: List<Listing>,
    isLoading: Boolean,
    emptyMessage: String,
    onOpenListing: (Listing) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading && listings.isEmpty() -> Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        listings.isEmpty() -> Box(
            modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                emptyMessage,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Adaptive rather than Fixed(2) so a tablet or an unfolded phone gets
        // more columns instead of two very wide cards.
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 170.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(listings, key = { it.id }) { listing ->
                ListingCard(listing = listing, onClick = { onOpenListing(listing) })
            }
        }
    }
}
