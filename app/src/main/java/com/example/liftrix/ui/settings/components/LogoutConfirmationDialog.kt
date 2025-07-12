package com.example.liftrix.ui.settings.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Modal dialog for logout confirmation with proper Material3 design and actions.
 * 
 * This component provides a confirmation dialog for the logout action with:
 * - Clear warning message about logout consequences
 * - Cancel button to dismiss without action
 * - Destructive "Sign Out" button with error color styling
 * - Proper accessibility support with clear button labels
 * - Material3 AlertDialog design with consistent typography
 * - Proper dismissal behavior on outside clicks
 * 
 * @param isVisible Whether the dialog should be displayed
 * @param onDismiss Callback invoked when the dialog is dismissed
 * @param onConfirm Callback invoked when the logout is confirmed
 * @param modifier Modifier for styling the dialog
 */
@Composable
fun LogoutConfirmationDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "Sign Out",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to sign out? This will clear your local data and return you to the login screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        text = "Sign Out",
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            modifier = modifier
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LogoutConfirmationDialogPreview() {
    LiftrixTheme {
        LogoutConfirmationDialog(
            isVisible = true,
            onDismiss = { },
            onConfirm = { }
        )
    }
}