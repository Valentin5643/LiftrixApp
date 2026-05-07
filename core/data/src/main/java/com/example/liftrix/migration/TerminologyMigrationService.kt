package com.example.liftrix.migration

import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.service.AnalyticsService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for managing terminology migration for existing users
 * Handles transition from 'template' terminology to new workflow-based language
 */
@Singleton
class TerminologyMigrationService @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val analyticsService: AnalyticsService
) {
    
    suspend fun checkMigrationStatus(userId: String): MigrationStatus {
        val settings = settingsRepository.getUserSettingsSync(userId) ?: return MigrationStatus.PENDING
        
        return when {
            settings.migrationCompleted -> MigrationStatus.COMPLETED
            settings.migrationExplanationSeen -> MigrationStatus.IN_PROGRESS
            else -> MigrationStatus.PENDING
        }
    }
    
    suspend fun startMigration(userId: String) {
        analyticsService.logEvent("terminology_migration_started", mapOf(
            "user_id" to userId
        ))
        
        settingsRepository.updateMigrationExplanationSeen(userId, true)
    }
    
    suspend fun completeMigration(userId: String) {
        settingsRepository.updateMigrationCompleted(userId, true)
        
        analyticsService.logEvent("terminology_migration_completed", mapOf(
            "user_id" to userId
        ))
    }
    
    suspend fun setTerminologyPreference(userId: String, preference: TerminologyPreference) {
        val preferenceString = when (preference) {
            TerminologyPreference.NEW -> "NEW"
            TerminologyPreference.LEGACY -> "LEGACY"
        }
        
        settingsRepository.updateTerminologyPreference(userId, preferenceString)
        
        analyticsService.logEvent("terminology_preference_changed", mapOf(
            "user_id" to userId,
            "preference" to preferenceString
        ))
    }
    
    suspend fun shouldShowOldTerminology(userId: String): Boolean {
        val settings = settingsRepository.getUserSettingsSync(userId) ?: return false
        return settings.terminologyPreference == "LEGACY"
    }
    
    suspend fun shouldShowMigrationDialog(userId: String): Boolean {
        val status = checkMigrationStatus(userId)
        return status == MigrationStatus.PENDING
    }
}

enum class MigrationStatus {
    PENDING, IN_PROGRESS, COMPLETED
}

enum class TerminologyPreference {
    NEW, LEGACY
}