package com.example.liftrix.ui.help

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.help.HelpArticle
import com.example.liftrix.domain.model.help.HelpCategory
import com.example.liftrix.ui.common.state.UiState

/**
 * UI state for help center screen following UiState<T> pattern
 */
typealias HelpUiState = UiState<HelpUiData>

/**
 * Extensions for HelpUiState
 */
fun initialHelpUiState(): HelpUiState = UiState.Loading

/**
 * Data class containing all help-related state
 */
data class HelpUiData(
    val searchQuery: String = "",
    val searchResults: List<HelpArticle> = emptyList(),
    val categories: List<HelpCategory> = emptyList(),
    val popularArticles: List<HelpArticle> = emptyList(),
    val featuredArticles: List<HelpArticle> = emptyList(),
    val selectedCategory: HelpCategory? = null,
    val selectedArticle: HelpArticle? = null,
    val isSearching: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false
) {
    /**
     * Checks if we should show search results vs default content
     */
    val shouldShowSearchResults: Boolean
        get() = searchQuery.isNotBlank() && hasSearched
    
    /**
     * Gets the current articles to display based on state
     */
    val currentArticles: List<HelpArticle>
        get() = when {
            shouldShowSearchResults -> searchResults
            selectedCategory != null -> popularArticles.filter { 
                it.category.equals(selectedCategory.name, ignoreCase = true) 
            }
            else -> popularArticles
        }
    
    /**
     * Checks if there are no articles to show
     */
    val hasNoResults: Boolean
        get() = when {
            shouldShowSearchResults -> searchResults.isEmpty()
            selectedCategory != null -> currentArticles.isEmpty()
            else -> popularArticles.isEmpty() && featuredArticles.isEmpty()
        }
}

// Type alias for convenience
typealias HelpData = HelpUiData