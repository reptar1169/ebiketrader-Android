package com.reptar.ebiketrader.support

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/** Ports src/lib/format.ts from the website, via the same port the iOS app
 * made. The thresholds and wording are copied deliberately rather than handed
 * to android.text.format.DateUtils, so a listing reads "3d ago" on Android,
 * on iOS and on the web instead of three different things. */
object Format {
    private val priceFormat: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 0 }

    private val absoluteDate = SimpleDateFormat("MMM d, yyyy", Locale.US)

    fun price(value: Double): String = priceFormat.format(value)

    /** [millis] is epoch milliseconds — the same unit the web's toMillis()
     * produces. null (an unresolved serverTimestamp) formats as an empty
     * string, same as on the web and on iOS. */
    fun relativeTime(millis: Long?): String {
        if (millis == null || millis <= 0L) return ""

        val diffSec = ((System.currentTimeMillis() - millis) / 1000.0).roundToLong()
        val diffMin = (diffSec / 60.0).roundToLong()
        val diffHr = (diffMin / 60.0).roundToLong()
        val diffDay = (diffHr / 24.0).roundToLong()

        return when {
            diffSec < 60 -> "just now"
            diffMin < 60 -> "${diffMin}m ago"
            diffHr < 24 -> "${diffHr}h ago"
            diffDay < 7 -> "${diffDay}d ago"
            else -> absoluteDate.format(Date(millis))
        }
    }

    /** "Austin, TX" — drops either half if it is blank. */
    fun location(city: String, state: String): String =
        listOf(city, state).filter { it.isNotEmpty() }.joinToString(", ")
}
