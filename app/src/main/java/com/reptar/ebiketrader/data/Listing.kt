package com.reptar.ebiketrader.data

import com.google.firebase.firestore.DocumentSnapshot
import com.reptar.ebiketrader.support.Format

/** Mirrors CONDITIONS / CONDITION_LABELS in the website's src/lib/types.ts.
 * [stored] is the exact string in Firestore — do not change without migrating
 * existing listings. */
enum class Condition(val stored: String, val label: String) {
    NEW("new", "New / never ridden"),
    LIKE_NEW("like-new", "Like new"),
    EXCELLENT("excellent", "Excellent"),
    GOOD("good", "Good"),
    FAIR("fair", "Fair"),
    NEEDS_WORK("needs-work", "Needs work");

    /** Fits on a listing card badge, where the full label wraps. */
    val shortLabel: String get() = if (this == NEW) "New" else label

    companion object {
        fun from(stored: String): Condition =
            entries.firstOrNull { it.stored == stored } ?: GOOD
    }
}

/** Mirrors MOTOR_TYPES in src/lib/types.ts — free text in Firestore, but the
 * post form offers these. */
val MOTOR_TYPES = listOf(
    "Hub motor (front)",
    "Hub motor (rear)",
    "Mid-drive",
    "Other / not sure",
)

/** How long a listing stays up after it's posted or renewed. Keep in sync
 * with LISTING_LIFETIME_DAYS in functions/index.js and src/lib/types.ts. */
const val LISTING_LIFETIME_DAYS = 30
/** The owner is offered "Renew" once a live listing is this close to expiring. */
const val RENEW_WINDOW_DAYS = 7
private const val DAY_MS = 24L * 60 * 60 * 1000

/** [badge] is the overlay on the listing's photo; null for live listings.
 * EXPIRED is only ever set by the expireListings Cloud Function. */
enum class ListingStatus(val stored: String, val badge: String?) {
    ACTIVE("active", null),
    SOLD("sold", "SOLD"),
    EXPIRED("expired", "EXPIRED");

    companion object {
        fun from(stored: String): ListingStatus =
            entries.firstOrNull { it.stored == stored } ?: ACTIVE
    }
}

data class Listing(
    val id: String,
    val title: String,
    val brand: String,
    val model: String,
    val year: Int?,
    val price: Double,
    val condition: Condition,
    val mileageMiles: Int?,
    val batteryHealthPct: Int?,
    val motorType: String,
    val frameSize: String,
    val wheelSize: String,
    val color: String,
    val description: String,
    val city: String,
    val state: String,
    /** Geocoded server-side by the onListingWrite Cloud Function — never
     * written by a client (firestore.rules enforces it). */
    val lat: Double?,
    val lng: Double?,
    val photos: List<String>,
    val sellerId: String,
    val sellerName: String,
    val status: ListingStatus,
    /** Maintained server-side by the favorite Cloud Function triggers; also
     * read-only to clients per firestore.rules. */
    val favoriteCount: Int,
    /** Start of the current 30-day window: set on creation, reset by
     * ListingWriter.renew(). Null only on old listings not yet backfilled. */
    val renewedAt: Long?,
    /** Epoch milliseconds (see FirestoreValue.millis). */
    val createdAt: Long?,
    val updatedAt: Long?,
) {
    val locationLine: String get() = Format.location(city, state)

    val primaryPhoto: String? get() = photos.firstOrNull()

    /** "2022 Rad Power RadCity 5 Plus" — the human name for this bike, used
     * where the seller's free-text title isn't wanted (share subject, the
     * brand/model link, the photo filename slug). */
    val makeModel: String
        get() = listOfNotNull(year?.toString(), brand, model)
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    val webUrl: String get() = "https://ebiketrader.net/listing/$id"

    /** When a live listing expires, or null if it isn't counting down. */
    val expiresAt: Long?
        get() = if (status != ListingStatus.ACTIVE) null
        else (renewedAt ?: createdAt)?.plus(LISTING_LIFETIME_DAYS * DAY_MS)

    /** Whole days until expiry, rounded up — 0 means today. */
    val daysUntilExpiry: Int?
        get() = expiresAt?.let { at ->
            val remaining = at - System.currentTimeMillis()
            if (remaining <= 0) 0 else ((remaining + DAY_MS - 1) / DAY_MS).toInt()
        }

    /** Expired, or live and inside the renew window. Sold listings come back
     * through "Mark as active", which renews them too. */
    val canRenew: Boolean
        get() = status == ListingStatus.EXPIRED ||
            (daysUntilExpiry?.let { it <= RENEW_WINDOW_DAYS } ?: false)

    companion object {
        /** Field-for-field port of fromDoc() in src/lib/listings.ts, including
         * each fallback — so a listing renders identically on Android, on iOS
         * and on the web even when optional fields are missing.
         *
         * Note `description` reads the Firestore key "description"; the iOS
         * model had to rename its property to listingDescription to dodge a
         * collision, but Kotlin has no such conflict. */
        fun fromDoc(doc: DocumentSnapshot): Listing {
            val d = doc.data ?: emptyMap<String, Any>()
            return Listing(
                id = doc.id,
                title = FirestoreValue.string(d["title"]),
                brand = FirestoreValue.string(d["brand"]),
                model = FirestoreValue.string(d["model"]),
                year = FirestoreValue.int(d["year"]),
                price = FirestoreValue.double(d["price"]) ?: 0.0,
                condition = Condition.from(FirestoreValue.string(d["condition"])),
                mileageMiles = FirestoreValue.int(d["mileageMiles"]),
                batteryHealthPct = FirestoreValue.int(d["batteryHealthPct"]),
                motorType = FirestoreValue.string(d["motorType"]),
                frameSize = FirestoreValue.string(d["frameSize"]),
                wheelSize = FirestoreValue.string(d["wheelSize"]),
                color = FirestoreValue.string(d["color"]),
                description = FirestoreValue.string(d["description"]),
                city = FirestoreValue.string(d["city"]),
                state = FirestoreValue.string(d["state"]),
                lat = FirestoreValue.double(d["lat"]),
                lng = FirestoreValue.double(d["lng"]),
                photos = FirestoreValue.stringList(d["photos"]),
                sellerId = FirestoreValue.string(d["sellerId"]),
                sellerName = FirestoreValue.string(d["sellerName"], fallback = "Seller"),
                status = ListingStatus.from(FirestoreValue.string(d["status"])),
                favoriteCount = FirestoreValue.int(d["favoriteCount"]) ?: 0,
                renewedAt = FirestoreValue.millis(d["renewedAt"]),
                createdAt = FirestoreValue.millis(d["createdAt"]),
                updatedAt = FirestoreValue.millis(d["updatedAt"]),
            )
        }
    }
}
