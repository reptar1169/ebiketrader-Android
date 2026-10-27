package com.reptar.ebiketrader.data

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/** Mirrors ebiketrader.net's src/lib/analytics.ts so the same event names show
 * up in GA4 whichever platform a seller uses. Google Ads counts app
 * conversions by importing these Firebase events, which is why there is no
 * Ads-specific call here. */
object AppAnalytics {
    /** Fired once a brand-new listing has been written (see
     * ListingFormViewModel's save) — not on edits, so they never double-count. */
    fun listingPosted(context: Context, listingId: String) {
        FirebaseAnalytics.getInstance(context).logEvent(
            "listing_posted",
            Bundle().apply { putString("listing_id", listingId) },
        )
    }
}
