package com.reptar.ebiketrader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/** Mirrors US_STATES in the website's ListingForm.tsx — the same 51 entries in
 * the same order, so every form offers identical choices and the stored
 * `state` value means the same thing everywhere. */
val US_STATES = listOf(
    "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID",
    "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS",
    "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "OH", "OK",
    "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV",
    "WI", "WY", "DC",
)

/** US city names per state, for the city suggestions on the post form.
 *
 * Bundled rather than fetched: the whole country is ~200KB, so shipping it
 * buys instant suggestions with no network round-trip on the first keystroke.
 * The same assets/cities.json the iOS app bundles, generated from the
 * website's scripts/generate-city-data.js (GeoNames, US cities with
 * population >= 1000).
 *
 * Licensing: the place names originate from GeoNames (geonames.org) and are
 * CC BY 4.0, which requires attribution — see the Credits section of
 * ebiketrader.net/support. The all-the-cities package that repackages them is
 * MIT.
 *
 * The point of all this, as on the web, is to steer sellers toward a spelling
 * that will actually geocode — the onListingWrite Cloud Function turns
 * city/state into the coordinates behind the map. A city that isn't listed can
 * still be typed by hand. */
object CityCatalog {
    private var byState: Map<String, List<String>> = emptyMap()

    /** Parsed once, off the main thread by whoever calls it first. Failure is
     * silent: suggestions simply don't appear and typing a city by hand still
     * works, same as on iOS. */
    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        // 200KB of JSON is not main-thread work. LaunchedEffect runs on the
        // main dispatcher, so parsing it there stalled the form's first frames.
        if (byState.isNotEmpty()) return@withContext
        byState = try {
            val text = context.assets.open("cities.json").bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            buildMap {
                json.keys().forEach { state ->
                    val array = json.getJSONArray(state)
                    put(state, List(array.length()) { array.getString(it) })
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun cities(state: String): List<String> = byState[state.uppercase(Locale.US)] ?: emptyList()

    /** Up to [limit] matches for [query] within [state].
     *
     * Names arrive most-populous-first and that order is kept, but names that
     * *start* with the query come ahead of ones that merely contain it. The
     * website's <datalist> leaves ranking to the browser, which is why typing
     * "s" there offers Los Angeles — technically a match, rarely what you
     * meant. */
    fun suggestions(query: String, state: String, limit: Int = 8): List<String> {
        val needle = query.trim().lowercase(Locale.US)
        if (needle.isEmpty() || state.isEmpty()) return emptyList()

        val startsWith = mutableListOf<String>()
        val contains = mutableListOf<String>()

        for (name in cities(state)) {
            val lower = name.lowercase(Locale.US)
            if (lower.startsWith(needle)) {
                startsWith += name
                if (startsWith.size >= limit) break
            } else if (contains.size < limit && lower.contains(needle)) {
                contains += name
            }
        }

        return (startsWith + contains).take(limit)
    }
}
