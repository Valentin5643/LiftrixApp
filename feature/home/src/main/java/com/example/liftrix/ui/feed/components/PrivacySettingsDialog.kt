package com.example.liftrix.ui.feed.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.liftrix.feature.home.R
import com.example.liftrix.domain.model.social.PostVisibility
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Dialog for selecting post privacy/visibility settings
 */
@Composable
fun PrivacySettingsDialog(
    currentVisibility: PostVisibility,
    onVisibilitySelected: (PostVisibility) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier,
            color = LiftrixColorsV2.surface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(LiftrixSpacing.large)
            ) {
                Text(
                    text = stringResource(R.string.privacy_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = LiftrixColorsV2.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.small))
                
                Text(
                    text = stringResource(R.string.privacy_settings_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiftrixColorsV2.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.large))
                
                // Visibility options
                PostVisibility.values().forEach { visibility ->
                    PrivacyOption(
                        visibility = visibility,
                        isSelected = visibility == currentVisibility,
                        onSelected = { onVisibilitySelected(visibility) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (visibility != PostVisibility.values().last()) {
                        Spacer(modifier = Modifier.height(LiftrixSpacing.small))
                    }
                }
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.large))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            color = LiftrixColorsV2.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyOption(
    visibility: PostVisibility,
    isSelected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .selectable(
                selected = isSelected,
                onClick = onSelected,
                role = Role.RadioButton
            ),
        color = if (isSelected) LiftrixColorsV2.primaryContainer else LiftrixColorsV2.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getVisibilityIcon(visibility),
                contentDescription = getVisibilityTitle(visibility),
                tint = if (isSelected) LiftrixColorsV2.onPrimaryContainer else LiftrixColorsV2.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(LiftrixSpacing.medium))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getVisibilityTitle(visibility),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) LiftrixColorsV2.onPrimaryContainer else LiftrixColorsV2.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = getVisibilityDescription(visibility),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) LiftrixColorsV2.onPrimaryContainer else LiftrixColorsV2.onSurfaceVariant
                )
            }
            
            RadioButton(
                selected = isSelected,
                onClick = null, // Handled by selectable modifier
                colors = RadioButtonDefaults.colors(
                    selectedColor = if (isSelected) LiftrixColorsV2.primary else LiftrixColorsV2.onSurfaceVariant,
                    unselectedColor = LiftrixColorsV2.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun getVisibilityIcon(visibility: PostVisibility): androidx.compose.ui.graphics.vector.ImageVector {
    return when (visibility) {
        PostVisibility.PUBLIC -> Icons.Filled.Public
        PostVisibility.FOLLOWERS -> Icons.Filled.People
        PostVisibility.PRIVATE -> Icons.Filled.Lock
    }
}

@Composable
private fun getVisibilityTitle(visibility: PostVisibility): String {
    return when (visibility) {
        PostVisibility.PUBLIC -> stringResource(R.string.visibility_public_title)
        PostVisibility.FOLLOWERS -> stringResource(R.string.visibility_followers_title)
        PostVisibility.PRIVATE -> stringResource(R.string.visibility_private_title)
    }
}

@Composable
private fun getVisibilityDescription(visibility: PostVisibility): String {
    return when (visibility) {
        PostVisibility.PUBLIC -> stringResource(R.string.visibility_public_description)
        PostVisibility.FOLLOWERS -> stringResource(R.string.visibility_followers_description)
        PostVisibility.PRIVATE -> stringResource(R.string.visibility_private_description)
    }
}
