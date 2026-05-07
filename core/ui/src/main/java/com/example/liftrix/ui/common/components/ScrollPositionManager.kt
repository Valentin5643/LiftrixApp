package com.example.liftrix.ui.common.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import kotlinx.coroutines.launch

/**
 * Utility composable for managing scroll position persistence
 * 
 * Automatically saves and restores scroll position for Column/ScrollableColumn
 * components with verticalScroll modifier.
 */
@Composable
fun rememberScrollStateWithPersistence(
    getSavedPosition: () -> Int,
    savePosition: (Int) -> Unit
): ScrollState {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    
    // Restore scroll position on initialization
    LaunchedEffect(Unit) {
        val savedPosition = getSavedPosition()
        if (savedPosition > 0) {
            coroutineScope.launch {
                scrollState.scrollTo(savedPosition)
            }
        }
    }
    
    // Save scroll position when it changes (with debouncing)
    LaunchedEffect(scrollState.value) {
        savePosition(scrollState.value)
    }
    
    return scrollState
}

/**
 * Utility composable for managing LazyList scroll position persistence
 * 
 * Automatically saves and restores scroll position for LazyColumn/LazyRow
 * components.
 */
@Composable
fun rememberLazyListStateWithPersistence(
    getSavedPosition: () -> Int,
    savePosition: (Int) -> Unit
): LazyListState {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Restore scroll position on initialization
    LaunchedEffect(Unit) {
        val savedPosition = getSavedPosition()
        if (savedPosition > 0) {
            coroutineScope.launch {
                listState.scrollToItem(savedPosition)
            }
        }
    }
    
    // Save scroll position when it changes
    LaunchedEffect(listState.firstVisibleItemIndex) {
        savePosition(listState.firstVisibleItemIndex)
    }
    
    return listState
}

/**
 * Extension functions to simplify usage with StatefulDetailViewModel
 */
@Composable
inline fun <reified T> T.rememberScrollStateWithPersistence(): ScrollState
    where T : com.example.liftrix.ui.common.viewmodel.StatefulDetailViewModel<*, *> {
    return rememberScrollStateWithPersistence(
        getSavedPosition = { this.getSavedScrollPosition() },
        savePosition = { position -> this.saveScrollPosition(position) }
    )
}

@Composable
inline fun <reified T> T.rememberLazyListStateWithPersistence(): LazyListState
    where T : com.example.liftrix.ui.common.viewmodel.StatefulDetailViewModel<*, *> {
    return rememberLazyListStateWithPersistence(
        getSavedPosition = { this.getSavedScrollPosition() },
        savePosition = { position -> this.saveScrollPosition(position) }
    )
}
