package com.reptar.ebiketrader.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.reptar.ebiketrader.data.Condition
import com.reptar.ebiketrader.data.ListingFilters

/** The filter controls the website keeps inline above the grid. There isn't
 * room for that on a phone, so they live in a bottom sheet and the browse
 * toolbar shows whether any are on.
 *
 * Chips rather than the navigation-link pickers the iOS sheet uses — on
 * Android a chip row shows what's selected without opening anything, and
 * tapping a selected chip clears it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filters: ListingFilters,
    availableBrands: List<String>,
    onApply: (ListingFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    // Prices are held as text so a half-typed value doesn't fight the parser;
    // they're read back into the filters on Apply.
    var minPriceText by remember { mutableStateOf(filters.minPrice?.toInt()?.toString() ?: "") }
    var maxPriceText by remember { mutableStateOf(filters.maxPrice?.toInt()?.toString() ?: "") }
    var draft by remember { mutableStateOf(filters) }

    fun withPrices() = draft.copy(
        minPrice = minPriceText.filter(Char::isDigit).toDoubleOrNull(),
        maxPrice = maxPriceText.filter(Char::isDigit).toDoubleOrNull(),
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Condition", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Condition.entries.forEach { condition ->
                        FilterChip(
                            selected = draft.condition == condition,
                            // Tapping the selected chip clears it, so there is
                            // no separate "Any" option to keep in sync.
                            onClick = {
                                draft = draft.copy(
                                    condition = if (draft.condition == condition) null else condition
                                )
                            },
                            label = { Text(condition.shortLabel) },
                        )
                    }
                }
            }

            if (availableBrands.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Built from the brands actually in the feed, same idea as
                    // the homepage's "Shop by brand" row — a hardcoded list
                    // drifts out of date the moment someone lists a new make.
                    Text("Brand", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableBrands.forEach { brand ->
                            FilterChip(
                                selected = draft.brand == brand,
                                onClick = {
                                    draft = draft.copy(
                                        brand = if (draft.brand == brand) null else brand
                                    )
                                },
                                label = { Text(brand) },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Price", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = minPriceText,
                        onValueChange = { minPriceText = it.filter(Char::isDigit) },
                        label = { Text("Min") },
                        placeholder = { Text("Any") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxPriceText,
                        onValueChange = { maxPriceText = it.filter(Char::isDigit) },
                        label = { Text("Max") },
                        placeholder = { Text("Any") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = {
                        draft = ListingFilters()
                        minPriceText = ""
                        maxPriceText = ""
                    },
                    enabled = withPrices().isActive,
                ) { Text("Clear all") }

                Button(
                    onClick = { onApply(withPrices()) },
                    modifier = Modifier.weight(1f),
                ) { Text("Show results") }
            }
        }
    }
}
