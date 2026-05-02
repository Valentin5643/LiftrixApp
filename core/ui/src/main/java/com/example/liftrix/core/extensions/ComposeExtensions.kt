package com.example.liftrix.core.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun <T> rememberDerivedStateOf(
    vararg keys: Any?,
    calculation: () -> T
): State<T> = remember(*keys) { derivedStateOf(calculation) }

@Composable
fun <T> Flow<T>.collectAsOptimizedState(
    equalityComparator: ((T, T) -> Boolean)? = null
): State<T?> {
    val state = remember { mutableStateOf<T?>(null) }
    LaunchedEffect(this) {
        val distinctFlow = if (equalityComparator != null) {
            this@collectAsOptimizedState.distinctUntilChanged(equalityComparator)
        } else {
            this@collectAsOptimizedState.distinctUntilChanged()
        }
        distinctFlow.collect { state.value = it }
    }
    return state
}

@Composable
fun <W, D> rememberWidgetDataCache(
    widgets: List<W>,
    dataProvider: (W) -> D
): Map<W, D> {
    val widgetKeys = remember(widgets) { widgets.hashCode() }
    return remember(widgetKeys) { widgets.associateWith { widget -> dataProvider(widget) } }
}

@Composable
fun <T> rememberPreviousValidData(
    currentData: T,
    isValid: (T) -> Boolean
): T {
    val lastValid = remember { mutableStateOf(currentData) }
    if (isValid(currentData)) {
        lastValid.value = currentData
    }
    return lastValid.value
}

object WidgetPerformanceExt
