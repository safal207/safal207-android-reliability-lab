package com.robys.coffeehouse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val RobysColors = lightColorScheme(
    primary = Espresso,
    onPrimary = Cream,
    secondary = Caramel,
    onSecondary = Espresso,
    tertiary = Sand,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = CreamDeep,
    onSurfaceVariant = Muted
)

@Composable
fun RobysCoffeeHouseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RobysColors, content = content)
}
