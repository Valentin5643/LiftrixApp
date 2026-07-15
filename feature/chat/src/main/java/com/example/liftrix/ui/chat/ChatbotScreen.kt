package com.example.liftrix.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.example.liftrix.feature.chat.R
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.chat.components.AIChatDisclaimerBanner
import com.example.liftrix.ui.chat.components.TypingIndicator
import com.example.liftrix.ui.chat.components.AIMessageReportDialog
import com.example.liftrix.ui.chat.components.AIReportReason
import com.example.liftrix.domain.model.ai.GeneratedPrescriptionType
import com.example.liftrix.domain.model.ai.GeneratedWorkoutExercise
import com.example.liftrix.domain.model.ai.WorkoutGenerationResult
import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.model.chat.UsageLimits
import com.example.liftrix.domain.model.chat.ChatConversation
import com.example.liftrix.ui.chat.components.ConversationHistoryPane
import com.example.liftrix.ui.chat.components.RenameConversationDialog
import com.example.liftrix.ui.chat.components.DeleteConversationDialog
import com.example.liftrix.ui.chat.workoutbuilder.components.GeneratedWorkoutPlanPreview
import com.example.liftrix.ui.common.WindowWidthSizeClass
import com.example.liftrix.ui.common.rememberWindowSizeClass
import kotlin.math.absoluteValue
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

