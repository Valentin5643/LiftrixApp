package com.example.liftrix.ui.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for LiftrixRoute sealed class hierarchy
 * 
 * Verifies type-safe navigation route serialization, deserialization,
 * and compile-time type safety for all route definitions.
 */
class LiftrixRouteTest {
    
    private val json = Json { 
        // Configure JSON for route serialization testing
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    
    @Test
    fun `test simple route serialization`() {
        // Given simple routes without parameters
        val routes = listOf(
            LiftrixRoute.Home,
            LiftrixRoute.Workout,
            LiftrixRoute.Progress,
            LiftrixRoute.Coach,
            LiftrixRoute.Friends,
            LiftrixRoute.TemplateCreation,
            LiftrixRoute.Settings,
            LiftrixRoute.Onboarding
        )
        
        // When serializing each route
        routes.forEach { route ->
            // Then serialization should work without errors
            val serialized = json.encodeToString<LiftrixRoute>(route)
            assertNotNull(serialized, "Route $route should serialize successfully")
            assertTrue(serialized.isNotEmpty(), "Serialized route should not be empty")
            
            // And deserialization should restore original route
            val deserialized = json.decodeFromString<LiftrixRoute>(serialized)
            assertEquals(route, deserialized, "Route should deserialize to original value")
        }
    }
    
    @Test
    fun `test parameterized route serialization`() {
        // Given routes with parameters
        val workoutDetails = LiftrixRoute.WorkoutDetails("workout-123")
        val exerciseSelection = LiftrixRoute.ExerciseSelection("template-456", isForTemplate = true)
        val activeWorkout = LiftrixRoute.ActiveWorkout("template-789", isBlankWorkout = false)
        val exerciseDetails = LiftrixRoute.ExerciseDetails("exercise-abc")
        
        val parameterizedRoutes = listOf(
            workoutDetails,
            exerciseSelection,
            activeWorkout,
            exerciseDetails
        )
        
        // When serializing parameterized routes
        parameterizedRoutes.forEach { route ->
            // Then serialization should preserve parameters
            val serialized = json.encodeToString<LiftrixRoute>(route)
            val deserialized = json.decodeFromString<LiftrixRoute>(serialized)
            assertEquals(route, deserialized, "Parameterized route should preserve parameters")
        }
    }
    
    @Test
    fun `test exercise selection route with default parameters`() {
        // Given exercise selection with default parameters
        val exerciseSelectionDefault = LiftrixRoute.ExerciseSelection()
        val exerciseSelectionWithTemplate = LiftrixRoute.ExerciseSelection("template-123")
        val exerciseSelectionForTemplate = LiftrixRoute.ExerciseSelection(isForTemplate = true)
        
        // When serializing routes with default parameters
        val serializedDefault = json.encodeToString<LiftrixRoute>(exerciseSelectionDefault)
        val serializedWithTemplate = json.encodeToString<LiftrixRoute>(exerciseSelectionWithTemplate)
        val serializedForTemplate = json.encodeToString<LiftrixRoute>(exerciseSelectionForTemplate)
        
        // Then deserialization should preserve default values correctly
        val deserializedDefault = json.decodeFromString<LiftrixRoute>(serializedDefault)
        val deserializedWithTemplate = json.decodeFromString<LiftrixRoute>(serializedWithTemplate)
        val deserializedForTemplate = json.decodeFromString<LiftrixRoute>(serializedForTemplate)
        
        assertEquals(exerciseSelectionDefault, deserializedDefault)
        assertEquals(exerciseSelectionWithTemplate, deserializedWithTemplate)
        assertEquals(exerciseSelectionForTemplate, deserializedForTemplate)
    }
    
    @Test
    fun `test active workout route parameter combinations`() {
        // Given various active workout configurations
        val blankWorkout = LiftrixRoute.ActiveWorkout(isBlankWorkout = true)
        val templateWorkout = LiftrixRoute.ActiveWorkout("template-123")
        val templateBlankWorkout = LiftrixRoute.ActiveWorkout("template-456", isBlankWorkout = true)
        val defaultWorkout = LiftrixRoute.ActiveWorkout()
        
        val workoutRoutes = listOf(
            blankWorkout,
            templateWorkout,
            templateBlankWorkout,
            defaultWorkout
        )
        
        // When serializing different workout configurations
        workoutRoutes.forEach { route ->
            // Then all parameter combinations should work correctly
            val serialized = json.encodeToString<LiftrixRoute>(route)
            val deserialized = json.decodeFromString<LiftrixRoute>(serialized)
            assertEquals(route, deserialized, "Active workout route parameters should be preserved")
        }
    }
    
    @Test
    fun `test route type safety at compile time`() {
        // Given type-safe route construction
        val workoutId = "workout-123"
        val exerciseId = "exercise-abc"
        val templateId = "template-456"
        
        // When creating routes with type-safe parameters
        val workoutDetails = LiftrixRoute.WorkoutDetails(workoutId)
        val exerciseDetails = LiftrixRoute.ExerciseDetails(exerciseId)
        val exerciseSelection = LiftrixRoute.ExerciseSelection(templateId, isForTemplate = true)
        
        // Then route parameters should be type-safe (compile-time validation)
        assertEquals(workoutId, workoutDetails.workoutId)
        assertEquals(exerciseId, exerciseDetails.exerciseId)
        assertEquals(templateId, exerciseSelection.templateId)
        assertEquals(true, exerciseSelection.isForTemplate)
    }
    
    @Test
    fun `test route hierarchy sealed class behavior`() {
        // Given sealed class hierarchy
        val routes: List<LiftrixRoute> = listOf(
            LiftrixRoute.Home,
            LiftrixRoute.WorkoutDetails("test"),
            LiftrixRoute.ExerciseSelection(),
            LiftrixRoute.ActiveWorkout()
        )
        
        // When using sealed class pattern matching
        routes.forEach { route ->
            val routeType = when (route) {
                is LiftrixRoute.Home -> "Home"
                is LiftrixRoute.Workout -> "Workout"
                is LiftrixRoute.Progress -> "Progress"
                is LiftrixRoute.Coach -> "Coach"
                is LiftrixRoute.Friends -> "Friends"
                is LiftrixRoute.WorkoutDetails -> "WorkoutDetails"
                is LiftrixRoute.ExerciseSelection -> "ExerciseSelection"
                is LiftrixRoute.ActiveWorkout -> "ActiveWorkout"
                is LiftrixRoute.TemplateCreation -> "TemplateCreation"
                is LiftrixRoute.ExerciseDetails -> "ExerciseDetails"
                is LiftrixRoute.Settings -> "Settings"
                is LiftrixRoute.Onboarding -> "Onboarding"
            }
            
            // Then sealed class should enable exhaustive pattern matching
            assertNotNull(routeType, "Route type should be determined by sealed class pattern matching")
        }
    }
}