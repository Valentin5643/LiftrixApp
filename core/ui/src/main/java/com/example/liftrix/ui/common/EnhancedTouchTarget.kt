package com.example.liftrix.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTokens

/**
 * Enhanced touch target extension for accessibility compliance
 * Ensures minimum touch target size of 48dp as per Material Design guidelines
 */
@Composable
fun Modifier.enhancedTouchTarget(): Modifier = this.sizeIn(
    minWidth = LiftrixTokens.TouchTarget.Minimum,
    minHeight = LiftrixTokens.TouchTarget.Minimum
) 