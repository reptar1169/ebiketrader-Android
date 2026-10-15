package com.reptar.ebiketrader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.reptar.ebiketrader.support.await
import com.reptar.ebiketrader.support.buildPhotoSlugBase
import com.reptar.ebiketrader.support.logDebug
import com.reptar.ebiketrader.support.photoUniqueId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.min

/** The editable half of a listing — everything a seller types. Deliberately
 * excludes sellerId/status/favoriteCount/lat/lng/timestamps: those are set
 * once at creation or maintained server-side, and firestore.rules rejects a
 * client that tries to change them. */
data class ListingInput(
    val title: String = "",
    val brand: String = "",
    val model: String = "",
    val year: Int? = null,
    val price: Double? = null,
    val condition: Condition = Condition.GOOD,
    val mileageMiles: Int? = null,
    val batteryHealthPct: Int? = null,
    val motorType: String = "",
    val frameSize: String = "",
    val wheelSize: String = "",
    val color: String = "",
    val description: String = "",
    val city: String = "",
    val state: String = "",
) {
    /** Same three the website's ListingForm requires. */
    val validationError: String?
        get() = when {
            title.isBlank() || brand.isBlank() || price == null ->
                "Title, brand, and price are required."
            price < 0 || price.isNaN() || price.isInfinite() -> "Enter a valid price."
            else -> null
        }

    /** Firestore payload. Optional numbers go out as explicit nulls rather
     * than being omitted, matching what the web form writes — the readers
     * treat a missing field and a null one the same, but keeping the shape
     * identical means a listing edited in one client doesn't look different
     * in another. */
    fun firestoreData(): Map<String, Any?> = mapOf(
        "title" to title.trim(),
        "brand" to brand.trim(),
        "model" to model.trim(),
        "year" to year,
        "price" to (price ?: 0.0),
        "condition" to condition.stored,
        "mileageMiles" to mileageMiles,
        "batteryHealthPct" to batteryHealthPct,
        "motorType" to motorType.trim(),
        "frameSize" to frameSize.trim(),
        "wheelSize" to wheelSize.trim(),
        "color" to color.trim(),
        "description" to description.trim(),
        "city" to city.trim(),
        "state" to state.trim(),
    )

    companion object {
        /** Seeds the form when editing an existing listing. */
        fun from(listing: Listing) = ListingInput(
            title = listing.title,
            brand = listing.brand,
            model = listing.model,
            year = listing.year,
            price = listing.price,
            condition = listing.condition,
            mileageMiles = listing.mileageMiles,
            batteryHealthPct = listing.batteryHealthPct,
            motorType = listing.motorType,
            frameSize = listing.frameSize,
            wheelSize = listing.wheelSize,
            color = listing.color,
            description = listing.description,
            city = listing.city,
            state = listing.state,
        )
    }
}

/** One photo in the seller's chosen order — port of PhotoPlanItem in
 * src/lib/types.ts. The array's order is the saved `photos` order, which is
 * what lets a freshly shot photo be dragged ahead of an existing one. */
sealed interface PhotoPlanItem {
    val id: String

    data class Existing(val url: String) : PhotoPlanItem {
        override val id: String get() = url
    }

    /** A photo the seller just picked or shot, still only a content Uri. Held
     * as a Uri rather than a decoded Bitmap so a plan of eight photos doesn't
     * sit in memory as eight full-resolution bitmaps — the decode happens once,
     * at upload. */
    data class New(val uri: Uri, private val key: String = UUID.randomUUID().toString()) :
        PhotoPlanItem {
        override val id: String get() = key
    }
}

fun List<PhotoPlanItem>.withPhoto(uri: Uri): List<PhotoPlanItem> =
    if (size >= ListingWriter.MAX_PHOTOS) this else this + PhotoPlanItem.New(uri)

object ListingWriter {
    private const val TAG = "ebt-write"

    const val MAX_PHOTOS = 8

    private val db get() = FirebaseFirestore.getInstance()

    /** Phone photos run 4-12MB each; Storage caps a single upload at 15MB
     * (storage.rules) and every byte is also the buyer's data plan on the way
     * back down. 1600px on the long edge is still more than any view in the
     * app or on the website renders at. */
    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 80

