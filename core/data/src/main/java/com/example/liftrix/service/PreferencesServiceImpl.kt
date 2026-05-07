package com.example.liftrix.service

import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.DashboardLayoutMode
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.model.analytics.WidgetLayoutMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PreferencesService providing user preferences management with atomic updates.
 * 
 * This service coordinates between widget preferences and user authentication to provide
 * a comprehensive preference management system. It handles validation, default restoration,
 * and ensures preference consistency across all operations.
 * 
 * Architecture:
 * - Repository pattern for data access
 * - Background processing using IoDispatcher
 * - Atomic preference updates with validation
 * - Clean Architecture compliance with domain layer separation
 * 
 * Thread Safety:
 * All operations are thread-safe through proper use of coroutine contexts and
 * atomic repository operations.
 * 
 * Error Handling:
 * Comprehensive error handling with LiftrixError mapping for consistent error
 * reporting and recovery mechanisms.
 * 
 * @property preferencesRepository Repository for widget preferences persistence
 * @property authRepository Repository for user authentication state
 * @property dispatcher Coroutine dispatcher for background operations
 */
@Singleton
class PreferencesServiceImpl @Inject constructor(
    private val preferencesRepository: WidgetPreferencesRepository,
    private val authRepository: AuthRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : PreferencesService {
    
    override suspend fun getUserPreferences(userId: String): LiftrixResult<WidgetPreferences> = 
        withContext(dispatcher) {
            runCatching {
                preferencesRepository.getWidgetPreferences(userId)
                    .first()
                    .getOrElse { 
                        // If no preferences exist, create defaults based on user level
                        val defaultPreferences = WidgetPreferences.createDefault(userId)
                        preferencesRepository.saveWidgetPreferences(defaultPreferences)
                            .getOrThrow()
                        defaultPreferences
                    }
            }.fold(
                onSuccess = { preferences ->
                    // Validate preferences before returning
                    try {
                        preferences.validate()
                        Result.success(preferences)
                    } catch (e: IllegalArgumentException) {
                        Result.failure(LiftrixError.ValidationError(
                            field = "preferences",
                            violations = listOf("Invalid preferences: ${e.message}"),
                            analyticsContext = mapOf(
                                "userId" to userId,
                                "operation" to "getUserPreferences"
                            )
                        ))
                    }
                },
                onFailure = { throwable ->
                    Result.failure(LiftrixError.DatabaseError(
                        errorMessage = "Failed to retrieve user preferences: ${throwable.message}",
                        analyticsContext = mapOf(
                            "userId" to userId,
                            "operation" to "getUserPreferences",
                            "error" to throwable.javaClass.simpleName
                        )
                    ))
                }
            )
        }
    
    override suspend fun updateLayoutMode(userId: String, mode: WidgetLayoutMode): LiftrixResult<Unit> = 
        withContext(dispatcher) {
            runCatching {
                val currentPreferences = getUserPreferences(userId).getOrThrow()
                val dashboardLayoutMode = mapWidgetLayoutModeToDashboardLayoutMode(mode)
                val updatedPreferences = currentPreferences.updateLayout(dashboardLayoutMode)
                
                preferencesRepository.saveWidgetPreferences(updatedPreferences)
                    .getOrThrow()
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { throwable ->
                    Result.failure(LiftrixError.BusinessLogicError(
                        code = "layout_mode_update",
                        errorMessage = "Failed to update layout mode: ${throwable.message}",
                        analyticsContext = mapOf(
                            "userId" to userId,
                            "layoutMode" to mode.name,
                            "operation" to "updateLayoutMode"
                        )
                    ))
                }
            )
        }
    
    override suspend fun updateUserLevel(userId: String, level: UserLevel): LiftrixResult<Unit> = 
        withContext(dispatcher) {
            runCatching {
                val currentPreferences = getUserPreferences(userId).getOrThrow()
                val updatedPreferences = currentPreferences.updateUserLevel(level)
                
                preferencesRepository.saveWidgetPreferences(updatedPreferences)
                    .getOrThrow()
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { throwable ->
                    Result.failure(LiftrixError.BusinessLogicError(
                        code = "user_level_update",
                        errorMessage = "Failed to update user level: ${throwable.message}",
                        analyticsContext = mapOf(
                            "userId" to userId,
                            "userLevel" to level.name,
                            "operation" to "updateUserLevel"
                        )
                    ))
                }
            )
        }
    
    override suspend fun resetToDefaults(userId: String): LiftrixResult<Unit> = 
        withContext(dispatcher) {
            runCatching {
                val currentPreferences = getUserPreferences(userId).getOrThrow()
                val defaultPreferences = WidgetPreferences.createDefault(userId, currentPreferences.userLevel)
                
                preferencesRepository.saveWidgetPreferences(defaultPreferences)
                    .getOrThrow()
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { throwable ->
                    Result.failure(LiftrixError.BusinessLogicError(
                        code = "preferences_reset",
                        errorMessage = "Failed to reset preferences to defaults: ${throwable.message}",
                        analyticsContext = mapOf(
                            "userId" to userId,
                            "operation" to "resetToDefaults"
                        )
                    ))
                }
            )
        }
    
    override suspend fun updateWidgetVisibility(
        userId: String, 
        widgetName: String, 
        visible: Boolean
    ): LiftrixResult<Unit> = 
        withContext(dispatcher) {
            runCatching {
                preferencesRepository.updateWidgetVisibility(userId, widgetName, visible)
                    .getOrThrow()
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { throwable ->
                    Result.failure(LiftrixError.BusinessLogicError(
                        code = "widget_visibility_update",
                        errorMessage = "Failed to update widget visibility: ${throwable.message}",
                        analyticsContext = mapOf(
                            "userId" to userId,
                            "widgetName" to widgetName,
                            "visible" to visible.toString(),
                            "operation" to "updateWidgetVisibility"
                        )
                    ))
                }
            )
        }
    
    override suspend fun updateWidgetOrder(
        userId: String, 
        widgetOrder: List<String>
    ): LiftrixResult<Unit> = 
        withContext(dispatcher) {
            runCatching {
                preferencesRepository.updateWidgetOrder(userId, widgetOrder)
                    .getOrThrow()
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { throwable ->
                    Result.failure(LiftrixError.BusinessLogicError(
                        code = "widget_order_update",
                        errorMessage = "Failed to update widget order: ${throwable.message}",
                        analyticsContext = mapOf(
                            "userId" to userId,
                            "widgetCount" to widgetOrder.size.toString(),
                            "operation" to "updateWidgetOrder"
                        )
                    ))
                }
            )
        }
    
    override suspend fun updateAutoRefreshSettings(
        userId: String,
        enabled: Boolean,
        intervalMinutes: Int
    ): LiftrixResult<Unit> = 
        withContext(dispatcher) {
            runCatching {
                // Validate interval range
                if (intervalMinutes !in 1..60) {
                    throw IllegalArgumentException("Refresh interval must be between 1 and 60 minutes")
                }
                
                preferencesRepository.updateAutoRefreshSettings(userId, enabled, intervalMinutes)
                    .getOrThrow()
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { throwable ->
                    val errorType = if (throwable is IllegalArgumentException) {
                        LiftrixError.ValidationError(
                            field = "intervalMinutes",
                            violations = listOf("Invalid refresh interval: ${throwable.message}"),
                            analyticsContext = mapOf(
                                "userId" to userId,
                                "intervalMinutes" to intervalMinutes.toString(),
                                "operation" to "updateAutoRefreshSettings"
                            )
                        )
                    } else {
                        LiftrixError.BusinessLogicError(
                            code = "auto_refresh_update",
                            errorMessage = "Failed to update auto-refresh settings: ${throwable.message}",
                            analyticsContext = mapOf(
                                "userId" to userId,
                                "enabled" to enabled.toString(),
                                "intervalMinutes" to intervalMinutes.toString(),
                                "operation" to "updateAutoRefreshSettings"
                            )
                        )
                    }
                    Result.failure(errorType)
                }
            )
        }
    
    override suspend fun toggleSection(userId: String, sectionName: String): LiftrixResult<Unit> = 
        withContext(dispatcher) {
            runCatching {
                preferencesRepository.toggleSection(userId, sectionName)
                    .getOrThrow()
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { throwable ->
                    Result.failure(LiftrixError.BusinessLogicError(
                        code = "section_toggle",
                        errorMessage = "Failed to toggle section: ${throwable.message}",
                        analyticsContext = mapOf(
                            "userId" to userId,
                            "sectionName" to sectionName,
                            "operation" to "toggleSection"
                        )
                    ))
                }
            )
        }
    
    /**
     * Maps UI WidgetLayoutMode to domain DashboardLayoutMode.
     * 
     * Provides translation between UI layer enums and domain layer enums,
     * maintaining separation of concerns between layers.
     * 
     * @param mode UI layer widget layout mode
     * @return Domain layer dashboard layout mode
     */
    private fun mapWidgetLayoutModeToDashboardLayoutMode(mode: WidgetLayoutMode): DashboardLayoutMode {
        return when (mode) {
            WidgetLayoutMode.GRID -> DashboardLayoutMode.GRID
            WidgetLayoutMode.STAGGERED -> DashboardLayoutMode.CUSTOM // Staggered maps to custom
            WidgetLayoutMode.LIST -> DashboardLayoutMode.SECTIONS // List becomes sections
            WidgetLayoutMode.SECTIONS -> DashboardLayoutMode.SECTIONS
        }
    }
    
    override suspend fun saveWidgetPreferences(preferences: WidgetPreferences): LiftrixResult<Unit> = 
        withContext(dispatcher) {
            runCatching {
                preferencesRepository.saveWidgetPreferences(preferences)
                    .getOrThrow()
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { throwable ->
                    Result.failure(LiftrixError.BusinessLogicError(
                        code = "save_preferences",
                        errorMessage = "Failed to save widget preferences: ${throwable.message}",
                        analyticsContext = mapOf(
                            "userId" to preferences.userId,
                            "operation" to "saveWidgetPreferences"
                        )
                    ))
                }
            )
        }
}