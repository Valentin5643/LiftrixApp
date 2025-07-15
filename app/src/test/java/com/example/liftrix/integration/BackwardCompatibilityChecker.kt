package com.example.liftrix.integration

import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.GetWorkoutHistoryUseCase
import com.example.liftrix.ui.home.HomeViewModel
import com.example.liftrix.ui.home.HomeUiState
import com.example.liftrix.ui.home.HomeEvent
import kotlin.reflect.KClass
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Validates backward compatibility of existing APIs after theme enhancements.
 * Ensures no breaking changes to public interfaces and method signatures.
 */
class BackwardCompatibilityChecker {

    /**
     * Validates that ViewModel public APIs remain unchanged
     */
    fun validateViewModelAPIs(): CompatibilityResult {
        val violations = mutableListOf<String>()
        
        // Validate HomeViewModel API preservation
        val homeViewModelResult = validateHomeViewModelAPI()
        if (homeViewModelResult is CompatibilityResult.Failure) {
            violations.addAll(homeViewModelResult.violations)
        }
        
        return if (violations.isEmpty()) {
            CompatibilityResult.Success("All ViewModel APIs preserved")
        } else {
            CompatibilityResult.Failure("ViewModel API compatibility violations found", violations)
        }
    }
    
    /**
     * Validates that repository interfaces remain unchanged
     */
    fun validateRepositoryInterfaces(): CompatibilityResult {
        val violations = mutableListOf<String>()
        
        // Validate WorkoutRepository interface
        val workoutRepoResult = validateWorkoutRepositoryInterface()
        if (workoutRepoResult is CompatibilityResult.Failure) {
            violations.addAll(workoutRepoResult.violations)
        }
        
        return if (violations.isEmpty()) {
            CompatibilityResult.Success("All repository interfaces preserved")
        } else {
            CompatibilityResult.Failure("Repository interface compatibility violations found", violations)
        }
    }
    
    /**
     * Validates that use case interfaces remain unchanged
     */
    fun validateUseCaseInterfaces(): CompatibilityResult {
        val violations = mutableListOf<String>()
        
        // Validate GetWorkoutHistoryUseCase
        val useCaseResult = validateGetWorkoutHistoryUseCaseInterface()
        if (useCaseResult is CompatibilityResult.Failure) {
            violations.addAll(useCaseResult.violations)
        }
        
        return if (violations.isEmpty()) {
            CompatibilityResult.Success("All use case interfaces preserved")
        } else {
            CompatibilityResult.Failure("Use case interface compatibility violations found", violations)
        }
    }
    
    /**
     * Validates that domain model classes remain unchanged
     */
    fun validateDomainModels(): CompatibilityResult {
        val violations = mutableListOf<String>()
        
        // Check that essential domain models have required properties
        val requiredModels = mapOf(
            "HomeUiState" to listOf("isLoading", "recentWorkouts", "workoutStats", "errorMessage"),
            "HomeEvent" to listOf("RefreshData", "LoadMoreWorkouts", "WorkoutOpened", "ErrorDismissed")
        )
        
        requiredModels.forEach { (modelName, requiredProperties) ->
            try {
                val modelClass = when (modelName) {
                    "HomeUiState" -> HomeUiState::class
                    "HomeEvent" -> HomeEvent::class
                    else -> null
                }
                
                modelClass?.let { clazz ->
                    val properties = clazz.memberProperties.map { it.name }
                    val sealedSubclasses = clazz.sealedSubclasses?.map { it.simpleName } ?: emptyList()
                    
                    requiredProperties.forEach { requiredProp ->
                        if (!properties.contains(requiredProp) && !sealedSubclasses.contains(requiredProp)) {
                            violations.add("$modelName missing required property/subclass: $requiredProp")
                        }
                    }
                }
            } catch (e: Exception) {
                violations.add("Failed to validate $modelName: ${e.message}")
            }
        }
        
        return if (violations.isEmpty()) {
            CompatibilityResult.Success("All domain models preserved")
        } else {
            CompatibilityResult.Failure("Domain model compatibility violations found", violations)
        }
    }
    
