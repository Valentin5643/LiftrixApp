package com.example.liftrix.ui.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.social.SearchFilters
import com.example.liftrix.domain.model.FitnessLevel
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.ui.social.components.UserSearchResultCard
import com.example.liftrix.ui.theme.LiftrixTheme
import java.time.LocalDateTime

/**
 * Screen for searching and discovering users
 * 
 * Provides real-time search with debouncing, advanced filtering options,
 * and optimized result display with caching indicators.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSearchScreen(
    onNavigateToProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserSearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilters by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Search bar with filter button
        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = { viewModel.handleEvent(UserSearchEvent.UpdateSearchQuery(it)) },
            onClearClick = { viewModel.handleEvent(UserSearchEvent.ClearSearch) },
            onFilterClick = { showFilters = true },
            hasActiveFilters = uiState.hasActiveFilters,
            isSearching = uiState.isSearching,
            isCachedResult = uiState.isCachedResult,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        // Search results or states
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when {
                uiState.error != null -> {
                    val error = uiState.error
                    ErrorState(
                        error = error!!,
                        onRetry = { viewModel.handleEvent(UserSearchEvent.RetrySearch) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                uiState.canShowResults -> {
                    SearchResults(
                        results = uiState.searchResults,
                        onUserClick = onNavigateToProfile,
                        onConnectClick = { userId ->
                            // Handle follow action
                            viewModel.handleEvent(UserSearchEvent.FollowUser(userId))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                uiState.canShowEmptyState -> {
                    EmptySearchState(
                        query = uiState.searchQuery,
                        hasFilters = uiState.hasActiveFilters,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                else -> {
                    InitialSearchState(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Filter bottom sheet
    if (showFilters) {
        FilterBottomSheet(
            currentFilters = uiState.appliedFilters,
            onFiltersApplied = { filters ->
                viewModel.handleEvent(UserSearchEvent.ApplyFilters(filters))
                showFilters = false
            },
            onClearFilters = {
                viewModel.handleEvent(UserSearchEvent.ClearFilters)
                showFilters = false
            },
            onDismiss = { showFilters = false }
        )
    }
}

/**
 * Search bar with query input and filter controls
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearClick: () -> Unit,
    onFilterClick: () -> Unit,
    hasActiveFilters: Boolean,
    isSearching: Boolean,
    isCachedResult: Boolean,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Search input
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search users by name or interests...") },
            leadingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClearClick) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    IconButton(onClick = onFilterClick) {
                        Icon(
                            imageVector = if (hasActiveFilters) Icons.Default.FilterAlt else Icons.Default.FilterList,
                            contentDescription = "Filter results",
                            tint = if (hasActiveFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { keyboardController?.hide() }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.secondary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                cursorColor = MaterialTheme.colorScheme.secondary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Search for users by name or interests"
                }
        )

        // Search status indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasActiveFilters) {
                AssistChip(
                    onClick = onFilterClick,
                    label = { Text("Filters active") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FilterAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            
            if (isCachedResult && query.isNotEmpty()) {
                AssistChip(
                    onClick = { },
                    label = { Text("Cached results") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

/**
 * Search results list
 */
@Composable
private fun SearchResults(
    results: List<UserSearchResult>,
    onUserClick: (String) -> Unit,
    onConnectClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "${results.size} users found",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        items(results, key = { it.userId }) { user ->
            UserSearchResultCard(
                user = user,
                onUserClick = { onUserClick(user.userId) },
                onConnectClick = { onConnectClick(user.userId) }
            )
        }
    }
}

/**
 * Empty search state when no results found
 */
@Composable
private fun EmptySearchState(
    query: String,
    hasFilters: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No users found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        val description = if (hasFilters) {
            "Try adjusting your filters or search for different terms"
        } else {
            "Try searching with different keywords or check your spelling"
        }

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/**
 * Initial state before any search is performed
 */
@Composable
private fun InitialSearchState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.People,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No new people\nto discover right now",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.titleLarge.lineHeight * 1.2
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Check back later or explore\nyour existing connections",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/**
 * Error state with retry option
 */
@Composable
private fun ErrorState(
    error: com.example.liftrix.domain.model.error.LiftrixError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Search failed",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = error.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}

/**
 * Filter bottom sheet component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    currentFilters: SearchFilters,
    onFiltersApplied: (SearchFilters) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    var filters by remember { mutableStateOf(currentFilters) }
    val bottomSheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter Results",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                
                TextButton(onClick = onClearFilters) {
                    Text("Clear All")
                }
            }

            // Fitness Level Filter
            FilterSection(
                title = "Fitness Level",
                content = {
                    LazyColumn {
                        items(FitnessLevel.values()) { level ->
                            FilterChip(
                                selected = filters.fitnessLevel == level,
                                onClick = {
                                    filters = filters.copy(
                                        fitnessLevel = if (filters.fitnessLevel == level) null else level
                                    )
                                },
                                label = { Text(level.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            )

            // Equipment Filter
            FilterSection(
                title = "Equipment",
                content = {
                    LazyColumn {
                        items(Equipment.values().take(5)) { equipment ->
                            FilterChip(
                                selected = filters.equipment.contains(equipment),
                                onClick = {
                                    filters = if (filters.equipment.contains(equipment)) {
                                        filters.copy(equipment = filters.equipment - equipment)
                                    } else {
                                        filters.copy(equipment = filters.equipment + equipment)
                                    }
                                },
                                label = { Text(equipment.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                
                Button(
                    onClick = { onFiltersApplied(filters) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply Filters")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Filter section component
 */
@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Preview(showBackground = true)
@Composable
private fun UserSearchScreenPreview() {
    LiftrixTheme {
        UserSearchScreen(
            onNavigateToProfile = { }
        )
    }
}