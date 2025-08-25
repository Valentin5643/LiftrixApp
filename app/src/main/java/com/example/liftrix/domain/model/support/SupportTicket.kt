package com.example.liftrix.domain.model.support

import java.time.Instant

/**
 * Domain model representing a support ticket
 * Contains user support request information and status tracking
 */
data class SupportTicket(
    val id: String,
    val userId: String,
    val category: SupportCategory,
    val subject: String,
    val description: String,
    val deviceInfo: DeviceInfo? = null,
    val appVersion: String? = null,
    val status: SupportStatus = SupportStatus.OPEN,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val messages: List<SupportTicketMessage> = emptyList(),
    val isSynced: Boolean = false
) {
    /**
     * Checks if the ticket is in an active state
     * @return True if ticket is open or in progress
     */
    fun isActive(): Boolean = status in listOf(SupportStatus.OPEN, SupportStatus.IN_PROGRESS)
    
    /**
     * Checks if the ticket is resolved or closed
     * @return True if ticket is resolved or closed
     */
    fun isResolved(): Boolean = status in listOf(SupportStatus.RESOLVED, SupportStatus.CLOSED)
    
    /**
     * Gets the age of the ticket in days
     * @return Number of days since ticket creation
     */
    fun getAgeInDays(): Long {
        val now = Instant.now()
        return java.time.Duration.between(createdAt, now).toDays()
    }
    
    /**
     * Gets all messages in chronological order (oldest first)
     * @return List of messages sorted by creation time
     */
    fun getMessagesChronologically(): List<SupportTicketMessage> = messages.sortedBy { it.createdAt }
    
    /**
     * Gets the latest message in the conversation
     * @return Most recent message or null if no messages
     */
    fun getLatestMessage(): SupportTicketMessage? = messages.maxByOrNull { it.createdAt }
    
    /**
     * Gets the count of unsynced messages
     * @return Number of messages that haven't been synced
     */
    fun getUnsyncedMessageCount(): Int = messages.count { !it.isSynced }
    
    /**
     * Checks if the ticket has any messages from support
     * @return True if there are messages from support team
     */
    fun hasSupportMessages(): Boolean = messages.any { it.isFromSupport }
    
    /**
     * Gets the total number of messages in the conversation
     * @return Total message count
     */
    fun getMessageCount(): Int = messages.size
    
    /**
     * Adds a new message to the ticket
     * @param message The message to add
     * @return Updated ticket with the new message
     */
    fun addMessage(message: SupportTicketMessage): SupportTicket = copy(
        messages = messages + message,
        updatedAt = Instant.now(),
        isSynced = false
    )
    
    /**
     * Marks the ticket as updated
     * @param newStatus Optional new status for the ticket
     * @return Updated ticket with new status and timestamp
     */
    fun markUpdated(newStatus: SupportStatus? = null): SupportTicket = copy(
        status = newStatus ?: status,
        updatedAt = Instant.now(),
        isSynced = false
    )
}


/**
 * Support ticket status tracking
 */
enum class SupportStatus(val displayName: String, val isActive: Boolean) {
    OPEN("Open", true),
    IN_PROGRESS("In Progress", true),
    WAITING_FOR_USER("Waiting for User", true),
    RESOLVED("Resolved", false),
    CLOSED("Closed", false);
    
    companion object {
        /**
         * Gets all active statuses
         */
        fun getActiveStatuses(): List<SupportStatus> = values().filter { it.isActive }
        
        /**
         * Gets all resolved statuses
         */
        fun getResolvedStatuses(): List<SupportStatus> = values().filter { !it.isActive }
    }
}

/**
 * Device information for support context
 */
data class DeviceInfo(
    val deviceModel: String,
    val androidVersion: String,
    val appVersion: String,
    val buildNumber: String,
    val deviceId: String? = null,
    val screenResolution: String? = null,
    val totalMemoryMB: Long? = null,
    val availableMemoryMB: Long? = null,
    val deviceLanguage: String? = null,
    val networkType: String? = null
) {
    /**
     * Formats device info as a readable string for support agents
     */
    fun formatForSupport(): String = buildString {
        appendLine("Device: $deviceModel")
        appendLine("Android: $androidVersion")
        appendLine("App: $appVersion ($buildNumber)")
        screenResolution?.let { appendLine("Screen: $it") }
        totalMemoryMB?.let { appendLine("Memory: ${it}MB total") }
        availableMemoryMB?.let { appendLine("Available: ${it}MB") }
        deviceLanguage?.let { appendLine("Language: $it") }
        networkType?.let { appendLine("Network: $it") }
    }.trim()
    
    companion object {
        /**
         * Creates device info from system information
         */
        fun fromSystem(
            deviceModel: String,
            androidVersion: String,
            appVersion: String,
            buildNumber: String
        ): DeviceInfo = DeviceInfo(
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            appVersion = appVersion,
            buildNumber = buildNumber
        )
    }
}

/**
 * Support ticket creation request
 */
data class CreateSupportTicketRequest(
    val userId: String,
    val category: SupportCategory,
    val subject: String,
    val description: String,
    val deviceInfo: DeviceInfo? = null,
    val attachments: List<String> = emptyList() // URIs of attached files
) {
    /**
     * Validates the support ticket request
     * @return List of validation errors, empty if valid
     */
    fun validate(): List<String> = buildList {
        if (subject.isBlank()) add("Subject is required")
        if (subject.length < 5) add("Subject must be at least 5 characters")
        if (subject.length > 100) add("Subject must be less than 100 characters")
        
        if (description.isBlank()) add("Description is required")
        if (description.length < 10) add("Description must be at least 10 characters")
        if (description.length > 2000) add("Description must be less than 2000 characters")
        
        if (userId.isBlank()) add("User ID is required")
        
        if (attachments.size > 5) add("Maximum 5 attachments allowed")
    }
    
    /**
     * Checks if the request is valid
     */
    fun isValid(): Boolean = validate().isEmpty()
}