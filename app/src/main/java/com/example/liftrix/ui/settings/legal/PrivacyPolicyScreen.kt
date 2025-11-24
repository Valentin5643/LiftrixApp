package com.example.liftrix.ui.settings.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.ui.common.FeedItemShimmer
import com.example.liftrix.ui.common.ShimmerPlaceholder
import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.settings.legal.LegalDocumentViewModel
import com.example.liftrix.ui.settings.legal.LegalDocumentUiState
import timber.log.Timber

/**
 * Privacy Policy screen displaying the current privacy policy document
 *
 * Features:
 * - WebView or text display of privacy policy content
 * - Download as PDF option
 * - Last updated date display
 * - Search functionality within document
 * - Offline content caching
 * - Loading and error states
 * - Accessibility support
 *
 * Modernization: Uses direct function calls instead of event-based pattern
 *
 * @param onNavigateBack Callback to navigate back to previous screen
 * @param viewModel LegalDocumentViewModel for managing content state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit,
    viewModel: LegalDocumentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(viewModel) {
        viewModel.loadPrivacyPolicy()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Privacy Policy",
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
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshContent() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh content"
                        )
                    }

                    IconButton(
                        onClick = { viewModel.downloadAsPdf("privacy_policy") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download as PDF"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        val currentUiState = uiState
        when (currentUiState) {
            is LegalDocumentUiState.Loading -> {
                LegalDocumentLoadingContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            
            is LegalDocumentUiState.Success -> {
                LegalDocumentContent(
                    document = currentUiState.data.privacyPolicy,
                    lastUpdated = currentUiState.data.privacyPolicyLastUpdated,
                    isRefreshing = currentUiState.data.isRefreshing,
                    onRefresh = { viewModel.refreshContent() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            
            is LegalDocumentUiState.Error -> {
                LegalDocumentErrorContent(
                    error = currentUiState.error,
                    onRetry = { viewModel.loadPrivacyPolicy() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            is LegalDocumentUiState.Empty -> {
                LegalDocumentEmptyContent(
                    onRetry = { viewModel.loadPrivacyPolicy() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Terms of Service screen displaying the current terms document
 * 
 * Features:
 * - WebView or text display of terms content
 * - Download as PDF option  
 * - Last updated date display
 * - Search functionality within document
 * - Offline content caching
 * - Loading and error states
 * - Accessibility support
 * 
 * @param onNavigateBack Callback to navigate back to previous screen
 * @param viewModel LegalDocumentViewModel for managing content state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(
    onNavigateBack: () -> Unit,
    viewModel: LegalDocumentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(viewModel) {
        viewModel.loadTermsOfService()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Terms of Service",
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
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshContent() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh content"
                        )
                    }

                    IconButton(
                        onClick = { viewModel.downloadAsPdf("terms_of_service") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download as PDF"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        val currentUiState = uiState
        when (currentUiState) {
            is LegalDocumentUiState.Loading -> {
                LegalDocumentLoadingContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            
            is LegalDocumentUiState.Success -> {
                LegalDocumentContent(
                    document = currentUiState.data.termsOfService,
                    lastUpdated = currentUiState.data.termsOfServiceLastUpdated,
                    isRefreshing = currentUiState.data.isRefreshing,
                    onRefresh = { viewModel.refreshContent() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            is LegalDocumentUiState.Error -> {
                LegalDocumentErrorContent(
                    error = currentUiState.error,
                    onRetry = { viewModel.loadTermsOfService() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            is LegalDocumentUiState.Empty -> {
                LegalDocumentEmptyContent(
                    onRetry = { viewModel.loadTermsOfService() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Loading state content
 */
@Composable
private fun LegalDocumentLoadingContent(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(8) {
            ShimmerPlaceholder(
                modifier = Modifier.fillMaxWidth().height(60.dp)
            )
        }
    }
}

/**
 * Main document content display
 */
@Composable
private fun LegalDocumentContent(
    document: String?,
    lastUpdated: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Last updated info
        if (lastUpdated != null) {
            item {
                LastUpdatedCard(lastUpdated = lastUpdated)
            }
        }
        
        // Document content
        if (document != null) {
            item {
                DocumentContentCard(
                    content = document,
                    isRefreshing = isRefreshing
                )
            }
        }
    }
}

/**
 * Last updated information card
 */
@Composable
private fun LastUpdatedCard(
    lastUpdated: String,
    modifier: Modifier = Modifier
) {
    ElevatedLiftrixCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Column {
                Text(
                    text = "Last Updated",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = lastUpdated,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Document content card
 */
@Composable
private fun DocumentContentCard(
    content: String,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    ElevatedLiftrixCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isRefreshing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Updating content...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Document content - simplified text display
            // In a real implementation, you might use a WebView or Markdown renderer
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Error state content
 */
@Composable
private fun LegalDocumentErrorContent(
    error: com.example.liftrix.domain.model.error.LiftrixError,
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
            text = "Failed to load document",
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
private fun LegalDocumentEmptyContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "Document not available",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "The document is currently unavailable. Please try again later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Try Again")
        }
    }
}

/**
 * Preview for privacy policy screen
 */
@Preview(showBackground = true)
@Composable
private fun PrivacyPolicyScreenPreview() {
    LiftrixTheme {
        // Preview would need mock ViewModel
    }
}

/**
 * Preview for terms of service screen
 */
@Preview(showBackground = true)
@Composable
private fun TermsOfServiceScreenPreview() {
    LiftrixTheme {
        // Preview would need mock ViewModel
    }
}