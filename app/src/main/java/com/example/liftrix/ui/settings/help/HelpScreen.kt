package com.example.liftrix.ui.settings.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.help.HelpArticle
import com.example.liftrix.domain.model.help.HelpCategory
import com.example.liftrix.ui.common.ContextualColorOverlay
import com.example.liftrix.ui.common.ColorContext
import com.example.liftrix.ui.common.FeedItemShimmer
import com.example.liftrix.ui.common.ShimmerPlaceholder
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard
import com.example.liftrix.ui.theme.LiftrixTheme
import eu.bambooapps.material3.pullrefresh.PullRefreshIndicator
import eu.bambooapps.material3.pullrefresh.pullRefresh
import eu.bambooapps.material3.pullrefresh.rememberPullRefreshState
import timber.log.Timber

/**
 * Help center screen with search functionality and categorized articles
 * 
 * Features:
 * - Search bar with debounced input for help articles
 * - Popular articles displayed prominently
 * - Category-based navigation and filtering
 * - Pull-to-refresh functionality for content updates
 * - Floating action button for direct support contact
 * - Article feedback and engagement tracking
 * - Error handling with retry capabilities
 * - Accessibility support and semantic descriptions
 * 
 * @param onNavigateBack Callback to navigate back to previous screen
 * @param onNavigateToArticle Callback to navigate to specific article detail
 * @param onNavigateToSupport Callback to navigate to support ticket creation
 * @param viewModel HelpViewModel instance for managing state and events
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit,
    onNavigateToArticle: (String) -> Unit,
    onNavigateToSupport: () -> Unit,
    viewModel: HelpViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Handle side effects
    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is HelpSideEffect.NavigateToArticle -> {
                    onNavigateToArticle(effect.articleId)
                }
                is HelpSideEffect.NavigateToSupport -> {
                    onNavigateToSupport()
                }
                is HelpSideEffect.ShowError -> {
                    // Handle error display (could integrate with SnackbarHost)
                    Timber.e("Help screen error: ${effect.message}")
                }
                is HelpSideEffect.ShowFeedbackConfirmation -> {
                    // Handle feedback confirmation
                    Timber.d("Feedback submitted: ${if (effect.helpful) "helpful" else "not helpful"}")
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Help Center",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToSupport,
                text = { Text("Contact Support") },
                icon = { 
                    Icon(
                        imageVector = Icons.Default.Email, 
                        contentDescription = null
                    )
                },
                modifier = Modifier.semantics {
                    contentDescription = "Contact support for personalized help"
                }
            )
        }
    ) { paddingValues ->
        val currentUiState = uiState
        when (currentUiState) {
            is HelpUiState.Loading -> {
                HelpLoadingContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            
            is HelpUiState.Success -> {
                HelpSuccessContent(
                    data = currentUiState.data,
                    onSearchQueryChanged = { query ->
                        viewModel.handleEvent(HelpEvent.SearchArticles(query))
                    },
                    onClearSearch = {
                        viewModel.handleEvent(HelpEvent.ClearSearch)
                    },
                    onCategorySelected = { category ->
                        viewModel.handleEvent(HelpEvent.SelectCategory(category))
                    },
                    onArticleClicked = { articleId ->
                        viewModel.handleEvent(HelpEvent.ViewArticle(articleId))
                    },
                    onRefresh = {
                        viewModel.handleEvent(HelpEvent.RefreshContent)
                    },
                    onContactSupport = onNavigateToSupport,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            
            is HelpUiState.Error -> {
                HelpErrorContent(
                    error = currentUiState.error,
                    data = currentUiState.previousData ?: HelpUiState.Data(),
                    onRetry = {
                        viewModel.handleEvent(HelpEvent.Retry)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            
            is HelpUiState.Empty -> {
                HelpEmptyContent(
                    onContactSupport = onNavigateToSupport,
                    onRetry = {
                        viewModel.handleEvent(HelpEvent.Retry)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Loading state content with shimmer effects
 */
@Composable
private fun HelpLoadingContent(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search bar shimmer
        item {
            ShimmerPlaceholder(
                modifier = Modifier.fillMaxWidth().height(56.dp)
            )
        }
        
        // Popular articles shimmer
        items(3) {
            ShimmerPlaceholder(
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
        }
        
        // Categories shimmer
        items(4) {
            ShimmerPlaceholder(
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )
        }
    }
}

/**
 * Success state content with search and categories
 */
