package com.reptar.ebiketrader.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.content.Intent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.reptar.ebiketrader.data.AuthViewModel
import com.reptar.ebiketrader.data.ChatService
import com.reptar.ebiketrader.data.ListingWriter
import com.reptar.ebiketrader.data.Listing
import com.reptar.ebiketrader.data.UserDataViewModel
import com.reptar.ebiketrader.data.ListingDetailViewModel
import com.reptar.ebiketrader.data.LISTING_LIFETIME_DAYS
import com.reptar.ebiketrader.data.ListingStatus
import com.reptar.ebiketrader.support.Format
import com.reptar.ebiketrader.ui.components.PhotoGallery
import com.reptar.ebiketrader.ui.components.ProgressOverlay
import com.reptar.ebiketrader.ui.theme.BrandDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    listingId: String,
    authViewModel: AuthViewModel,
    userDataViewModel: UserDataViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onOpenThread: (String) -> Unit,
    onEdit: (String) -> Unit,
    viewModel: ListingDetailViewModel = viewModel { ListingDetailViewModel(listingId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val auth by authViewModel.state.collectAsStateWithLifecycle()
    val userData by userDataViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(listingId) { viewModel.startIfNeeded() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var contactError by remember { mutableStateOf<String?>(null) }
    var isStartingThread by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    // A Bool couldn't tell marking-sold apart from deleting in the overlay.
    var busyTitle by remember { mutableStateOf<String?>(null) }

    val listing = state.listing
    val isFavorite = userData.isFavorite(listingId)
    val isOwner = auth.uid != null && auth.uid == listing?.sellerId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listing?.makeModel?.takeIf { it.isNotEmpty() } ?: "Listing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (auth.isSignedIn) {
                                userDataViewModel.toggleFavorite(listingId)
                            } else {
                                onSignIn()
                            }
                        },
                    ) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "Saved" else "Save",
                            tint = if (isFavorite) Color.Red else LocalContentColor.current,
                        )
                    }

                    listing?.let { current ->
                        IconButton(
                            onClick = {
                                // Shares the web URL rather than anything
                                // app-specific, so the link opens for whoever
                                // receives it whether or not they have the app.
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, current.title)
                                    putExtra(Intent.EXTRA_TEXT, current.webUrl)
                                }
                                context.startActivity(Intent.createChooser(share, null))
                            },
                        ) { Icon(Icons.Default.Share, contentDescription = "Share") }

                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            if (isOwner) {
                                DropdownMenuItem(
                                    text = { Text("Edit listing") },
                                    onClick = {
                                        menuOpen = false
                                        onEdit(listingId)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (current.status == ListingStatus.SOLD) {
                                                "Mark as active"
                                            } else {
                                                "Mark as sold"
                                            }
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        val wasSold = current.status == ListingStatus.SOLD
                                        busyTitle = "Updating…"
                                        scope.launch {
                                            // Reactivating renews — see ListingWriter.renew().
                                            runCatching {
                                                if (wasSold) {
                                                    ListingWriter.renew(listingId)
                                                } else {
                                                    ListingWriter.markSold(listingId)
                                                }
                                            }.onFailure { contactError = it.message }
                                            busyTitle = null
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete listing") },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Report listing") },
                                    onClick = {
                                        menuOpen = false
                                        if (auth.isSignedIn) showReport = true else onSignIn()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Block this seller") },
                                    onClick = {
                                        menuOpen = false
                                        if (auth.isSignedIn) confirmBlock = true else onSignIn()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            when {
                state.isLoading && listing == null ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.wasRemoved || listing == null ->
                    Text(
                        "This listing is no longer available.",
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )

                else -> Body(
                    listing = listing,
                    isOwner = isOwner,
                    isStartingThread = isStartingThread,
                    contactError = contactError,
                    onRenew = {
                        busyTitle = "Renewing…"
                        scope.launch {
                            runCatching { ListingWriter.renew(listingId) }
                                .onFailure { contactError = it.message }
                            busyTitle = null
                        }
                    },
                    onContact = {
                        if (!auth.isSignedIn) {
                            onSignIn()
                            return@Body
                        }
                        val buyerId = auth.uid ?: return@Body
                        isStartingThread = true
                        contactError = null
                        scope.launch {
                            try {
                                val id = ChatService.getOrCreateConversation(
                                    listing = listing,
                                    buyerId = buyerId,
                                    buyerName = auth.displayName,
                                )
                                onOpenThread(id)
                            } catch (e: Exception) {
                                // Surfaced rather than swallowed: a silent
                                // failure here looks like a dead button.
                                contactError = e.message ?: "Couldn't start that conversation."
                            }
                            isStartingThread = false
                        }
                    },
                )
            }
        }
    }

    ProgressOverlay(visible = busyTitle != null, title = busyTitle.orEmpty())

    if (showReport && listing != null) {
        ReportListingSheet(
            listing = listing,
            reporterId = auth.uid.orEmpty(),
            onDismiss = { showReport = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this listing?") },
            text = { Text("This can't be undone. Its photos are removed too.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    busyTitle = "Deleting…"
                    scope.launch {
                        val result = runCatching { ListingWriter.delete(listingId) }
                        busyTitle = null
                        result.onSuccess { onBack() }
                            .onFailure { contactError = it.message }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmBlock && listing != null) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Block ${listing.sellerName}?") },
            text = {
                Text(
                    "Their listings will stop appearing for you, and neither of you will be " +
                        "able to message the other. You can undo this under Account."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    scope.launch {
                        userDataViewModel.block(listing.sellerId, listing.sellerName)
                        onBack()
                    }
                }) { Text("Block") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlock = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Body(
    listing: Listing,
    isOwner: Boolean,
    isStartingThread: Boolean,
    contactError: String?,
    onRenew: () -> Unit,
    onContact: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PhotoGallery(photos = listing.photos, badge = listing.status.badge)

        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    Format.price(listing.price),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandDark,
                )
                Text(listing.title, style = MaterialTheme.typography.titleMedium)

                val posted = Format.relativeTime(listing.createdAt)
                Text(
                    listOf(listing.locationLine, posted.takeIf { it.isNotEmpty() }?.let { "Listed $it" })
                        .filterNotNull()
                        .filter { it.isNotEmpty() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (listing.favoriteCount > 0) {
                    val people =
                        if (listing.favoriteCount == 1) "person has" else "people have"
                    Text(
                        "${listing.favoriteCount} $people saved this",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isOwner) {
                ExpiryCard(listing, onRenew)
            } else if (listing.status == ListingStatus.EXPIRED) {
                Text(
                    "This listing has expired and may no longer be available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Specs(listing)

            if (listing.description.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Description", style = MaterialTheme.typography.titleSmall)
                    // Android's Text is selectable inside a SelectionContainer,
                    // which the NavHost wraps — no UITextView equivalent needed
                    // the way iOS required for partial selection.
                    Text(
                        listing.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Seller",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(listing.sellerName, style = MaterialTheme.typography.bodyLarge)
                }
            }

            contactError?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (!isOwner && listing.status == ListingStatus.ACTIVE) {
                Button(
                    onClick = onContact,
                    enabled = !isStartingThread,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Message seller") }
            }
        }
    }
}

/** The owner's view of the 30-day clock, with Renew once it's close or
 * already run out. Nothing for sold listings — they don't expire. */
@Composable
private fun ExpiryCard(listing: Listing, onRenew: () -> Unit) {
    val message = when {
        listing.status == ListingStatus.EXPIRED ->
            "Expired and hidden from buyers. Renew to put it back up for $LISTING_LIFETIME_DAYS days."
        else -> when (val days = listing.daysUntilExpiry) {
            null -> return
            0 -> "Expires today."
            1 -> "Expires in 1 day."
            else -> "Expires in $days days."
        }
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (listing.canRenew) {
                Button(onClick = onRenew, modifier = Modifier.fillMaxWidth()) {
                    Text("Renew for $LISTING_LIFETIME_DAYS days")
                }
            }
        }
    }
}

/** Same rows, same order, same hide-it-when-blank behaviour as the Spec
 * component in ListingDetailClient.tsx and the iOS SpecRow. */
@Composable
private fun Specs(listing: Listing) {
    val rows = listOf(
        "Brand" to listing.brand,
        "Model" to listing.model,
        "Year" to listing.year?.toString(),
        "Condition" to listing.condition.label,
        "Mileage" to listing.mileageMiles?.let { "$it mi" },
        "Battery health" to listing.batteryHealthPct?.let { "$it%" },
        "Motor" to listing.motorType,
        "Frame size" to listing.frameSize,
        "Wheel size" to listing.wheelSize,
        "Color" to listing.color,
    ).mapNotNull { (label, value) ->
        value?.takeIf { it.isNotEmpty() }?.let { label to it }
    }

    if (rows.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
                if (index < rows.lastIndex) HorizontalDivider()
            }
        }
    }
}
