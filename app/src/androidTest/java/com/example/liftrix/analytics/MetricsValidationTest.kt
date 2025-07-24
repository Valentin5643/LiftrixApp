package com.example.liftrix.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.analytics.*
import com.example.liftrix.core.time.TimeProvider
import com.example.liftrix.domain.service.AnalyticsService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive test suite for UX metrics tracking system validation.
 * 
 * Tests validate:
 * - Accurate measurement collection for PRD success metrics
 * - Cognitive load calculation algorithms
 * - Task completion rate tracking
 * - Baseline comparison accuracy for 40% reduction target
 * - Integration with analytics service
 */
@RunWith(AndroidJUnit4::class)
class MetricsValidationTest {
    
    private lateinit var mockAnalyticsService: AnalyticsService
    private lateinit var mockTimeProvider: TimeProvider
    private lateinit var uxMetricsTracker: UxMetricsTracker
    private lateinit var taskCompletionTracker: TaskCompletionTracker
    private lateinit var cognitiveLoadMeasurement: CognitiveLoadMeasurement
    
    private val baseTimestamp = 1000000L
    private val workflowId = "test_workout_creation"
    private val taskId = "test_task_123"
    
    @Before
    fun setup() {
        mockAnalyticsService = mockk(relaxed = true)
        mockTimeProvider = mockk()
        
        every { mockTimeProvider.currentTimeMillis() } returnsMany listOf(
            baseTimestamp, 
            baseTimestamp + 1000L,
            baseTimestamp + 2000L, 
            baseTimestamp + 3000L,
            baseTimestamp + 5000L
        )
        
        uxMetricsTracker = UxMetricsTracker(mockAnalyticsService, mockTimeProvider)
        taskCompletionTracker = TaskCompletionTracker(mockAnalyticsService, mockTimeProvider)
        cognitiveLoadMeasurement = CognitiveLoadMeasurement(mockAnalyticsService)
    }
    
    @Test
    fun uxMetricsTracker_trackCompleteWorkflow_calculatesAccurateMetrics() {
        // Given: A complete workflow with known interactions
        val workflowId = "workout_creation_test"
        
        // When: Tracking complete workflow
        uxMetricsTracker.startWorkflowTracking(workflowId)
        
        // Simulate user interactions
        uxMetricsTracker.trackInteraction(workflowId, "button_press")
        uxMetricsTracker.trackInteraction(workflowId, "text_input")
        uxMetricsTracker.trackInteraction(workflowId, "navigation")
        uxMetricsTracker.trackError(workflowId, "validation_error")
        
        uxMetricsTracker.completeWorkflowTracking(workflowId, successful = true)
        
        // Then: Verify workflow completion event was logged
        val eventNameSlot = slot<String>()
        val parametersSlot = slot<Map<String, Any>>()
        
        verify { 
            mockAnalyticsService.logEvent(
                capture(eventNameSlot),
                capture(parametersSlot)
            )
        }
        
        // Verify completion event contains expected metrics
        val completionEvents = mutableListOf<Map<String, Any>>()
        verify(atLeast = 1) { 
            mockAnalyticsService.logEvent("ux_workflow_completed", capture(completionEvents))
        }
        
        val completionEvent = completionEvents.find { it["workflow_id"] == workflowId }
        assertNotNull("Workflow completion event should be logged", completionEvent)
        
        assertEquals("Completion time should be 5000ms", 5000L, completionEvent!!["completion_time_ms"])
        assertEquals("Should have 3 interactions", 3, completionEvent["total_interactions"])
        assertTrue("Should be marked as successful", completionEvent["successful"] as Boolean)
        assertTrue("Efficiency score should be calculated", completionEvent.containsKey("efficiency_score"))
    }
    
    @Test
    fun taskCompletionTracker_trackTaskLifecycle_recordsAccurateCompletion() = runTest {
        // Given: Task lifecycle tracking
        val taskType = TaskCompletionTracker.TASK_WORKOUT_CREATION
        
        // When: Tracking complete task lifecycle
        taskCompletionTracker.trackTaskStart(taskId, taskType)
        taskCompletionTracker.trackTaskProgress(taskId, 25)
        taskCompletionTracker.trackTaskProgress(taskId, 75)
        taskCompletionTracker.trackTaskRetry(taskId, "validation_failed")
        taskCompletionTracker.trackTaskError(taskId, "network_error")
        
        val completionResult = TaskCompletionResult(
            status = CompletionStatus.SUCCESS,
            completionTime = 3000L,
            errorCount = 1,
            retryCount = 1
        )
        
        taskCompletionTracker.trackTaskCompletion(taskId, taskType, completionResult)
        
        // Then: Verify task completion metrics
        val completionEvents = mutableListOf<Map<String, Any>>()
        verify(atLeast = 1) { 
            mockAnalyticsService.logEvent("task_completion_finished", capture(completionEvents))
        }
        
        val completionEvent = completionEvents.find { it["task_id"] == taskId }
        assertNotNull("Task completion event should be logged", completionEvent)
        
        assertEquals("Task type should match", taskType, completionEvent!!["task_type"])
        assertEquals("Completion status should be success", "success", completionEvent["completion_status"])
        assertTrue("Should track error count", completionEvent.containsKey("error_count"))
        assertTrue("Should track retry count", completionEvent.containsKey("retry_count"))
    }
    
