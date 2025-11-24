package com.example.liftrix.ui.settings.support

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.support.SupportCategory
import com.example.liftrix.domain.service.AppInfoService
import com.example.liftrix.domain.service.SupportService
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.support.AddReplyToSupportTicketUseCase
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.ui.support.SupportEvent
import com.example.liftrix.ui.support.SupportSideEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for support ticket creation and management implementing modernized MVI pattern
 *
 * Features:
 * - Form validation with real-time feedback
 * - File attachment management (up to 5 files)
 * - Device info auto-population for support context
 * - User ticket history and status tracking
 * - Error handling with retry capabilities
 * - Offline support with sync capabilities
 */
@HiltViewModel
class SupportViewModel @Inject constructor(
    private val supportService: SupportService,
    private val appInfoService: AppInfoService,
    private val authQueryUseCase: AuthQueryUseCase,
    private val addReplyToSupportTicketUseCase: AddReplyToSupportTicketUseCase
) : ModernBaseViewModel<SupportUiState>(initialState = SupportUiState.Loading) {
    
    private val _sideEffects = MutableSharedFlow<SupportSideEffect>()
    val sideEffects = _sideEffects.asSharedFlow()
    
    init {
        // Load initial content when ViewModel is created
        loadContent()
    }

    /**
     * Handles events from the UI layer using MVI pattern
     */
    fun handleEvent(event: SupportEvent) {
        when (event) {
            is SupportEvent.LoadContent -> loadContent()
            is SupportEvent.RefreshTickets -> refreshTickets()
            is SupportEvent.UpdateCategory -> updateCategory(event.category)
            is SupportEvent.UpdateSubject -> updateSubject(event.subject)
            is SupportEvent.UpdateDescription -> updateDescription(event.description)
            is SupportEvent.AddAttachment -> addAttachment(event.uri)
            is SupportEvent.RemoveAttachment -> removeAttachment(event.uri)
            is SupportEvent.SubmitTicket -> submitTicket()
            is SupportEvent.ViewTicket -> viewTicket(event.ticketId)
            is SupportEvent.AddReply -> addReply(event.ticketId, event.content, event.attachments)
            is SupportEvent.ClearForm -> clearForm()
            is SupportEvent.Retry -> retry()
            is SupportEvent.ValidateForm -> validateForm()
        }
    }

    /**
     * Loads initial support content including device info and user tickets
     */
    fun loadContent() {
        viewModelScope.launch {
            try {
                // Get current user ID
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { throw LiftrixError.AuthenticationError("User not authenticated") }
                )

                // Get device info for support context
                val deviceInfo = appInfoService.getDeviceInfo()

                // Get user's existing tickets
                val userTickets = supportService.getUserTickets(userId).fold(
                    onSuccess = { it },
                    onFailure = { emptyList() }
                )

                val data = SupportUiState.Data(
                    ticketForm = TicketForm(userId = userId),
                    deviceInfo = deviceInfo,
                    userTickets = userTickets
                )

                if (data.userTickets.isEmpty() && data.deviceInfo == null) {
                    updateState { SupportUiState.Empty() }
                } else {
                    updateState { SupportUiState.Success(data) }
                }
                Timber.d("Support content loaded: ${data.userTickets.size} tickets")
            } catch (e: Exception) {
                val error = if (e is LiftrixError) e else LiftrixError.BusinessLogicError(
                    code = "LOAD_CONTENT_FAILED",
                    errorMessage = e.message ?: "Failed to load support content",
                    analyticsContext = mapOf("operation" to "LOAD_SUPPORT_CONTENT")
                )
                updateState { SupportUiState.Error(error) }
                Timber.e("Failed to load support content: $error")
            }
        }
    }
    
    /**
     * Refreshes user tickets from remote sources
     */
    fun refreshTickets() {
        val currentData = getCurrentData()
        updateState { SupportUiState.Success(currentData.copy(isRefreshing = true)) }

        viewModelScope.launch {
            try {
                // Sync tickets with remote
                supportService.syncTickets(currentData.ticketForm.userId).fold(
                    onSuccess = { /* Success */ },
                    onFailure = { error -> throw error }
                )

                // Fetch updated ticket list
                val updatedTickets = supportService.getUserTickets(currentData.ticketForm.userId).fold(
                    onSuccess = { it },
                    onFailure = { emptyList() }
                )

                val data = currentData.copy(
                    userTickets = updatedTickets,
                    isRefreshing = false
                )

                updateState { SupportUiState.Success(data) }
                Timber.d("Support tickets refreshed: ${data.userTickets.size} tickets")
            } catch (e: Exception) {
                val error = if (e is LiftrixError) e else LiftrixError.BusinessLogicError(
                    code = "REFRESH_TICKETS_FAILED",
                    errorMessage = e.message ?: "Failed to refresh tickets",
                    analyticsContext = mapOf("operation" to "REFRESH_SUPPORT_TICKETS")
                )
                updateState {
                    SupportUiState.Error(error, currentData.copy(isRefreshing = false))
                }
                emitSideEffect(SupportSideEffect.ShowError("Failed to refresh tickets"))
                Timber.e("Failed to refresh support tickets: $error")
            }
        }
    }
    
    /**
     * Updates the selected support category
     */
    fun updateCategory(category: SupportCategory) {
        val currentData = getCurrentData()
        val updatedForm = currentData.ticketForm.copy(category = category)
        updateState { 
            SupportUiState.Success(currentData.copy(
                ticketForm = updatedForm,
                validationErrors = currentData.validationErrors - "category"
            ))
        }
        Timber.d("Updated support category to: ${category.displayName}")
    }
    
    /**
     * Updates the ticket subject with validation
     */
    fun updateSubject(subject: String) {
        val currentData = getCurrentData()
        val updatedForm = currentData.ticketForm.copy(subject = subject)
        val errors = updatedForm.validate()
        
        updateState { 
            SupportUiState.Success(currentData.copy(
                ticketForm = updatedForm,
                validationErrors = if (errors.containsKey("subject")) {
                    currentData.validationErrors + ("subject" to errors["subject"]!!)
                } else {
                    currentData.validationErrors - "subject"
                }
            ))
        }
    }
    
    /**
     * Updates the ticket description with validation
     */
    fun updateDescription(description: String) {
        val currentData = getCurrentData()
        val updatedForm = currentData.ticketForm.copy(description = description)
        val errors = updatedForm.validate()
        
        updateState { 
            SupportUiState.Success(currentData.copy(
                ticketForm = updatedForm,
                validationErrors = if (errors.containsKey("description")) {
                    currentData.validationErrors + ("description" to errors["description"]!!)
                } else {
                    currentData.validationErrors - "description"
                }
            ))
        }
    }
    
    /**
     * Adds a file attachment to the ticket
     */
    fun addAttachment(uri: Uri) {
        val currentData = getCurrentData()
        val currentAttachments = currentData.ticketForm.attachments
        
        if (currentAttachments.size >= 5) {
            emitSideEffect(SupportSideEffect.ShowError("Maximum 5 attachments allowed"))
            return
        }
        
        if (currentAttachments.contains(uri)) {
            emitSideEffect(SupportSideEffect.ShowError("File already attached"))
            return
        }
        
        val updatedForm = currentData.ticketForm.copy(
            attachments = currentAttachments + uri
        )
        
        updateState { 
            SupportUiState.Success(currentData.copy(
                ticketForm = updatedForm,
                validationErrors = currentData.validationErrors - "attachments"
            ))
        }
        
        Timber.d("Added attachment: $uri")
    }
    
    /**
     * Removes a file attachment from the ticket
     */
    fun removeAttachment(uri: Uri) {
        val currentData = getCurrentData()
        val updatedForm = currentData.ticketForm.copy(
            attachments = currentData.ticketForm.attachments - uri
        )
        
        updateState { 
            SupportUiState.Success(currentData.copy(ticketForm = updatedForm))
        }
        
        Timber.d("Removed attachment: $uri")
    }
    
    /**
     * Submits the support ticket
     */
    fun submitTicket() {
        val currentData = getCurrentData()
        
        // Validate form first
        val validationErrors = currentData.ticketForm.validate()
        if (validationErrors.isNotEmpty()) {
            updateState { 
                SupportUiState.Success(currentData.copy(validationErrors = validationErrors))
            }
            emitSideEffect(SupportSideEffect.ShowError("Please fix form errors before submitting"))
            return
        }
        
        updateState { SupportUiState.Success(currentData.copy(isSubmitting = true)) }

        viewModelScope.launch {
            try {
                val request = currentData.supportRequest
                    ?: throw LiftrixError.ValidationError(
                        field = "form",
                        violations = listOf("Invalid form data"),
                        analyticsContext = mapOf("operation" to "SUBMIT_SUPPORT_TICKET")
                    )

                // Create the support ticket
                val ticketId = supportService.createTicket(request).fold(
                    onSuccess = { it },
                    onFailure = { error -> throw error }
                )

                // Return updated data with ticket ID
                val data = currentData.copy(
                    isSubmitting = false,
                    lastCreatedTicketId = ticketId,
                    ticketForm = TicketForm(userId = currentData.ticketForm.userId) // Reset form
                )

                updateState { SupportUiState.Success(data) }

                emitSideEffect(SupportSideEffect.ShowTicketCreated(ticketId))
                emitSideEffect(SupportSideEffect.CopyTicketId(ticketId))

                Timber.d("Support ticket created successfully: $ticketId")

                // Refresh tickets to show the new one
                refreshTickets()
            } catch (e: Exception) {
                val error = if (e is LiftrixError) e else LiftrixError.BusinessLogicError(
                    code = "SUBMIT_TICKET_FAILED",
                    errorMessage = e.message ?: "Failed to create support ticket",
                    analyticsContext = mapOf("operation" to "SUBMIT_SUPPORT_TICKET")
                )
                updateState {
                    SupportUiState.Error(error, currentData.copy(isSubmitting = false))
                }
                emitSideEffect(SupportSideEffect.ShowError("Failed to create support ticket"))
                Timber.e("Failed to create support ticket: $error")
            }
        }
    }
    
    /**
     * Views a specific ticket detail
     */
    fun viewTicket(ticketId: String) {
        emitSideEffect(SupportSideEffect.NavigateToTicket(ticketId))
        Timber.d("Navigating to ticket: $ticketId")
    }
    
    /**
     * Adds a reply to an existing support ticket
     */
    fun addReply(ticketId: String, content: String, attachments: List<String>) {
        val currentData = getCurrentData()
        updateState { SupportUiState.Success(currentData.copy(isSubmitting = true)) }

        viewModelScope.launch {
            try {
                addReplyToSupportTicketUseCase(
                    ticketId = ticketId,
                    content = content,
                    attachments = attachments
                ).fold(
                    onSuccess = { /* Success */ },
                    onFailure = { error -> throw error }
                )

                // Refresh tickets to show the new reply
                val updatedTickets = supportService.getUserTickets(currentData.ticketForm.userId).fold(
                    onSuccess = { it },
                    onFailure = { emptyList() }
                )

                val data = currentData.copy(
                    userTickets = updatedTickets,
                    isSubmitting = false
                )

                updateState { SupportUiState.Success(data) }
                emitSideEffect(SupportSideEffect.ShowReplySubmitted(ticketId))
                Timber.d("Reply added successfully to ticket: $ticketId")
            } catch (e: Exception) {
                val error = if (e is LiftrixError) e else LiftrixError.BusinessLogicError(
                    code = "ADD_REPLY_FAILED",
                    errorMessage = e.message ?: "Failed to submit reply",
                    analyticsContext = mapOf("operation" to "ADD_REPLY_TO_TICKET", "ticketId" to ticketId)
                )
                updateState {
                    SupportUiState.Error(error, currentData.copy(isSubmitting = false))
                }
                emitSideEffect(SupportSideEffect.ShowError("Failed to submit reply"))
                Timber.e("Failed to add reply to ticket $ticketId: $error")
            }
        }
    }
    
    /**
     * Clears the form and resets to initial state
     */
    fun clearForm() {
        val currentData = getCurrentData()
        
        if (currentData.ticketForm.subject.isNotBlank() || 
            currentData.ticketForm.description.isNotBlank() ||
            currentData.ticketForm.attachments.isNotEmpty()) {
            emitSideEffect(SupportSideEffect.ShowClearFormConfirmation)
        } else {
            performClearForm()
        }
    }
    
    /**
     * Actually clears the form after confirmation
     */
    fun performClearForm() {
        val currentData = getCurrentData()
        val clearedForm = TicketForm(userId = currentData.ticketForm.userId)
        
        updateState { 
            SupportUiState.Success(currentData.copy(
                ticketForm = clearedForm,
                validationErrors = emptyMap()
            ))
        }
        
        Timber.d("Support form cleared")
    }
    
    /**
     * Validates the current form
     */
    fun validateForm() {
        val currentData = getCurrentData()
        val validationErrors = currentData.ticketForm.validate()
        
        updateState { 
            SupportUiState.Success(currentData.copy(validationErrors = validationErrors))
        }
    }
    
    /**
     * Retries the last failed operation
     */
    fun retry() {
        when (val currentState = uiState.value) {
            is SupportUiState.Error -> {
                if (currentState.previousData?.userTickets?.isEmpty() != false) {
                    // Retry initial load
                    loadContent()
                } else {
                    // Retry refresh or submission based on state
                    if (currentState.previousData?.isSubmitting == true) {
                        submitTicket()
                    } else {
                        refreshTickets()
                    }
                }
            }
            is SupportUiState.Empty -> loadContent()
            else -> {
                // Nothing to retry
                Timber.d("No failed operation to retry")
            }
        }
    }
    
    /**
     * Gets current data from state or returns default
     */
    private fun getCurrentData(): SupportUiState.Data {
        return when (val currentState = uiState.value) {
            is SupportUiState.Success -> currentState.data
            is SupportUiState.Error -> currentState.previousData ?: SupportUiState.Data()
            else -> SupportUiState.Data()
        }
    }
    
    /**
     * Emits a side effect to be handled by the UI
     */
    private fun emitSideEffect(effect: SupportSideEffect) {
        viewModelScope.launch {
            _sideEffects.emit(effect)
        }
    }
}