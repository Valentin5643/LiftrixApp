package com.example.liftrix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.theme.ThemeConfig
import com.example.liftrix.ui.theme.ThemeVersion

/**
 * Demo component showcasing the V2 color system
 * Use this as a reference for implementing the new colors in your components
 */
@Composable
fun ColorSystemDemo(
    isDarkTheme: Boolean = false
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        item {
            Text(
                text = "Liftrix V2 Color System",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Modern Teal-based palette with enhanced data visualization",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Primary Brand Colors
        item {
            ColorSection(
                title = "Brand Colors",
                colors = listOf(
                    ColorInfo("Teal", LiftrixColorsV2.Teal, "Primary brand"),
                    ColorInfo("Teal Hover", LiftrixColorsV2.TealHover, "Hover states"),
                    ColorInfo("Teal Dark", LiftrixColorsV2.TealDark, "Pressed states"),
                    ColorInfo("Teal Light", LiftrixColorsV2.TealLight, "Highlights")
                )
            )
        }
        
        // Background Hierarchy
        item {
            val backgrounds = if (isDarkTheme) {
                listOf(
                    ColorInfo("Background Primary", LiftrixColorsV2.Dark.BackgroundPrimary, "Main background"),
                    ColorInfo("Background Secondary", LiftrixColorsV2.Dark.BackgroundSecondary, "Cards"),
                    ColorInfo("Background Tertiary", LiftrixColorsV2.Dark.BackgroundTertiary, "Input fields"),
                    ColorInfo("Background Elevated", LiftrixColorsV2.Dark.BackgroundElevated, "Modals")
                )
            } else {
                listOf(
                    ColorInfo("Background Primary", LiftrixColorsV2.Light.BackgroundPrimary, "Main background"),
                    ColorInfo("Background Secondary", LiftrixColorsV2.Light.BackgroundSecondary, "Cards"),
                    ColorInfo("Background Tertiary", LiftrixColorsV2.Light.BackgroundTertiary, "Input fields"),
                    ColorInfo("Background Elevated", LiftrixColorsV2.Light.BackgroundElevated, "Modals")
                )
            }
            ColorSection(
                title = "Background Hierarchy",
                colors = backgrounds
            )
        }
        
        // Text Hierarchy
        item {
            val textColors = if (isDarkTheme) {
                listOf(
                    ColorInfo("Text Primary", LiftrixColorsV2.Dark.TextPrimary, "Main text"),
                    ColorInfo("Text Secondary", LiftrixColorsV2.Dark.TextSecondary, "Secondary text"),
                    ColorInfo("Text Tertiary", LiftrixColorsV2.Dark.TextTertiary, "Hints"),
                    ColorInfo("Text Disabled", LiftrixColorsV2.Dark.TextDisabled, "Disabled")
                )
            } else {
                listOf(
                    ColorInfo("Text Primary", LiftrixColorsV2.Light.TextPrimary, "Main text"),
                    ColorInfo("Text Secondary", LiftrixColorsV2.Light.TextSecondary, "Secondary text"),
                    ColorInfo("Text Tertiary", LiftrixColorsV2.Light.TextTertiary, "Hints"),
                    ColorInfo("Text Disabled", LiftrixColorsV2.Light.TextDisabled, "Disabled")
                )
            }
            ColorSection(
                title = "Text Hierarchy",
                colors = textColors
            )
        }
        
        // Data Visualization Palette
        item {
            ColorSection(
                title = "Data Visualization",
                colors = listOf(
                    ColorInfo("Series 1", LiftrixColorsV2.DataViz.Series1, "Primary data"),
                    ColorInfo("Series 2", LiftrixColorsV2.DataViz.Series2, "Complementary"),
                    ColorInfo("Series 3", LiftrixColorsV2.DataViz.Series3, "Analogous"),
                    ColorInfo("Series 4", LiftrixColorsV2.DataViz.Series4, "Positive"),
                    ColorInfo("Series 5", LiftrixColorsV2.DataViz.Series5, "Split-comp"),
                    ColorInfo("Series 6", LiftrixColorsV2.DataViz.Series6, "Triadic"),
                    ColorInfo("Series 7", LiftrixColorsV2.DataViz.Series7, "Neutral"),
                    ColorInfo("Series 8", LiftrixColorsV2.DataViz.Series8, "Alerts")
                )
            )
        }
        
        // Semantic Colors
        item {
            val semanticColors = if (isDarkTheme) {
                listOf(
                    ColorInfo("Error", LiftrixColorsV2.Dark.Error, "Error states"),
                    ColorInfo("Warning", LiftrixColorsV2.Dark.Warning, "Warnings"),
                    ColorInfo("Success", LiftrixColorsV2.Dark.Success, "Success"),
                    ColorInfo("Info", LiftrixColorsV2.Dark.Info, "Information")
                )
            } else {
                listOf(
                    ColorInfo("Error", LiftrixColorsV2.Light.Error, "Error states"),
                    ColorInfo("Warning", LiftrixColorsV2.Light.Warning, "Warnings"),
                    ColorInfo("Success", LiftrixColorsV2.Light.Success, "Success"),
                    ColorInfo("Info", LiftrixColorsV2.Light.Info, "Information")
                )
            }
            ColorSection(
                title = "Semantic Colors",
                colors = semanticColors
            )
        }
        
        // Gradients
        item {
            GradientSection(
                title = "Gradients",
                gradients = listOf(
                    GradientInfo("Primary", LiftrixColorsV2.Gradients.PrimaryGradient),
                    GradientInfo("Primary Vertical", LiftrixColorsV2.Gradients.PrimaryVerticalGradient),
                    GradientInfo("Chart Dark", LiftrixColorsV2.Gradients.ChartGradientDark),
                    GradientInfo("Success", LiftrixColorsV2.Gradients.SuccessGradient),
                    GradientInfo("Error", LiftrixColorsV2.Gradients.ErrorGradient)
                )
            )
        }
        
        // Component Examples
        item {
            ComponentExamples()
        }
        
        // Migration Reference
        item {
            MigrationReference()
        }
    }
}

@Composable
private fun ColorSection(
    title: String,
    colors: List<ColorInfo>
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(colors) { colorInfo ->
                ColorCard(colorInfo)
            }
        }
    }
}

