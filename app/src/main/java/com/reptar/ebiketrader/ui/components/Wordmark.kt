package com.reptar.ebiketrader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reptar.ebiketrader.ui.theme.Brand

private val Spoke = Color(0xFF5FC4D6)
private val BoltFill = Color(0xFFFBBF24)
private val BoltEdge = Color(0xFF0E7490)

/**
 * The eBikeTrader lockup: the badge mark, then "eBike", an amber bolt, and
 * "Trader" in brand teal. Port of Wordmark.swift, which is itself a port of
 * the website's Navbar.tsx.
 *
 * Drawn rather than shipped as a PNG for the same reasons as on iOS: it stays
 * sharp at any size, it scales from one parameter instead of needing a drawable
 * per density bucket, and the "eBike" half can take its color from the theme so
 * it turns white in dark mode. A flattened image would stay near-black and
 * disappear against the app's dark background.
 */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    badgeSize: Dp = 26.dp,
    fontSize: TextUnit = 17.sp,
) {
    val density = LocalDensity.current
    val textHeight = with(density) { fontSize.toDp() }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(badgeSize * 0.25f),
    ) {
        Box(
            modifier = Modifier
                .size(badgeSize)
                // The web's corner ratio; at 26dp this is the same squircle-ish
                // rounding as the launcher icon reads at.
                .background(Brand, RoundedCornerShape(badgeSize * 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            // 22/32 of the badge, the ratio the website uses.
            Canvas(Modifier.size(badgeSize * 0.6875f)) { drawBikeMark() }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "eBike",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                // tracking-tighter on the web.
                letterSpacing = (-0.5).sp,
            )

            // Its own box, sized to hug the shape rather than forced square:
            // squaring it letterboxes the bolt and reopens a gap between the
            // two words that no amount of zero spacing closes.
            Canvas(Modifier.size(textHeight * 0.6f, textHeight * 0.95f)) {
                drawPath(boltGlyph(size), BoltFill)
            }

            Text(
                "Trader",
                color = Brand,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )
        }
    }
}

/** The bike-and-bolt mark in the same 48-unit space as the website's icon.tsx,
 * the iOS AppIcon and res/drawable/ic_launcher_foreground.xml. */
private fun DrawScope.drawBikeMark() {
    // Scaling the whole drawing takes the stroke widths with it, which is what
    // keeps the mark identical at 26dp and at 96dp instead of going spindly.
    scale(size.minDimension / 48f, pivot = Offset.Zero) {
        val frame = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        drawCircle(Spoke, radius = 7f, center = Offset(13f, 33f), style = Stroke(2.4f))
        drawCircle(Spoke, radius = 7f, center = Offset(35f, 33f), style = Stroke(2.4f))

        // Frame, seat tube and down tube.
        drawPath(
            Path().apply {
                moveTo(13f, 33f)
                lineTo(23f, 33f)
                lineTo(19f, 14f)
                lineTo(31f, 14f)
                lineTo(35f, 33f)
                moveTo(23f, 33f)
                lineTo(31f, 14f)
            },
            Spoke,
            style = frame,
        )

        // Saddle.
        drawLine(
            Spoke,
            Offset(15.5f, 13f),
            Offset(22.5f, 13f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round,
        )

        // Stem and handlebar.
        drawPath(
            Path().apply {
                moveTo(31f, 14f)
                lineTo(31f, 9f)
                moveTo(27f, 9f)
                lineTo(33f, 9f)
            },
            Spoke,
            style = frame,
        )

        // The bolt, across the whole mark.
        val bolt = Path().apply {
            moveTo(25.9f, 5.5f)
            lineTo(7.4f, 27.7f)
            lineTo(24f, 27.7f)
            lineTo(22.2f, 42.5f)
            lineTo(40.7f, 20.3f)
            lineTo(24f, 20.3f)
            close()
        }
        drawPath(bolt, BoltFill)
        drawPath(bolt, BoltEdge, style = Stroke(width = 1.3f, join = StrokeJoin.Round))
    }
}

/** The small bolt between "eBike" and "Trader", in its own 14x22 space. */
private fun boltGlyph(box: Size): Path {
    val x = box.width / 14f
    val y = box.height / 22f
    return Path().apply {
        moveTo(8 * x, 0f)
        lineTo(1 * x, 12 * y)
        lineTo(6 * x, 12 * y)
        lineTo(4 * x, 22 * y)
        lineTo(14 * x, 8 * y)
        lineTo(8 * x, 8 * y)
        close()
    }
}
