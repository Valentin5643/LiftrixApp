package com.example.liftrix.ui.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.help.HelpArticle
import com.example.liftrix.domain.model.help.HelpCategory
import com.example.liftrix.ui.common.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/**
 * ViewModel for help center screen implementing MVI pattern
 * 
 * Features:
 * - Article search with debounced input
 * - Category filtering and navigation
 * - Content refresh from remote sources
 * - Engagement tracking (views, feedback)
 * - Error handling with retry capabilities
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class HelpViewModel @Inject constructor(
    // Add use cases here when they're available
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<HelpUiState>(UiState.Loading)
    val uiState: StateFlow<HelpUiState> = _uiState.asStateFlow()
    
    private val _sideEffects = MutableSharedFlow<HelpSideEffect>()
    val sideEffects = _sideEffects.asSharedFlow()
    
    private val searchQueryFlow = MutableSharedFlow<String>()
    
    init {
        // Setup debounced search
        viewModelScope.launch {
            searchQueryFlow
                .debounce(300) // Wait 300ms after user stops typing
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collect { query ->
                    performSearch(query)
                }
        }
        
        // Load initial content
        handleEvent(HelpEvent.LoadContent)
    }
    
    fun handleEvent(event: HelpEvent) {
        when (event) {
            is HelpEvent.LoadContent -> loadContent()
            is HelpEvent.LoadArticle -> loadArticle(event.articleId)
            is HelpEvent.RecordArticleView -> recordArticleView(event.articleId)
            is HelpEvent.RefreshContent -> refreshContent()
            is HelpEvent.SearchArticles -> handleSearch(event.query)
            is HelpEvent.ClearSearch -> clearSearch()
            is HelpEvent.SelectCategory -> selectCategory(event.category)
            is HelpEvent.ViewArticle -> navigateToArticle(event.articleId)
            is HelpEvent.ViewTicket -> navigateToSupport()
            is HelpEvent.MarkArticleHelpful -> markArticleHelpful(event.articleId, event.helpful)
            is HelpEvent.Retry -> retry()
        }
    }
    
    /**
     * Loads initial help content (categories, popular articles, featured articles)
     */
    private fun loadContent() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            try {
                // Mock data for now - would normally come from repository
                val categories = getMockCategories()
                val popularArticles = getMockPopularArticles()
                val featuredArticles = getMockFeaturedArticles()
                
                val data = HelpUiData(
                    categories = categories,
                    popularArticles = popularArticles,
                    featuredArticles = featuredArticles
                )
                
                if (data.categories.isEmpty() && data.popularArticles.isEmpty() && data.featuredArticles.isEmpty()) {
                    _uiState.value = UiState.Empty()
                } else {
                    _uiState.value = UiState.Success(data)
                }
            } catch (e: Exception) {
                val error = LiftrixError.BusinessLogicError(
                    code = "HELP_LOAD_FAILED",
                    errorMessage = "Failed to load help content",
                    analyticsContext = mapOf("operation" to "LOAD_HELP_CONTENT")
                )
_uiState.value = UiState.Error(error)
                Timber.e(e, "Failed to load help content")
            }
        }
    }
    
    /**
     * Loads a specific article by ID
     */
    private fun loadArticle(articleId: String) {
        viewModelScope.launch {
            try {
                val currentData = getCurrentData()
                _uiState.value = UiState.Success(currentData.copy(isLoading = true))
                
                // Mock article loading - would normally come from repository
                val article = getMockArticle(articleId)
                
                val updatedData = currentData.copy(
                    selectedArticle = article,
                    isLoading = false
                )
_uiState.value = UiState.Success(updatedData)
            } catch (e: Exception) {
                val error = LiftrixError.BusinessLogicError(
                    code = "ARTICLE_LOAD_FAILED",
                    errorMessage = "Failed to load article",
                    analyticsContext = mapOf("operation" to "LOAD_ARTICLE", "articleId" to articleId)
                )
_uiState.value = UiState.Error(error)
                Timber.e(e, "Failed to load article: $articleId")
            }
        }
    }
    
    /**
     * Records that an article was viewed
     */
    private fun recordArticleView(articleId: String) {
        // Record view asynchronously without blocking UI
        viewModelScope.launch {
            try {
                // Mock view recording - would normally call repository
                Timber.d("Recorded view for article $articleId")
            } catch (e: Exception) {
                Timber.w(e, "Failed to record view for article $articleId")
            }
        }
    }
    
    /**
     * Refreshes help content from remote sources
     */
    private fun refreshContent() {
        val currentData = getCurrentData()
        _uiState.value = UiState.Success(currentData.copy(isRefreshing = true))
        
        viewModelScope.launch {
            try {
                // Mock refresh - would normally refresh from remote
                kotlinx.coroutines.delay(1000) // Simulate network delay
                
                val categories = getMockCategories()
                val popularArticles = getMockPopularArticles()
                val featuredArticles = getMockFeaturedArticles()
                
                val updatedData = currentData.copy(
                    categories = categories,
                    popularArticles = popularArticles,
                    featuredArticles = featuredArticles,
                    isRefreshing = false
                )
_uiState.value = UiState.Success(updatedData)
                Timber.d("Help content refreshed successfully")
            } catch (e: Exception) {
                val error = LiftrixError.BusinessLogicError(
                    code = "HELP_REFRESH_FAILED",
                    errorMessage = "Failed to refresh content",
                    analyticsContext = mapOf("operation" to "REFRESH_HELP_CONTENT")
                )
_uiState.value = UiState.Error(error)
                emitSideEffect(HelpSideEffect.ShowError("Failed to refresh content"))
                Timber.e(e, "Failed to refresh help content")
            }
        }
    }
    
    /**
     * Handles search query input with debouncing
     */
    private fun handleSearch(query: String) {
        val currentData = getCurrentData()
        _uiState.value = UiState.Success(currentData.copy(
            searchQuery = query,
            selectedCategory = null // Clear category when searching
        ))
        
        if (query.isBlank()) {
            // Clear search immediately if query is empty
            _uiState.value = UiState.Success(currentData.copy(
                searchQuery = "",
                searchResults = emptyList(),
                hasSearched = false
            ))
        } else {
            // Emit to debounced search flow
            viewModelScope.launch {
                searchQueryFlow.emit(query)
            }
        }
    }
    
    /**
     * Performs the actual search operation
     */
    private fun performSearch(query: String) {
        val currentData = getCurrentData()
        _uiState.value = UiState.Success(currentData.copy(isSearching = true))
        
        viewModelScope.launch {
            try {
                // Mock search - would normally call repository
                val allArticles = currentData.popularArticles + currentData.featuredArticles
                val results = allArticles.filter { article ->
                    article.title.contains(query, ignoreCase = true) ||
                    article.content.contains(query, ignoreCase = true) ||
                    article.keywords.any { it.contains(query, ignoreCase = true) }
                }
                
                val updatedData = currentData.copy(
                    searchResults = results,
                    isSearching = false,
                    hasSearched = true
                )
_uiState.value = UiState.Success(updatedData)
                
                Timber.d("Search for '$query' returned ${results.size} results")
            } catch (e: Exception) {
                val error = LiftrixError.BusinessLogicError(
                    code = "SEARCH_FAILED",
                    errorMessage = "Search failed",
                    analyticsContext = mapOf("operation" to "SEARCH_ARTICLES", "query" to query)
                )
_uiState.value = UiState.Error(error)
                emitSideEffect(HelpSideEffect.ShowError("Search failed"))
                Timber.e(e, "Search failed for query '$query'")
            }
        }
    }
    
    /**
     * Clears search and returns to default view
     */
    private fun clearSearch() {
        val currentData = getCurrentData()
        _uiState.value = UiState.Success(currentData.copy(
            searchQuery = "",
            searchResults = emptyList(),
            hasSearched = false,
            selectedCategory = null
        ))
    }
    
    /**
     * Selects a category to filter articles
     */
    private fun selectCategory(category: HelpCategory?) {
        val currentData = getCurrentData()
        _uiState.value = UiState.Success(currentData.copy(
            selectedCategory = category,
            searchQuery = "", // Clear search when selecting category
            searchResults = emptyList(),
            hasSearched = false
        ))
    }
    
    /**
     * Navigates to article detail screen
     */
    private fun navigateToArticle(articleId: String) {
        recordArticleView(articleId)
        emitSideEffect(HelpSideEffect.NavigateToArticle(articleId))
    }
    
    /**
     * Navigates to support screen
     */
    private fun navigateToSupport() {
        emitSideEffect(HelpSideEffect.NavigateToSupport)
    }
    
    /**
     * Marks an article as helpful or not helpful
     */
    private fun markArticleHelpful(articleId: String, helpful: Boolean) {
        viewModelScope.launch {
            try {
                // Mock feedback submission - would normally call repository
                emitSideEffect(HelpSideEffect.ShowFeedbackConfirmation(helpful))
                Timber.d("Marked article $articleId as ${if (helpful) "helpful" else "not helpful"}")
            } catch (e: Exception) {
                emitSideEffect(HelpSideEffect.ShowError("Failed to submit feedback"))
                Timber.e(e, "Failed to mark article $articleId as helpful")
            }
        }
    }
    
    /**
     * Retries the last failed operation
     */
    private fun retry() {
        when (val currentState = uiState.value) {
            is UiState.Error -> {
                loadContent()
            }
            is UiState.Empty -> loadContent()
            else -> {
                // Nothing to retry
                Timber.d("No failed operation to retry")
            }
        }
    }
    
    /**
     * Gets current data from state or returns default
     */
    private fun getCurrentData(): HelpUiData {
        return when (val currentState = uiState.value) {
            is UiState.Success -> currentState.data
            else -> HelpUiData()
        }
    }
    
    
    /**
     * Emits a side effect to be handled by the UI
     */
    private fun emitSideEffect(effect: HelpSideEffect) {
        viewModelScope.launch {
            _sideEffects.emit(effect)
        }
    }
    
    // Mock data methods - would normally be injected repositories
    
    private fun getMockCategories(): List<HelpCategory> {
        return HelpCategory.getDefaultCategories()
    }
    
    private fun getMockPopularArticles(): List<HelpArticle> {
        return listOf(
            HelpArticle(
                id = "1",
                category = HelpArticle.Companion.Category.GETTING_STARTED,
                title = "Getting Started with Liftrix",
                content = "Welcome to Liftrix! This comprehensive guide will help you get started with tracking your workouts, setting goals, and making the most of our features.",
                keywords = listOf("getting started", "setup", "onboarding", "tutorial"),
                viewCount = 1250,
                helpfulCount = 89,
                notHelpfulCount = 12,
                lastUpdated = Instant.now(),
                isFeatured = true
            ),
            HelpArticle(
                id = "2",
                category = HelpArticle.Companion.Category.WORKOUTS,
                title = "Creating Your First Workout",
                content = "Learn how to create custom workouts tailored to your fitness goals. This guide covers exercise selection, set configuration, and workout planning.",
                keywords = listOf("workout", "create", "custom", "exercises"),
                viewCount = 980,
                helpfulCount = 76,
                notHelpfulCount = 8,
                lastUpdated = Instant.now()
            ),
            HelpArticle(
                id = "3",
                category = HelpArticle.Companion.Category.PROGRESS_TRACKING,
                title = "Understanding Your Progress Charts",
                content = "Discover how to read and interpret your progress charts to better understand your fitness journey and identify areas for improvement.",
                keywords = listOf("progress", "charts", "analytics", "tracking"),
                viewCount = 756,
                helpfulCount = 62,
                notHelpfulCount = 15,
                lastUpdated = Instant.now()
            )
        )
    }
    
    private fun getMockFeaturedArticles(): List<HelpArticle> {
        return getMockPopularArticles().filter { it.isFeatured }
    }
    
    private fun getMockArticle(articleId: String): HelpArticle? {
        return getMockPopularArticles().find { it.id == articleId }
    }
}