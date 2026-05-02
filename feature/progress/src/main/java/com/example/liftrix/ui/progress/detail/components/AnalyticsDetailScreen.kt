package com.example.liftrix.ui.progress.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * Base detail screen container for analytics detail views
 * 
 * Provides consistent layout structure with:
 * - Simple content container that integrates with global navigation
 * - Proper content padding and layout
 * - Material 3 design system integration
 * - No duplicate navigation bars
 * 
 * Note: Navigation is handled by the global UnifiedNavigationContainer
 */
@Composable
fun AnalyticsDetailScreen(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    // Simple container - navigation is handled globally
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        content()
    }
}