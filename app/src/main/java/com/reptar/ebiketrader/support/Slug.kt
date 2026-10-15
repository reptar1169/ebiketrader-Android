package com.reptar.ebiketrader.support

import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("[\\u0300-\\u036f]")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
private const val BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz"

/** Port of src/lib/slug.ts. All three clients have to produce byte-identical
 * slugs: they name the photo files in Storage, and the website's
 * /brand/[brand]/[model] catalog pages are keyed off the same function, so a
 * bike listed from an Android phone has to land on the same page as one listed
 * from the web or from an iPhone.
 *
 * This follows the web's algorithm literally — NFKD normalise, strip the
 * U+0300-U+036F combining marks, lowercase, collapse runs of
 * non-alphanumerics to a single dash, trim the ends. (The iOS port used
 * .diacriticInsensitive folding plus a hand-written loop to reach the same
 * result; NFKD is available directly here, so this is the closer match.) */
fun slugify(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.US)
        .replace(NON_ALPHANUMERIC, "-")
        .trim('-')

/** Port of buildPhotoSlugBase() in src/lib/listings.ts —
 * "2022-rad-power-radcity-5-plus". This is what gives an uploaded photo a
 * filename Google Images can rank, instead of an opaque timestamp. */
fun buildPhotoSlugBase(year: Int?, brand: String, model: String): String {
    val parts = listOf(year?.toString() ?: "", brand, model)
        .map(::slugify)
        .filter { it.isNotEmpty() }
    return if (parts.isEmpty()) "ebike" else parts.joinToString("-")
}

/** Mirrors the web's `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
 * — keeps filenames collision-free and non-guessable even though the readable
 * part is shared across a listing's photos. */
fun photoUniqueId(): String {
    val suffix = (0 until 6).map { BASE36.random() }.joinToString("")
    return "${System.currentTimeMillis()}-$suffix"
}
