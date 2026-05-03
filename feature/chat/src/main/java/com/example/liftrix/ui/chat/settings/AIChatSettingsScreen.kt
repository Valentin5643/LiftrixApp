package com.example.liftrix.ui.chat.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.chat.settings.components.*
import com.example.liftrix.ui.common.state.UiState

/**
 * Settings screen for AI Chat configuration.
 * Provides comprehensive controls for AI behavior, usage limits, and data management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AIChatSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Handle success messages
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            // Auto-dismiss success message after 3 seconds
            kotlinx.coroutines.delay(3000)
            viewModel.handleEvent(AIChatSettingsEvent.DismissSuccessMessage)
        }
    }
    
    Scaffold(
        topBar = {
            AIChatSettingsTopBar(
                onNavigateBack = onNavigateBack,
                language = uiState.preferences?.preferredLanguage ?: "en"
            )
        },
        containerColor = LiftrixColorsV2.surface
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val preferencesState = uiState.preferencesState) {
                is UiState.Loading -> {
                    LoadingState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                is UiState.Success -> {
                    AIChatSettingsContent(
                        uiState = uiState,
                        onEvent = viewModel::handleEvent,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                is UiState.Error -> {
                    ErrorState(
                        error = preferencesState.error,
                        onRetry = { }, // No retry needed for preferences loading
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                is UiState.Empty -> {
                    EmptyState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                else -> {
                    // This should never happen as all cases are covered
                    LoadingState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            
            // Success message overlay
            uiState.successMessage?.let { message ->
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(LiftrixSpacing.medium),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(LiftrixSpacing.cardPadding),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Error message overlay
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(LiftrixSpacing.medium),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(LiftrixSpacing.cardPadding)
                    ) {
                        Text(
                            text = error.message ?: "Unknown error occurred",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(
                            onClick = { viewModel.handleEvent(AIChatSettingsEvent.DismissError) }
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
            }
            
            // Dialogs
            if (uiState.showClearHistoryDialog) {
                ClearHistoryDialog(
                    totalMessages = uiState.totalMessages,
                    requiredConfirmationText = viewModel.getRequiredConfirmationText(),
                    language = uiState.preferences?.preferredLanguage ?: "en",
                    onConfirm = { confirmationText ->
                        viewModel.handleEvent(AIChatSettingsEvent.ClearAllHistory(confirmationText))
                    },
                    onDismiss = {
                        viewModel.handleEvent(AIChatSettingsEvent.HideClearHistoryDialog)
                    }
                )
            }
            
            if (uiState.showExportDialog) {
                ExportProgressDialog(
                    isExporting = uiState.exportInProgress,
                    exportedData = uiState.exportedData,
                    language = uiState.preferences?.preferredLanguage ?: "en",
                    onDismiss = {
                        viewModel.handleEvent(AIChatSettingsEvent.HideExportDialog)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIChatSettingsTopBar(
    onNavigateBack: () -> Unit,
    language: String
) {
    TopAppBar(
        title = {
            Text(
                text = when (language) {
                    "ro" -> "Setări AI Chat"
                    else -> "AI Chat Settings"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = when (language) {
                        "ro" -> "Înapoi"
                        else -> "Back"
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = LiftrixColorsV2.surface,
            titleContentColor = LiftrixColorsV2.onSurface,
            navigationIconContentColor = LiftrixColorsV2.onSurface
        )
    )
}

@Composable
private fun AIChatSettingsContent(
    uiState: AIChatSettingsUiState,
    onEvent: (AIChatSettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val preferences = uiState.preferences ?: return
    
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(LiftrixSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
    ) {
        // Language Settings
        LanguageSettingsCard(
            preferredLanguage = preferences.preferredLanguage,
            autoDetectLanguage = preferences.autoDetectLanguage,
            onLanguageChange = { language ->
                onEvent(AIChatSettingsEvent.UpdateLanguagePreference(language))
            },
            onAutoDetectChange = { enabled ->
                onEvent(AIChatSettingsEvent.UpdateAutoDetectLanguage(enabled))
            }
        )
        
        // Personalization Settings
        PersonalizationCard(
            aiResponseStyle = preferences.aiResponseStyle,
            userContextPrompt = preferences.userContextPrompt,
            includeWorkoutHistory = preferences.includeWorkoutHistory,
            includeExerciseFormTips = preferences.includeExerciseFormTips,
            language = preferences.preferredLanguage,
            onResponseStyleChange = { style ->
                onEvent(AIChatSettingsEvent.UpdateResponseStyle(style))
            },
            onUserContextChange = { prompt ->
                onEvent(AIChatSettingsEvent.UpdateUserContextPrompt(prompt))
            },
            onWorkoutHistoryChange = { include ->
                onEvent(AIChatSettingsEvent.UpdateWorkoutHistoryInclusion(include))
            },
            onExerciseFormTipsChange = { include ->
                onEvent(AIChatSettingsEvent.UpdateExerciseFormTips(include))
            }
        )
        
        // Usage Limits Settings
        UsageLimitsCard(
            maxMessagesPerDay = preferences.maxMessagesPerDay,
            maxTokensPerMonth = preferences.maxTokensPerMonth,
            usageNotificationsThreshold = preferences.usageNotificationsThreshold,
            language = preferences.preferredLanguage,
            onMaxMessagesChange = { maxMessages ->
                onEvent(AIChatSettingsEvent.UpdateMaxMessagesPerDay(maxMessages))
            },
            onMaxTokensChange = { maxTokens ->
                onEvent(AIChatSettingsEvent.UpdateMaxTokensPerMonth(maxTokens))
            },
            onThresholdChange = { threshold ->
                onEvent(AIChatSettingsEvent.UpdateUsageThreshold(threshold))
            }
        )
        
        // Data Management Settings
        DataManagementCard(
            totalMessages = uiState.totalMessages,
            totalTokens = uiState.totalTokens,
            conversationSaveEnabled = preferences.conversationSaveEnabled,
            autoClearDays = preferences.autoClearDays,
            language = preferences.preferredLanguage,
            isLoading = uiState.clearHistoryInProgress || uiState.exportInProgress,
            onClearHistory = {
                onEvent(AIChatSettingsEvent.ShowClearHistoryDialog)
            },
            onExportHistory = {
                onEvent(AIChatSettingsEvent.ShowExportDialog)
            },
            onConversationSaveChange = { enabled ->
                onEvent(AIChatSettingsEvent.UpdateConversationSaveEnabled(enabled))
            }
        )
        
        // Bottom spacing for scroll
        Spacer(modifier = Modifier.height(LiftrixSpacing.large))
    }
}

@Composable
private fun LoadingState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
    ) {
        CircularProgressIndicator(
            color = LiftrixColorsV2.primary
        )
        Text(
            text = "Loading settings...",
            style = MaterialTheme.typography.bodyMedium,
            color = LiftrixColorsV2.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorState(
    error: LiftrixError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(LiftrixSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
    ) {
        Text(
            text = "Failed to load settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Text(
            text = error.message ?: "Unknown error occurred",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(LiftrixSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
    ) {
        Text(
            text = "No settings available",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Please check your connection and try again",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}
