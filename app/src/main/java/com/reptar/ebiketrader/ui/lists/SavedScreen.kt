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
import com.reptar.ebiketrader.data.SavedListingsViewModel
import com.reptar.ebiketrader.data.UserDataViewModel
import com.reptar.ebiketrader.ui.components.ListingGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    userDataViewModel: UserDataViewModel,
    onOpenListing: (Listing) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: SavedListingsViewModel = viewModel(),
) {
    val userData by userDataViewModel.state.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Reloads whenever the favourite set changes, so un-saving a bike here
    // removes it without a manual refresh.
    LaunchedEffect(userData.favoriteIds) { viewModel.load(userData.favoriteIds) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved") },
                navigationIcon = {
                    // Null when this is the Saved tab: a tab has nowhere to go
                    // back to, and an arrow that pops to Browse reads as a bug.
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { insets ->
        ListingGrid(
            listings = state.listings,
            isLoading = state.isLoading,
            emptyMessage = "Nothing saved yet. Tap the heart on a bike to keep it here.",
            onOpenListing = onOpenListing,
            modifier = Modifier.fillMaxSize().padding(insets),
        )
    }
}
