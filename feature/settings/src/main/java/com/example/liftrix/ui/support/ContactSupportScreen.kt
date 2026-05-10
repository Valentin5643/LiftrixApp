@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.liftrix.ui.support

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.support.SupportCategory
import com.example.liftrix.ui.common.ContextualColorOverlay
import com.example.liftrix.ui.common.ColorContext
import com.example.liftrix.ui.common.FeedItemShimmer
import com.example.liftrix.ui.common.ShimmerPlaceholder
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.settings.support.SupportViewModel
import com.example.liftrix.ui.settings.support.SupportUiState
import com.example.liftrix.ui.settings.support.TicketForm
import eu.bambooapps.material3.pullrefresh.PullRefreshIndicator
import eu.bambooapps.material3.pullrefresh.pullRefresh
import eu.bambooapps.material3.pullrefresh.rememberPullRefreshState
import timber.log.Timber

/**
 * Support ticket creation screen with comprehensive form validation
 * 
 * Features:
 * - Category selection with predefined options
 * - Subject and description with character limits and validation
 * - File attachment support (up to 5 files)
 * - Device info auto-population for support context
 * - Real-time form validation with error feedback
 * - Ticket history display for existing users
 * - Success confirmation with ticket ID
 * - Pull-to-refresh for ticket history
 * - Accessibility support throughout
 * 
 * @param onNavigateBack Callback to navigate back to previous screen
 * @param onNavigateToTicket Callback to navigate to specific ticket detail
 * @param viewModel SupportViewModel instance for managing state and events
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSupportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTicket: (String) -> Unit,
    showTopBar: Boolean = true,
    viewModel: SupportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            viewModel.handleEvent(SupportEvent.AddAttachment(uri))
        }
    }
    
    // Handle side effects
    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is SupportSideEffect.NavigateToTicket -> {
                    onNavigateToTicket(effect.ticketId)
                }
                is SupportSideEffect.NavigateBack -> {
                    onNavigateBack()
                }
                is SupportSideEffect.ShowTicketCreated -> {
                    // Ticket creation success handled in UI
                    Timber.d("Support ticket created: ${effect.ticketId}")
                }
                is SupportSideEffect.ShowError -> {
                    // Error display handled in UI
                    Timber.e("Support screen error: ${effect.message}")
                }
                is SupportSideEffect.ShowFilePicker -> {
                    filePickerLauncher.launch("*/*")
                }
                is SupportSideEffect.ShowClearFormConfirmation -> {
                    // Show confirmation dialog - handled in UI
                }
                is SupportSideEffect.CopyTicketId -> {
                    clipboardManager.setText(AnnotatedString(effect.ticketId))
                    // Could show snackbar here
                }
                is SupportSideEffect.ShowReplySubmitted -> {
                    // Reply submission success handled in UI
                    Timber.d("Reply submitted to ticket: ${effect.ticketId}")
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                title = { 
                    Text(
                        text = "Contact Support",
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
                    // Clear form action
                    IconButton(
                        onClick = { viewModel.handleEvent(SupportEvent.ClearForm) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear form"
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
        when (uiState) {
            is SupportUiState.Loading -> {
                SupportLoadingContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            
            is SupportUiState.Success -> {
                SupportSuccessContent(
                    data = (uiState as SupportUiState.Success).data,
                    onCategorySelected = { category ->
                        viewModel.handleEvent(SupportEvent.UpdateCategory(category))
                    },
                    onSubjectChanged = { subject ->
                        viewModel.handleEvent(SupportEvent.UpdateSubject(subject))
                    },
                    onDescriptionChanged = { description ->
                        viewModel.handleEvent(SupportEvent.UpdateDescription(description))
                    },
                    onAddAttachment = { uri ->
                        viewModel.handleEvent(SupportEvent.AddAttachment(uri))
                    },
                    onRemoveAttachment = { uri ->
                        viewModel.handleEvent(SupportEvent.RemoveAttachment(uri))
                    },
                    onSubmitTicket = {
                        viewModel.handleEvent(SupportEvent.SubmitTicket)
                    },
                    onRefresh = {
                        viewModel.handleEvent(SupportEvent.RefreshTickets)
                    },
                    onTicketClicked = { ticketId ->
                        viewModel.handleEvent(SupportEvent.ViewTicket(ticketId))
                    },
                    onShowFilePicker = {
                        filePickerLauncher.launch("*/*")
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            
            is SupportUiState.Error -> {
                SupportErrorContent(
                    error = (uiState as SupportUiState.Error).error,
                    data = (uiState as SupportUiState.Error).previousData ?: SupportUiState.Data(),
                    onRetry = {
                        viewModel.handleEvent(SupportEvent.Retry)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            
            is SupportUiState.Empty -> {
                SupportEmptyContent(
                    onRetry = {
                        viewModel.handleEvent(SupportEvent.Retry)
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
 * Loading state content
 */
@Composable
private fun SupportLoadingContent(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form shimmer
        items(6) {
            ShimmerPlaceholder(
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )
        }
    }
}

/**
 * Success state content with form and ticket history
 */
@Composable
private fun SupportSuccessContent(
    data: SupportUiState.Data,
    onCategorySelected: (SupportCategory) -> Unit,
    onSubjectChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onAddAttachment: (Uri) -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    onSubmitTicket: () -> Unit,
    onRefresh: () -> Unit,
    onTicketClicked: (String) -> Unit,
    onShowFilePicker: () -> Unit,
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
            // Ticket creation success message
            if (data.lastCreatedTicketId != null) {
                item {
                    TicketCreatedCard(
                        ticketId = data.lastCreatedTicketId,
                        onTicketClicked = onTicketClicked
                    )
                }
            }
            
            // Active tickets notification
            if (data.hasActiveTickets) {
                item {
                    ActiveTicketsCard(
                        activeCount = data.activeTicketCount,
                        onViewTickets = { /* Navigate to ticket history */ }
                    )
                }
            }
            
            // Support form
            item {
                SupportFormCard(
                    form = data.ticketForm,
                    validationErrors = data.validationErrors,
                    deviceInfo = data.deviceInfo,
                    isSubmitting = data.isSubmitting,
                    onCategorySelected = onCategorySelected,
                    onSubjectChanged = onSubjectChanged,
                    onDescriptionChanged = onDescriptionChanged,
                    onAddAttachment = onShowFilePicker,
                    onRemoveAttachment = onRemoveAttachment,
                    onSubmitTicket = onSubmitTicket,
                    isFormValid = data.isFormValid
                )
            }
            
            // Recent tickets
            if (data.userTickets.isNotEmpty()) {
                item {
                    Text(
                        text = "Your Recent Tickets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(data.userTickets.take(5)) { ticket ->
                    TicketCard(
                        ticket = ticket,
                        onClick = { onTicketClicked(ticket.id) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
 * Ticket creation success card
 */
@Composable
private fun TicketCreatedCard(
    ticketId: String,
    onTicketClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ContextualColorOverlay(
        context = ColorContext.UserPreference(
            preferredColor = Color(0xFF10B981), // Success green
            intensity = 0.3f
        ),
        modifier = modifier
    ) {
        LiftrixCard(
            onClick = { onTicketClicked(ticketId) },
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
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Ticket created",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ticket Created Successfully",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Text(
                    text = "Your support ticket has been created. Ticket ID: $ticketId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "We'll get back to you as soon as possible. You can track your ticket status here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Active tickets notification card
 */
@Composable
private fun ActiveTicketsCard(
    activeCount: Int,
    onViewTickets: () -> Unit,
    modifier: Modifier = Modifier
) {
    ContextualColorOverlay(
        context = ColorContext.UserPreference(
            preferredColor = Color(0xFFF59E0B), // Warning orange
            intensity = 0.3f
        ),
        modifier = modifier
    ) {
        LiftrixCard(
            onClick = onViewTickets,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Active tickets info",
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "You have $activeCount active ${if (activeCount == 1) "ticket" else "tickets"}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "View your ticket status and updates",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View tickets",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Main support form card
 */
@Composable
private fun SupportFormCard(
    form: TicketForm,
    validationErrors: Map<String, String>,
    deviceInfo: com.example.liftrix.domain.model.support.DeviceInfo?,
    isSubmitting: Boolean,
    onCategorySelected: (SupportCategory) -> Unit,
    onSubjectChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onAddAttachment: () -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    onSubmitTicket: () -> Unit,
    isFormValid: Boolean,
    modifier: Modifier = Modifier
) {
    ElevatedLiftrixCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Create Support Ticket",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            // Category selection
            CategorySelectionSection(
                selectedCategory = form.category,
                onCategorySelected = onCategorySelected,
                error = validationErrors["category"]
            )
            
            // Subject field
            SubjectField(
                subject = form.subject,
                onSubjectChanged = onSubjectChanged,
                error = validationErrors["subject"],
                isApproachingLimit = form.isSubjectApproachingLimit(),
                characterCount = form.getSubjectCharacterCount()
            )
            
            // Description field
            DescriptionField(
                description = form.description,
                onDescriptionChanged = onDescriptionChanged,
                error = validationErrors["description"],
                isApproachingLimit = form.isDescriptionApproachingLimit(),
                characterCount = form.getDescriptionCharacterCount()
            )
            
            // Attachments section
            AttachmentsSection(
                attachments = form.attachments,
                onAddAttachment = onAddAttachment,
                onRemoveAttachment = onRemoveAttachment,
                error = validationErrors["attachments"]
            )
            
            // Device info section
            if (deviceInfo != null) {
                DeviceInfoSection(deviceInfo = deviceInfo)
            }
            
            // Submit button
            Button(
                onClick = onSubmitTicket,
                enabled = isFormValid && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Submit Ticket")
                }
            }
        }
    }
}

/**
 * Category selection section
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelectionSection(
    selectedCategory: SupportCategory,
    onCategorySelected: (SupportCategory) -> Unit,
    error: String?,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Category *",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedCategory.displayName,
                onValueChange = { },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                isError = error != null
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                SupportCategory.values().forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = category.displayName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = category.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onCategorySelected(category)
                            expanded = false
                        }
                    )
                }
            }
        }
        
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Subject input field
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectField(
    subject: String,
    onSubjectChanged: (String) -> Unit,
    error: String?,
    isApproachingLimit: Boolean,
    characterCount: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Subject *",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = characterCount,
                style = MaterialTheme.typography.labelSmall,
                color = if (isApproachingLimit) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        
        OutlinedTextField(
            value = subject,
            onValueChange = onSubjectChanged,
            placeholder = { Text("Brief description of your issue") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = error != null
        )
        
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Description input field
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DescriptionField(
    description: String,
    onDescriptionChanged: (String) -> Unit,
    error: String?,
    isApproachingLimit: Boolean,
    characterCount: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Description *",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = characterCount,
                style = MaterialTheme.typography.labelSmall,
                color = if (isApproachingLimit) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChanged,
            placeholder = { Text("Please describe your issue in detail...") },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
            isError = error != null
        )
        
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Attachments section
 */
@Composable
private fun AttachmentsSection(
    attachments: List<Uri>,
    onAddAttachment: () -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    error: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Attachments (${attachments.size}/5)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            OutlinedButton(
                onClick = onAddAttachment,
                enabled = attachments.size < 5
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Add attachment",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add File")
            }
        }
        
        if (attachments.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(attachments) { uri ->
                    AttachmentChip(
                        uri = uri,
                        onRemove = { onRemoveAttachment(uri) }
                    )
                }
            }
        }
        
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Individual attachment chip
 */
@Composable
private fun AttachmentChip(
    uri: Uri,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    InputChip(
        onClick = { },
        label = {
            Text(
                text = uri.lastPathSegment ?: "File",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        selected = false,
        trailingIcon = {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove attachment",
                    modifier = Modifier.size(12.dp)
                )
            }
        },
        modifier = modifier
    )
}

/**
 * Device info section
 */
@Composable
private fun DeviceInfoSection(
    deviceInfo: com.example.liftrix.domain.model.support.DeviceInfo,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Device Information",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide" else "Show")
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Hide device information" else "Show device information"
                )
            }
        }
        
        if (expanded) {
            ElevatedLiftrixCard {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = deviceInfo.formatForSupport(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
        
        Text(
            text = "This information helps our support team troubleshoot your issue",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Individual ticket card
 */
@Composable
private fun TicketCard(
    ticket: com.example.liftrix.domain.model.support.SupportTicket,
    onClick: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ticket.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                StatusChip(status = ticket.status)
            }
            
            Text(
                text = ticket.category.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Created ${ticket.getAgeInDays()} days ago",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Status chip for tickets
 */
@Composable
private fun StatusChip(
    status: com.example.liftrix.domain.model.support.SupportStatus,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = when (status) {
        com.example.liftrix.domain.model.support.SupportStatus.OPEN -> 
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        com.example.liftrix.domain.model.support.SupportStatus.IN_PROGRESS -> 
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        com.example.liftrix.domain.model.support.SupportStatus.WAITING_FOR_USER -> 
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        com.example.liftrix.domain.model.support.SupportStatus.RESOLVED -> 
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        com.example.liftrix.domain.model.support.SupportStatus.CLOSED -> 
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    AssistChip(
        onClick = { },
        label = {
            Text(
                text = status.displayName,
                style = MaterialTheme.typography.labelSmall
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = contentColor
        ),
        modifier = modifier
    )
}

/**
 * Error state content
 */
@Composable
private fun SupportErrorContent(
    error: com.example.liftrix.domain.model.error.LiftrixError,
    data: SupportUiState.Data,
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
            text = "Failed to load support content",
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
private fun SupportEmptyContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SupportAgent,
            contentDescription = "Support unavailable",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "Support service unavailable",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Please try again later",
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
 * Preview for support screen
 */
@Preview(showBackground = true)
@Composable
private fun ContactSupportScreenPreview() {
    LiftrixTheme {
        // Preview would need mock data
    }
}