    /**
     * Comprehensive backward compatibility validation
     */
    fun validateCompleteBackwardCompatibility(): CompatibilityResult {
        val results = listOf(
            validateViewModelAPIs(),
            validateRepositoryInterfaces(),
            validateUseCaseInterfaces(),
            validateDomainModels()
        )
        
        val failures = results.filterIsInstance<CompatibilityResult.Failure>()
        
        return if (failures.isEmpty()) {
            CompatibilityResult.Success("Complete backward compatibility validated successfully")
        } else {
            val allViolations = failures.flatMap { it.violations }
            CompatibilityResult.Failure("Backward compatibility violations found", allViolations)
        }
    }
    
    private fun validateHomeViewModelAPI(): CompatibilityResult {
        val violations = mutableListOf<String>()
        
        try {
            val homeViewModelClass = HomeViewModel::class
            
            // Validate constructor parameters
            val constructor = homeViewModelClass.primaryConstructor
            val expectedParameters = listOf(
                "workoutRepository",
                "authRepository", 
                "analyticsService",
                "socialRepository",
                "getWorkoutHistoryUseCase"
            )
            
            val actualParameters = constructor?.parameters?.map { it.name } ?: emptyList()
            expectedParameters.forEach { expectedParam ->
                if (!actualParameters.contains(expectedParam)) {
                    violations.add("HomeViewModel constructor missing parameter: $expectedParam")
                }
            }
            
            // Validate public methods
            val functions = homeViewModelClass.functions
            val requiredMethods = listOf("onEvent", "loadHomeData", "loadFeedWorkouts", "refreshData")
            
            requiredMethods.forEach { methodName ->
                if (!functions.any { it.name == methodName }) {
                    violations.add("HomeViewModel missing required method: $methodName")
                }
            }
            
            // Validate uiState property
            val properties = homeViewModelClass.memberProperties
            if (!properties.any { it.name == "uiState" }) {
                violations.add("HomeViewModel missing uiState property")
            }
            
        } catch (e: Exception) {
            violations.add("Failed to validate HomeViewModel API: ${e.message}")
        }
        
        return if (violations.isEmpty()) {
            CompatibilityResult.Success("HomeViewModel API preserved")
        } else {
            CompatibilityResult.Failure("HomeViewModel API violations found", violations)
        }
    }
    
    private fun validateWorkoutRepositoryInterface(): CompatibilityResult {
        val violations = mutableListOf<String>()
        
        try {
            val workoutRepoClass = WorkoutRepository::class
            val functions = workoutRepoClass.functions
            
            // Validate essential methods are present
            val requiredMethods = listOf(
                "getRecentWorkouts",
                "getWorkoutStats", 
                "getFeedWorkouts",
                "getUserWorkoutHistory",
                "saveWorkout",
                "updateWorkout",
                "deleteWorkoutForUser"
            )
            
            requiredMethods.forEach { methodName ->
                if (!functions.any { it.name == methodName }) {
                    violations.add("WorkoutRepository missing required method: $methodName")
                }
            }
            
        } catch (e: Exception) {
            violations.add("Failed to validate WorkoutRepository interface: ${e.message}")
        }
        
        return if (violations.isEmpty()) {
            CompatibilityResult.Success("WorkoutRepository interface preserved")
        } else {
            CompatibilityResult.Failure("WorkoutRepository interface violations found", violations)
        }
    }
    
    private fun validateGetWorkoutHistoryUseCaseInterface(): CompatibilityResult {
        val violations = mutableListOf<String>()
        
        try {
            val useCaseClass = GetWorkoutHistoryUseCase::class
            val functions = useCaseClass.functions
            
            // Validate execute method exists
            if (!functions.any { it.name == "execute" }) {
                violations.add("GetWorkoutHistoryUseCase missing execute method")
            }
            
            // Validate getHistoryCount method exists
            if (!functions.any { it.name == "getHistoryCount" }) {
                violations.add("GetWorkoutHistoryUseCase missing getHistoryCount method")
            }
            
        } catch (e: Exception) {
            violations.add("Failed to validate GetWorkoutHistoryUseCase interface: ${e.message}")
        }
        
        return if (violations.isEmpty()) {
            CompatibilityResult.Success("GetWorkoutHistoryUseCase interface preserved")
        } else {
            CompatibilityResult.Failure("GetWorkoutHistoryUseCase interface violations found", violations)
        }
    }
}

/**
 * Compatibility validation result
 */
sealed class CompatibilityResult {
    data class Success(val message: String) : CompatibilityResult()
    data class Failure(val message: String, val violations: List<String>) : CompatibilityResult()
} 