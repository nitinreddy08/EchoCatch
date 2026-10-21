// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5F66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB9ECF1),
    onPrimaryContainer = Color(0xFF002024),
    secondaryContainer = Color(0xFFDCE8EA),
    onSecondaryContainer = Color(0xFF151D1E),
    background = Color(0xFFF7FAFA),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFF7FAFA),
    onSurface = Color(0xFF191C1D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD3DB),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF004F55),
    onPrimaryContainer = Color(0xFFB9ECF1),
    secondaryContainer = Color(0xFF334042),
    onSecondaryContainer = Color(0xFFDCE8EA),
    background = Color(0xFF101415),
    onBackground = Color(0xFFE0E3E3),
    surface = Color(0xFF101415),
    onSurface = Color(0xFFE0E3E3),
)

/** One fixed teal palette in light and dark. Wallpaper colours are not used, so the look is the same on every phone. */
@Composable
fun EchoCatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
