package com.example.liftrix.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.social.ContentType
import com.example.liftrix.domain.model.social.ReportReason
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Bottom sheet for reporting content with reason selection and optional description.
 * Part of social privacy and moderation system from SPEC-20250116-social-privacy-moderation.
 * 
 * Provides a user-friendly interface for reporting inappropriate content with
 * predefined reasons and optional additional details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportContentBottomSheet(
    contentType: ContentType,
    contentId: String,
    onDismiss: () -> Unit,
    onReport: (ReportReason, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedReason by remember { mutableStateOf<ReportReason?>(null) }
    var additionalDetails by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp) // Constrain height to enable scrolling
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 32.dp) // Extra padding for bottom sheet
        ) {
            // Header
            Text(
                text = "Report ${contentType.displayName}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Help us understand what's happening. Your report will be reviewed by our moderation team.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Reason selection
            Text(
                text = "Why are you reporting this ${contentType.displayName.lowercase()}?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Report reasons
            ReportReason.values().forEach { reason ->
                ReportReasonItem(
                    reason = reason,
                    isSelected = selectedReason == reason,
                    onSelected = { selectedReason = reason },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Spacer before action section
            Spacer(modifier = Modifier.height(16.dp))
            
            // Additional details section (visible when reason is selected)
            if (selectedReason != null) {
                Text(
                    text = "Additional details (optional)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = additionalDetails,
                    onValueChange = { additionalDetails = it },
                    placeholder = { 
                        Text("Please provide more context about why you're reporting this content...")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isSubmitting
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Action buttons (always visible when reason is selected)
            if (selectedReason != null) {
                // Submit button
                Button(
                    onClick = {
                        selectedReason?.let { reason ->
                            isSubmitting = true
                            onReport(
                                reason,
                                additionalDetails.takeIf { it.isNotBlank() }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedReason != null && !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isSubmitting) "Submitting Report..." else "Submit Report"
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                ) {
                    Text("Cancel")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Privacy notice
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Your report is anonymous and will be reviewed within 24 hours. False reports may result in account restrictions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

/**
 * Individual report reason item with selection state
 */
@Composable
private fun ReportReasonItem(
    reason: ReportReason,
    isSelected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onSelected() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(
                width = 2.dp,
                brush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.primary
                )
            )
        } else {
            CardDefaults.outlinedCardBorder()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelected,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = reason.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = reason.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReportContentBottomSheetPreview() {
    LiftrixTheme {
        ReportContentBottomSheet(
            contentType = ContentType.POST,
            contentId = "sample-post-id",
            onDismiss = {},
            onReport = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
private fun ReportContentBottomSheetDarkPreview() {
    LiftrixTheme(darkTheme = true) {
        ReportContentBottomSheet(
            contentType = ContentType.PROFILE,
            contentId = "sample-user-id",
            onDismiss = {},
            onReport = { _, _ -> }
        )
    }
}