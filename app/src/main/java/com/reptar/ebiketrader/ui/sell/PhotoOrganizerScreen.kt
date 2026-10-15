package com.reptar.ebiketrader.ui.sell

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.reptar.ebiketrader.data.ListingWriter
import com.reptar.ebiketrader.data.PhotoPlanItem
import com.reptar.ebiketrader.data.withPhoto
import com.reptar.ebiketrader.ui.theme.Brand
import java.io.File

/** Arranges a listing's photos on a screen of their own.
 *
 * Built this way from the start rather than as the strip-of-thumbnails-with-
 * tiny-arrows the iOS app shipped first: the whole set is visible at once, the
 * delete and options targets are 44dp, and "Make cover photo" does in one tap
 * the reorder that mattered — moving the last of eight photos to the front took
 * seven taps on the old arrows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoOrganizerScreen(
    plan: List<PhotoPlanItem>,
    onPlanChange: (List<PhotoPlanItem>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val remaining = (ListingWriter.MAX_PHOTOS - plan.size).coerceAtLeast(0)

    // The modern photo picker: no storage permission, and the user only ever
    // exposes the photos they choose.
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = remaining.coerceAtLeast(2),
        )
    ) { uris ->
        var next = plan
        uris.forEach { next = next.withPhoto(it) }
        onPlanChange(next)
    }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (saved && uri != null) onPlanChange(plan.withPhoto(uri))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            if (plan.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Bikes with clear photos of the frame, drivetrain and battery sell faster.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(plan, key = { _, item -> item.id }) { index, item ->
                        PhotoCell(
                            item = item,
                            index = index,
                            total = plan.size,
                            onDelete = { onPlanChange(plan.filterNot { it.id == item.id }) },
                            onMakeCover = {
                                onPlanChange(
                                    listOf(plan[index]) + plan.filterIndexed { i, _ -> i != index }
                                )
                            },
                            onMove = { to ->
                                val next = plan.toMutableList()
                                next.add(to, next.removeAt(index))
                                onPlanChange(next)
                            },
                        )
                    }
                }
            }

            HorizontalDivider()

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            pickPhotos.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = remaining > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text("Choose photos") }

                    OutlinedButton(
                        onClick = {
                            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                            val file = File(dir, "shot-${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            pendingCameraUri = uri
                            takePhoto.launch(uri)
                        },
                        enabled = remaining > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text("Camera") }
                }

                Text(
                    if (remaining == 0) {
                        "That's the maximum of ${ListingWriter.MAX_PHOTOS}. Remove one to add another."
                    } else {
                        "The first photo is the one buyers see in search results."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhotoCell(
    item: PhotoPlanItem,
    index: Int,
    total: Int,
    onDelete: () -> Unit,
    onMakeCover: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(Modifier.aspectRatio(1f)) {
        AsyncImage(
            model = when (item) {
                is PhotoPlanItem.Existing -> item.url
                is PhotoPlanItem.New -> item.uri
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        if (index == 0) {
            Text(
                "COVER",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brand)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }

        // 44dp is the minimum comfortable touch target; the glyph is ~24dp and
        // the rest is padding that has to be tappable, not decorative.
        IconButton(
            onClick = onDelete,
            modifier = Modifier.align(Alignment.TopEnd).size(44.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove photo",
                tint = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(4.dp),
            )
        }

        Box(Modifier.align(Alignment.BottomEnd)) {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Photo options",
                    tint = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(4.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (index != 0) {
                    DropdownMenuItem(
                        text = { Text("Make cover photo") },
                        onClick = { menuOpen = false; onMakeCover() },
                    )
                }
                if (index > 0) {
                    DropdownMenuItem(
                        text = { Text("Move earlier") },
                        onClick = { menuOpen = false; onMove(index - 1) },
                    )
                }
                if (index < total - 1) {
                    DropdownMenuItem(
                        text = { Text("Move later") },
                        onClick = { menuOpen = false; onMove(index + 1) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete photo") },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}
