package com.example.liftrix.ui.feed.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.liftrix.R
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Dialog for selecting media source (camera or gallery)
 */
@Composable
fun MediaPickerDialog(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
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
                    text = stringResource(R.string.media_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = LiftrixColorsV2.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.large))
                
                // Camera option
                MediaPickerOption(
                    icon = Icons.Filled.PhotoCamera,
                    title = stringResource(R.string.media_picker_camera),
                    description = stringResource(R.string.media_picker_camera_description),
                    onClick = onCameraClick,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
                
                // Gallery option
                MediaPickerOption(
                    icon = Icons.Filled.PhotoLibrary,
                    title = stringResource(R.string.media_picker_gallery),
                    description = stringResource(R.string.media_picker_gallery_description),
                    onClick = onGalleryClick,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.large))
                
                // Cancel button
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
private fun MediaPickerOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = LiftrixColorsV2.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = LiftrixColorsV2.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(LiftrixSpacing.medium))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = LiftrixColorsV2.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColorsV2.onSurfaceVariant
                )
            }
        }
    }
}
