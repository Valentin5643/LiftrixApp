package com.example.liftrix.ui.settings.support

import android.net.Uri
import androidx.compose.runtime.Stable
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.support.CreateSupportTicketRequest
import com.example.liftrix.domain.model.support.DeviceInfo
import com.example.liftrix.domain.model.support.SupportCategory
import com.example.liftrix.domain.model.support.SupportTicket
import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * UI state for support ticket creation and management
 */
@Stable
sealed class SupportUiState {
    
    /**
     * Data class containing all support-related state
     */
    @Stable
    data class Data(
        val ticketForm: TicketForm = TicketForm(),
        val deviceInfo: DeviceInfo? = null,
        val userTickets: List<SupportTicket> = emptyList(),
        val isSubmitting: Boolean = false,
        val isLoadingTickets: Boolean = false,
        val isRefreshing: Boolean = false,
        val validationErrors: Map<String, String> = emptyMap(),
        val lastCreatedTicketId: String? = null
    ) {
        /**
         * Checks if the current form is valid for submission
         */
        val isFormValid: Boolean
            get() = ticketForm.isValid() && validationErrors.isEmpty()
        
        /**
         * Gets the current support ticket creation request
         */
        val supportRequest: CreateSupportTicketRequest?
            get() = if (ticketForm.isValid()) {
                CreateSupportTicketRequest(
                    userId = ticketForm.userId,
                    category = ticketForm.category,
                    subject = ticketForm.subject,
                    description = ticketForm.description,
                    deviceInfo = deviceInfo,
                    attachments = ticketForm.attachments.map { it.toString() }
                )
            } else null
        
        /**
         * Checks if there are active tickets
         */
        val hasActiveTickets: Boolean
            get() = userTickets.any { it.isActive() }
        
        /**
         * Gets the count of active tickets
         */
        val activeTicketCount: Int
            get() = userTickets.count { it.isActive() }
    }
    
    /**
     * Loading state while fetching support data
     */
    @Stable
    data object Loading : SupportUiState()
    
    /**
     * Success state with support data
     */
    @Stable
    data class Success(
        val data: Data,
        val isRefreshing: Boolean = false
    ) : SupportUiState()
    
    /**
     * Error state with failure information
     */
    @Stable
    data class Error(
        val error: LiftrixError,
        val previousData: Data? = null
    ) : SupportUiState()
    
    /**
     * Empty state when no support data is available
     */
    @Stable
    data class Empty(
        val message: String = "No support tickets available",
        val actionText: String? = "Create Ticket",
        val showAction: Boolean = true
    ) : SupportUiState()
}

/**
 * Form data for creating a support ticket
 */
@Stable
data class TicketForm(
    val userId: String = "",
    val category: SupportCategory = SupportCategory.GENERAL_QUESTION,
    val subject: String = "",
    val description: String = "",
    val attachments: List<Uri> = emptyList()
) {
    /**
     * Validates the form data
     */
    fun validate(): Map<String, String> = buildMap {
        if (subject.isBlank()) put("subject", "Subject is required")
        else if (subject.length < 5) put("subject", "Subject must be at least 5 characters")
        else if (subject.length > 100) put("subject", "Subject must be less than 100 characters")
        
        if (description.isBlank()) put("description", "Description is required")
        else if (description.length < 10) put("description", "Description must be at least 10 characters")
        else if (description.length > 2000) put("description", "Description must be less than 2000 characters")
        
        if (userId.isBlank()) put("userId", "User ID is required")
        
        if (attachments.size > 5) put("attachments", "Maximum 5 attachments allowed")
    }
    
    /**
     * Checks if the form is valid
     */
    fun isValid(): Boolean = validate().isEmpty()
    
    /**
     * Gets character count for subject field
     */
    fun getSubjectCharacterCount(): String = "${subject.length}/100"
    
    /**
     * Gets character count for description field  
     */
    fun getDescriptionCharacterCount(): String = "${description.length}/2000"
    
    /**
     * Checks if subject is approaching limit
     */
    fun isSubjectApproachingLimit(): Boolean = subject.length > 80
    
    /**
     * Checks if description is approaching limit
     */
    fun isDescriptionApproachingLimit(): Boolean = description.length > 1800
}

