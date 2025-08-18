package com.example.liftrix.ui.help

import com.example.liftrix.domain.model.help.HelpCategory
import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * Events that can be triggered from the help UI
 */
sealed class HelpEvent : ViewModelEvent {
    /**
     * Load initial help content
     */
    data object LoadContent : HelpEvent()
    
    /**
     * Load a specific article by ID
     */
    data class LoadArticle(val articleId: String) : HelpEvent()
    
    /**
     * Record that an article was viewed
     */
    data class RecordArticleView(val articleId: String) : HelpEvent()
    
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
     * Navigate to view an article
     */
    data class ViewArticle(val articleId: String) : HelpEvent()
    
    /**
     * Navigate to view a support ticket
     */
    data class ViewTicket(val ticketId: String) : HelpEvent()
    
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