@Composable
private fun ColorCard(
    colorInfo: ColorInfo
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorInfo.color)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = colorInfo.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = colorInfo.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = colorToHex(colorInfo.color),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GradientSection(
    title: String,
    gradients: List<GradientInfo>
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(gradients) { gradientInfo ->
                GradientCard(gradientInfo)
            }
        }
    }
}

@Composable
private fun GradientCard(
    gradientInfo: GradientInfo
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(gradientInfo.brush)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = gradientInfo.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ComponentExamples() {
    Column {
        Text(
            text = "Component Examples",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiftrixColorsV2.Teal
                )
            ) {
                Text("Primary Button")
            }
            OutlinedButton(
                onClick = { },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = LiftrixColorsV2.Teal
                )
            ) {
                Text("Secondary Button")
            }
            TextButton(
                onClick = { },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LiftrixColorsV2.TealHover
                )
            ) {
                Text("Text Button")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Cards
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Workout Card Example",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "This card uses the new surface colors",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { 0.7f },
                    modifier = Modifier.fillMaxWidth(),
                    color = LiftrixColorsV2.Teal,
                    trackColor = LiftrixColorsV2.Teal.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
private fun MigrationReference() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Migration Quick Reference",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            MigrationItem("Persian Green (#339989)", "Teal (#06B6D4)")
            MigrationItem("Tiffany Blue (#7DE2D1)", "Teal Hover (#0891B2)")
            MigrationItem("Night (#131515)", "Pure Black (#000000)")
            MigrationItem("Jet (#2B2C28)", "Gray (#1A1A1A)")
            MigrationItem("Snow (#FFFAFB)", "Pure White (#FFFFFF)")
        }
    }
}

@Composable
private fun MigrationItem(old: String, new: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = old,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = "→",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
            text = new,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.Medium
        )
    }
}

// Helper data classes
private data class ColorInfo(
    val name: String,
    val color: Color,
    val description: String
)

private data class GradientInfo(
    val name: String,
    val brush: Brush
)

// Helper function to convert Color to hex string
private fun colorToHex(color: Color): String {
    val red = (color.red * 255).toInt()
    val green = (color.green * 255).toInt()
    val blue = (color.blue * 255).toInt()
    return "#%02X%02X%02X".format(red, green, blue)
}

// Preview
@Preview(name = "Color System Demo - Light")
@Composable
private fun ColorSystemDemoLightPreview() {
    LiftrixTheme(
        darkTheme = false,
        themeVersion = ThemeVersion.V2
    ) {
        ColorSystemDemo(isDarkTheme = false)
    }
}

@Preview(name = "Color System Demo - Dark")
@Composable
private fun ColorSystemDemoDarkPreview() {
    LiftrixTheme(
        darkTheme = true,
        themeVersion = ThemeVersion.V2
    ) {
        ColorSystemDemo(isDarkTheme = true)
    }
}