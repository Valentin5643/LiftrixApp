package com.example.liftrix.ui.settings.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    showTopBar: Boolean = true,
    viewModel: LegalDocumentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(viewModel) {
        viewModel.loadPrivacyPolicy()
    }
    
    Scaffold(
        topBar = {
            if (showTopBar) {
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

                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
                )
            }
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
    showTopBar: Boolean = true,
    viewModel: LegalDocumentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(viewModel) {
        viewModel.loadTermsOfService()
    }
    
    Scaffold(
        topBar = {
            if (showTopBar) {
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

                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
                )
            }
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
    var scrollToAnchorId by rememberSaveable { mutableStateOf<String?>(null) }
    var scrollRequestKey by rememberSaveable { mutableStateOf(0) }
    val anchors = remember(document) { document?.let { extractHtmlAnchors(it) } ?: emptyList() }

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
                    isRefreshing = isRefreshing,
                    anchors = anchors,
                    onJumpToAnchor = { anchorId ->
                        scrollToAnchorId = anchorId
                        scrollRequestKey += 1
                    },
                    scrollToAnchorId = scrollToAnchorId,
                    scrollRequestKey = scrollRequestKey
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
    ElevatedLiftrixCard(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Last updated",
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
 * Document content card with hybrid HTML rendering
 *
 * Uses HtmlContent composable which intelligently chooses:
 * - HtmlCompat (default) for semantic HTML
 * - WebView (fallback) for complex CSS
 */
@Composable
private fun DocumentContentCard(
    content: String,
    isRefreshing: Boolean,
    anchors: List<HtmlAnchor>,
    onJumpToAnchor: (String) -> Unit,
    scrollToAnchorId: String?,
    scrollRequestKey: Int,
    modifier: Modifier = Modifier
) {
    ElevatedLiftrixCard(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DocumentControls(
                anchors = anchors,
                onJumpToAnchor = onJumpToAnchor
            )

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

            // Hybrid HTML renderer: HtmlCompat (default) → WebView (fallback)
            // Handles both plain text and HTML content intelligently
            HtmlContent(
                html = content,
                modifier = Modifier.fillMaxWidth(),
                anchors = anchors,
                scrollToAnchorId = scrollToAnchorId,
                scrollRequestKey = scrollRequestKey
            )
        }
    }
}

@Composable
private fun DocumentControls(
    anchors: List<HtmlAnchor>,
    onJumpToAnchor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val hasAnchors = anchors.isNotEmpty()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                TextButton(
                    onClick = { isMenuExpanded = true },
                    enabled = hasAnchors
                ) {
                    Text("Jump to section")
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand sections"
                    )
                }
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    anchors.forEach { anchor ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = anchor.title,
                                    modifier = Modifier.padding(start = if (anchor.level >= 3) 12.dp else 0.dp)
                                )
                            },
                            onClick = {
                                isMenuExpanded = false
                                onJumpToAnchor(anchor.id)
                            }
                        )
                    }
                }
            }
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
            contentDescription = "Error",
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
            contentDescription = "Document",
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
 * AI Disclaimer screen displaying the AI usage disclaimer
 *
 * Features:
 * - WebView or text display of AI disclaimer content
 * - Last updated date display
 * - Loading and error states
 * - Accessibility support
 *
 * @param onNavigateBack Callback to navigate back to previous screen
 * @param viewModel LegalDocumentViewModel for managing content state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIDisclaimerScreen(
    onNavigateBack: () -> Unit,
    showTopBar: Boolean = true,
    viewModel: LegalDocumentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.loadAIDisclaimer()
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                title = {
                    Text(
                        text = "AI Disclaimer",
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

                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
                )
            }
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
                    document = currentUiState.data.aiDisclaimer,
                    lastUpdated = currentUiState.data.aiDisclaimerLastUpdated,
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
                    onRetry = { viewModel.loadAIDisclaimer() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            is LegalDocumentUiState.Empty -> {
                LegalDocumentEmptyContent(
                    onRetry = { viewModel.loadAIDisclaimer() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Community Guidelines screen displaying community conduct policies
 *
 * @param onNavigateBack Callback to navigate back to previous screen
 * @param viewModel LegalDocumentViewModel for managing content state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityGuidelinesScreen(
    onNavigateBack: () -> Unit,
    showTopBar: Boolean = true,
    viewModel: LegalDocumentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.loadCommunityGuidelines()
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                title = {
                    Text(
                        text = "Community Guidelines",
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

                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
                )
            }
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
                    document = currentUiState.data.communityGuidelines,
                    lastUpdated = currentUiState.data.communityGuidelinesLastUpdated,
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
                    onRetry = { viewModel.loadCommunityGuidelines() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            is LegalDocumentUiState.Empty -> {
                LegalDocumentEmptyContent(
                    onRetry = { viewModel.loadCommunityGuidelines() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Content Moderation Policy screen
 *
 * @param onNavigateBack Callback to navigate back to previous screen
 * @param viewModel LegalDocumentViewModel for managing content state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentModerationPolicyScreen(
    onNavigateBack: () -> Unit,
    showTopBar: Boolean = true,
    viewModel: LegalDocumentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.loadContentModerationPolicy()
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                title = {
                    Text(
                        text = "Content Moderation Policy",
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

                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
                )
            }
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
                    document = currentUiState.data.contentModerationPolicy,
                    lastUpdated = currentUiState.data.contentModerationPolicyLastUpdated,
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
                    onRetry = { viewModel.loadContentModerationPolicy() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            is LegalDocumentUiState.Empty -> {
                LegalDocumentEmptyContent(
                    onRetry = { viewModel.loadContentModerationPolicy() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Refund and Subscription Policy screen
 *
 * @param onNavigateBack Callback to navigate back to previous screen
 * @param viewModel LegalDocumentViewModel for managing content state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefundSubscriptionPolicyScreen(
    onNavigateBack: () -> Unit,
    showTopBar: Boolean = true,
    viewModel: LegalDocumentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.loadRefundSubscriptionPolicy()
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                title = {
                    Text(
                        text = "Refund & Subscription Policy",
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

                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
                )
            }
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
                    document = currentUiState.data.refundSubscriptionPolicy,
                    lastUpdated = currentUiState.data.refundSubscriptionPolicyLastUpdated,
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
                    onRetry = { viewModel.loadRefundSubscriptionPolicy() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            is LegalDocumentUiState.Empty -> {
                LegalDocumentEmptyContent(
                    onRetry = { viewModel.loadRefundSubscriptionPolicy() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
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
