package com.example.liftrix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RadialProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    label: String? = null,
    centerContent: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.size(size)
        )
        centerContent?.invoke()
    }
}
