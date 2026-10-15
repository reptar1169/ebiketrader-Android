package com.reptar.ebiketrader.ui.lists

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reptar.ebiketrader.data.Listing
import com.reptar.ebiketrader.data.ListingsViewModel
import com.reptar.ebiketrader.ui.components.ListingGrid

/** Everything you've listed, sold ones included — which is why the status
 * filter is null here rather than ACTIVE. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListingsScreen(
    uid: String,
    onOpenListing: (Listing) -> Unit,
    onBack: () -> Unit,
    viewModel: ListingsViewModel = viewModel {
        ListingsViewModel(status = null, sellerId = uid)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(uid) { viewModel.startIfNeeded() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My listings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { insets ->
        ListingGrid(
            listings = state.listings,
            isLoading = state.isLoading,
            emptyMessage = "You haven't listed a bike yet.",
            onOpenListing = onOpenListing,
            modifier = Modifier.fillMaxSize().padding(insets),
        )
    }
}