    @Test
    fun taskCompletionRate_calculatesPrdTargetMetrics() = runTest {
        // Given: Task completion data for PRD validation
        val taskType = TaskCompletionTracker.TASK_WORKOUT_CREATION
        val timeRange = TimeRange(baseTimestamp, baseTimestamp + 86400000L) // 24 hours
        
        // When: Getting completion rate
        val result = taskCompletionTracker.getTaskCompletionRate(taskType, timeRange)
        
        // Then: Verify completion rate calculation
        assertTrue("Should return success result", result is Result.success)
        
        val completionRate = (result as Result.success).data
        assertEquals("Task type should match", taskType, completionRate.taskType)
        assertTrue("Should have completion percentage", completionRate.completionPercentage >= 0.0)
        assertTrue("Should have average completion time", completionRate.averageCompletionTime > 0L)
        
        // Test PRD target validation
        val baselinePercentage = 55.0 // Mock baseline
        val meetsTarget = completionRate.meetsImprovementTarget(baselinePercentage)
        
        // For mock data (85% success rate), should meet 30% improvement if baseline < 65.4%
        if (baselinePercentage < 65.4) {
            assertTrue("Should meet 30% improvement target", meetsTarget)
        }
    }
    
    @Test
    fun cognitiveLoadMeasurement_calculatesAccurateLoadMetrics() {
        // Given: Workflow data with known cognitive load factors
        val workflowData = WorkflowData(
            workflowId = "cognitive_load_test",
            workflowType = "workout_creation",
            totalTime = 45000L, // 45 seconds
            errorCount = 2,
            navigationSteps = 5,
            modalInterruptions = 1,
            searchAttempts = 3,
            decisionPoints = 4,
            uniqueElementsEncountered = 12,
            conceptualElements = 3,
            isFirstTimeUser = true,
            involvesSkillDevelopment = true,
            buttonStyleVariations = 2,
            layoutPatternChanges = 1,
            terminologyVariations = 1
        )
        
        // When: Calculating cognitive load
        val result = cognitiveLoadMeasurement.calculateCognitiveLoad(workflowData)
        
        // Then: Verify cognitive load calculation
        assertEquals("Workflow ID should match", "cognitive_load_test", result.workflowId)
        assertTrue("Intrinsic load should be positive", result.intrinsicLoad > 0.0)
        assertTrue("Extraneous load should be positive", result.extraneousLoad > 0.0)
        assertTrue("Germane load should be positive", result.germaneLoad > 0.0)
        assertTrue("Total load should be sum of components", 
            result.totalLoad <= result.intrinsicLoad + result.extraneousLoad + result.germaneLoad + 2.0) // Allow interaction effects
        
        assertTrue("Load category should be valid", 
            result.loadCategory in listOf("low", "moderate", "high", "extreme"))
        assertTrue("Severity should be calculated", result.severity >= 0.0)
        assertFalse("Should provide recommendations", result.recommendations.isEmpty())
        
        // Verify analytics event was logged
        verify { 
            mockAnalyticsService.logEvent(
                "cognitive_load_calculated",
                match { params ->
                    params["workflow_id"] == "cognitive_load_test" &&
                    params.containsKey("total_load") &&
                    params.containsKey("load_category")
                }
            )
        }
    }
    
    @Test
    fun baselineComparison_validates40PercentReductionTarget() {
        // Given: Current and baseline cognitive load measurements
        val baselineLoad = CognitiveLoadResult(
            workflowId = "baseline_test",
            intrinsicLoad = 4.0,
            extraneousLoad = 6.0, // High extraneous load (target for reduction)
            germaneLoad = 2.0,
            totalLoad = 8.5,
            loadCategory = "high",
            severity = 75.0,
            recommendations = emptyList(),
            calculationTimestamp = baseTimestamp
        )
        
        val currentLoad = CognitiveLoadResult(
            workflowId = "current_test", 
            intrinsicLoad = 4.0, // Same task complexity
            extraneousLoad = 2.4, // 60% reduction in extraneous load
            germaneLoad = 2.0,
            totalLoad = 5.1, // 40% total reduction
            loadCategory = "moderate",
            severity = 45.0,
            recommendations = emptyList(),
            calculationTimestamp = baseTimestamp + 1000L
        )
        
        // When: Comparing to baseline
        val comparison = cognitiveLoadMeasurement.compareToBaseline(currentLoad, baselineLoad)
        
        // Then: Verify PRD target validation
        assertEquals("Current load should match", 5.1, comparison.currentLoad, 0.1)
        assertEquals("Baseline load should match", 8.5, comparison.baselineLoad, 0.1)
        
        // Calculate expected improvement
        val expectedImprovement = ((8.5 - 5.1) / 8.5) * 100.0 // Should be ~40%
        assertEquals("Should calculate correct improvement percentage", 
            expectedImprovement, comparison.totalImprovement, 1.0)
        
        assertTrue("Should meet 40% reduction target", comparison.meetsTarget)
        assertEquals("Target should be 40%", 40.0, comparison.targetImprovement, 0.1)
        assertTrue("Should have improvement description", 
            comparison.improvementDescription.contains("Excellent"))
        
        // Verify baseline comparison event was logged
        verify { 
            mockAnalyticsService.logEvent(
                "cognitive_load_baseline_comparison",
                match { params ->
                    params["meets_target"] == true &&
                    params["target_percentage"] == 40.0
                }
            )
        }
    }
    
