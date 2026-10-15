package com.reptar.ebiketrader.data

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.reptar.ebiketrader.support.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListingFormUiState(
    val input: ListingInput = ListingInput(),
    val plan: List<PhotoPlanItem> = emptyList(),
    // Numeric fields are held as text so a half-typed value doesn't fight the
    // parser; they're read into `input` on save.
    val priceText: String = "",
    val yearText: String = "",
    val mileageText: String = "",
    val batteryText: String = "",
    val isSeeded: Boolean = false,
    val isSaving: Boolean = false,
    val uploadedCount: Int = 0,
    val uploadTotal: Int = 0,
    val errorMessage: String? = null,
    val savedListingId: String? = null,
) {
    val progressDetail: String?
        get() = if (uploadTotal > 0) {
            "Photo ${minOf(uploadedCount + 1, uploadTotal)} of $uploadTotal"
        } else {
            null
        }
}

/** Post a new listing, or edit one you already own. */
class ListingFormViewModel(private val existingId: String?) : ViewModel() {

    private val _state = MutableStateFlow(ListingFormUiState())
    val state: StateFlow<ListingFormUiState> = _state.asStateFlow()

    val isEditing: Boolean get() = existingId != null

    fun seedIfNeeded() {
        if (_state.value.isSeeded) return
        val id = existingId ?: run {
            _state.update { it.copy(isSeeded = true) }
            return
        }

        viewModelScope.launch {
            try {
                val doc = FirebaseFirestore.getInstance()
                    .collection("listings").document(id).get().await()
                if (!doc.exists()) {
                    _state.update { it.copy(isSeeded = true, errorMessage = "That listing is gone.") }
                    return@launch
                }
                val listing = Listing.fromDoc(doc)
                _state.update {
                    it.copy(
                        isSeeded = true,
                        input = ListingInput.from(listing),
                        plan = listing.photos.map(PhotoPlanItem::Existing),
                        priceText = listing.price.toInt().toString(),
                        yearText = listing.year?.toString() ?: "",
                        mileageText = listing.mileageMiles?.toString() ?: "",
                        batteryText = listing.batteryHealthPct?.toString() ?: "",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSeeded = true, errorMessage = e.message) }
            }
        }
    }

    fun update(transform: (ListingInput) -> ListingInput) =
        _state.update { it.copy(input = transform(it.input)) }

    fun setPlan(plan: List<PhotoPlanItem>) = _state.update { it.copy(plan = plan) }
    fun setPriceText(text: String) = _state.update { it.copy(priceText = text.filter(Char::isDigit)) }
    fun setYearText(text: String) = _state.update { it.copy(yearText = text.filter(Char::isDigit)) }
    fun setMileageText(text: String) = _state.update { it.copy(mileageText = text.filter(Char::isDigit)) }
    fun setBatteryText(text: String) = _state.update { it.copy(batteryText = text.filter(Char::isDigit)) }
    fun clearError() = _state.update { it.copy(errorMessage = null) }

    fun save(context: Context, sellerId: String, sellerName: String) {
        val current = _state.value
        if (current.isSaving) return

        val input = current.input.copy(
            price = current.priceText.toDoubleOrNull(),
            year = current.yearText.toIntOrNull(),
            mileageMiles = current.mileageText.toIntOrNull(),
            batteryHealthPct = current.batteryText.toIntOrNull(),
        )

        input.validationError?.let { problem ->
            _state.update { it.copy(input = input, errorMessage = problem) }
            return
        }

        val newPhotos = current.plan.count { it is PhotoPlanItem.New }
        _state.update {
            it.copy(
                input = input,
                isSaving = true,
                errorMessage = null,
                uploadedCount = 0,
                uploadTotal = newPhotos,
            )
        }

        viewModelScope.launch {
            try {
                val progress: (Int, Int) -> Unit = { done, total ->
                    _state.update { it.copy(uploadedCount = done, uploadTotal = total) }
                }
                val id = if (existingId != null) {
                    ListingWriter.update(
                        context, existingId, sellerId, input, current.plan, progress
                    )
                    existingId
                } else {
                    ListingWriter.create(
                        context, input, sellerId, sellerName, current.plan, progress
                    )
                }
                _state.update { it.copy(isSaving = false, savedListingId = id) }
            } catch (e: Exception) {
                // Logged as well as shown: the on-screen message is whatever
                // e.message happens to say, which for a Firebase exception is
                // often less useful than the stack trace and the cause.
                Log.e("ebt-write", "save failed", e)
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "${e.javaClass.simpleName}: ${e.message ?: "no message"}",
                    )
                }
            }
        }
    }
}
