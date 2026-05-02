package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun ProgressPrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    ) {
        ProgressButtonContent(text = text, leadingIcon = leadingIcon)
    }
}

@Composable
fun ProgressSecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    ) {
        ProgressButtonContent(text = text, leadingIcon = leadingIcon)
    }
}

@Composable
private fun RowScope.ProgressButtonContent(
    text: String,
    leadingIcon: ImageVector?
) {
    leadingIcon?.let {
        Icon(imageVector = it, contentDescription = null)
    }
    Text(text = text)
}
