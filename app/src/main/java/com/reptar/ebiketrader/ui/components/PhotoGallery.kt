package com.reptar.ebiketrader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlin.math.abs

/** The inline gallery on a listing: swipe between photos, tap one to open it
 * full screen. Mirrors PhotoGalleryView on iOS. */
@Composable
fun PhotoGallery(
    photos: List<String>,
    /** "SOLD" / "EXPIRED" over the photos; null for a live listing. */
    badge: String?,
    modifier: Modifier = Modifier,
) {
    var viewerStart by remember { mutableStateOf<Int?>(null) }
    val pagerState = rememberPagerState(pageCount = { photos.size.coerceAtLeast(1) })

    Box(modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
        if (photos.isEmpty()) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Text("No photos") }
        } else {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = photos[page],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(page) {
                            detectTapGestures { viewerStart = page }
                        },
                )
            }

            if (photos.size > 1) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(photos.size) { index ->
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage) Color.White
                                    else Color.White.copy(alpha = 0.45f)
                                )
                        )
                    }
                }
            }
        }

        if (badge != null) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    badge,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    viewerStart?.let { start ->
        PhotoViewer(
            photos = photos,
            startIndex = start,
            onDismiss = { viewerStart = null },
        )
    }
}

/** Full-screen viewer: swipe between photos, pinch or double-tap to zoom, drag
 * to pan while zoomed. */
@Composable
fun PhotoViewer(
    photos: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pagerState = rememberPagerState(
            initialPage = startIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
            pageCount = { photos.size },
        )
        var zoomed by remember { mutableStateOf(false) }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                // While a photo is zoomed in, a horizontal drag has to pan it
                // rather than flick to the next photo. Same conflict the iOS
                // viewer resolves by disabling UIScrollView's pan recogniser
                // at minimum zoom.
                userScrollEnabled = !zoomed,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomablePhoto(
                    url = photos[page],
                    isActive = page == pagerState.currentPage,
                    onZoomChanged = { isZoomed ->
                        if (page == pagerState.currentPage) zoomed = isZoomed
                    },
                )
            }

            if (photos.size > 1) {
                Text(
                    "${pagerState.currentPage + 1} of ${photos.size}",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

private const val MAX_SCALE = 4f
private const val DOUBLE_TAP_SCALE = 2.5f

@Composable
private fun ZoomablePhoto(url: String, isActive: Boolean, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    /** Keeps a zoomed photo from being dragged off screen: at scale s the
     * image overhangs the viewport by (s-1)/2 in each direction, and that is
     * exactly how far it may travel. */
    fun clamp(candidate: Offset, atScale: Float): Offset {
        if (atScale <= 1f) return Offset.Zero
        val maxX = size.width * (atScale - 1f) / 2f
        val maxY = size.height * (atScale - 1f) / 2f
        return Offset(
            candidate.x.coerceIn(-maxX, maxX),
            candidate.y.coerceIn(-maxY, maxY),
        )
    }

    fun apply(newScale: Float, newOffset: Offset) {
        val clampedScale = newScale.coerceIn(1f, MAX_SCALE)
        scale = clampedScale
        offset = clamp(newOffset, clampedScale)
        onZoomChanged(abs(clampedScale - 1f) > 0.01f)
    }

    // Swiping to another photo leaves this one where the pager can compose it
    // again; without this it would still be zoomed when you swipe back.
    LaunchedEffect(isActive) {
        if (!isActive && scale != 1f) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            // Hand-rolled rather than detectTransformGestures, which consumes
            // every drag it sees — including a one-finger swipe at 1x, which
            // then never reaches the pager and paging silently stops working.
            // Here the gesture is only consumed when it was actually used for
            // zoom or pan, so a plain swipe still pages.
            .pointerInput(url) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val isPinch = event.changes.size > 1
                        if (isPinch || scale > 1f) {
                            apply(scale * event.calculateZoom(), offset + event.calculatePan())
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(url) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            apply(1f, Offset.Zero)
                        } else {
                            // Zoom toward the point tapped rather than the
                            // middle, so double-tapping a detail brings that
                            // detail up.
                            val centre = Offset(size.width / 2f, size.height / 2f)
                            apply(DOUBLE_TAP_SCALE, (centre - tap) * (DOUBLE_TAP_SCALE - 1f))
                        }
                    },
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
