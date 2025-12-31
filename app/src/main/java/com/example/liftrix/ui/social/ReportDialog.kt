package com.example.liftrix.ui.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.common.AccessibilityUtils.ensureMinimumTouchTarget

/**
 * Report content reasons enum aligned with backend ContentReportReason
 */
enum class ReportReason(val displayName: String, val value: String) {
    SPAM("Spam or misleading content", "SPAM"),
    HARASSMENT("Harassment or bullying", "HARASSMENT"),
    HATE_SPEECH("Hate speech or discrimination", "HATE_SPEECH"),
    INAPPROPRIATE("Inappropriate or offensive content", "INAPPROPRIATE"),
    MISINFORMATION("False or misleading information", "MISINFORMATION"),
    OTHER("Other", "OTHER")
}

/**
 * Reusable dialog for reporting content (profiles, posts, comments, AI output).
 *
 * Features:
 * - Radio button selection for report reasons
 * - Optional free-text notes field
 * - Link to Community Guidelines
 * - Accessibility support with proper semantics
 * - Material 3 design
 *
 * @param contentType Type of content being reported (e.g., "Profile", "Post", "Comment")
 * @param contentId ID of the content being reported
 * @param onDismiss Called when dialog is dismissed without reporting
 * @param onSubmit Called when user submits the report (reason, notes)
 * @param onViewGuidelines Called when user taps Community Guidelines link
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDialog(
    contentType: String,
    contentId: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: ReportReason, notes: String) -> Unit,
    onViewGuidelines: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedReason by remember { mutableStateOf<ReportReason?>(null) }
    var additionalNotes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Report content",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )

                        Text(
                            text = "Report $contentType",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close dialog",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Description
                Text(
                    text = "Help us understand what's wrong with this $contentType. Your report is anonymous.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Reason selection
                    Text(
                        text = "Reason for reporting",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .selectableGroup()
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportReason.values().forEach { reason ->
                            ReportReasonOption(
                                reason = reason,
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Additional notes
                    Text(
                        text = "Additional details (optional)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = additionalNotes,
                        onValueChange = { additionalNotes = it },
                        placeholder = {
                            Text(
                                text = "Provide more context about why you're reporting this...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        maxLines = 4,
                        enabled = !isSubmitting,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Community Guidelines link
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewGuidelines() }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Information",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )

                        Text(
                            text = "View Community Guidelines",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            selectedReason?.let { reason ->
                                isSubmitting = true
                                onSubmit(reason, additionalNotes.trim())
                            }
                        },
                        enabled = selectedReason != null && !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.ensureMinimumTouchTarget()
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onError
                            )
                        } else {
                            Text("Submit Report")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual report reason radio button option
 */
@Composable
private fun ReportReasonOption(
    reason: ReportReason,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = null, // Handled by Row
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.error,
                unselectedColor = MaterialTheme.colorScheme.outline
            )
        )

        Text(
            text = reason.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}
