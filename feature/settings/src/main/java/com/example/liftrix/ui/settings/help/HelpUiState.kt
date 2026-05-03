package com.example.liftrix.ui.settings.help

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.help.HelpArticle
import com.example.liftrix.domain.model.help.HelpCategory
import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * UI state for help center screen with search and navigation capabilities
 */
sealed class HelpUiState {
    
    /**
     * Data class containing all help-related state
     */
    data class Data(
        val searchQuery: String = "",
        val searchResults: List<HelpArticle> = emptyList(),
        val categories: List<HelpCategory> = emptyList(),
        val popularArticles: List<HelpArticle> = emptyList(),
        val featuredArticles: List<HelpArticle> = emptyList(),
        val selectedCategory: HelpCategory? = null,
        val isSearching: Boolean = false,
        val isRefreshing: Boolean = false,
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
    
    /**
     * Loading state while fetching help content
     */
    data object Loading : HelpUiState()
    
    /**
     * Success state with help data
     */
    data class Success(
        val data: Data,
        val isRefreshing: Boolean = false
    ) : HelpUiState()
    
    /**
     * Error state with failure information
     */
    data class Error(
        val error: LiftrixError,
        val previousData: Data? = null
    ) : HelpUiState()
    
    /**
     * Empty state when no help content is available
     */
    data class Empty(
        val message: String = "No help articles available",
        val actionText: String? = "Browse Help Center",
        val showAction: Boolean = true
    ) : HelpUiState()
}

/**
 * Events that can be triggered from the help UI
 */
sealed class HelpEvent : ViewModelEvent {
    /**
     * Load initial help content
     */
    data object LoadContent : HelpEvent()
    
    /**
     * Refresh help content from remote sources
     */
    data object RefreshContent : HelpEvent()
    
    /**
     * Search for help articles
     */
    data class SearchArticles(val query: String) : HelpEvent()
    
    /**
     * Clear search and return to default view
     */
    data object ClearSearch : HelpEvent()
    
    /**
     * Select a category to filter articles
     */
    data class SelectCategory(val category: HelpCategory?) : HelpEvent()
    
    /**
     * Record that an article was viewed
     */
    data class ViewArticle(val articleId: String) : HelpEvent()
    
    /**
     * Mark an article as helpful or not helpful
     */
    data class MarkArticleHelpful(val articleId: String, val helpful: Boolean) : HelpEvent()
    
    /**
     * Retry failed operations
     */
    data object Retry : HelpEvent()
}

/**
 * Side effects that should be handled by the UI
 */
sealed class HelpSideEffect {
    /**
     * Navigate to article detail screen
     */
    data class NavigateToArticle(val articleId: String) : HelpSideEffect()
    
    /**
     * Navigate to support ticket creation
     */
    data object NavigateToSupport : HelpSideEffect()
    
    /**
     * Show feedback confirmation
     */
    data class ShowFeedbackConfirmation(val helpful: Boolean) : HelpSideEffect()
    
    /**
     * Show error message
     */
    data class ShowError(val message: String) : HelpSideEffect()
}