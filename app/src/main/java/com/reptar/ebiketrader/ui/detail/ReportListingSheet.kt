package com.reptar.ebiketrader.ui.detail

import androidx.compose.runtime.Composable
import com.reptar.ebiketrader.data.Listing
import com.reptar.ebiketrader.ui.components.ReportSheet

@Composable
fun ReportListingSheet(
    listing: Listing,
    reporterId: String,
    onDismiss: () -> Unit,
) {
    ReportSheet(
        title = "Report this listing",
        footnote = "We review every report. You can also block this seller so " +
            "their listings stop appearing for you.",
        identity = mapOf(
            "kind" to "listing",
            "listingId" to listing.id,
            "listingTitle" to listing.title,
            "listingSellerId" to listing.sellerId,
            "reporterId" to reporterId,
        ),
        onDismiss = onDismiss,
    )
}
