package com.example.liftrix.ui.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Reusable navigation item component for settings actions with Material3 list item design.
 * 
 * This component provides a consistent Material3 ListItem layout with:
 * - Optional leading icon with proper tint and sizing
 * - Title with Material3 typography
 * - Optional subtitle with appropriate color contrast
 * - Trailing navigation arrow indicator
 * - Ripple effect on click with proper accessibility
 * - Consistent spacing and elevation with Material3 guidelines
 * 
 * @param title The main text displayed in the list item
 * @param subtitle Optional supporting text displayed below the title
 * @param icon Optional leading icon displayed before the title
 * @param onClick Callback invoked when the item is clicked
 * @param showDivider Whether to show a divider below this item
 * @param modifier Modifier for styling the component
 */
@Composable
fun SettingsNavigationItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        ListItem(
            modifier = Modifier
                .clickable { onClick() }
                .semantics {
                    contentDescription = buildString {
                        append(title)
                        if (subtitle != null) {
                            append(", ")
                            append(subtitle)
                        }
                        append(", button")
                    }
                },
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            },
            supportingContent = subtitle?.let { supportingText ->
                {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            leadingContent = icon?.let { leadingIcon ->
                {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Open setting",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        }
    }
}
