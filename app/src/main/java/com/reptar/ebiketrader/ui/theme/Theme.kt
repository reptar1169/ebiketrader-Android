package com.reptar.ebiketrader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = BrandDark,
    onSecondary = androidx.compose.ui.graphics.Color.White,
)

private val DarkColors = darkColorScheme(
    primary = BrandLight,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    secondary = BrandLight,
    onSecondary = androidx.compose.ui.graphics.Color.Black,
)

/** dynamicColor is deliberately NOT offered.
 *
 * Material You would repaint the app from the user's wallpaper, which is a
 * nice Android citizen but destroys the one thing this app has to hold onto:
 * looking like ebiketrader.net and the iPhone app. A teal marketplace that
 * renders mauve on someone's phone is not the same product.
 *
 * Surfaces and text still come from Material's own light/dark schemes, so dark
 * mode works properly — only the accent is pinned. Same split the iOS app made
 * (brand accents, system semantic backgrounds). */
@Composable
fun EBikeTraderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
