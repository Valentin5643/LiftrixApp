package com.example.liftrix.data.migration

import com.example.liftrix.migration.MigrationStatus
import com.example.liftrix.migration.TerminologyPreference
import java.time.Instant

/**
 * Data class representing user migration state and preferences
 * Used for tracking terminology migration progress and user choices
 */
data class UserPreferenceMigration(
    val userId: String,
    val migrationStatus: MigrationStatus,
    val terminologyPreference: TerminologyPreference,
    val migrationStartedAt: Instant?,
    val migrationCompletedAt: Instant?,
    val explanationShownAt: Instant?
) {
    
    val isNewUser: Boolean
        get() = migrationStartedAt == null && migrationStatus == MigrationStatus.COMPLETED
    
    val needsMigrationDialog: Boolean
        get() = migrationStatus == MigrationStatus.PENDING
    
    val isUsingLegacyTerminology: Boolean
        get() = terminologyPreference == TerminologyPreference.LEGACY
    
    companion object {
        /**
         * Creates migration data for a new user (no migration needed)
         */
        fun forNewUser(userId: String): UserPreferenceMigration = UserPreferenceMigration(
            userId = userId,
            migrationStatus = MigrationStatus.COMPLETED,
            terminologyPreference = TerminologyPreference.NEW,
            migrationStartedAt = null,
            migrationCompletedAt = Instant.now(),
            explanationShownAt = null
        )
        
        /**
         * Creates migration data for an existing user (migration pending)
         */
        fun forExistingUser(userId: String): UserPreferenceMigration = UserPreferenceMigration(
            userId = userId,
            migrationStatus = MigrationStatus.PENDING,
            terminologyPreference = TerminologyPreference.NEW,
            migrationStartedAt = null,
            migrationCompletedAt = null,
            explanationShownAt = null
        )
    }
}

/**
 * Migration tracking events for analytics
 */
sealed class MigrationEvent {
    data class Started(
        val userId: String,
        val timestamp: Instant = Instant.now()
    ) : MigrationEvent()
    
    data class ExplanationShown(
        val userId: String,
        val timestamp: Instant = Instant.now()
    ) : MigrationEvent()
    
    data class PreferenceSelected(
        val userId: String,
        val preference: TerminologyPreference,
        val timestamp: Instant = Instant.now()
    ) : MigrationEvent()
    
    data class Completed(
        val userId: String,
        val finalPreference: TerminologyPreference,
        val timestamp: Instant = Instant.now()
    ) : MigrationEvent()
}