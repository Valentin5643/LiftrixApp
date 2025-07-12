package com.example.liftrix.ui.components.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Preview provider for LiftrixButton components.
 * Demonstrates all button variants, states, and styling options.
 */
@Preview(showBackground = true, name = "All Button Variants")
@Composable
fun ButtonVariantsPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Primary Buttons")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Primary")
                }
                
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Primary,
                    enabled = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disabled")
                }
            }
            
            Text("Secondary Buttons")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Secondary")
                }
                
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Secondary,
                    enabled = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disabled")
                }
            }
            
            Text("Accent Buttons")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Accent,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Accent")
                }
                
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Accent,
                    enabled = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disabled")
                }
            }
            
            Text("Outlined Buttons")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Outlined")
                }
                
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Outlined,
                    enabled = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disabled")
                }
            }
            
            Text("Text Buttons")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Text,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Text")
                }
                
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Text,
                    enabled = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disabled")
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Buttons with Icons")
@Composable
fun ButtonsWithIconsPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Buttons with Leading Icons")
            
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Workout")
            }
            
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Session")
            }
            
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Accent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Favorite")
            }
            
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Outlined,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rate Workout")
            }
        }
    }
}

@Preview(showBackground = true, name = "Convenience Functions")
@Composable
fun ConvenienceFunctionsPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Convenience Functions")
            
            PrimaryLiftrixButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Primary Button")
            }
            
            SecondaryLiftrixButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Secondary Button")
            }
            
            AccentLiftrixButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Accent Button")
            }
        }
    }
}

@Preview(showBackground = true, name = "Button Sizing")
@Composable
fun ButtonSizingPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Button Sizing Examples")
            
            // Full width button
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Full Width Button")
            }
            
            // Compact buttons in row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Confirm")
                }
            }
            
            // Centered button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Accent
                ) {
                    Text("Centered")
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Athletic Button Interactions")
@Composable
fun AthleticButtonInteractionsPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Athletic Button Interactions")
            
            // Demonstration of athletic button press animation
            AthleticButtonPress(
                pressed = false,
                enabled = true
            ) {
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Athletic Press Animation")
                }
            }
            
            // Demonstration of glow effect
            GlowOnPress(
                pressed = false,
                enabled = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                LiftrixButton(
                    onClick = {},
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Glow Effect Demo")
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Dark Theme Buttons")
@Composable
fun DarkThemeButtonsPreview() {
    LiftrixTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Dark Theme Button Variants")
            
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Primary Dark")
            }
            
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Secondary Dark")
            }
            
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Accent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Accent Dark")
            }
            
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Outlined,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Outlined Dark")
            }
            
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Text,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Text Dark")
            }
        }
    }
}

@Preview(showBackground = true, name = "Button Accessibility States")
@Composable
fun ButtonAccessibilityStatesPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Button Accessibility States")
            
            // Enabled state
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Primary,
                enabled = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enabled Button")
            }
            
            // Disabled state
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Primary,
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Disabled Button")
            }
            
            // High contrast example
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Outlined,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("High Contrast Outline")
            }
        }
    }
}

@Preview(showBackground = true, name = "Button Typography Examples")
@Composable
fun ButtonTypographyExamplesPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Button Typography Examples")
            
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SHORT")
            }
            
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Medium Length Button")
            }
            
            LiftrixButton(
                onClick = {},
                variant = ButtonVariant.Accent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Very Long Button Text Example")
            }
        }
    }
}