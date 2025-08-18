package com.example.liftrix.domain.model.support

/**
 * Represents different categories of support requests and issues
 * Used for ticket routing, priority assignment, and analytics
 */
enum class SupportCategory(
    val displayName: String,
    val description: String,
    val iconName: String,
    val priority: SupportPriority,
    val estimatedResponseTime: Int, // in hours
    val requiresSpecialist: Boolean = false
) {
    /**
     * General questions not fitting other categories
     */
    GENERAL_QUESTION(
        displayName = "General Question",
        description = "General inquiries about the app",
        iconName = "help",
        priority = SupportPriority.LOW,
        estimatedResponseTime = 24
    ),
    
    /**
     * Issues with account creation, login, password reset
     */
    ACCOUNT_ACCESS(
        displayName = "Account & Login",
        description = "Account access and authentication issues",
        iconName = "account",
        priority = SupportPriority.HIGH,
        estimatedResponseTime = 4,
        requiresSpecialist = true
    ),
    
    /**
     * Data synchronization problems between devices
     */
    DATA_SYNC(
        displayName = "Data Sync",
        description = "Issues with data synchronization across devices",
        iconName = "sync",
        priority = SupportPriority.MEDIUM,
        estimatedResponseTime = 12,
        requiresSpecialist = true
    ),
    
    /**
     * App crashes, freezes, or performance issues
     */
    TECHNICAL_ISSUE(
        displayName = "Technical Issue",
        description = "App crashes, bugs, or performance problems",
        iconName = "bug_report",
        priority = SupportPriority.HIGH,
        estimatedResponseTime = 8,
        requiresSpecialist = true
    ),
    
    /**
     * Billing, subscription, or payment related issues
     */
    BILLING_PAYMENT(
        displayName = "Billing & Payment",
        description = "Subscription and payment related issues",
        iconName = "payment",
        priority = SupportPriority.HIGH,
        estimatedResponseTime = 6,
        requiresSpecialist = true
    ),
    
    /**
     * Feature requests and suggestions
     */
    FEATURE_REQUEST(
        displayName = "Feature Request",
        description = "Suggestions for new features or improvements",
        iconName = "lightbulb",
        priority = SupportPriority.LOW,
        estimatedResponseTime = 48
    ),
    
    /**
     * Privacy concerns and data handling questions
     */
    PRIVACY_SECURITY(
        displayName = "Privacy & Security",
        description = "Privacy concerns and security questions",
        iconName = "security",
        priority = SupportPriority.MEDIUM,
        estimatedResponseTime = 8,
        requiresSpecialist = true
    ),
    
    /**
     * Issues with workout tracking functionality
     */
    WORKOUT_TRACKING(
        displayName = "Workout Tracking",
        description = "Problems with workout logging and tracking",
        iconName = "fitness_center",
        priority = SupportPriority.MEDIUM,
        estimatedResponseTime = 12
    ),
    
    /**
     * Problems with social features like gym buddies
     */
    SOCIAL_FEATURES(
        displayName = "Social Features",
        description = "Issues with gym buddies and social interactions",
        iconName = "people",
        priority = SupportPriority.MEDIUM,
        estimatedResponseTime = 16
    ),
    
    /**
     * Export/import functionality issues
     */
    DATA_EXPORT_IMPORT(
        displayName = "Data Export/Import",
        description = "Issues with data import or export functionality",
        iconName = "import_export",
        priority = SupportPriority.MEDIUM,
        estimatedResponseTime = 16,
        requiresSpecialist = true
    ),
    
    /**
     * Apple Health, Google Fit integration issues
     */
    THIRD_PARTY_INTEGRATION(
        displayName = "Third-party Integration",
        description = "Issues with external app integrations",
        iconName = "link",
        priority = SupportPriority.MEDIUM,
        estimatedResponseTime = 20,
        requiresSpecialist = true
    ),
    
    /**
     * Inappropriate content or user behavior
     */
    REPORT_ABUSE(
        displayName = "Report Abuse",
        description = "Report inappropriate content or behavior",
        iconName = "report",
        priority = SupportPriority.CRITICAL,
        estimatedResponseTime = 2,
        requiresSpecialist = true
    ),
    
    /**
     * Other issues not covered by above categories
     */
    OTHER(
        displayName = "Other",
        description = "Other issues not covered by the categories above",
        iconName = "more_horiz",
        priority = SupportPriority.LOW,
        estimatedResponseTime = 24
    );
    
    /**
     * Gets the support level required for this category
     */
    fun getSupportLevel(): SupportLevel = when {
        requiresSpecialist -> SupportLevel.SPECIALIST
        priority == SupportPriority.CRITICAL -> SupportLevel.SENIOR
        priority == SupportPriority.HIGH -> SupportLevel.INTERMEDIATE
        else -> SupportLevel.BASIC
    }
    
    /**
     * Gets the maximum auto-escalation time for this category
     * After this time without response, ticket is escalated
     */
    fun getAutoEscalationTime(): Int = when (priority) {
        SupportPriority.CRITICAL -> 1 // 1 hour
        SupportPriority.HIGH -> 6 // 6 hours
        SupportPriority.MEDIUM -> 24 // 1 day
        SupportPriority.LOW -> 72 // 3 days
    }
    
    /**
     * Gets follow-up questions that might be helpful for this category
     */
    fun getFollowUpQuestions(): List<String> = when (this) {
        ACCOUNT_ACCESS -> listOf(
            "What error message are you seeing?",
            "Are you using the correct email address?",
            "Have you tried resetting your password?"
        )
        
        TECHNICAL_ISSUE -> listOf(
            "What device are you using?",
            "What version of the app?",
            "When does the issue occur?",
            "Can you reproduce the issue consistently?"
        )
        
        DATA_SYNC -> listOf(
            "Are you signed in on multiple devices?",
            "When did you last see your data sync correctly?",
            "Are you connected to the internet?"
        )
        
        BILLING_PAYMENT -> listOf(
            "What subscription plan do you have?",
            "When was your last successful payment?",
            "What payment method are you using?"
        )
        
        WORKOUT_TRACKING -> listOf(
            "What specific exercise or workout?",
            "What data is missing or incorrect?",
            "Were you using any integrations?"
        )
        
        else -> listOf(
            "Can you provide more details about the issue?",
            "When did this problem start?",
            "Have you tried restarting the app?"
        )
    }
    
    /**
     * Gets suggested resolution steps for this category
     */
    fun getSuggestedResolutions(): List<String> = when (this) {
        ACCOUNT_ACCESS -> listOf(
            "Try resetting your password",
            "Check your email for verification links",
            "Ensure you're using the correct email address"
        )
        
        TECHNICAL_ISSUE -> listOf(
            "Force close and restart the app",
            "Update to the latest app version",
            "Restart your device",
            "Check available storage space"
        )
        
        DATA_SYNC -> listOf(
            "Check your internet connection",
            "Sign out and sign back in",
            "Force sync in settings",
            "Update the app to latest version"
        )
        
        else -> listOf(
            "Update to the latest app version",
            "Restart the app",
            "Check your internet connection"
        )
    }
    
    companion object {
        /**
         * Gets categories that require immediate attention
         */
        fun getCriticalCategories(): List<SupportCategory> = values().filter { 
            it.priority == SupportPriority.CRITICAL 
        }
        
        /**
         * Gets categories that require specialist handling
         */
        fun getSpecialistCategories(): List<SupportCategory> = values().filter { 
            it.requiresSpecialist 
        }
        
        /**
         * Gets categories ordered by priority (most urgent first)
         */
        fun getByPriority(): List<SupportCategory> = values().sortedBy { it.priority.ordinal }
        
        /**
         * Gets categories suitable for self-service resolution
         */
        fun getSelfServiceCategories(): List<SupportCategory> = values().filter { 
            !it.requiresSpecialist && it.priority != SupportPriority.CRITICAL 
        }
        
        /**
         * Gets the most common categories for quick access
         */
        fun getCommonCategories(): List<SupportCategory> = listOf(
            GENERAL_QUESTION,
            TECHNICAL_ISSUE,
            ACCOUNT_ACCESS,
            WORKOUT_TRACKING,
            DATA_SYNC
        )
    }
}

/**
 * Priority levels for support requests
 */
enum class SupportPriority(val displayName: String, val colorCode: String) {
    CRITICAL("Critical", "#FF5722"), // Red
    HIGH("High", "#FF9800"),        // Orange
    MEDIUM("Medium", "#FFC107"),    // Yellow
    LOW("Low", "#4CAF50")           // Green
}

/**
 * Support team levels for ticket routing
 */
enum class SupportLevel(val displayName: String) {
    BASIC("Basic Support"),
    INTERMEDIATE("Intermediate Support"),
    SENIOR("Senior Support"),
    SPECIALIST("Specialist Support")
}