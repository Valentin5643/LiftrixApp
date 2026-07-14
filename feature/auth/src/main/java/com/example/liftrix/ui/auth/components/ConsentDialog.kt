package com.example.liftrix.ui.auth.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Consent Dialog for GDPR/CCPA compliance.
 *
 * Displays explicit consent checkboxes for:
 * - Privacy Policy (required)
 * - Health Data Processing (required)
 * - AI Chat Feature (required)
 * - Analytics/Crashlytics (optional)
 *
 * IMPORTANT: No pre-checked boxes. All consents must be explicit.
 * User cannot proceed without checking required consents.
 */
@Composable
fun ConsentDialog(
    onConsentProvided: (ConsentData) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onPrivacyPolicyClick: () -> Unit = {},
    onTermsOfServiceClick: () -> Unit = {}
) {
    var privacyPolicyChecked by remember { mutableStateOf(false) }
    var healthDataChecked by remember { mutableStateOf(false) }
    var aiChatChecked by remember { mutableStateOf(false) }
    var analyticsChecked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { /* Cannot dismiss - required consent */ },
        modifier = modifier,
        title = {
            Text(
                "Privacy & Data Usage",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Before creating your account, please review and accept the following:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                // Privacy Policy (required)
                ConsentCheckboxRow(
                    checked = privacyPolicyChecked,
                    onCheckedChange = { privacyPolicyChecked = it },
                    label = "I have read and agree to the Privacy Policy",
                    required = true,
                    linkText = "Privacy Policy",
                    onLinkClick = onPrivacyPolicyClick
                )

                Spacer(Modifier.height(12.dp))

                // Health data processing (required)
                ConsentCheckboxRow(
                    checked = healthDataChecked,
                    onCheckedChange = { healthDataChecked = it },
                    label = "I consent to processing my fitness and health data",
                    required = true,
                    description = "We collect workout metrics, body measurements, and progress photos to provide personalized fitness insights and track your progress over time."
                )

                Spacer(Modifier.height(12.dp))

                // AI chat (required)
                ConsentCheckboxRow(
                    checked = aiChatChecked,
                    onCheckedChange = { aiChatChecked = it },
                    label = "I understand AI Coach responses may be inaccurate and do not replace professional medical advice",
                    required = true,
                    description = "Our AI Coach uses Google's Gemini model to provide fitness guidance. Responses are not reviewed by medical professionals and should not replace consultations with healthcare providers."
                )

                Spacer(Modifier.height(12.dp))

                // Analytics (optional)
                ConsentCheckboxRow(
                    checked = analyticsChecked,
                    onCheckedChange = { analyticsChecked = it },
                    label = "Help improve Liftrix by sharing usage data",
                    required = false,
                    description = "We use Firebase Analytics and Crashlytics with pseudonymous identifiers to understand app usage and fix bugs. Data is processed by Google (Firebase) on our behalf as described in the Privacy Policy."
                )

                TextButton(onClick = onTermsOfServiceClick) {
                    Text(
                        text = "Review Terms of Service",
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConsentProvided(ConsentData(
                        privacyPolicy = privacyPolicyChecked,
                        healthData = healthDataChecked,
                        aiChat = aiChatChecked,
                        analytics = analyticsChecked
                    ))
                },
                enabled = privacyPolicyChecked && healthDataChecked && aiChatChecked
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConsentCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    required: Boolean,
    description: String? = null,
    linkText: String? = null,
    onLinkClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.clickable { onCheckedChange(!checked) }
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (required) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "*",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (linkText != null && onLinkClick != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        linkText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable(onClick = onLinkClick)
                    )
                }
            }
        }
    }
}

/**
 * UI-owned consent choices collected by the auth dialog.
 */
data class ConsentData(
    val privacyPolicy: Boolean,
    val healthData: Boolean,
    val aiChat: Boolean,
    val analytics: Boolean
)