@Composable
private fun HelpSuccessContent(
    data: HelpUiState.Data,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onCategorySelected: (HelpCategory?) -> Unit,
    onArticleClicked: (String) -> Unit,
    onRefresh: () -> Unit,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRefreshing = data.isRefreshing
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh
    )
    
    Box(modifier = modifier.pullRefresh(pullRefreshState)) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search bar
            item {
                HelpSearchBar(
                    query = data.searchQuery,
                    onQueryChanged = onSearchQueryChanged,
                    onClearSearch = onClearSearch,
                    isSearching = data.isSearching,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            if (data.shouldShowSearchResults) {
                // Search results
                if (data.searchResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "Search Results (${data.searchResults.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(data.searchResults) { article ->
                        HelpArticleCard(
                            article = article,
                            onClick = { onArticleClicked(article.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (data.hasSearched) {
                    item {
                        NoSearchResultsContent(
                            query = data.searchQuery,
                            onContactSupport = onContactSupport
                        )
                    }
                }
            } else {
                // Default content - popular articles and categories
                
                // Popular articles
                if (data.popularArticles.isNotEmpty()) {
                    item {
                        Text(
                            text = "Popular Articles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(data.popularArticles.take(5)) { article ->
                        HelpArticleCard(
                            article = article,
                            onClick = { onArticleClicked(article.id) },
                            showEngagement = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Featured articles
                if (data.featuredArticles.isNotEmpty()) {
                    item {
                        Text(
                            text = "Featured Articles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(data.featuredArticles.take(3)) { article ->
                        FeaturedArticleCard(
                            article = article,
                            onClick = { onArticleClicked(article.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Categories
                if (data.categories.isNotEmpty()) {
                    item {
                        Text(
                            text = "Browse by Category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(data.categories) { category ->
                        CategoryCard(
                            category = category,
                            isSelected = data.selectedCategory == category,
                            onClick = { onCategorySelected(category) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Bottom spacing for FAB
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
        
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * Search bar component with debounced input
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        placeholder = { 
            Text("Search for help articles...") 
        },
        leadingIcon = { 
            Icon(
                imageVector = Icons.Default.Search, 
                contentDescription = null
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearSearch) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search"
                    )
                }
            } else if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        },
        singleLine = true,
        modifier = modifier.semantics {
            contentDescription = "Search help articles"
        }
    )
}

/**
 * Help article card component
 */
@Composable
private fun HelpArticleCard(
    article: HelpArticle,
    onClick: () -> Unit,
    showEngagement: Boolean = false,
    modifier: Modifier = Modifier
) {
    ElevatedLiftrixCard(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = article.getContentPreview(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = article.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (showEngagement && article.viewCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${article.viewCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Featured article card with enhanced styling
 */
@Composable
private fun FeaturedArticleCard(
    article: HelpArticle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ContextualColorOverlay(
        context = ColorContext.UserPreference(
            preferredColor = MaterialTheme.colorScheme.primary,
            intensity = 0.3f
        ),
        modifier = modifier
    ) {
        LiftrixCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Featured",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = article.getContentPreview(200),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Category card component
 */
@Composable
private fun CategoryCard(
    category: HelpCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    ElevatedLiftrixCard(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = getCategoryIcon(category.name),
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                if (category.description.isNotEmpty()) {
                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            if (category.articleCount > 0) {
                Text(
                    text = "${category.articleCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * No search results content
 */
@Composable
private fun NoSearchResultsContent(
    query: String,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "No results for \"$query\"",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Try adjusting your search terms or browse by category",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Button(
            onClick = onContactSupport,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Contact Support")
        }
    }
}

/**
 * Error state content
 */
@Composable
private fun HelpErrorContent(
    error: com.example.liftrix.domain.model.error.LiftrixError,
    data: HelpUiState.Data,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Text(
            text = "Failed to load help content",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = error.message ?: "Please check your connection and try again",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Retry")
        }
    }
}

/**
 * Empty state content
 */
@Composable
private fun HelpEmptyContent(
    onContactSupport: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.HelpOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "No help content available",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Check back later or contact support for assistance",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onRetry) {
                Text("Retry")
            }
            
            Button(onClick = onContactSupport) {
                Text("Contact Support")
            }
        }
    }
}

/**
 * Helper function to get icon for category
 */
private fun getCategoryIcon(categoryName: String) = when (categoryName) {
    HelpCategory.Companion.Category.GETTING_STARTED -> Icons.Default.PlayArrow
    HelpCategory.Companion.Category.WORKOUTS -> Icons.Default.FitnessCenter
    HelpCategory.Companion.Category.EXERCISES -> Icons.Default.DirectionsRun
    HelpCategory.Companion.Category.PROGRESS_TRACKING -> Icons.Default.TrendingUp
    HelpCategory.Companion.Category.SOCIAL_FEATURES -> Icons.Default.Group
    HelpCategory.Companion.Category.SETTINGS -> Icons.Default.Settings
    HelpCategory.Companion.Category.TROUBLESHOOTING -> Icons.Default.Build
    HelpCategory.Companion.Category.ACCOUNT -> Icons.Default.Person
    HelpCategory.Companion.Category.SYNC -> Icons.Default.Sync
    HelpCategory.Companion.Category.PREMIUM_FEATURES -> Icons.Default.Star
    else -> Icons.Default.Help
}

/**
 * Preview for help screen
 */
@Preview(showBackground = true)
@Composable
private fun HelpScreenPreview() {
    LiftrixTheme {
        // Preview would need mock data
    }
}