package com.reptar.ebiketrader.data

import com.google.firebase.Timestamp

/** Small readers for the untyped Map a Firestore document hands back.
 *
 * Everything goes through these rather than Firestore's toObject() on purpose:
 * the website writes these documents with plain object literals and has added
 * fields over time (favoriteCount, lat/lng), so a strict decoder would fail on
 * perfectly good older listings. Mirrors the hand-written fromDoc() in
 * src/lib/listings.ts and the iOS FirestoreValue, fallback for fallback. */
object FirestoreValue {
    /** Firestore hands integers back as Long and decimals as Double, so a
     * direct `as? Int` fails whenever a price happened to be written as 1200.0
     * rather than 1200. Going through Number accepts either — the same reason
     * the iOS port goes through NSNumber. */
    fun int(value: Any?): Int? = (value as? Number)?.toInt()

    fun double(value: Any?): Double? = (value as? Number)?.toDouble()

    fun string(value: Any?, fallback: String = ""): String = value as? String ?: fallback

    /** Epoch milliseconds, matching the `number | null` the web's toMillis()
     * produces. A serverTimestamp() the server hasn't resolved yet arrives as
     * null here exactly as it does on the web, so just-posted content simply
     * has no timestamp for a moment. */
    fun millis(value: Any?): Long? {
        val timestamp = value as? Timestamp ?: return null
        return timestamp.seconds * 1000 + timestamp.nanoseconds / 1_000_000
    }

    @Suppress("UNCHECKED_CAST")
    fun stringList(value: Any?): List<String> =
        (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
}
