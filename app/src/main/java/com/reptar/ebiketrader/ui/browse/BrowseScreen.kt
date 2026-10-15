package com.reptar.ebiketrader.ui.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reptar.ebiketrader.data.UserDataViewModel
import com.reptar.ebiketrader.data.Listing
import com.reptar.ebiketrader.data.ListingSort
import com.reptar.ebiketrader.data.ListingsViewModel
import com.reptar.ebiketrader.ui.components.ListingGrid
import com.reptar.ebiketrader.ui.components.Wordmark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    userDataViewModel: UserDataViewModel,
    onOpenListing: (Listing) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val userData by userDataViewModel.state.collectAsStateWithLifecycle()
    // Hiding a blocked seller's listings is the cosmetic half; firestore.rules
    // also refuses messages in either direction across a block.
    val visible = state.visible.filter { it.sellerId !in userData.blockedIds }
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.startIfNeeded() }

    // The Scaffold is load-bearing, not decoration: enableEdgeToEdge() lets
    // content draw under the status bar and camera cutout, and without a
    // Scaffold supplying insets the first thing on screen ends up underneath
    // them and untappable.
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Wordmark() },
                actions = {
                    SortMenu(state.sort, viewModel::setSort)
                    IconButton(onClick = { showFilters = true }) {
                        // The badge is the only cue that a filter is narrowing
                        // the grid; without it an empty result looks the same
                        // as empty inventory.
                        BadgedBox(badge = { if (state.filters.isActive) Badge() }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filters")
                        }
                    }
                },
            )
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::setSearch,
                placeholder = { Text("Search brand, model or city") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )

            ListingGrid(
                listings = visible,
                isLoading = state.isLoading,
                emptyMessage = if (state.search.isNotEmpty() || state.filters.isActive) {
                    "No bikes match that. Try a different search."
                } else {
                    "No listings yet."
                },
                onOpenListing = onOpenListing,
                modifier = Modifier.weight(1f),
            )

            state.errorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            filters = state.filters,
            availableBrands = state.brands,
            onApply = {
                viewModel.setFilters(it)
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun SortMenu(current: ListingSort, onPick: (ListingSort) -> Unit) {
    var open by remember { mutableStateOf(false) }

    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.Sort, contentDescription = "Sort")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        ListingSort.entries.forEach { sort ->
            DropdownMenuItem(
                text = {
                    Text(
                        sort.label,
                        fontWeight = if (sort == current) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                onClick = {
                    onPick(sort)
                    open = false
                },
            )
        }
    }
}