/**
 * Chatbot screen providing AI-powered workout guidance interface.
 * 
 * Features:
 * - Real-time chat interface with message bubbles
 * - Typing indicators and smooth animations
 * - Usage limit monitoring and warnings
 * - Multi-language support (English/Romanian)
 * - Auto-scroll to latest messages
 * - Error handling with retry functionality
 * 
 * @param conversationId Optional conversation ID to resume existing chat
 * @param initialWorkoutContext Optional workout context for AI responses
 * @param onNavigateBack Callback for navigation back action
 * @param viewModel Injected ChatbotViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotScreen(
    conversationId: String? = null,
    initialWorkoutContext: String? = null,
    onNavigateBack: () -> Unit,
    onCreateWorkoutPlan: (String?, String?) -> Unit = { _, _ -> },
    showTopBar: Boolean = true,
    viewModel: ChatbotViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDisclaimer by remember { mutableStateOf(true) }
    val windowSize = rememberWindowSizeClass()
    val compact = windowSize.widthSizeClass == WindowWidthSizeClass.COMPACT
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var renameTarget by remember { mutableStateOf<ChatConversation?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatConversation?>(null) }
    LaunchedEffect(uiState.workoutBuilderNavigation) {
        uiState.workoutBuilderNavigation?.let { request ->
            onCreateWorkoutPlan(request.conversationId, request.seedPrompt)
            viewModel.handleEvent(ChatbotEvent.WorkoutBuilderNavigationConsumed)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = compact,
        drawerContent = {
            if (compact) ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
                ConversationHistoryPane(
                    conversations = uiState.conversations,
                    activeConversationId = uiState.activeConversationId,
                    newConversationEnabled = !uiState.isTyping,
                    onNewConversation = { viewModel.handleEvent(ChatbotEvent.NewConversation); scope.launch { drawerState.close() } },
                    onOpenConversation = { viewModel.handleEvent(ChatbotEvent.OpenConversation(it)); scope.launch { drawerState.close() } },
                    onRenameConversation = { renameTarget = it },
                    onDeleteConversation = { deleteTarget = it }
                )
            }
        }
    ) {
    Scaffold(
        topBar = {
            if (showTopBar) {
                ChatbotTopBar(
                    onNavigateBack = onNavigateBack,
                    usageLimits = uiState.usageLimits,
                    currentLanguage = uiState.currentLanguage,
                    onOpenHistory = { scope.launch { drawerState.open() } },
                    showHistoryButton = compact
                )
            }
        },
        bottomBar = {
            Column {
                CreateWorkoutPlanAction(
                    currentLanguage = uiState.currentLanguage,
                    onClick = { viewModel.handleEvent(ChatbotEvent.CreateWorkoutPlan) },
                    enabled = uiState.canUseAiAction,
                    modifier = Modifier.padding(horizontal = LiftrixSpacing.medium)
                )
                ChatInputBar(
                    text = uiState.currentInput,
                    onTextChange = { viewModel.handleEvent(ChatbotEvent.UpdateInput(it)) },
                    onSend = { viewModel.handleEvent(ChatbotEvent.SendMessage(it)) },
                    enabled = uiState.canUseAiAction,
                    currentLanguage = uiState.currentLanguage
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(Modifier.fillMaxSize()) {
                if (!compact) {
                    ConversationHistoryPane(
                        conversations = uiState.conversations,
                        activeConversationId = uiState.activeConversationId,
                        newConversationEnabled = !uiState.isTyping,
                        onNewConversation = { viewModel.handleEvent(ChatbotEvent.NewConversation) },
                        onOpenConversation = { viewModel.handleEvent(ChatbotEvent.OpenConversation(it)) },
                        onRenameConversation = { renameTarget = it },
                        onDeleteConversation = { deleteTarget = it },
                        modifier = Modifier.width(300.dp).fillMaxHeight()
                    )
                }
            MessageList(
                messages = uiState.messages,
                isTyping = uiState.isTyping,
                generatedProgram = uiState.pendingGeneratedProgram,
                isSavingGeneratedProgram = uiState.isSavingGeneratedProgram,
                generatedProgramSaved = uiState.generatedProgramSaved,
                currentLanguage = uiState.currentLanguage,
                onSaveGeneratedProgram = {
                    viewModel.handleEvent(ChatbotEvent.SaveGeneratedProgram)
                },
                onOverwriteGeneratedProgram = {
                    viewModel.handleEvent(ChatbotEvent.OverwriteGeneratedProgram)
                },
                onDismissGeneratedProgram = {
                    viewModel.handleEvent(ChatbotEvent.DismissGeneratedProgram)
                },
                onReportMessage = { messageId, messageContent, reason, notes ->
                    viewModel.handleEvent(ChatbotEvent.ReportAIMessage(messageId, messageContent, reason, notes))
                },
                onPromptSelected = { prompt ->
                    viewModel.handleEvent(ChatbotEvent.UpdateInput(prompt))
                },
                modifier = Modifier
                    .weight(1f)
            )
            }

            if (!uiState.isOnline) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.align(Alignment.TopCenter)) {
                    Text("Offline — saved chats remain available; sending is disabled.", modifier = Modifier.padding(12.dp))
                }
            }

            if (!showTopBar && compact) {
                TextButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = LiftrixSpacing.small, end = LiftrixSpacing.medium)
                        .heightIn(min = 48.dp)
                        .testTag("conversation_history_button")
                ) {
                    Icon(Icons.Outlined.Forum, contentDescription = null)
                    Spacer(Modifier.width(LiftrixSpacing.small))
                    Text(if (uiState.currentLanguage == Language.ROMANIAN) "Chat-uri" else "Chats")
                }
            }

            // AI Disclaimer Banner (session-scoped)
            AnimatedVisibility(
                visible = showDisclaimer,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                AIChatDisclaimerBanner(
                    onDismiss = { showDisclaimer = false },
                    modifier = Modifier.padding(LiftrixSpacing.medium)
                )
            }

            // Usage warning overlay
            AnimatedVisibility(
                visible = uiState.showUsageWarning,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                UsageWarningCard(
                    limits = uiState.usageLimits,
                    currentLanguage = uiState.currentLanguage,
                    modifier = Modifier.padding(LiftrixSpacing.medium)
                )
            }
            
            // Error handling overlay
            uiState.error?.let { error ->
                ErrorCard(
                    error = error,
                    currentLanguage = uiState.currentLanguage,
                    onRetry = { viewModel.handleEvent(ChatbotEvent.RetryLastMessage) },
                    onDismiss = { viewModel.handleEvent(ChatbotEvent.DismissError) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(LiftrixSpacing.medium)
                )
            }
        }
    }
    }

    renameTarget?.let { conversation ->
        RenameConversationDialog(conversation, onDismiss = { renameTarget = null }) { title ->
            viewModel.handleEvent(ChatbotEvent.RenameConversation(conversation.id, title)); renameTarget = null
        }
    }
    deleteTarget?.let { conversation ->
        DeleteConversationDialog(conversation, onDismiss = { deleteTarget = null }) {
            viewModel.handleEvent(ChatbotEvent.DeleteConversation(conversation.id)); deleteTarget = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatbotTopBar(
    onNavigateBack: () -> Unit,
    usageLimits: UsageLimits?,
    currentLanguage: Language,
    onOpenHistory: () -> Unit,
    showHistoryButton: Boolean
) {
    TopAppBar(
        title = { 
            Text(
                text = if (currentLanguage == Language.ROMANIAN) "Antrenor AI" else "AI Coach",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = if (currentLanguage == Language.ROMANIAN) "Înapoi" else "Back"
                )
            }
        },
        actions = {
            if (showHistoryButton) {
                TextButton(
                    onClick = onOpenHistory,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("conversation_history_button")
                ) {
                    Icon(Icons.Outlined.Forum, contentDescription = null)
                    Spacer(Modifier.width(LiftrixSpacing.small))
                    Text(if (currentLanguage == Language.ROMANIAN) "Chat-uri" else "Chats")
                }
            }
            // Usage limits display
            usageLimits?.let { limits ->
                if (limits.dailyMessagesRemaining < 20) {
                    Text(
                        text = if (currentLanguage == Language.ROMANIAN)
                            "${limits.dailyMessagesRemaining} rămase azi"
                        else
                            "${limits.dailyMessagesRemaining} left today",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (limits.dailyMessagesRemaining < 5) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = LiftrixSpacing.medium)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun CreateWorkoutPlanAction(
    currentLanguage: Language,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("create_workout_plan_button")
    ) {
        Icon(Icons.Outlined.FitnessCenter, contentDescription = null)
        Spacer(Modifier.width(LiftrixSpacing.small))
        Text(
            text = if (currentLanguage == Language.ROMANIAN) {
                "Creează un plan de antrenament"
            } else {
                "Create a workout plan"
            },
            maxLines = 1
        )
        Spacer(Modifier.width(LiftrixSpacing.small))
        Icon(Icons.Outlined.ArrowForward, contentDescription = null)
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    isTyping: Boolean,
    generatedProgram: WorkoutGenerationResult?,
    isSavingGeneratedProgram: Boolean,
    generatedProgramSaved: Boolean,
    currentLanguage: Language,
    onSaveGeneratedProgram: () -> Unit,
    onOverwriteGeneratedProgram: () -> Unit,
    onDismissGeneratedProgram: () -> Unit,
    onReportMessage: (messageId: String, messageContent: String, reason: AIReportReason, notes: String?) -> Unit,
    onPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showJumpToLatest by remember { mutableStateOf(false) }
    val isNearBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    val generatedProgramAnchorMessageId = remember(messages, generatedProgram) {
        generatedProgram?.let {
            messages.lastOrNull { message -> message.isGeneratedProgramPreviewMessage() }?.id
        }
    }

    LaunchedEffect(messages.size, generatedProgram, isTyping) {
        if (messages.isNotEmpty()) {
            val ownSend = messages.lastOrNull()?.type == MessageType.USER
            if (isNearBottom || ownSend || listState.layoutInfo.totalItemsCount == 0) {
                val targetIndex = if (generatedProgram != null || isTyping) messages.size else messages.lastIndex
                listState.animateScrollToItem(targetIndex)
                showJumpToLatest = false
            } else {
                showJumpToLatest = true
            }
        }
    }

    Box(modifier) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(LiftrixSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
    ) {
        if (messages.isEmpty() && generatedProgram == null && !isTyping) {
            item(key = "empty_ai_coach_state") {
                AiCoachEmptyState(
                    currentLanguage = currentLanguage,
                    onPromptSelected = onPromptSelected,
                    modifier = Modifier.fillParentMaxSize()
                )
            }
        }

        items(
            items = messages,
            key = { it.id }
        ) { message ->
            MessageBubble(
                message = message,
                onReport = { reason, notes ->
                    onReportMessage(message.id, message.content, reason, notes)
                },
                modifier = Modifier.animateItem()
            )

            if (message.id == generatedProgramAnchorMessageId && generatedProgram != null) {
                GeneratedProgramPreviewCard(
                    result = generatedProgram,
                    isSaving = isSavingGeneratedProgram,
                    isSaved = generatedProgramSaved,
                    currentLanguage = currentLanguage,
                    onSave = onSaveGeneratedProgram,
                    onOverwrite = onOverwriteGeneratedProgram,
                    onDismiss = onDismissGeneratedProgram,
                    modifier = Modifier.padding(top = LiftrixSpacing.small)
                )
            }
        }

        if (generatedProgram != null && generatedProgramAnchorMessageId == null) {
            item(key = generatedProgram.previewItemKey()) {
                GeneratedProgramPreviewCard(
                    result = generatedProgram,
                    isSaving = isSavingGeneratedProgram,
                    isSaved = generatedProgramSaved,
                    currentLanguage = currentLanguage,
                    onSave = onSaveGeneratedProgram,
                    onOverwrite = onOverwriteGeneratedProgram,
                    onDismiss = onDismissGeneratedProgram,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Typing indicator
        if (isTyping) {
            item {
                TypingIndicator(
                    modifier = Modifier
                        .padding(start = LiftrixSpacing.medium)
                        .testTag("typing_indicator")
                )
            }
        }
    }
        if (showJumpToLatest) {
            FilledTonalButton(
                onClick = {
                    scope.launch { listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0)); showJumpToLatest = false }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).heightIn(min = 48.dp)
            ) { Text("Jump to latest") }
        }
    }
}

private fun ChatMessage.isGeneratedProgramPreviewMessage(): Boolean {
    if (type != MessageType.AI_RESPONSE) return false
    return content.contains("Review the preview below")
}

private fun WorkoutGenerationResult.previewItemKey(): String {
    val programFingerprint = "${program.workoutName}_${program.days.size}_${program.days.sumOf { it.exercises.size }}"
        .hashCode()
        .absoluteValue
    return "generated_program_preview_$programFingerprint"
}

@Composable
private fun AiCoachEmptyState(
    currentLanguage: Language,
    onPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val prompts = if (currentLanguage == Language.ROMANIAN) {
        listOf("Îmbunătățește-mi forma", "Ce ar trebui să antrenez azi?")
    } else {
        listOf("Improve my form", "What should I train today?")
    }

    Column(
        modifier = modifier.padding(horizontal = LiftrixSpacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ai_coach_empty),
            contentDescription = "AI Coach empty state illustration",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(164.dp)
        )

        Spacer(modifier = Modifier.height(LiftrixSpacing.medium))

        Text(
            text = if (currentLanguage == Language.ROMANIAN) {
                "Antrenorul tău AI este pregătit."
            } else {
                "Your AI Coach is ready."
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(LiftrixSpacing.large))

        Text(
            text = if (currentLanguage == Language.ROMANIAN) {
                "Încearcă o întrebare"
            } else {
                "Try a quick question"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(LiftrixSpacing.small))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            prompts.forEach { prompt ->
                OutlinedButton(
                    onClick = { onPromptSelected(prompt) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(text = prompt, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun GeneratedProgramPreviewCard(
    result: WorkoutGenerationResult,
    isSaving: Boolean,
    isSaved: Boolean,
    currentLanguage: Language,
    onSave: () -> Unit,
    onOverwrite: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
        ) {
            Text(
                text = result.program.workoutName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (currentLanguage == Language.ROMANIAN) {
                    if (result.sourceReference != null) {
                        "${result.program.days.size} zile modificate"
                    } else {
                        "${result.program.days.size} zile generate"
                    }
                } else {
                    if (result.sourceReference != null) {
                        "${result.program.days.size} modified days"
                    } else {
                        "${result.program.days.size} generated days"
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )

            result.sourceReference?.let { source ->
                Text(
                    text = "Source: ${source.sourceName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (result.changeSummaries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    result.changeSummaries.take(4).forEach { change ->
                        Text(
                            text = "${change.type.name.lowercase().replace('_', ' ')}: ${change.before} -> ${change.after}. ${change.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Box(Modifier.heightIn(max = 520.dp)) {
                GeneratedWorkoutPlanPreview(result = result, compact = true)
            }

            if (result.validationWarnings.isNotEmpty()) {
                Text(
                    text = result.validationWarnings.joinToString(separator = "\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            result.optionalQuestion?.let { question ->
                Text(
                    text = question,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSave,
                    enabled = !isSaving && !isSaved
                ) {
                    Text(
                        text = when {
                            isSaved -> if (currentLanguage == Language.ROMANIAN) "Salvat" else "Saved"
                            isSaving -> if (currentLanguage == Language.ROMANIAN) "Se salveaza" else "Saving"
                            result.sourceReference != null -> if (currentLanguage == Language.ROMANIAN) "Copiaza" else "Save Copy"
                            else -> if (currentLanguage == Language.ROMANIAN) "Salveaza" else "Save"
                        }
                    )
                }
                if (result.saveTargetTemplateId != null) {
                    OutlinedButton(
                        onClick = onOverwrite,
                        enabled = !isSaving && !isSaved
                    ) {
                        Text(text = if (currentLanguage == Language.ROMANIAN) "Suprascrie" else "Overwrite")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(text = if (currentLanguage == Language.ROMANIAN) "Inchide" else "Dismiss")
                }
            }
        }
    }
}

@Composable
private fun GeneratedExerciseRow(exercise: GeneratedWorkoutExercise) {
    Text(
        text = "${exercise.exerciseName}: ${exercise.sets} x ${exercise.prescriptionText()}, ${exercise.restSeconds}s rest",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun GeneratedWorkoutExercise.prescriptionText(): String {
    val base = when (type) {
        GeneratedPrescriptionType.REPS -> "${repsMin}-${repsMax} reps"
        GeneratedPrescriptionType.TIME -> "${durationSeconds}s"
    }
    return if (isUnilateral) "$base each side" else base
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onReport: (reason: AIReportReason, notes: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUserMessage = message.type == MessageType.USER
    var showReportDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUserMessage)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUserMessage) 16.dp else 4.dp,
                bottomEnd = if (isUserMessage) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(LiftrixSpacing.cardPadding)
            ) {
                Text(
                    text = message.content,
                    color = if (isUserMessage)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUserMessage)
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    // Report button for AI messages only
                    if (!isUserMessage) {
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.Outlined.Flag,
                            contentDescription = "Report AI response",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { showReportDialog = true },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    // Report dialog
    if (showReportDialog) {
        AIMessageReportDialog(
            messageId = message.id,
            messageContent = message.content,
            onDismiss = { showReportDialog = false },
            onReport = { reason, notes ->
                onReport(reason, notes)
                showReportDialog = false
            }
        )
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    enabled: Boolean,
    currentLanguage: Language,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { 
                    Text(
                        text = if (currentLanguage == Language.ROMANIAN)
                            "Întreabă despre antrenamentul tău..."
                        else
                            "Ask about your workout...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.Sentences
                ),
                keyboardActions = KeyboardActions(
                    onSend = { 
                        if (text.isNotBlank()) {
                            onSend(text)
                        }
                    }
                ),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.weight(1f)
            )

            if (text.isNotBlank()) {
                Spacer(modifier = Modifier.width(LiftrixSpacing.small))

                FilledIconButton(
                    onClick = {
                        onSend(text)
                    },
                    enabled = enabled,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = if (currentLanguage == Language.ROMANIAN)
                            "Trimite mesaj" else "Send message"
                    )
                }
            }
        }
    }
}

// TypingIndicator component moved to separate file

@Composable
private fun UsageWarningCard(
    limits: UsageLimits?,
    currentLanguage: Language,
    modifier: Modifier = Modifier
) {
    limits?.let {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (it.dailyMessagesRemaining < 5)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(LiftrixSpacing.cardPadding),
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Usage alert",
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = if (currentLanguage == Language.ROMANIAN) "Alertă Utilizare" else "Usage Alert",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = it.getWarningMessage(currentLanguage),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(
    error: com.example.liftrix.domain.model.error.LiftrixError,
    currentLanguage: Language,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.cardPadding)
        ) {
            Text(
                text = if (currentLanguage == Language.ROMANIAN) "Eroare" else "Error",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (error.isRecoverable) {
                    TextButton(onClick = onRetry) {
                        Text(
                            text = if (currentLanguage == Language.ROMANIAN) "Reîncearcă" else "Retry",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = if (currentLanguage == Language.ROMANIAN) "Închide" else "Dismiss",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

/**
 * Helper function to format timestamp for message display.
 */
private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
