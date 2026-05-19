package com.example.liftrix.coroutines

import com.example.liftrix.analytics.CognitiveLoadMeasurement
import com.example.liftrix.analytics.WorkflowData
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.service.AnalyticsService
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CoroutineScopeHardeningTest {

    @Test
    fun cognitiveLoadMeasurementUsesInjectedScopeForAnalytics() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val analytics = RecordingAnalyticsService()
        val measurement = CognitiveLoadMeasurement(analytics, scope)

        measurement.calculateCognitiveLoad(workflowData())

        assertEquals(emptyList<String>(), analytics.loggedEvents)

        scope.advanceUntilIdle()

        assertEquals(listOf("cognitive_load_calculated"), analytics.loggedEvents)
    }

    private fun workflowData(): WorkflowData = WorkflowData(
        workflowId = "workflow-1",
        workflowType = "navigation",
        totalTime = 100,
        errorCount = 0,
        navigationSteps = 1,
        modalInterruptions = 0,
        searchAttempts = 0,
        decisionPoints = 1,
        uniqueElementsEncountered = 2,
        conceptualElements = 1,
        isFirstTimeUser = false,
        involvesSkillDevelopment = false,
        buttonStyleVariations = 0,
        layoutPatternChanges = 0,
        terminologyVariations = 0
    )

    private class RecordingAnalyticsService : AnalyticsService {
        val loggedEvents = mutableListOf<String>()

        override suspend fun setUserProperties(user: User): Result<Unit> = Result.success(Unit)
        override suspend fun logWorkoutStart(userId: String, workoutId: String, workoutName: String): Result<Unit> = Result.success(Unit)
        override suspend fun logWorkoutComplete(userId: String, workoutId: String, workoutName: String, metrics: Any, durationMinutes: Long?): Result<Unit> = Result.success(Unit)
        override suspend fun logWorkoutCreationEvent(userId: String, workoutId: String, workoutName: String, workoutType: String, exerciseCount: Int): Result<Unit> = Result.success(Unit)
        override suspend fun logExerciseSelectionEvent(userId: String, exerciseId: String, exerciseName: String, selectionMethod: String): Result<Unit> = Result.success(Unit)
        override suspend fun logPersonalRecord(userId: String, exerciseName: String, recordType: String, newValue: Double, previousValue: Double?): Result<Unit> = Result.success(Unit)
        override suspend fun logAiSummaryViewed(userId: String, workoutId: String, summaryType: String): Result<Unit> = Result.success(Unit)
        override suspend fun logSpotterAdded(userId: String, spotterUserId: String, connectionType: String): Result<Unit> = Result.success(Unit)
        override suspend fun logFriendRequestSent(userId: String, targetUserId: String): Result<Unit> = Result.success(Unit)
        override suspend fun logFriendRequestResponse(userId: String, targetUserId: String, accepted: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun logWorkoutShared(userId: String, workoutId: String, workoutName: String, shareMethod: String): Result<Unit> = Result.success(Unit)
        override suspend fun logSocialWorkoutViewed(userId: String, workoutId: String, friendUserId: String, workoutName: String): Result<Unit> = Result.success(Unit)
        override suspend fun logSocialFeedEvent(userId: String, eventType: String, additionalData: Map<String, Any>): Result<Unit> = Result.success(Unit)
        override suspend fun logEvent(eventName: String, parameters: Map<String, Any>): Result<Unit> {
            loggedEvents += eventName
            return Result.success(Unit)
        }
        override suspend fun logUxWorkflowStart(workflowId: String, workflowType: String, userId: String): Result<Unit> = Result.success(Unit)
        override suspend fun logUxWorkflowInteraction(workflowId: String, interactionType: String, interactionCount: Int): Result<Unit> = Result.success(Unit)
        override suspend fun logUxWorkflowCompletion(workflowId: String, completionTimeMs: Long, totalInteractions: Int, successful: Boolean, efficiencyScore: Double, cognitiveLoadScore: Double): Result<Unit> = Result.success(Unit)
        override suspend fun logTaskCompletionMetrics(taskId: String, taskType: String, completionStatus: String, completionTimeMs: Long, errorCount: Int, retryCount: Int): Result<Unit> = Result.success(Unit)
        override suspend fun recordException(throwable: Throwable, additionalData: Map<String, String>): Result<Unit> = Result.success(Unit)
        override suspend fun setCustomKey(key: String, value: String): Result<Unit> = Result.success(Unit)
        override suspend fun trackFeedLoadTime(duration: Long): Result<Unit> = Result.success(Unit)
        override suspend fun trackUserDiscoveryEngagement(action: String, additionalData: Map<String, Any>): Result<Unit> = Result.success(Unit)
        override suspend fun trackFeedScrollDepth(itemCount: Int): Result<Unit> = Result.success(Unit)
        override suspend fun clearUserProperties(): Result<Unit> = Result.success(Unit)
    }
}
