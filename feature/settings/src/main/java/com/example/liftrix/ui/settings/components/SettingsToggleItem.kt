package com.example.liftrix.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.common.AccessibilityUtils.accessibilitySemantics
import com.example.liftrix.ui.common.AccessibilityUtils.ensureMinimumTouchTarget
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Settings toggle item component with Material3 Switch and immediate state persistence.
 * 
 * Provides consistent toggle switch behavior with accessibility support and proper
 * Material3 theming following WCAG 2.1 AA guidelines.
 * 
 * @param title The primary label text displayed for the toggle
 * @param subtitle Optional secondary descriptive text
 * @param isChecked Current checked state of the toggle
 * @param onToggle Callback invoked when the toggle state changes
 * @param modifier Modifier to be applied to the component
 * @param enabled Whether the toggle is enabled for interaction
 */
@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String? = null,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .accessibilitySemantics(
                description = "$title ${if (isChecked) "enabled" else "disabled"}${subtitle?.let { ". $it" } ?: ""}",
                role = Role.Switch,
                stateDescription = if (isChecked) "On" else "Off"
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
            
            subtitle?.let { subtitleText ->
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }
        }
        
        // Material3 Switch
        Switch(
            checked = isChecked,
            onCheckedChange = onToggle,
            enabled = enabled,
            modifier = Modifier.ensureMinimumTouchTarget(),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                disabledCheckedThumbColor = MaterialTheme.colorScheme.surface,
                disabledCheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                disabledUncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        )
    }
}

/**
 * Preview for SettingsToggleItem in checked state
 */
@Preview(showBackground = true)
@Composable
private fun SettingsToggleItemCheckedPreview() {
    LiftrixTheme {
        SettingsToggleItem(
            title = "Dark Mode",
            subtitle = "Use dark theme throughout the app",
            isChecked = true,
            onToggle = { }
        )
    }
}

/**
 * Preview for SettingsToggleItem in unchecked state
 */
@Preview(showBackground = true)
@Composable
private fun SettingsToggleItemUncheckedPreview() {
    LiftrixTheme {
        SettingsToggleItem(
            title = "Push Notifications",
            subtitle = "Receive notifications for workout reminders",
            isChecked = false,
            onToggle = { }
        )
    }
}

/**
 * Preview for SettingsToggleItem without subtitle
 */
@Preview(showBackground = true)
@Composable
private fun SettingsToggleItemNoSubtitlePreview() {
    LiftrixTheme {
        SettingsToggleItem(
            title = "Auto-sync",
            isChecked = true,
            onToggle = { }
        )
    }
}

/**
 * Preview for SettingsToggleItem in disabled state
 */
@Preview(showBackground = true)
@Composable
private fun SettingsToggleItemDisabledPreview() {
    LiftrixTheme {
        SettingsToggleItem(
            title = "Premium Features",
            subtitle = "Available with Pro subscription",
            isChecked = false,
            onToggle = { },
            enabled = false
        )
    }
}

/**
 * Preview for multiple SettingsToggleItem instances
 */
@Preview(showBackground = true)
@Composable
private fun SettingsToggleItemListPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsToggleItem(
                title = "Dark Mode",
                subtitle = "Use dark theme throughout the app",
                isChecked = true,
                onToggle = { }
            )
            
            SettingsToggleItem(
                title = "Push Notifications",
                subtitle = "Receive notifications for workout reminders",
                isChecked = false,
                onToggle = { }
            )
            
            SettingsToggleItem(
                title = "Auto-sync",
                isChecked = true,
                onToggle = { }
            )
            
            SettingsToggleItem(
                title = "Premium Features",
                subtitle = "Available with Pro subscription",
                isChecked = false,
                onToggle = { },
                enabled = false
            )
        }
    }
}