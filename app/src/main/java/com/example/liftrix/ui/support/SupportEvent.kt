package com.example.liftrix.ui.support

import android.net.Uri
import com.example.liftrix.domain.model.support.SupportCategory
import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * Events that can be triggered from the support UI
 */
sealed class SupportEvent : ViewModelEvent {
    /**
     * Load initial support data and user tickets
     */
    data object LoadContent : SupportEvent()
    
    /**
     * Refresh support tickets from remote sources
     */
    data object RefreshTickets : SupportEvent()
    
    /**
     * Update form category
     */
    data class UpdateCategory(val category: SupportCategory) : SupportEvent()
    
    /**
     * Update form subject
     */
    data class UpdateSubject(val subject: String) : SupportEvent()
    
    /**
     * Update form description
     */
    data class UpdateDescription(val description: String) : SupportEvent()
    
    /**
     * Add file attachment
     */
    data class AddAttachment(val uri: Uri) : SupportEvent()
    
    /**
     * Remove file attachment
     */
    data class RemoveAttachment(val uri: Uri) : SupportEvent()
    
    /**
     * Submit support ticket
     */
    data object SubmitTicket : SupportEvent()
    
    /**
     * View existing ticket details
     */
    data class ViewTicket(val ticketId: String) : SupportEvent()
    
    /**
     * Clear form and reset to initial state
     */
    data object ClearForm : SupportEvent()
    
    /**
     * Retry failed operations
     */
    data object Retry : SupportEvent()
    
    /**
     * Validate form fields
     */
    data object ValidateForm : SupportEvent()
}

/**
 * Side effects that should be handled by the UI
 */
sealed class SupportSideEffect {
    /**
     * Navigate to ticket detail screen
     */
    data class NavigateToTicket(val ticketId: String) : SupportSideEffect()
    
    /**
     * Navigate back after successful submission
     */
    data object NavigateBack : SupportSideEffect()
    
    /**
     * Show ticket creation success message
     */
    data class ShowTicketCreated(val ticketId: String) : SupportSideEffect()
    
    /**
     * Show error message
     */
    data class ShowError(val message: String) : SupportSideEffect()
    
    /**
     * Show file picker for attachments
     */
    data object ShowFilePicker : SupportSideEffect()
    
    /**
     * Show confirmation dialog before clearing form
     */
    data object ShowClearFormConfirmation : SupportSideEffect()
    
    /**
     * Copy ticket ID to clipboard
     */
    data class CopyTicketId(val ticketId: String) : SupportSideEffect()
}