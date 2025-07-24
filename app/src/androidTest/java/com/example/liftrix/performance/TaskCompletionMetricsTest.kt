package com.example.liftrix.performance

import android.view.Choreographer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.liftrix.domain.service.AnalyticsService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import javax.inject.Inject

/**
 * Comprehensive test suite for task completion metrics and workflow efficiency measurement.
 * 
 * Validates PRD success metrics including:
 * - Task completion rate improvements 
 * - Cognitive load reduction validation
 * - Workflow efficiency measurement
 * - User interaction pattern analysis
 * 
 * Tests ensure the redesigned UI meets performance targets and provides
 * measurable improvements over legacy system.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class TaskCompletionMetricsTest {
    
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var choreographer: Choreographer
    
    @Inject
    lateinit var analyticsService: AnalyticsService
    
    private lateinit var performanceValidator: PerformanceValidator
    private lateinit var metricsCollector: MetricsCollector
    
    companion object {
        // PRD Success Metrics Targets
        private const val TARGET_TASK_COMPLETION_RATE = 0.95 // 95% success rate
        private const val TARGET_COGNITIVE_LOAD_REDUCTION = 0.4 // 40% reduction from baseline
        private const val EXCELLENT_COMPLETION_TIME_MS = 2000L // 2 seconds
        private const val GOOD_COMPLETION_TIME_MS = 5000L // 5 seconds
        
        // Workflow Types for Testing
        private const val WORKOUT_CREATION_WORKFLOW = "workout_creation"
        private const val SESSION_START_WORKFLOW = "session_start"
        private const val EXERCISE_SELECTION_WORKFLOW = "exercise_selection"
        private const val SET_COMPLETION_WORKFLOW = "set_completion"
    }
    
    @Before
    fun setup() {
        hiltRule.inject()
        performanceValidator = PerformanceValidator(choreographer, analyticsService)
        metricsCollector = MetricsCollector(performanceValidator, analyticsService)
    }
    
    @Test
    fun workoutCreationWorkflow_taskCompletionMetrics_meetsPRDTargets() = runBlocking {
        val workflowId = "workout_creation_test_${System.currentTimeMillis()}"
        val userId = "test_user_001"
        
        // Start workflow metrics collection
        val session = metricsCollector.startWorkflowMetrics(
            workflowId = workflowId,
            workflowType = WORKOUT_CREATION_WORKFLOW,
            userId = userId
        )
        
        // Simulate efficient workout creation workflow
        simulateWorkoutCreationWorkflow(workflowId)
        
        // Complete workflow with success
        val completedMetrics = metricsCollector.completeWorkflowMetrics(
            workflowId = workflowId,
            successful = true
        )
        
        // Validate PRD targets
        assertTrue(
            "Workout creation should complete within excellent time threshold. " +
                    "Actual: ${completedMetrics.completionTimeMs}ms",
            completedMetrics.completionTimeMs <= EXCELLENT_COMPLETION_TIME_MS
        )
        
        assertEquals(
            "Successful workflow should have 100% completion rate",
            1.0,
            completedMetrics.taskCompletionRate,
            0.01
        )
        
        assertTrue(
            "Cognitive load should be low for efficient workflow",
            completedMetrics.cognitiveLoadScore <= 0.3
        )
        
        assertTrue(
            "Efficiency score should be high for excellent performance",
            completedMetrics.efficiencyScore >= 0.8
        )
        
        Timber.i("Workout creation metrics: ${completedMetrics.completionTimeMs}ms, " +
                "efficiency: ${completedMetrics.efficiencyScore}, " +
                "cognitive load: ${completedMetrics.cognitiveLoadScore}")
    }
    
    @Test
    fun sessionStartWorkflow_errorRecovery_maintainsPerformance() = runBlocking {
        val workflowId = "session_start_error_test_${System.currentTimeMillis()}"
        val userId = "test_user_002"
        
        val session = metricsCollector.startWorkflowMetrics(
            workflowId = workflowId,
            workflowType = SESSION_START_WORKFLOW,
            userId = userId
        )
        
        // Simulate workflow with recoverable error
        simulateSessionStartWithError(workflowId)
        
        val completedMetrics = metricsCollector.completeWorkflowMetrics(
            workflowId = workflowId,
            successful = true // Eventually successful after error recovery
        )
        
        // Validate error recovery doesn't severely impact performance
        assertTrue(
            "Error recovery should still complete within good time threshold",
            completedMetrics.completionTimeMs <= GOOD_COMPLETION_TIME_MS
        )
        
        assertTrue(
            "Error recovery should maintain reasonable efficiency",
            completedMetrics.efficiencyScore >= 0.4
        )
        
        assertTrue(
            "Cognitive load should remain manageable despite error",
            completedMetrics.cognitiveLoadScore <= 0.7
        )
        
        assertEquals(
            "Should record one error", 
            1, 
            completedMetrics.errorCount
        )
        
        assertEquals(
            "Error should be recoverable",
            1,
            completedMetrics.recoveredErrorCount
        )
        
        Timber.i("Error recovery metrics: ${completedMetrics.completionTimeMs}ms, " +
                "efficiency: ${completedMetrics.efficiencyScore}, errors: ${completedMetrics.errorCount}")
    }
    
    @Test
    fun multipleWorkflows_aggregateMetrics_validatePRDSuccess() = runBlocking {
        val workflowIds = (1..5).map { "aggregate_test_$it_${System.currentTimeMillis()}" }
        val userId = "test_user_aggregate"
        
        // Execute multiple workflows of different types
        workflowIds.forEachIndexed { index, workflowId ->
            val workflowType = when (index) {
                0 -> WORKOUT_CREATION_WORKFLOW
                1 -> SESSION_START_WORKFLOW
                2 -> EXERCISE_SELECTION_WORKFLOW
                3 -> SET_COMPLETION_WORKFLOW
                else -> WORKOUT_CREATION_WORKFLOW
            }
            
            metricsCollector.startWorkflowMetrics(workflowId, workflowType, userId)
            
            // Simulate different workflow patterns
            when (workflowType) {
                WORKOUT_CREATION_WORKFLOW -> simulateWorkoutCreationWorkflow(workflowId)
                SESSION_START_WORKFLOW -> simulateSessionStartWorkflow(workflowId)
                EXERCISE_SELECTION_WORKFLOW -> simulateExerciseSelectionWorkflow(workflowId)
                SET_COMPLETION_WORKFLOW -> simulateSetCompletionWorkflow(workflowId)
            }
            
            metricsCollector.completeWorkflowMetrics(workflowId, successful = true)
        }
        
        // Get aggregate PRD success metrics
        val prdMetrics = metricsCollector.getPRDSuccessMetrics()
        
        // Validate PRD targets
        assertTrue(
            "Task completion rate should meet 95% target. Actual: ${prdMetrics.averageTaskCompletionRate}",
            prdMetrics.averageTaskCompletionRate >= TARGET_TASK_COMPLETION_RATE
        )
        
        assertTrue(
            "Cognitive load reduction should meet 40% target. " +
                    "Actual reduction: ${prdMetrics.averageCognitiveLoadReduction}",
            prdMetrics.averageCognitiveLoadReduction >= TARGET_COGNITIVE_LOAD_REDUCTION
        )
        
        assertTrue(
            "Overall success should be achieved",
            prdMetrics.overallSuccess
        )
        
        assertEquals(
            "Should track all 5 workflows",
            5,
            prdMetrics.totalWorkflows
        )
        
        Timber.i("PRD Success Metrics - Completion Rate: ${prdMetrics.averageTaskCompletionRate}, " +
                "Cognitive Load Reduction: ${prdMetrics.averageCognitiveLoadReduction}, " +
                "Efficiency: ${prdMetrics.averageEfficiencyScore}")
    }
    
    @Test
    fun cognitiveLoadAssessment_interactionPatterns_accurateMeasurement() = runBlocking {
        val workflowId = "cognitive_load_test_${System.currentTimeMillis()}"
        val userId = "test_user_cognitive"
        
        metricsCollector.startWorkflowMetrics(workflowId, WORKOUT_CREATION_WORKFLOW, userId)
        
        // Simulate confused user interaction pattern (high cognitive load)
        simulateConfusedUserPattern(workflowId)
        
        val completedMetrics = metricsCollector.completeWorkflowMetrics(workflowId, successful = true)
        
        // Validate cognitive load assessment accuracy
        assertTrue(
            "Confused interaction pattern should result in higher cognitive load",
            completedMetrics.cognitiveLoadScore >= 0.5
        )
        
        assertTrue(
            "Multiple errors should increase cognitive load",
            completedMetrics.cognitiveLoadScore > 0.3
        )
        
        assertTrue(
            "Efficiency should be lower due to confusion pattern",
            completedMetrics.efficiencyScore < 0.6
        )
        
        assertTrue(
            "Should record multiple interactions due to confusion",
            completedMetrics.totalInteractions >= 10
        )
        
        Timber.i("Cognitive load assessment - Score: ${completedMetrics.cognitiveLoadScore}, " +
                "Interactions: ${completedMetrics.totalInteractions}, " +
                "Errors: ${completedMetrics.errorCount}")
    }
    
    @Test
    fun efficientUserPattern_lowCognitiveLoad_highEfficiency() = runBlocking {
        val workflowId = "efficient_pattern_test_${System.currentTimeMillis()}"
        val userId = "test_user_efficient"
        
        metricsCollector.startWorkflowMetrics(workflowId, SESSION_START_WORKFLOW, userId)
        
        // Simulate efficient, experienced user pattern
        simulateEfficientUserPattern(workflowId)
        
        val completedMetrics = metricsCollector.completeWorkflowMetrics(workflowId, successful = true)
        
        // Validate efficient pattern results
        assertTrue(
            "Efficient pattern should result in low cognitive load",
            completedMetrics.cognitiveLoadScore <= 0.3
        )
        
        assertTrue(
            "Efficient pattern should achieve high efficiency score",
            completedMetrics.efficiencyScore >= 0.8
        )
        
        assertTrue(
            "Efficient completion should be within excellent time threshold",
            completedMetrics.completionTimeMs <= EXCELLENT_COMPLETION_TIME_MS
        )
        
        assertEquals(
            "Efficient user should have zero errors",
            0,
            completedMetrics.errorCount
        )
        
        assertTrue(
            "Efficient user should use minimal interactions",
            completedMetrics.totalInteractions <= 5
        )
        
        Timber.i("Efficient pattern metrics - Cognitive load: ${completedMetrics.cognitiveLoadScore}, " +
                "Efficiency: ${completedMetrics.efficiencyScore}, " +
                "Time: ${completedMetrics.completionTimeMs}ms")
    }
    
    @Test
    fun performanceRegression_detection_identifiesDeclines() = runBlocking {
        // Simulate baseline performance
        val baselineWorkflows = (1..3).map { "baseline_$it_${System.currentTimeMillis()}" }
        
        baselineWorkflows.forEach { workflowId ->
            metricsCollector.startWorkflowMetrics(workflowId, WORKOUT_CREATION_WORKFLOW, "baseline_user")
            simulateWorkoutCreationWorkflow(workflowId)
            metricsCollector.completeWorkflowMetrics(workflowId, successful = true)
        }
        
        val baselineMetrics = metricsCollector.getPRDSuccessMetrics()
        
        // Clear metrics and simulate performance regression
        metricsCollector.clearAllMetrics()
        
        val regressionWorkflows = (1..3).map { "regression_$it_${System.currentTimeMillis()}" }
        
        regressionWorkflows.forEach { workflowId ->
            metricsCollector.startWorkflowMetrics(workflowId, WORKOUT_CREATION_WORKFLOW, "regression_user")
            simulateSlowWorkflow(workflowId) // Simulate regression
            metricsCollector.completeWorkflowMetrics(workflowId, successful = true)
        }
        
        val regressionMetrics = metricsCollector.getPRDSuccessMetrics()
        
        // Validate regression detection
        assertTrue(
            "Regression should result in lower efficiency",
            regressionMetrics.averageEfficiencyScore < baselineMetrics.averageEfficiencyScore
        )
        
        assertTrue(
            "Regression should result in higher cognitive load",
            regressionMetrics.averageCognitiveLoadReduction < baselineMetrics.averageCognitiveLoadReduction
        )
        
        Timber.w("Performance regression detected - Baseline efficiency: ${baselineMetrics.averageEfficiencyScore}, " +
                "Regression efficiency: ${regressionMetrics.averageEfficiencyScore}")
    }
    
    // Workflow Simulation Methods
    
    private suspend fun simulateWorkoutCreationWorkflow(workflowId: String) {
        delay(100) // Navigate to creation screen
        metricsCollector.recordInteraction(workflowId, "screen_navigation", true, 100)
        
        delay(200) // Enter workout name
        metricsCollector.recordInteraction(workflowId, "text_input", true, 200)
        
        delay(150) // Select exercise
        metricsCollector.recordInteraction(workflowId, "exercise_selection", true, 150)
        
        delay(100) // Save workout
        metricsCollector.recordInteraction(workflowId, "save_action", true, 100)
    }
    
    private suspend fun simulateSessionStartWorkflow(workflowId: String) {
        delay(150) // Select workout
        metricsCollector.recordInteraction(workflowId, "workout_selection", true, 150)
        
        delay(100) // Confirm start
        metricsCollector.recordInteraction(workflowId, "start_confirmation", true, 100)
        
        delay(50) // Session initialized
        metricsCollector.recordInteraction(workflowId, "session_initialization", true, 50)
    }
    
    private suspend fun simulateSessionStartWithError(workflowId: String) {
        delay(150)
        metricsCollector.recordInteraction(workflowId, "workout_selection", true, 150)
        
        delay(100)
        metricsCollector.recordInteraction(workflowId, "start_confirmation", false, 100)
        metricsCollector.recordError(workflowId, "network_timeout", "Failed to start session", true)
        
        delay(200) // User retries
        metricsCollector.recordInteraction(workflowId, "retry_start", true, 200)
        
        delay(50)
        metricsCollector.recordInteraction(workflowId, "session_initialization", true, 50)
    }
    
    private suspend fun simulateExerciseSelectionWorkflow(workflowId: String) {
        delay(100)
        metricsCollector.recordInteraction(workflowId, "exercise_search", true, 100)
        
        delay(80)
        metricsCollector.recordInteraction(workflowId, "exercise_filter", true, 80)
        
        delay(120)
        metricsCollector.recordInteraction(workflowId, "exercise_select", true, 120)
    }
    
    private suspend fun simulateSetCompletionWorkflow(workflowId: String) {
        delay(50) // Enter weight
        metricsCollector.recordInteraction(workflowId, "weight_input", true, 50)
        
        delay(30) // Enter reps
        metricsCollector.recordInteraction(workflowId, "reps_input", true, 30)
        
        delay(20) // Complete set
        metricsCollector.recordInteraction(workflowId, "set_completion", true, 20)
    }
    
    private suspend fun simulateConfusedUserPattern(workflowId: String) {
        // Rapid, confused interactions with errors
        repeat(8) { index ->
            delay(100) // Quick succession (confusion)
            metricsCollector.recordInteraction(
                workflowId, 
                "confused_action_$index", 
                index % 3 != 0, // Some failures
                100
            )
            
            if (index % 3 == 0) {
                metricsCollector.recordError(workflowId, "user_error", "Incorrect action", true)
            }
        }
        
        delay(2000) // Long pause (hesitation)
        metricsCollector.recordInteraction(workflowId, "final_correct_action", true, 300)
    }
    
    private suspend fun simulateEfficientUserPattern(workflowId: String) {
        // Minimal, precise interactions with no errors
        delay(150)
        metricsCollector.recordInteraction(workflowId, "direct_workout_selection", true, 150)
        
        delay(100)
        metricsCollector.recordInteraction(workflowId, "immediate_start", true, 100)
        
        delay(50)
        metricsCollector.recordInteraction(workflowId, "session_ready", true, 50)
    }
    
    private suspend fun simulateSlowWorkflow(workflowId: String) {
        // Simulate performance regression with slow operations
        delay(1000) // Slow screen load
        metricsCollector.recordInteraction(workflowId, "slow_screen_load", true, 1000)
        
        delay(800) // Slow text input
        metricsCollector.recordInteraction(workflowId, "slow_text_input", true, 800)
        
        delay(600) // Slow exercise selection
        metricsCollector.recordInteraction(workflowId, "slow_exercise_selection", true, 600)
        
        delay(400) // Slow save operation
        metricsCollector.recordInteraction(workflowId, "slow_save", true, 400)
    }
}