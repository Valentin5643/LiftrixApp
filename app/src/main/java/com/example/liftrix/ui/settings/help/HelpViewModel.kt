package com.example.liftrix.ui.settings.help

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.help.HelpCategory
import com.example.liftrix.domain.service.HelpCenterService
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import timber.log.Timber
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
    private val helpCenterService: HelpCenterService,
    errorHandler: ErrorHandler
) : BaseViewModel<HelpUiState, HelpEvent>(errorHandler) {
    
    override val _uiState = MutableStateFlow<HelpUiState>(HelpUiState.Loading)
    
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
    
    override fun handleEvent(event: HelpEvent) {
        when (event) {
            is HelpEvent.LoadContent -> loadContent()
            is HelpEvent.RefreshContent -> refreshContent()
            is HelpEvent.SearchArticles -> handleSearch(event.query)
            is HelpEvent.ClearSearch -> clearSearch()
            is HelpEvent.SelectCategory -> selectCategory(event.category)
            is HelpEvent.ViewArticle -> recordArticleView(event.articleId)
            is HelpEvent.MarkArticleHelpful -> markArticleHelpful(event.articleId, event.helpful)
            is HelpEvent.Retry -> retry()
        }
    }
    
    /**
     * Loads initial help content (categories, popular articles, featured articles)
     */
    private fun loadContent() {
        viewModelScope.launch {
            updateState { HelpUiState.Loading }
            
            val categories = helpCenterService.getCategories().fold(
                onSuccess = { it },
                onFailure = { emptyList() }
            )
            val popularArticles = helpCenterService.getPopularArticles().fold(
                onSuccess = { it },
                onFailure = { emptyList() }
            )
            val featuredArticles = helpCenterService.getFeaturedArticles().fold(
                onSuccess = { it },
                onFailure = { emptyList() }
            )
            
            val data = HelpUiState.Data(
                categories = categories,
                popularArticles = popularArticles,
                featuredArticles = featuredArticles
            )
            
            if (categories.isEmpty() && popularArticles.isEmpty() && featuredArticles.isEmpty()) {
                updateState { HelpUiState.Empty() }
            } else {
                updateState { HelpUiState.Success(data) }
            }
            Timber.d("Loaded help content with ${categories.size} categories")
        }
    }
    
    /**
     * Refreshes help content from remote sources
     */
    private fun refreshContent() {
        viewModelScope.launch {
            val currentData = getCurrentData()
            updateState { HelpUiState.Success(currentData.copy(isRefreshing = true)) }
            
            try {
                // Refresh content from remote
                val refreshResult = helpCenterService.refreshContent(forceRefresh = true)
                refreshResult.fold(
                    onSuccess = { /* Continue */ },
                    onFailure = { error -> 
                        Timber.w("Remote refresh failed: $error")
                    }
                )
                
                // Reload all content
                val categories = helpCenterService.getCategories().fold(
                    onSuccess = { it },
                    onFailure = { emptyList() }
                )
                val popularArticles = helpCenterService.getPopularArticles().fold(
                    onSuccess = { it },
                    onFailure = { emptyList() }
                )
                val featuredArticles = helpCenterService.getFeaturedArticles().fold(
                    onSuccess = { it },
                    onFailure = { emptyList() }
                )
                
                val data = currentData.copy(
                    categories = categories,
                    popularArticles = popularArticles,
                    featuredArticles = featuredArticles,
                    isRefreshing = false
                )
                
                updateState { HelpUiState.Success(data) }
                Timber.d("Help content refreshed successfully")
            } catch (e: Exception) {
                val error = LiftrixError.BusinessLogicError(
                    code = "REFRESH_FAILED",
                    errorMessage = "Failed to refresh content: ${e.message}",
                    analyticsContext = mapOf("operation" to "REFRESH_HELP_CONTENT")
                )
                updateState { 
                    HelpUiState.Error(error, previousData = currentData.copy(isRefreshing = false))
                }
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
        updateState { 
            HelpUiState.Success(currentData.copy(
                searchQuery = query,
                selectedCategory = null // Clear category when searching
            ))
        }
        
        if (query.isBlank()) {
            // Clear search immediately if query is empty
            updateState { 
                HelpUiState.Success(currentData.copy(
                    searchQuery = "",
                    searchResults = emptyList(),
                    hasSearched = false
                ))
            }
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
        viewModelScope.launch {
            val currentData = getCurrentData()
            updateState { 
                HelpUiState.Success(currentData.copy(isSearching = true))
            }
            
            val results = helpCenterService.searchArticles(query).fold(
                onSuccess = { it },
                onFailure = { error ->
                    Timber.e("Search failed for query '$query': $error")
                    emptyList()
                }
            )
            
            val updatedData = currentData.copy(
                searchResults = results,
                isSearching = false,
                hasSearched = true
            )
            updateState { HelpUiState.Success(updatedData) }
            
            Timber.d("Search for '$query' returned ${results.size} results")
        }
    }
    
    /**
     * Clears search and returns to default view
     */
    private fun clearSearch() {
        val currentData = getCurrentData()
        updateState { 
            HelpUiState.Success(currentData.copy(
                searchQuery = "",
                searchResults = emptyList(),
                hasSearched = false,
                selectedCategory = null
            ))
        }
    }
    
    /**
     * Selects a category to filter articles
     */
    private fun selectCategory(category: HelpCategory?) {
        val currentData = getCurrentData()
        updateState { 
            HelpUiState.Success(currentData.copy(
                selectedCategory = category,
                searchQuery = "", // Clear search when selecting category
                searchResults = emptyList(),
                hasSearched = false
            ))
        }
        
        if (category != null) {
            // Load articles for the selected category
            viewModelScope.launch {
                val articles = helpCenterService.getArticlesByCategory(category.name).fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        emitSideEffect(HelpSideEffect.ShowError("Failed to load category articles"))
                        Timber.e("Failed to load articles for category ${category.name}: $error")
                        emptyList()
                    }
                )
                
                val updatedData = getCurrentData().copy(
                    popularArticles = articles // Use popularArticles to display category results
                )
                updateState { HelpUiState.Success(updatedData) }
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
                helpCenterService.recordArticleView(articleId)
                Timber.d("Recorded view for article $articleId")
            } catch (e: Exception) {
                Timber.w(e, "Failed to record view for article $articleId")
            }
        }
        
        // Navigate to article
        emitSideEffect(HelpSideEffect.NavigateToArticle(articleId))
    }
    
    /**
     * Marks an article as helpful or not helpful
     */
    private fun markArticleHelpful(articleId: String, helpful: Boolean) {
        viewModelScope.launch {
            helpCenterService.markHelpful(articleId, helpful).fold(
                onSuccess = {
                    emitSideEffect(HelpSideEffect.ShowFeedbackConfirmation(helpful))
                    Timber.d("Marked article $articleId as ${if (helpful) "helpful" else "not helpful"}")
                },
                onFailure = { error ->
                    emitSideEffect(HelpSideEffect.ShowError("Failed to submit feedback"))
                    Timber.e("Failed to mark article $articleId as helpful: $error")
                }
            )
        }
    }
    
    /**
     * Retries the last failed operation
     */
    private fun retry() {
        when (val currentState = _uiState.value) {
            is HelpUiState.Error -> {
                if (currentState.previousData == null || currentState.previousData.categories.isEmpty()) {
                    // Retry initial load
                    loadContent()
                } else {
                    // Retry refresh
                    refreshContent()
                }
            }
            is HelpUiState.Empty -> loadContent()
            else -> {
                // Nothing to retry
                Timber.d("No failed operation to retry")
            }
        }
    }
    
    /**
     * Gets current data from state or returns default
     */
    private fun getCurrentData(): HelpUiState.Data {
        return when (val currentState = _uiState.value) {
            is HelpUiState.Success -> currentState.data
            is HelpUiState.Error -> currentState.previousData ?: HelpUiState.Data()
            else -> HelpUiState.Data()
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
}