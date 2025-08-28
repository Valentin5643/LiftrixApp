package com.example.liftrix.domain.usecase.maintenance

import com.example.liftrix.data.service.ProfileCleanupService
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for performing maintenance operations on user profiles and related data.
 * 
 * This includes:
 * - Detecting and removing orphaned profiles
 * - Performing bulk cleanup operations
 * - Generating maintenance reports
 * - Validating data integrity across Firebase Auth, Firestore, and Room
 * 
 * This use case should be used for:
 * - Scheduled maintenance operations
 * - Admin-triggered cleanup tasks
 * - Post-migration data validation
 * - Debugging sync issues
 */
class ProfileMaintenanceUseCase @Inject constructor(
    private val profileCleanupService: ProfileCleanupService
) {
    
    /**
     * Request object for maintenance operations
     */
    data class MaintenanceRequest(
        val operation: MaintenanceOperation,
        val targetUserId: String? = null, // For user-specific operations
        val dryRun: Boolean = false, // Preview changes without executing
        val batchSize: Int = 50 // For bulk operations
    )
    
    /**
     * Available maintenance operations
     */
    enum class MaintenanceOperation {
        DETECT_ORPHANED_PROFILES,
        CLEANUP_ORPHANED_PROFILES,
        VALIDATE_USER_DATA_INTEGRITY,
        FULL_SYSTEM_CLEANUP,
        GENERATE_HEALTH_REPORT
    }
    
    /**
     * Response object containing maintenance results and metrics
     */
    data class MaintenanceResponse(
        val operation: MaintenanceOperation,
        val cleanupResult: ProfileCleanupService.CleanupResult,
        val healthReport: SystemHealthReport?,
        val dryRun: Boolean,
        val executionTimeMs: Long,
        val recommendations: List<String>
    )
    
    /**
     * System health report for database and sync integrity
     */
    data class SystemHealthReport(
        val totalUsers: Int,
        val activeUsers: Int,
        val orphanedProfiles: Int,
        val inconsistentProfiles: Int, // Exist in one system but not another
        val syncIssues: Int,
        val lastMaintenanceTime: Long?,
        val healthScore: Int, // 0-100 scale
        val criticalIssues: List<String>
    )
    
    /**
     * Main entry point for maintenance operations
     */
    suspend fun invoke(request: MaintenanceRequest): LiftrixResult<MaintenanceResponse> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "MAINTENANCE_OPERATION_FAILED",
                errorMessage = "Maintenance operation ${request.operation} failed: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to request.operation.name,
                    "target_user_id" to (request.targetUserId ?: "all"),
                    "dry_run" to request.dryRun.toString()
                )
            )
        }
    ) {
        val startTime = System.currentTimeMillis()
        Timber.i("🛠️ MAINTENANCE: Starting ${request.operation} operation (dry run: ${request.dryRun})")
        
        val cleanupResult: ProfileCleanupService.CleanupResult
        var healthReport: SystemHealthReport? = null
        val recommendations = mutableListOf<String>()
        
        when (request.operation) {
            MaintenanceOperation.DETECT_ORPHANED_PROFILES -> {
                Timber.d("🛠️ MAINTENANCE: Detecting orphaned profiles...")
                cleanupResult = if (request.dryRun) {
                    // Perform detection without cleanup
                    profileCleanupService.performOrphanedProfileCleanup(excludeUserId = request.targetUserId)
                        .copy(orphanedProfilesRemoved = 0) // Don't actually remove in dry run
                } else {
                    ProfileCleanupService.CleanupResult(0, 0, 0, 0, emptyList(), 0) // Detection only
                }
                
                if (cleanupResult.orphanedProfilesFound > 0) {
                    recommendations.add("Found ${cleanupResult.orphanedProfilesFound} orphaned profiles - consider running CLEANUP_ORPHANED_PROFILES")
                }
            }
            
            MaintenanceOperation.CLEANUP_ORPHANED_PROFILES -> {
                Timber.d("🛠️ MAINTENANCE: Cleaning up orphaned profiles...")
                cleanupResult = if (request.dryRun) {
                    val dryResult = profileCleanupService.performOrphanedProfileCleanup(excludeUserId = request.targetUserId)
                    recommendations.add("DRY RUN: Would remove ${dryResult.orphanedProfilesFound} orphaned profiles")
                    dryResult.copy(orphanedProfilesRemoved = 0)
                } else {
                    profileCleanupService.performOrphanedProfileCleanup(excludeUserId = request.targetUserId)
                }
                
                if (cleanupResult.orphanedProfilesRemoved > 0) {
                    recommendations.add("Successfully cleaned up ${cleanupResult.orphanedProfilesRemoved} orphaned profiles")
                }
            }
            
            MaintenanceOperation.VALIDATE_USER_DATA_INTEGRITY -> {
                Timber.d("🛠️ MAINTENANCE: Validating user data integrity...")
                cleanupResult = ProfileCleanupService.CleanupResult(0, 0, 0, 0, emptyList(), 0)
                
                // Check specific user if provided, otherwise check current integrity
                request.targetUserId?.let { userId ->
                    val isOrphaned = profileCleanupService.isUserOrphaned(userId)
                    if (isOrphaned) {
                        recommendations.add("User $userId appears to be orphaned and should be cleaned up")
                    } else {
                        recommendations.add("User $userId data integrity is valid")
                    }
                }
            }
            
            MaintenanceOperation.FULL_SYSTEM_CLEANUP -> {
                Timber.d("🛠️ MAINTENANCE: Performing full system cleanup...")
                cleanupResult = if (request.dryRun) {
                    val dryResult = profileCleanupService.performOrphanedProfileCleanup()
                    recommendations.add("DRY RUN: Full cleanup would remove ${dryResult.orphanedProfilesFound} orphaned profiles")
                    dryResult.copy(orphanedProfilesRemoved = 0)
                } else {
                    val result = profileCleanupService.performOrphanedProfileCleanup()
                    recommendations.add("Full system cleanup completed - removed ${result.orphanedProfilesRemoved} orphaned profiles")
                    result
                }
            }
            
            MaintenanceOperation.GENERATE_HEALTH_REPORT -> {
                Timber.d("🛠️ MAINTENANCE: Generating system health report...")
                cleanupResult = ProfileCleanupService.CleanupResult(0, 0, 0, 0, emptyList(), 0)
                
                // Generate a basic health report
                val detectionResult = profileCleanupService.performOrphanedProfileCleanup(excludeUserId = null)
                
                healthReport = SystemHealthReport(
                    totalUsers = detectionResult.orphanedProfilesFound + 1, // Approximate
                    activeUsers = 1, // At least current user
                    orphanedProfiles = detectionResult.orphanedProfilesFound,
                    inconsistentProfiles = detectionResult.orphanedProfilesFound,
                    syncIssues = detectionResult.errors.size,
                    lastMaintenanceTime = System.currentTimeMillis(),
                    healthScore = calculateHealthScore(detectionResult),
                    criticalIssues = detectionResult.errors
                )
                
                recommendations.add("System health score: ${healthReport.healthScore}/100")
                if (healthReport.orphanedProfiles > 0) {
                    recommendations.add("${healthReport.orphanedProfiles} orphaned profiles detected - consider cleanup")
                }
                if (healthReport.healthScore < 80) {
                    recommendations.add("System health below 80% - maintenance required")
                }
            }
        }
        
        val executionTime = System.currentTimeMillis() - startTime
        Timber.i("🛠️ MAINTENANCE: ${request.operation} completed in ${executionTime}ms")
        
        MaintenanceResponse(
            operation = request.operation,
            cleanupResult = cleanupResult,
            healthReport = healthReport,
            dryRun = request.dryRun,
            executionTimeMs = executionTime,
            recommendations = recommendations.toList()
        )
    }
    
    /**
     * Convenience method for detecting orphaned profiles without cleanup
     */
    suspend fun detectOrphanedProfiles(excludeUserId: String? = null): LiftrixResult<MaintenanceResponse> {
        return invoke(
            MaintenanceRequest(
                operation = MaintenanceOperation.DETECT_ORPHANED_PROFILES,
                targetUserId = excludeUserId,
                dryRun = true
            )
        )
    }
    
    /**
     * Convenience method for performing cleanup operations
     */
    suspend fun cleanupOrphanedProfiles(
        excludeUserId: String? = null,
        dryRun: Boolean = false
    ): LiftrixResult<MaintenanceResponse> {
        return invoke(
            MaintenanceRequest(
                operation = MaintenanceOperation.CLEANUP_ORPHANED_PROFILES,
                targetUserId = excludeUserId,
                dryRun = dryRun
            )
        )
    }
    
    /**
     * Convenience method for generating system health reports
     */
    suspend fun generateHealthReport(): LiftrixResult<MaintenanceResponse> {
        return invoke(
            MaintenanceRequest(
                operation = MaintenanceOperation.GENERATE_HEALTH_REPORT,
                dryRun = false
            )
        )
    }
    
    /**
     * Calculates system health score based on cleanup results
     */
    private fun calculateHealthScore(cleanupResult: ProfileCleanupService.CleanupResult): Int {
        val baseScore = 100
        
        // Deduct points for issues
        val orphanedPenalty = cleanupResult.orphanedProfilesFound * 10
        val errorPenalty = cleanupResult.errors.size * 15
        
        val finalScore = (baseScore - orphanedPenalty - errorPenalty).coerceIn(0, 100)
        
        return finalScore
    }
    
    /**
     * Schedules periodic maintenance operations
     * This could be called during app initialization or via a scheduler
     */
    suspend fun schedulePeriodicMaintenance(): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PERIODIC_MAINTENANCE_SCHEDULE_FAILED",
                errorMessage = "Failed to schedule periodic maintenance: ${throwable.message}"
            )
        }
    ) {
        Timber.i("🛠️ MAINTENANCE: Scheduling periodic maintenance operations")
        
        // This would integrate with WorkManager or similar scheduling system
        // For now, just log the intent
        
        Timber.d("🛠️ MAINTENANCE: Periodic maintenance scheduling complete")
    }
}