    /** Decodes, downscales and re-encodes one picked photo.
     *
     * inSampleSize first so a 12-megapixel JPEG is never fully decoded into
     * memory — decoding at full size and then scaling down is how photo
     * uploads OOM on cheaper phones.
     *
     * Also honours the EXIF orientation tag. Unlike UIImage on iOS, Android's
     * Bitmap carries no orientation of its own, so a photo shot in portrait
     * would upload sideways without this. */
    private fun encode(context: Context, uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // The stream-null check and the decode result have to stay separate.
        // With inJustDecodeBounds set, decodeStream returns null BY DESIGN —
        // it only fills in `bounds` — so a `?: return null` wrapped around the
        // whole use{} block bails on every photo, every time.
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null

        var sample = 1
        while (longEdge / (sample * 2) >= MAX_DIMENSION) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val scale = min(1f, MAX_DIMENSION.toFloat() / maxOf(decoded.width, decoded.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }

        val oriented = applyExifRotation(context, uri, scaled)

        return ByteArrayOutputStream().use { out ->
            oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }

        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Port of uploadListingPhotos(). Filenames are
     * "{slugBase}-{index}-{uniqueId}.jpg" under
     * listing-photos/{uid}/{listingId}/, exactly as on the web — which is what
     * makes the filenames themselves rankable in Google Images. */
    private suspend fun upload(
        context: Context,
        uris: List<Uri>,
        uid: String,
        listingId: String,
        slugBase: String,
        progress: ((Int, Int) -> Unit)?,
    ): List<String> = withContext(Dispatchers.IO) {
        val urls = mutableListOf<String>()

        uris.forEachIndexed { index, uri ->
            progress?.invoke(index, uris.size)

            val bytes = encode(context, uri)
                ?: throw IllegalStateException("Couldn't read photo ${index + 1}.")
            logDebug(TAG, "uploading photo ${index + 1}/${uris.size}, ${bytes.size} bytes")
            val name = "$slugBase-${index + 1}-${photoUniqueId()}.jpg"
            val reference = FirebaseStorage.getInstance()
                .reference
                .child("listing-photos/$uid/$listingId/$name")

            val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
            logDebug(TAG, "putBytes -> ${reference.path}")
            reference.putBytes(bytes, metadata).await()
            urls += reference.downloadUrl.await().toString()
        }

        progress?.invoke(uris.size, uris.size)
        urls
    }

    /** Port of resolvePhotoPlan(): uploads whatever is new, passes existing
     * URLs through untouched, and returns them all in plan order — so a photo
     * added during this edit can sit first in the saved array rather than
     * always being appended after the existing ones. */
    private suspend fun resolve(
        context: Context,
        plan: List<PhotoPlanItem>,
        uid: String,
        listingId: String,
        input: ListingInput,
        progress: ((Int, Int) -> Unit)?,
    ): List<String> {
        val newUris = plan.filterIsInstance<PhotoPlanItem.New>().map { it.uri }

        val uploaded = if (newUris.isEmpty()) {
            emptyList()
        } else {
            upload(
                context = context,
                uris = newUris,
                uid = uid,
                listingId = listingId,
                slugBase = buildPhotoSlugBase(input.year, input.brand, input.model),
                progress = progress,
            )
        }

        var nextUpload = 0
        val resolved = plan.mapNotNull { item ->
            when (item) {
                is PhotoPlanItem.Existing -> item.url
                is PhotoPlanItem.New -> uploaded.getOrNull(nextUpload++)
            }
        }

        check(resolved.size == plan.size) {
            "Only ${resolved.size} of ${plan.size} photos were ready — nothing was saved."
        }
        return resolved
    }

    /** Port of createListing(): the document is created first so photos can be
     * namespaced under its id, then the URLs are attached in a follow-up
     * update. */
    suspend fun create(
        context: Context,
        input: ListingInput,
        sellerId: String,
        sellerName: String,
        plan: List<PhotoPlanItem>,
        progress: ((Int, Int) -> Unit)? = null,
    ): String {
        val data = input.firestoreData().toMutableMap()
        data["photos"] = emptyList<String>()
        data["sellerId"] = sellerId
        data["sellerName"] = sellerName
        // No sellerEmail: listing documents are world-readable per
        // firestore.rules, so anything written here is public. The address
        // stays on the private users/{uid} document.
        data["status"] = ListingStatus.ACTIVE.stored
        // firestore.rules requires this to be exactly 0 on create, and lat/lng
        // to be absent — they're filled in by the geocoding Cloud Function.
        data["favoriteCount"] = 0
        // Starts the 30-day clock; firestore.rules requires server time.
        data["renewedAt"] = FieldValue.serverTimestamp()
        data["createdAt"] = FieldValue.serverTimestamp()
        data["updatedAt"] = FieldValue.serverTimestamp()

        val reference = db.collection("listings").add(data).await()

        if (plan.isNotEmpty()) {
            val photos = resolve(context, plan, sellerId, reference.id, input, progress)
            reference.update(
                mapOf("photos" to photos, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
        }

        return reference.id
    }

    /** Port of updateListing(). Never touches sellerId, favoriteCount or
     * lat/lng — firestore.rules rejects the whole write if any of them move. */
    suspend fun update(
        context: Context,
        listingId: String,
        uid: String,
        input: ListingInput,
        plan: List<PhotoPlanItem>,
        progress: ((Int, Int) -> Unit)? = null,
    ) {
        logDebug(TAG, "update $listingId as uid=$uid, plan=${plan.size}")
        val photos = resolve(context, plan, uid, listingId, input, progress)
        logDebug(TAG, "resolved ${photos.size} photo urls; writing document")
        val data = input.firestoreData().toMutableMap()
        data["photos"] = photos
        data["updatedAt"] = FieldValue.serverTimestamp()
        db.collection("listings").document(listingId).update(data).await()
    }

    suspend fun markSold(listingId: String) {
        db.collection("listings").document(listingId).update(
            mapOf(
                "status" to ListingStatus.SOLD.stored,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    /** Port of renewListing(): back to active for another 30 days. Also how
     * a sold listing is reactivated, or one sold over a month ago would just
     * expire again. Leaves createdAt alone, so renewing doesn't bump it up
     * the newest-first feed. */
    suspend fun renew(listingId: String) {
        db.collection("listings").document(listingId).update(
            mapOf(
                "status" to ListingStatus.ACTIVE.stored,
                "renewedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun delete(listingId: String) {
        db.collection("listings").document(listingId).delete().await()
    }
}
