package com.example.liftrix.ui.common.extensions

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Safe StateFlow collection utilities for preventing NullPointerException crashes.
 * 
 * These utilities provide defensive ways to collect StateFlow values in Compose,
 * handling cases where the StateFlow might be null due to dependency injection
 * failures or improper ViewModel initialization.
 */

/**
 * Creates a safe flow that falls back to a default value if the original StateFlow is null.
 * 
 * Usage:
 * ```kotlin
 * val uiState by remember {
 *     viewModel.uiState.orFallbackFlow(UiState.Loading)
 * }.collectAsState()
 * ```
 */
fun <T> StateFlow<T>?.orFallbackFlow(fallback: T): StateFlow<T> {
    return this ?: flowOf(fallback) as StateFlow<T>
}

/**
 * Compose-safe way to collect StateFlow with null safety.
 * 
 * Usage:
 * ```kotlin
 * val uiState by remember {
 *     viewModel.uiState ?: flowOf(UiState.Loading)
 * }.collectAsState()
 * ```
 */
@Composable
fun <T> StateFlow<T>?.collectAsStateOrDefault(fallback: T): State<T> {
    val safeFlow = remember(this) { this ?: flowOf(fallback) }
    return safeFlow.collectAsState(initial = fallback)
}