package com.example.liftrix.ui.settings.upgrade

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.common.PerformanceOptimizations
import com.example.liftrix.ui.components.actions.UnifiedWorkoutCard
import com.example.liftrix.ui.components.actions.PrimaryActionButton
import com.example.liftrix.ui.components.actions.SecondaryActionButton
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Upgrade to Premium screen with modern Material 3 design and Liftrix theming.
 * 
 * Features:
 * - Premium feature showcase with compelling benefits
 * - Honest preview-only availability state
 * - Responsive design with proper spacing and cards
 * - Error handling and loading states
 * - Analytics tracking for conversion optimization
 * - Accessibility support with semantic descriptions
 * 
 * @param onNavigateBack Callback to navigate back to settings
 * @param onContactSupport Callback to navigate to support screen
 * @param modifier Modifier for styling the screen
 * @param viewModel UpgradeViewModel for state management (injectable for testing)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeToPremiumScreen(
    onNavigateBack: () -> Unit,
    onContactSupport: () -> Unit = {},
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    viewModel: UpgradeViewModel = hiltViewModel()
) {
    // Performance monitoring for upgrade screen
    PerformanceOptimizations.AnimationPerformanceMonitor.MonitorAnimation(
        key = "UpgradeToPremiumScreen"
    ) {
        val uiState by viewModel.uiState.collectAsState()
        
        // Stable callbacks to prevent unnecessary recompositions  
        val stableOnEvent: (UpgradeEvent) -> Unit = remember(viewModel) { viewModel::onEvent }
        val stableOnNavigateBack = remember(onNavigateBack) { onNavigateBack }
        val stableOnContactSupport = remember(onContactSupport) { onContactSupport }
        
        Column(
            modifier = modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Premium feature preview"
                }
        ) {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Upgrade to Premium",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = stableOnNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back to settings"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
            
            // Content with performance tracking
            PerformanceOptimizations.MemoryEfficientComponents.TrackRecomposition(
                key = "UpgradeContent"
            ) {
                when {
                    uiState.shouldShowLoading -> {
                        LoadingState(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    uiState.shouldShowError -> {
                        ErrorState(
                            error = uiState.error ?: LiftrixError.UnknownError("Something went wrong"),
                            onRetry = { stableOnEvent(UpgradeEvent.RefreshSubscription) },
                            onDismiss = { stableOnEvent(UpgradeEvent.DismissError) },
                            onContactSupport = {
                                stableOnEvent(UpgradeEvent.ContactSupport)
                                stableOnContactSupport()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    uiState.shouldShowContent -> {
                        UpgradeContent(
                            uiState = uiState,
                            onEvent = stableOnEvent,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        
    }
}

/**
 * Main content for upgrade screen with features and pricing
 */
@Composable
private fun UpgradeContent(
    uiState: UpgradeUiState,
    onEvent: (UpgradeEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Section
        item {
            HeroSection()
        }
        
        // Premium Features
        item {
            Text(
                text = "Premium Features",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        items(
            items = uiState.premiumFeatures,
            key = { feature -> feature.title }
        ) { feature ->
            PremiumFeatureCard(feature = feature)
        }
        
        // Availability
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Premium Preview",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Premium purchases are unavailable in this build. You can still preview the features above.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryActionButton(
                text = "Purchases unavailable",
                onClick = {},
                enabled = false,
                leadingIcon = Icons.Default.Star,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Terms and Support
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = "Questions about premium availability?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextButton(
                    onClick = { onEvent(UpgradeEvent.ContactSupport) }
                ) {
                    Text("Need help? Contact Support")
                }
            }
        }
    }
}

/**
 * Hero section with gradient background and compelling messaging
 */
@Composable
private fun HeroSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Premium",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Unlock Your Full Potential",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Get advanced analytics, unlimited workouts, and AI-powered coaching to reach your fitness goals faster.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * Premium feature card component
 */
@Composable
private fun PremiumFeatureCard(
    feature: PremiumFeature,
    modifier: Modifier = Modifier
) {
    UnifiedWorkoutCard(
        title = feature.title,
        subtitle = feature.description,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = getFeatureIcon(feature.iconName),
                contentDescription = feature.title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Included",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Loading state component
 */
@Composable
private fun LoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Loading upgrade options...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Error state component
 */
@Composable
private fun ErrorState(
    error: LiftrixError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Unable to Load Upgrade Options",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = when (error) {
                is LiftrixError.NetworkError -> "Please check your internet connection and try again."
                is LiftrixError.BusinessLogicError -> error.errorMessage
                else -> "Something went wrong. Please try again."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SecondaryActionButton(
                text = "Contact Support",
                onClick = onContactSupport,
                leadingIcon = Icons.Default.Support
            )
            
            PrimaryActionButton(
                text = "Retry",
                onClick = onRetry,
                leadingIcon = Icons.Default.Refresh
            )
        }
    }
}

/**
 * Get icon for premium feature
 */
private fun getFeatureIcon(iconName: String): ImageVector {
    return when (iconName) {
        "analytics" -> Icons.Default.Analytics
        "unlimited" -> Icons.Default.AllInclusive
        "ai_coach" -> Icons.Default.SmartToy
        "social" -> Icons.Default.People
        "export" -> Icons.Default.FileDownload
        "support" -> Icons.Default.Support
        else -> Icons.Default.Star
    }
}

/**
 * Preview for UpgradeToPremiumScreen
 */
@Preview(showBackground = true)
@Composable
private fun UpgradeToPremiumScreenPreview() {
    LiftrixTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            UpgradeToPremiumScreen(
                onNavigateBack = {},
                onContactSupport = {}
            )
        }
    }
}
