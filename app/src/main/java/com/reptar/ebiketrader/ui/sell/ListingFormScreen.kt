package com.reptar.ebiketrader.ui.sell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.reptar.ebiketrader.data.CityCatalog
import com.reptar.ebiketrader.data.Condition
import com.reptar.ebiketrader.data.ListingFormViewModel
import com.reptar.ebiketrader.data.MOTOR_TYPES
import com.reptar.ebiketrader.data.PhotoPlanItem
import com.reptar.ebiketrader.data.US_STATES
import com.reptar.ebiketrader.ui.components.ProgressOverlay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListingFormScreen(
    viewModel: ListingFormViewModel,
    sellerId: String,
    sellerName: String,
    onBack: () -> Unit,
    onOpenPhotos: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.seedIfNeeded()
        CityCatalog.load(context)
    }
    LaunchedEffect(state.savedListingId) { state.savedListingId?.let(onSaved) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Edit listing" else "List your ebike") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PhotosRow(plan = state.plan, onClick = onOpenPhotos)

                SectionLabel("The basics")

                OutlinedTextField(
                    value = state.input.title,
                    onValueChange = { v -> viewModel.update { it.copy(title = v) } },
                    label = { Text("Title") },
                    supportingText = { Text("Required") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.input.brand,
                    onValueChange = { v -> viewModel.update { it.copy(brand = v) } },
                    label = { Text("Brand") },
                    supportingText = { Text("Required") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.input.model,
                    onValueChange = { v -> viewModel.update { it.copy(model = v) } },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.priceText,
                        onValueChange = viewModel::setPriceText,
                        label = { Text("Price") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.yearText,
                        onValueChange = viewModel::setYearText,
                        label = { Text("Year") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                PickerField(
                    label = "Condition",
                    value = state.input.condition.label,
                    options = Condition.entries.map { it.label },
                    onPick = { index ->
                        viewModel.update { it.copy(condition = Condition.entries[index]) }
                    },
                )

                SectionLabel("Details (optional)")

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.mileageText,
                        onValueChange = viewModel::setMileageText,
                        label = { Text("Mileage") },
                        suffix = { Text("mi") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.batteryText,
                        onValueChange = viewModel::setBatteryText,
                        label = { Text("Battery") },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                PickerField(
                    label = "Motor",
                    value = state.input.motorType.ifEmpty { "Not specified" },
                    options = MOTOR_TYPES,
                    onPick = { index ->
                        viewModel.update { it.copy(motorType = MOTOR_TYPES[index]) }
                    },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.input.frameSize,
                        onValueChange = { v -> viewModel.update { it.copy(frameSize = v) } },
                        label = { Text("Frame size") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.input.wheelSize,
                        onValueChange = { v -> viewModel.update { it.copy(wheelSize = v) } },
                        label = { Text("Wheel size") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = state.input.color,
                    onValueChange = { v -> viewModel.update { it.copy(color = v) } },
                    label = { Text("Color") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionLabel("Location")

                // State first, then city — the city suggestions are scoped to
                // the chosen state, which is also the order the website's form
                // uses.
                PickerField(
                    label = "State",
                    value = state.input.state.ifEmpty { "Choose a state" },
                    options = US_STATES,
                    onPick = { index ->
                        viewModel.update { it.copy(state = US_STATES[index], city = "") }
                    },
                )
                CityField(
                    city = state.input.city,
                    state = state.input.state,
                    onCityChange = { v -> viewModel.update { it.copy(city = v) } },
                )

                SectionLabel("Description")

                OutlinedTextField(
                    value = state.input.description,
                    onValueChange = { v -> viewModel.update { it.copy(description = v) } },
                    placeholder = {
                        Text("Upgrades, wear, why you're selling — anything a buyer should know.")
                    },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.errorMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Button(
                    onClick = { viewModel.save(context, sellerId, sellerName) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(if (viewModel.isEditing) "Save changes" else "Post listing")
                }
            }

            state.errorMessage?.let { message ->
                AlertDialog(
                    onDismissRequest = viewModel::clearError,
                    title = { Text("Couldn't save") },
                    text = { Text(message) },
                    confirmButton = {
                        TextButton(onClick = viewModel::clearError) { Text("OK") }
                    },
                )
            }

            ProgressOverlay(
                visible = state.isSaving,
                title = if (viewModel.isEditing) "Saving changes…" else "Posting your listing…",
                detail = state.progressDetail,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun PhotosRow(plan: List<PhotoPlanItem>, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (plan.isEmpty()) {
            Icon(
                Icons.Default.AddAPhoto,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                plan.take(4).forEach { item ->
                    AsyncImage(
                        model = when (item) {
                            is PhotoPlanItem.Existing -> item.url
                            is PhotoPlanItem.New -> item.uri
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                if (plan.isEmpty()) "Add photos" else "${plan.size} photo${if (plan.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (plan.isEmpty()) "Tap to choose or shoot some" else "Tap to reorder or remove",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** A read-only field that opens a menu — Compose has no direct equivalent of
 * SwiftUI's navigationLink Picker, and a bare DropdownMenu with no anchor is
 * hard to discover. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    options: List<String>,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        // An invisible tap layer over the field: a readOnly OutlinedTextField
        // still consumes clicks itself, so clickable() on it never fires.
        Box(
            Modifier
                .matchParentSize()
                .clickable { open = true }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { open = false; onPick(index) },
                )
            }
        }
    }
}

/** City with suggestions from the bundled GeoNames data, scoped to the chosen
 * state. ExposedDropdownMenuBox anchors the list to the field and, unlike the
 * iOS version's first attempt, the platform keeps it clear of the keyboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CityField(city: String, state: String, onCityChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(city, state) { CityCatalog.suggestions(city, state) }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = city,
            onValueChange = {
                onCityChange(it)
                expanded = true
            },
            label = { Text("City") },
            enabled = state.isNotEmpty(),
            supportingText = {
                if (state.isEmpty()) Text("Choose a state first")
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            suggestions.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onCityChange(name)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