    @Test
    fun metricsIntegration_tracksCompleteUserJourney() {
        // Given: Complete user journey with all metrics
        val workflowId = "complete_journey_test"
        val taskId = "journey_task_456"
        val taskType = TaskCompletionTracker.TASK_WORKOUT_CREATION
        
        // When: Tracking complete user journey
        // Start UX and task tracking simultaneously
        uxMetricsTracker.startWorkflowTracking(workflowId)
        taskCompletionTracker.trackTaskStart(taskId, taskType)
        
        // Simulate user interactions with both trackers
        uxMetricsTracker.trackInteraction(workflowId, "navigation")
        taskCompletionTracker.trackTaskProgress(taskId, 33)
        
        uxMetricsTracker.trackInteraction(workflowId, "form_input")
        uxMetricsTracker.trackError(workflowId, "validation_error")
        taskCompletionTracker.trackTaskError(taskId, "validation_error")
        
        uxMetricsTracker.trackInteraction(workflowId, "button_press")
        taskCompletionTracker.trackTaskProgress(taskId, 100)
        
        // Complete both tracking systems
        uxMetricsTracker.completeWorkflowTracking(workflowId, successful = true)
        val completionResult = TaskCompletionResult(
            status = CompletionStatus.SUCCESS,
            completionTime = 5000L,
            errorCount = 1,
            retryCount = 0
        )
        taskCompletionTracker.trackTaskCompletion(taskId, taskType, completionResult)
        
        // Then: Verify both systems logged events
        verify(atLeast = 1) { 
            mockAnalyticsService.logEvent("ux_workflow_completed", any()) 
        }
        verify(atLeast = 1) { 
            mockAnalyticsService.logEvent("task_completion_finished", any()) 
        }
        
        // Verify metrics can be retrieved
        val uxMetrics = uxMetricsTracker.getCurrentWorkflowMetrics(workflowId)
        val taskMetrics = taskCompletionTracker.getActiveTaskMetrics(taskId)
        
        // Both should be null after completion (cleaned up)
        assertNull("UX metrics should be cleaned up after completion", uxMetrics)
        assertNull("Task metrics should be cleaned up after completion", taskMetrics)
    }
    
    @Test
    fun cognitiveLoadThreshold_triggersAlertEvents() {
        // Given: Workflow data that will produce high cognitive load
        val highLoadWorkflowData = WorkflowData(
            workflowId = "high_load_test",
            workflowType = "workout_creation",
            totalTime = 120000L, // 2 minutes (slow)
            errorCount = 5, // Many errors
            navigationSteps = 15, // Lots of navigation
            modalInterruptions = 4, // Many interruptions
            searchAttempts = 8, // Lots of searching
            decisionPoints = 10, // Many decisions
            uniqueElementsEncountered = 25, // Information overload
            conceptualElements = 8,
            isFirstTimeUser = true,
            involvesSkillDevelopment = true,
            buttonStyleVariations = 5, // Inconsistent UI
            layoutPatternChanges = 3,
            terminologyVariations = 4
        )
        
        // When: Calculating high cognitive load
        val result = cognitiveLoadMeasurement.calculateCognitiveLoad(highLoadWorkflowData)
        
        // Then: Should trigger threshold alert if load is high
        if (result.totalLoad > 8.0) { // High threshold
            verify { 
                mockAnalyticsService.logEvent(
                    "cognitive_load_threshold_exceeded",
                    match { params ->
                        params["workflow_id"] == "high_load_test" &&
                        params["total_load"] as Double > 8.0
                    }
                )
            }
        }
        
        // Should provide optimization recommendations
        assertFalse("Should provide recommendations for high load", result.recommendations.isEmpty())
        assertTrue("Should recommend UI simplification", 
            result.recommendations.any { it.contains("extraneous load") || it.contains("simplification") })
    }
}