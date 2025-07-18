package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for retrieving user widget preferences.
 * 
 * This use case provides a reactive stream of widget preferences for a specific user,
 * handling all business logic related to preference loading and validation.
 * It follows the established pattern for Liftrix use cases with LiftrixResult return types.
 * 
 * @property widgetPreferencesRepository Repository for widget preferences operations
 */
class GetWidgetPreferencesUseCase @Inject constructor(
    private val widgetPreferencesRepository: WidgetPreferencesRepository
) {
    
    /**
     * Gets widget preferences for a specific user as a reactive Flow.
     * 
     * This method provides real-time updates of widget preferences, automatically
     * handling preference changes and validation. The returned Flow emits new
     * values whenever preferences are updated.
     * 
     * @param userId The unique identifier for the user
     * @return Flow emitting LiftrixResult with WidgetPreferences or error
     */
    operator fun invoke(userId: String): Flow<LiftrixResult<WidgetPreferences>> {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        
        return widgetPreferencesRepository.getWidgetPreferences(userId)
    }
}