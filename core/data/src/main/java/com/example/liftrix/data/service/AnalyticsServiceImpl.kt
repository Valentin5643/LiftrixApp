package com.example.liftrix.data.service

import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.WorkoutMetrics
import com.example.liftrix.domain.service.AnalyticsService
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsServiceImpl @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics,
    private val firebaseCrashlytics: FirebaseCrashlytics
) : AnalyticsService {

    companion object {
        // Custom event names
        private const val EVENT_WORKOUT_STARTED = "workout_started"
        private const val EVENT_WORKOUT_COMPLETED = "workout_completed"
        private const val EVENT_WORKOUT_CREATED = "workout_created"
        private const val EVENT_EXERCISE_SELECTED = "exercise_selected"
        private const val EVENT_PR_ACHIEVED = "pr_achieved"
        private const val EVENT_AI_SUMMARY_VIEWED = "ai_summary_viewed"
        private const val EVENT_SPOTTER_ADDED = "spotter_added"
        
        // Social event names
        private const val EVENT_FRIEND_REQUEST_SENT = "friend_request_sent"
        private const val EVENT_FRIEND_REQUEST_RESPONSE = "friend_request_response"
        private const val EVENT_WORKOUT_SHARED = "workout_shared"
        private const val EVENT_SOCIAL_WORKOUT_VIEWED = "social_workout_viewed"
        private const val EVENT_SOCIAL_FEED_INTERACTION = "social_feed_interaction"
        
        // Performance monitoring event names
        private const val EVENT_FEED_LOAD_PERFORMANCE = "feed_load_performance"
        private const val EVENT_USER_DISCOVERY_ENGAGEMENT = "user_discovery_engagement"
        private const val EVENT_FEED_SCROLL_DEPTH = "feed_scroll_depth"
        
        // UX Metrics event names for PRD success tracking
        private const val EVENT_UX_WORKFLOW_START = "ux_workflow_started"
        private const val EVENT_UX_WORKFLOW_INTERACTION = "ux_workflow_interaction"
        private const val EVENT_UX_WORKFLOW_COMPLETION = "ux_workflow_completed"
        private const val EVENT_TASK_COMPLETION = "task_completion_finished"
        
        // Enhanced performance monitoring event names for PERF-003
        private const val EVENT_60FPS_VALIDATION = "performance_60fps_validation"
        private const val EVENT_PERFORMANCE_REGRESSION = "performance_regression_detected"
        private const val EVENT_COGNITIVE_LOAD_MEASUREMENT = "cognitive_load_measurement"
        private const val EVENT_INTERACTION_PATTERN_ANALYSIS = "interaction_pattern_analysis"
        private const val EVENT_CHOREOGRAPHER_PERFORMANCE = "choreographer_performance_measurement"
        
        // Parameter names
        private const val PARAM_WORKOUT_ID = "workout_id"
        private const val PARAM_WORKOUT_NAME = "workout_name"
        private const val PARAM_EXERCISE_COUNT = "exercise_count"
        private const val PARAM_TOTAL_SETS = "total_sets"
        private const val PARAM_COMPLETED_SETS = "completed_sets"
        private const val PARAM_COMPLETION_PERCENTAGE = "completion_percentage"
        private const val PARAM_DURATION_MINUTES = "duration_minutes"
        private const val PARAM_TOTAL_VOLUME_KG = "total_volume_kg"
        private const val PARAM_EXERCISE_NAME = "exercise_name"
        private const val PARAM_EXERCISE_ID = "exercise_id"
        private const val PARAM_SELECTION_METHOD = "selection_method"
        private const val PARAM_RECORD_TYPE = "record_type"
        private const val PARAM_NEW_VALUE = "new_value"
        private const val PARAM_PREVIOUS_VALUE = "previous_value"
        private const val PARAM_SUMMARY_TYPE = "summary_type"
        private const val PARAM_SPOTTER_USER_ID = "spotter_user_id"
        private const val PARAM_CONNECTION_TYPE = "connection_type"
        private const val PARAM_WORKOUT_TYPE = "workout_type"
        
        // Performance monitoring parameter names
        private const val PARAM_LOAD_TIME_MS = "load_time_ms"
        private const val PARAM_SCREEN = "screen"
        private const val PARAM_ACTION = "action"
        private const val PARAM_ITEM_COUNT = "item_count"
        
        // Social parameter names
        private const val PARAM_TARGET_USER_ID = "target_user_id"
        private const val PARAM_FRIEND_USER_ID = "friend_user_id"
        private const val PARAM_ACCEPTED = "accepted"
        private const val PARAM_SHARE_METHOD = "share_method"
        private const val PARAM_EVENT_TYPE = "event_type"
        
        // UX Metrics parameter names for PRD success tracking
        private const val PARAM_WORKFLOW_ID = "workflow_id"
        private const val PARAM_WORKFLOW_TYPE = "workflow_type"
        private const val PARAM_INTERACTION_TYPE = "interaction_type"
        private const val PARAM_INTERACTION_COUNT = "interaction_count"
        private const val PARAM_COMPLETION_TIME_MS = "completion_time_ms"
        private const val PARAM_TOTAL_INTERACTIONS = "total_interactions"
        private const val PARAM_SUCCESSFUL = "successful"
        private const val PARAM_EFFICIENCY_SCORE = "efficiency_score"
        private const val PARAM_COGNITIVE_LOAD_SCORE = "cognitive_load_score"
        private const val PARAM_TASK_ID = "task_id"
        private const val PARAM_TASK_TYPE = "task_type"
        private const val PARAM_COMPLETION_STATUS = "completion_status"
        private const val PARAM_ERROR_COUNT = "error_count"
        private const val PARAM_RETRY_COUNT = "retry_count"
        
        // Enhanced performance monitoring parameter names for PERF-003
        private const val PARAM_AVERAGE_FPS = "average_fps"
        private const val PARAM_FRAME_DROP_COUNT = "frame_drop_count"
        private const val PARAM_FRAME_DROP_PERCENTAGE = "frame_drop_percentage"
        private const val PARAM_MEETS_60FPS_TARGET = "meets_60fps_target"
        private const val PARAM_CHOREOGRAPHER_DURATION_MS = "choreographer_duration_ms"
        private const val PARAM_PERFORMANCE_REGRESSION_TYPE = "performance_regression_type"
        private const val PARAM_BASELINE_PERFORMANCE = "baseline_performance"
        private const val PARAM_CURRENT_PERFORMANCE = "current_performance"
        private const val PARAM_INTERACTION_PATTERN_TYPE = "interaction_pattern_type"
        private const val PARAM_PATTERN_SCORE = "pattern_score"
        private const val PARAM_RAPID_INTERACTIONS = "rapid_interactions"
        private const val PARAM_LONG_PAUSES = "long_pauses"
        
        // UI Redesign Production Rollout event names for PROD-001
        private const val EVENT_UI_REDESIGN_FEATURE_FLAG_EVALUATION = "ui_redesign_feature_flag_evaluation"
        private const val EVENT_UI_REDESIGN_USER_SESSION_START = "ui_redesign_session_start"
        private const val EVENT_UI_REDESIGN_USER_SESSION_END = "ui_redesign_session_end"
        private const val EVENT_UI_REDESIGN_PERFORMANCE_MONITORING = "ui_redesign_performance_monitoring"
        private const val EVENT_UI_REDESIGN_ROLLBACK_TRIGGERED = "ui_redesign_rollback_triggered"
        private const val EVENT_UI_REDESIGN_USER_FEEDBACK_COLLECTED = "ui_redesign_feedback_collected"
        private const val EVENT_UI_REDESIGN_AB_TEST_ASSIGNMENT = "ui_redesign_ab_test_assignment"
        private const val EVENT_UI_REDESIGN_COMPONENT_INTERACTION = "ui_redesign_component_interaction"
        
        // UI Redesign Production Rollout parameter names for PROD-001
        private const val PARAM_UI_REDESIGN_ENABLED = "ui_redesign_enabled"
        private const val PARAM_ROLLOUT_PERCENTAGE = "rollout_percentage"
        private const val PARAM_FEATURE_FLAG_RESULT = "feature_flag_result"
        private const val PARAM_SESSION_TYPE = "session_type"
        private const val PARAM_ROLLBACK_REASON = "rollback_reason"
        private const val PARAM_ROLLBACK_SEVERITY = "rollback_severity"
        private const val PARAM_FEEDBACK_RATING = "feedback_rating"
        private const val PARAM_FEEDBACK_CATEGORY = "feedback_category"
        private const val PARAM_FEEDBACK_SENTIMENT = "feedback_sentiment"
        private const val PARAM_AB_TEST_GROUP = "ab_test_group"
        private const val PARAM_COMPONENT_TYPE = "component_type"
        private const val PARAM_INTERACTION_SUCCESS = "interaction_success"
        
        // User property names
        private const val USER_PROP_SUBSCRIPTION_TIER = "subscription_tier"
        private const val USER_PROP_IS_PREMIUM = "is_premium"
        private const val USER_PROP_IS_ANONYMOUS = "is_anonymous"
        private const val USER_PROP_ONBOARDING_COMPLETED = "onboarding_completed"
        
        // Crashlytics custom keys
        private const val CRASHLYTICS_USER_ID = "user_pseudonymous_id"
        private const val CRASHLYTICS_SUBSCRIPTION_TIER = "subscription_tier"
        private const val CRASHLYTICS_APP_VERSION = "app_version"
    }

    override suspend fun setUserProperties(user: User): Result<Unit> {
        return try {
            val pseudonymousUserId = user.uid.toPseudonymousUserId()

            // Set Firebase Analytics user properties
            firebaseAnalytics.setUserId(pseudonymousUserId)
            firebaseAnalytics.setUserProperty(USER_PROP_SUBSCRIPTION_TIER, user.subscriptionTier.name.lowercase())
            firebaseAnalytics.setUserProperty(USER_PROP_IS_PREMIUM, user.isPremium.toString())
            firebaseAnalytics.setUserProperty(USER_PROP_IS_ANONYMOUS, user.isAnonymous.toString())
            firebaseAnalytics.setUserProperty(USER_PROP_ONBOARDING_COMPLETED, user.onboardingCompleted.toString())
            
            // Set minimal Crashlytics context without raw account identifiers.
            firebaseCrashlytics.setUserId(pseudonymousUserId)
            firebaseCrashlytics.setCustomKey(CRASHLYTICS_USER_ID, pseudonymousUserId)
            firebaseCrashlytics.setCustomKey(CRASHLYTICS_SUBSCRIPTION_TIER, user.subscriptionTier.name)
            firebaseCrashlytics.setCustomKey("is_premium", user.isPremium.toString())
            
            Timber.d("User properties set successfully")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to set user properties")
            Result.failure(exception)
        }
    }

    override suspend fun logWorkoutStart(
        userId: String,
        workoutId: String,
        workoutName: String
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_WORKOUT_STARTED) {
                param(FirebaseAnalytics.Param.ITEM_ID, workoutId)
                param(FirebaseAnalytics.Param.ITEM_NAME, workoutName)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "workout")
            }
            
            // Set context for potential crashes during workout
            firebaseCrashlytics.setCustomKey("current_workout_id", workoutId)
            firebaseCrashlytics.setCustomKey("workout_phase", "started")
            
            Timber.d("Workout start logged: $workoutId")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log workout start: $workoutId")
            Result.failure(exception)
        }
    }

    override suspend fun logWorkoutComplete(
        userId: String,
        workoutId: String,
        workoutName: String,
        metrics: Any,
        durationMinutes: Long?
    ): Result<Unit> {
        return try {
            val workoutMetrics = metrics as? WorkoutMetrics
            firebaseAnalytics.logEvent(EVENT_WORKOUT_COMPLETED) {
                param(FirebaseAnalytics.Param.ITEM_ID, workoutId)
                param(FirebaseAnalytics.Param.ITEM_NAME, workoutName)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "workout")
                param(PARAM_EXERCISE_COUNT, workoutMetrics?.exerciseCount?.toLong() ?: 0L)
                param(PARAM_TOTAL_SETS, workoutMetrics?.totalSets?.toLong() ?: 0L)
                param(PARAM_COMPLETED_SETS, workoutMetrics?.completedSets?.toLong() ?: 0L)
                param(PARAM_COMPLETION_PERCENTAGE, workoutMetrics?.completionPercentage ?: 0.0)
                param(PARAM_TOTAL_VOLUME_KG, workoutMetrics?.totalVolume?.kilograms ?: 0.0)
                durationMinutes?.let { param(PARAM_DURATION_MINUTES, it) }
            }
            
            // Clear workout context from crashlytics
            firebaseCrashlytics.setCustomKey("current_workout_id", "")
            firebaseCrashlytics.setCustomKey("workout_phase", "completed")
            
            Timber.d("Workout completion logged: $workoutId with ${workoutMetrics?.exerciseCount ?: 0} exercises, ${workoutMetrics?.totalSets ?: 0} sets")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log workout completion: $workoutId")
            Result.failure(exception)
        }
    }

    override suspend fun logWorkoutCreationEvent(
        userId: String,
        workoutId: String,
        workoutName: String,
        workoutType: String,
        exerciseCount: Int
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_WORKOUT_CREATED) {
                param(FirebaseAnalytics.Param.ITEM_ID, workoutId)
                param(FirebaseAnalytics.Param.ITEM_NAME, workoutName)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "workout")
                param(PARAM_WORKOUT_TYPE, workoutType)
                param(PARAM_EXERCISE_COUNT, exerciseCount.toLong())
            }
            
            Timber.d("Workout creation logged: $workoutId ($workoutType) with $exerciseCount exercises")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log workout creation: $workoutId")
            Result.failure(exception)
        }
    }

    override suspend fun logExerciseSelectionEvent(
        userId: String,
        exerciseId: String,
        exerciseName: String,
        selectionMethod: String
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_EXERCISE_SELECTED) {
                param(PARAM_EXERCISE_ID, exerciseId)
                param(PARAM_EXERCISE_NAME, exerciseName)
                param(PARAM_SELECTION_METHOD, selectionMethod)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "exercise_selection")
            }
            
            Timber.d("Exercise selection logged: $exerciseName via $selectionMethod")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log exercise selection: $exerciseName")
            Result.failure(exception)
        }
    }

    override suspend fun logPersonalRecord(
        userId: String,
        exerciseName: String,
        recordType: String,
        newValue: Double,
        previousValue: Double?
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_PR_ACHIEVED) {
                param("exercise_name", exerciseName)
                param("record_type", recordType)
                param("new_value", newValue)
                previousValue?.let { param("previous_value", it) }
            }
            
            Timber.d("Personal record logged: $exerciseName $recordType = $newValue")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log personal record: $exerciseName")
            Result.failure(exception)
        }
    }

    override suspend fun logAiSummaryViewed(
        userId: String,
        workoutId: String,
        summaryType: String
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_AI_SUMMARY_VIEWED) {
                param("workout_id", workoutId)
                param("summary_type", summaryType)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "ai_summary")
            }
            
            Timber.d("AI summary view logged: $summaryType for workout: $workoutId")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log AI summary view: $workoutId")
            Result.failure(exception)
        }
    }

    override suspend fun logSpotterAdded(
        userId: String,
        spotterUserId: String,
        connectionType: String
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_SPOTTER_ADDED) {
                param("spotter_user_id", spotterUserId)
                param("connection_type", connectionType)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "social_connection")
            }
            
            Timber.d("Spotter addition logged: $connectionType connection")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log spotter addition")
            Result.failure(exception)
        }
    }

    override suspend fun logEvent(
        eventName: String,
        parameters: Map<String, Any>
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(eventName) {
                parameters.forEach { (key, value) ->
                    when (value) {
                        is String -> param(key, value)
                        is Long -> param(key, value)
                        is Double -> param(key, value)
                        is Boolean -> param(key, value.toString())
                        is Int -> param(key, value.toLong())
                        is Float -> param(key, value.toDouble())
                        else -> param(key, value.toString())
                    }
                }
            }
            
            Timber.d("Custom event logged: $eventName with ${parameters.size} parameters")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log custom event: $eventName")
            Result.failure(exception)
        }
    }

    override suspend fun recordException(
        throwable: Throwable,
        additionalData: Map<String, String>
    ): Result<Unit> {
        return try {
            // Set additional context data
            additionalData.forEach { (key, value) ->
                firebaseCrashlytics.setCustomKey(key, value)
            }
            
            // Record the exception
            firebaseCrashlytics.recordException(throwable)
            
            Timber.d("Exception recorded: ${throwable.javaClass.simpleName} - ${throwable.message}")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to record exception")
            Result.failure(exception)
        }
    }

    override suspend fun setCustomKey(key: String, value: String): Result<Unit> {
        return try {
            firebaseCrashlytics.setCustomKey(key, value)
            Timber.d("Custom key set: $key = $value")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to set custom key: $key")
            Result.failure(exception)
        }
    }

    override suspend fun logFriendRequestSent(
        userId: String,
        targetUserId: String
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_FRIEND_REQUEST_SENT) {
                param(PARAM_TARGET_USER_ID, targetUserId)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "social_interaction")
            }
            
            Timber.d("Friend request sent logged")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log friend request sent")
            Result.failure(exception)
        }
    }

    override suspend fun logFriendRequestResponse(
        userId: String,
        targetUserId: String,
        accepted: Boolean
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_FRIEND_REQUEST_RESPONSE) {
                param(PARAM_TARGET_USER_ID, targetUserId)
                param(PARAM_ACCEPTED, accepted.toString())
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "social_interaction")
            }
            
            val action = if (accepted) "accepted" else "declined"
            Timber.d("Friend request $action logged")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log friend request response")
            Result.failure(exception)
        }
    }

    override suspend fun logWorkoutShared(
        userId: String,
        workoutId: String,
        workoutName: String,
        shareMethod: String
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_WORKOUT_SHARED) {
                param(FirebaseAnalytics.Param.ITEM_ID, workoutId)
                param(FirebaseAnalytics.Param.ITEM_NAME, workoutName)
                param(PARAM_SHARE_METHOD, shareMethod)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "workout_sharing")
            }
            
            Timber.d("Workout shared logged: $workoutId via $shareMethod")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log workout sharing")
            Result.failure(exception)
        }
    }

    override suspend fun logSocialWorkoutViewed(
        userId: String,
        workoutId: String,
        friendUserId: String,
        workoutName: String
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_SOCIAL_WORKOUT_VIEWED) {
                param(FirebaseAnalytics.Param.ITEM_ID, workoutId)
                param(FirebaseAnalytics.Param.ITEM_NAME, workoutName)
                param(PARAM_FRIEND_USER_ID, friendUserId)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "social_content")
            }
            
            Timber.d("Social workout viewed logged: $workoutId from friend $friendUserId")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log social workout viewed")
            Result.failure(exception)
        }
    }

    override suspend fun logSocialFeedEvent(
        userId: String,
        eventType: String,
        additionalData: Map<String, Any>
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_SOCIAL_FEED_INTERACTION) {
                param(PARAM_EVENT_TYPE, eventType)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "social_feed")
                
                // Add additional parameters
                additionalData.forEach { (key, value) ->
                    when (value) {
                        is String -> param(key, value)
                        is Long -> param(key, value)
                        is Double -> param(key, value)
                        is Boolean -> param(key, value.toString())
                        is Int -> param(key, value.toLong())
                        is Float -> param(key, value.toDouble())
                        else -> param(key, value.toString())
                    }
                }
            }
            
            Timber.d("Social feed event logged: $eventType with ${additionalData.size} parameters")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log social feed event: $eventType")
            Result.failure(exception)
        }
    }

    override suspend fun trackFeedLoadTime(duration: Long): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_FEED_LOAD_PERFORMANCE) {
                param(PARAM_LOAD_TIME_MS, duration)
                param(PARAM_SCREEN, "home_feed")
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "performance_monitoring")
            }
            
            // Log performance alert if load time exceeds threshold
            if (duration > 2000) {
                firebaseAnalytics.logEvent("performance_issue") {
                    param("issue_type", "feed_load_slow")
                    param(PARAM_LOAD_TIME_MS, duration)
                    param("threshold_ms", 2000L)
                }
            }
            
            Timber.d("Feed load time tracked: ${duration}ms")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track feed load time")
            Result.failure(exception)
        }
    }

    override suspend fun trackUserDiscoveryEngagement(
        action: String,
        additionalData: Map<String, Any>
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_USER_DISCOVERY_ENGAGEMENT) {
                param(PARAM_ACTION, action)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "user_engagement")
                
                // Add additional parameters
                additionalData.forEach { (key, value) ->
                    when (value) {
                        is String -> param(key, value)
                        is Long -> param(key, value)
                        is Double -> param(key, value)
                        is Boolean -> param(key, value.toString())
                        is Int -> param(key, value.toLong())
                        is Float -> param(key, value.toDouble())
                        else -> param(key, value.toString())
                    }
                }
            }
            
            Timber.d("User discovery engagement tracked: $action with ${additionalData.size} parameters")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track user discovery engagement: $action")
            Result.failure(exception)
        }
    }

    override suspend fun trackFeedScrollDepth(itemCount: Int): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_FEED_SCROLL_DEPTH) {
                param(PARAM_ITEM_COUNT, itemCount.toLong())
                param(PARAM_SCREEN, "home_feed")
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "engagement_tracking")
            }
            
            Timber.d("Feed scroll depth tracked: $itemCount items")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track feed scroll depth")
            Result.failure(exception)
        }
    }

    override suspend fun clearUserProperties(): Result<Unit> {
        return try {
            // Clear Analytics user properties
            firebaseAnalytics.setUserId(null)
            firebaseAnalytics.setUserProperty(USER_PROP_SUBSCRIPTION_TIER, null)
            firebaseAnalytics.setUserProperty(USER_PROP_IS_PREMIUM, null)
            firebaseAnalytics.setUserProperty(USER_PROP_IS_ANONYMOUS, null)
            firebaseAnalytics.setUserProperty(USER_PROP_ONBOARDING_COMPLETED, null)
            
            // Clear Crashlytics user context
            firebaseCrashlytics.setUserId("")
            firebaseCrashlytics.setCustomKey(CRASHLYTICS_USER_ID, "")
            firebaseCrashlytics.setCustomKey(CRASHLYTICS_SUBSCRIPTION_TIER, "")
            firebaseCrashlytics.setCustomKey("current_workout_id", "")
            firebaseCrashlytics.setCustomKey("workout_phase", "")
            
            Timber.d("User properties cleared")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to clear user properties")
            Result.failure(exception)
        }
    }
    
    // UX Metrics methods for PRD success tracking
    
    override suspend fun logUxWorkflowStart(
        workflowId: String,
        workflowType: String,
        userId: String
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_UX_WORKFLOW_START) {
                param(PARAM_WORKFLOW_ID, workflowId)
                param(PARAM_WORKFLOW_TYPE, workflowType)
                param("user_id", userId)
                param("timestamp", System.currentTimeMillis())
            }
            
            Timber.d("UX workflow start logged: $workflowType ($workflowId)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log UX workflow start")
            Result.failure(exception)
        }
    }
    
    override suspend fun logUxWorkflowInteraction(
        workflowId: String,
        interactionType: String,
        interactionCount: Int
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_UX_WORKFLOW_INTERACTION) {
                param(PARAM_WORKFLOW_ID, workflowId)
                param(PARAM_INTERACTION_TYPE, interactionType)
                param(PARAM_INTERACTION_COUNT, interactionCount.toLong())
            }
            
            Timber.v("UX workflow interaction logged: $interactionType (#$interactionCount)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log UX workflow interaction")
            Result.failure(exception)
        }
    }
    
    override suspend fun logUxWorkflowCompletion(
        workflowId: String,
        completionTimeMs: Long,
        totalInteractions: Int,
        successful: Boolean,
        efficiencyScore: Double,
        cognitiveLoadScore: Double
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_UX_WORKFLOW_COMPLETION) {
                param(PARAM_WORKFLOW_ID, workflowId)
                param(PARAM_COMPLETION_TIME_MS, completionTimeMs)
                param(PARAM_TOTAL_INTERACTIONS, totalInteractions.toLong())
                param(PARAM_SUCCESSFUL, if (successful) 1L else 0L)
                param(PARAM_EFFICIENCY_SCORE, efficiencyScore)
                param(PARAM_COGNITIVE_LOAD_SCORE, cognitiveLoadScore)
            }
            
            Timber.i("UX workflow completion logged: $workflowId (${completionTimeMs}ms, efficiency: $efficiencyScore, load: $cognitiveLoadScore)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log UX workflow completion")
            Result.failure(exception)
        }
    }
    
    override suspend fun logTaskCompletionMetrics(
        taskId: String,
        taskType: String,
        completionStatus: String,
        completionTimeMs: Long,
        errorCount: Int,
        retryCount: Int
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_TASK_COMPLETION) {
                param(PARAM_TASK_ID, taskId)
                param(PARAM_TASK_TYPE, taskType)
                param(PARAM_COMPLETION_STATUS, completionStatus)
                param(PARAM_COMPLETION_TIME_MS, completionTimeMs)
                param(PARAM_ERROR_COUNT, errorCount.toLong())
                param(PARAM_RETRY_COUNT, retryCount.toLong())
            }
            
            Timber.i("Task completion metrics logged: $taskType ($completionStatus) in ${completionTimeMs}ms")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log task completion metrics")
            Result.failure(exception)
        }
    }
    
    // Enhanced Performance Monitoring Methods for PERF-003
    
    /**
     * Log 60fps validation results from Choreographer-based monitoring
     * @param sessionId Performance monitoring session identifier
     * @param interactionType Type of interaction being validated
     * @param averageFps Measured average frames per second
     * @param frameDropCount Number of dropped frames
     * @param frameDropPercentage Percentage of frames dropped
     * @param meets60FpsTarget Whether performance meets 60fps target
     * @param durationMs Duration of monitoring session
     */
    suspend fun log60FpsValidation(
        sessionId: String,
        interactionType: String,
        averageFps: Float,
        frameDropCount: Int,
        frameDropPercentage: Double,
        meets60FpsTarget: Boolean,
        durationMs: Long
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_60FPS_VALIDATION) {
                param("session_id", sessionId)
                param(PARAM_INTERACTION_TYPE, interactionType)
                param(PARAM_AVERAGE_FPS, averageFps.toDouble())
                param(PARAM_FRAME_DROP_COUNT, frameDropCount.toLong())
                param(PARAM_FRAME_DROP_PERCENTAGE, frameDropPercentage)
                param(PARAM_MEETS_60FPS_TARGET, if (meets60FpsTarget) 1L else 0L)
                param(PARAM_CHOREOGRAPHER_DURATION_MS, durationMs)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "performance_validation")
            }
            
            Timber.d("60fps validation logged: $interactionType (${averageFps}fps, target met: $meets60FpsTarget)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log 60fps validation")
            Result.failure(exception)
        }
    }
    
    /**
     * Log performance regression detection for CI/CD monitoring
     * @param component Component experiencing regression
     * @param regressionType Type of performance regression detected
     * @param baselinePerformance Baseline performance metric
     * @param currentPerformance Current performance metric
     * @param severity Severity level of regression
     */
    suspend fun logPerformanceRegression(
        component: String,
        regressionType: String,
        baselinePerformance: Double,
        currentPerformance: Double,
        severity: String
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_PERFORMANCE_REGRESSION) {
                param("component_name", component)
                param(PARAM_PERFORMANCE_REGRESSION_TYPE, regressionType)
                param(PARAM_BASELINE_PERFORMANCE, baselinePerformance)
                param(PARAM_CURRENT_PERFORMANCE, currentPerformance)
                param("severity", severity)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "performance_regression")
            }
            
            Timber.w("Performance regression logged: $component $regressionType regression ($severity)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log performance regression")
            Result.failure(exception)
        }
    }
    
    /**
     * Log cognitive load measurement for UX analysis
     * @param workflowId Workflow identifier
     * @param workflowType Type of workflow
     * @param cognitiveLoadScore Calculated cognitive load score (0.0 to 1.0)
     * @param interactionCount Number of user interactions
     * @param errorCount Number of errors encountered
     * @param completionTime Time taken to complete workflow
     */
    suspend fun logCognitiveLoadMeasurement(
        workflowId: String,
        workflowType: String,
        cognitiveLoadScore: Double,
        interactionCount: Int,
        errorCount: Int,
        completionTime: Long
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_COGNITIVE_LOAD_MEASUREMENT) {
                param(PARAM_WORKFLOW_ID, workflowId)
                param(PARAM_WORKFLOW_TYPE, workflowType)
                param(PARAM_COGNITIVE_LOAD_SCORE, cognitiveLoadScore)
                param(PARAM_INTERACTION_COUNT, interactionCount.toLong())
                param(PARAM_ERROR_COUNT, errorCount.toLong())
                param(PARAM_COMPLETION_TIME_MS, completionTime)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "cognitive_load_analysis")
            }
            
            Timber.d("Cognitive load measurement logged: $workflowType (score: $cognitiveLoadScore)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log cognitive load measurement")
            Result.failure(exception)
        }
    }
    
    /**
     * Log interaction pattern analysis for UX optimization
     * @param workflowId Workflow identifier
     * @param patternType Type of interaction pattern detected
     * @param patternScore Score representing pattern efficiency/confusion
     * @param rapidInteractions Number of rapid successive interactions
     * @param longPauses Number of long pauses (hesitation indicators)
     * @param patternDescription Description of detected pattern
     */
    suspend fun logInteractionPatternAnalysis(
        workflowId: String,
        patternType: String,
        patternScore: Double,
        rapidInteractions: Int,
        longPauses: Int,
        patternDescription: String
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_INTERACTION_PATTERN_ANALYSIS) {
                param(PARAM_WORKFLOW_ID, workflowId)
                param(PARAM_INTERACTION_PATTERN_TYPE, patternType)
                param(PARAM_PATTERN_SCORE, patternScore)
                param(PARAM_RAPID_INTERACTIONS, rapidInteractions.toLong())
                param(PARAM_LONG_PAUSES, longPauses.toLong())
                param("pattern_description", patternDescription)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "interaction_pattern")
            }
            
            Timber.d("Interaction pattern analysis logged: $patternType (score: $patternScore)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log interaction pattern analysis")
            Result.failure(exception)
        }
    }
    
    /**
     * Log Choreographer-based performance measurement for detailed frame analysis
     * @param sessionId Monitoring session identifier
     * @param component Component being monitored
     * @param frameMetrics Detailed frame timing metrics
     * @param memoryUsage Memory usage during monitoring
     * @param deviceInfo Device specifications for context
     */
    suspend fun logChoreographerPerformance(
        sessionId: String,
        component: String,
        frameMetrics: Map<String, Any>,
        memoryUsage: Float,
        deviceInfo: Map<String, String>
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_CHOREOGRAPHER_PERFORMANCE) {
                param("session_id", sessionId)
                param("component_name", component)
                param("memory_usage_mb", memoryUsage.toDouble())
                
                // Add frame metrics
                frameMetrics.forEach { (key, value) ->
                    when (value) {
                        is String -> param("frame_$key", value)
                        is Long -> param("frame_$key", value)
                        is Double -> param("frame_$key", value)
                        is Float -> param("frame_$key", value.toDouble())
                        is Int -> param("frame_$key", value.toLong())
                        else -> param("frame_$key", value.toString())
                    }
                }
                
                // Add device context
                deviceInfo.forEach { (key, value) ->
                    param("device_$key", value)
                }
                
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "choreographer_analysis")
            }
            
            Timber.d("Choreographer performance logged: $component (session: $sessionId)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log Choreographer performance")
            Result.failure(exception)
        }
    }
    
    // UI Redesign Production Rollout Analytics Methods for PROD-001
    
    /**
     * Log UI redesign feature flag evaluation result for rollout analysis.
     * 
     * @param userId User identifier
     * @param enabled Whether UI redesign was enabled for this user
     * @param rolloutPercentage Current rollout percentage
     * @param evaluationMethod How feature flag was evaluated (percentage, override, etc.)
     */
    suspend fun logUiRedesignFeatureFlagEvaluation(
        userId: String,
        enabled: Boolean,
        rolloutPercentage: Int,
        evaluationMethod: String,
        appVersionCode: Int
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_UI_REDESIGN_FEATURE_FLAG_EVALUATION) {
                param("user_id", userId)
                param(PARAM_UI_REDESIGN_ENABLED, if (enabled) 1L else 0L)
                param(PARAM_ROLLOUT_PERCENTAGE, rolloutPercentage.toLong())
                param("evaluation_method", evaluationMethod)
                param("app_version_code", appVersionCode.toLong())
                param("timestamp", System.currentTimeMillis())
            }
            
            Timber.d("UI redesign feature flag evaluation logged: enabled=$enabled, rollout=$rolloutPercentage%")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log UI redesign feature flag evaluation")
            Result.failure(exception)
        }
    }
    
    /**
     * Log UI redesign user session start for A/B testing analysis.
     * 
     * @param userId User identifier
     * @param sessionId Unique session identifier
     * @param usingRedesign Whether user is in redesign group
     * @param sessionContext Additional session context
     */
    suspend fun logUiRedesignSessionStart(
        userId: String,
        sessionId: String,
        usingRedesign: Boolean,
        sessionContext: Map<String, Any> = emptyMap()
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_UI_REDESIGN_USER_SESSION_START) {
                param("user_id", userId)
                param("session_id", sessionId)
                param(PARAM_UI_REDESIGN_ENABLED, if (usingRedesign) 1L else 0L)
                param(PARAM_SESSION_TYPE, if (usingRedesign) "redesign" else "control")
                param("timestamp", System.currentTimeMillis())
                
                // Add session context
                sessionContext.forEach { (key, value) ->
                    when (value) {
                        is String -> param(key, value)
                        is Long -> param(key, value)
                        is Double -> param(key, value)
                        is Boolean -> param(key, if (value) 1L else 0L)
                        is Int -> param(key, value.toLong())
                        is Float -> param(key, value.toDouble())
                        else -> param(key, value.toString())
                    }
                }
            }
            
            // Set Crashlytics context
            firebaseCrashlytics.setCustomKey("ui_redesign_session_id", sessionId)
            firebaseCrashlytics.setCustomKey("ui_redesign_enabled", usingRedesign.toString())
            
            Timber.d("UI redesign session start logged: sessionId=$sessionId, redesign=$usingRedesign")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log UI redesign session start")
            Result.failure(exception)
        }
    }
    
    /**
     * Log UI redesign user session end with performance and behavior summary.
     * 
     * @param userId User identifier
     * @param sessionId Session identifier
     * @param sessionDuration Session duration in milliseconds
     * @param interactionCount Total interactions during session
     * @param errorCount Number of errors encountered
     * @param completedTasks Number of tasks completed successfully
     * @param performanceMetrics Performance metrics summary
     */
    suspend fun logUiRedesignSessionEnd(
        userId: String,
        sessionId: String,
        sessionDuration: Long,
        interactionCount: Int,
        errorCount: Int,
        completedTasks: Int,
        performanceMetrics: Map<String, Any> = emptyMap()
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_UI_REDESIGN_USER_SESSION_END) {
                param("user_id", userId)
                param("session_id", sessionId)
                param("session_duration_ms", sessionDuration)
                param("interaction_count", interactionCount.toLong())
                param("error_count", errorCount.toLong())
                param("completed_tasks", completedTasks.toLong())
                param("timestamp", System.currentTimeMillis())
                
                // Add performance metrics
                performanceMetrics.forEach { (key, value) ->
                    when (value) {
                        is String -> param("perf_$key", value)
                        is Long -> param("perf_$key", value)
                        is Double -> param("perf_$key", value)
                        is Boolean -> param("perf_$key", if (value) 1L else 0L)
                        is Int -> param("perf_$key", value.toLong())
                        is Float -> param("perf_$key", value.toDouble())
                        else -> param("perf_$key", value.toString())
                    }
                }
            }
            
            // Clear Crashlytics context
            firebaseCrashlytics.setCustomKey("ui_redesign_session_id", "")
            
            Timber.d("UI redesign session end logged: sessionId=$sessionId, duration=${sessionDuration}ms")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log UI redesign session end")
            Result.failure(exception)
        }
    }
    
    /**
     * Log UI redesign rollback trigger for rollback strategy analysis.
     * 
     * @param reason Reason for rollback trigger
     * @param severity Severity level (critical, warning, info)
     * @param rollbackType Type of rollback executed
     * @param affectedUsers Number of users potentially affected
     * @param additionalContext Additional rollback context
     */
    suspend fun logUiRedesignRollbackTrigger(
        reason: String,
        severity: String,
        rollbackType: String,
        affectedUsers: Int,
        additionalContext: Map<String, Any> = emptyMap()
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_UI_REDESIGN_ROLLBACK_TRIGGERED) {
                param(PARAM_ROLLBACK_REASON, reason)
                param(PARAM_ROLLBACK_SEVERITY, severity)
                param("rollback_type", rollbackType)
                param("affected_users", affectedUsers.toLong())
                param("timestamp", System.currentTimeMillis())
                
                // Add additional context
                additionalContext.forEach { (key, value) ->
                    when (value) {
                        is String -> param(key, value)
                        is Long -> param(key, value)
                        is Double -> param(key, value)
                        is Boolean -> param(key, if (value) 1L else 0L)
                        is Int -> param(key, value.toLong())
                        is Float -> param(key, value.toDouble())
                        else -> param(key, value.toString())
                    }
                }
            }
            
            // Log to Crashlytics for alerting
            firebaseCrashlytics.setCustomKey("ui_redesign_rollback_reason", reason)
            firebaseCrashlytics.setCustomKey("ui_redesign_rollback_severity", severity)
            
            Timber.w("UI redesign rollback triggered: $reason ($severity)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log UI redesign rollback trigger")
            Result.failure(exception)
        }
    }
    
    /**
     * Log UI redesign user feedback for satisfaction analysis.
     * 
     * @param userId User identifier
     * @param sessionId Session identifier
     * @param feedbackType Type of feedback (rating, survey, etc.)
     * @param rating Numerical rating (1-5)
     * @param category Feedback category
     * @param sentiment Sentiment analysis result
     * @param hasComment Whether feedback includes text comment
     */
    suspend fun logUiRedesignUserFeedback(
        userId: String,
        sessionId: String,
        feedbackType: String,
        rating: Float?,
        category: String,
        sentiment: String?,
        hasComment: Boolean
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_UI_REDESIGN_USER_FEEDBACK_COLLECTED) {
                param("user_id", userId)
                param("session_id", sessionId)
                param("feedback_type", feedbackType)
                param(PARAM_FEEDBACK_CATEGORY, category)
                rating?.let { param(PARAM_FEEDBACK_RATING, it.toDouble()) }
                sentiment?.let { param(PARAM_FEEDBACK_SENTIMENT, it) }
                param("has_comment", if (hasComment) 1L else 0L)
                param("timestamp", System.currentTimeMillis())
            }
            
            Timber.d("UI redesign feedback logged: rating=$rating, category=$category")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log UI redesign user feedback")
            Result.failure(exception)
        }
    }
    
    /**
     * Log UI redesign component interaction for usability analysis.
     * 
     * @param userId User identifier
     * @param sessionId Session identifier
     * @param componentType Type of component interacted with
     * @param interactionType Type of interaction (tap, swipe, etc.)
     * @param responseTime Response time in milliseconds
     * @param successful Whether interaction was successful
     * @param usingRedesign Whether user is in redesign group
     */
    suspend fun logUiRedesignComponentInteraction(
        userId: String,
        sessionId: String,
        componentType: String,
        interactionType: String,
        responseTime: Long,
        successful: Boolean,
        usingRedesign: Boolean
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_UI_REDESIGN_COMPONENT_INTERACTION) {
                param("user_id", userId)
                param("session_id", sessionId)
                param(PARAM_COMPONENT_TYPE, componentType)
                param(PARAM_INTERACTION_TYPE, interactionType)
                param("response_time_ms", responseTime)
                param(PARAM_INTERACTION_SUCCESS, if (successful) 1L else 0L)
                param(PARAM_UI_REDESIGN_ENABLED, if (usingRedesign) 1L else 0L)
                param("timestamp", System.currentTimeMillis())
            }
            
            Timber.v("UI redesign component interaction logged: $componentType.$interactionType (${responseTime}ms)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log UI redesign component interaction")
            Result.failure(exception)
        }
    }
    
    /**
     * Log UI redesign A/B test assignment for effectiveness analysis.
     * 
     * @param userId User identifier
     * @param testName A/B test name
     * @param group Test group assignment (control, treatment)
     * @param rolloutPercentage Current rollout percentage
     * @param assignmentMethod How assignment was determined
     */
    suspend fun logUiRedesignAbTestAssignment(
        userId: String,
        testName: String,
        group: String,
        rolloutPercentage: Int,
        assignmentMethod: String
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_UI_REDESIGN_AB_TEST_ASSIGNMENT) {
                param("user_id", userId)
                param("test_name", testName)
                param(PARAM_AB_TEST_GROUP, group)
                param(PARAM_ROLLOUT_PERCENTAGE, rolloutPercentage.toLong())
                param("assignment_method", assignmentMethod)
                param("timestamp", System.currentTimeMillis())
            }
            
            Timber.d("UI redesign A/B test assignment logged: group=$group")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to log UI redesign A/B test assignment")
            Result.failure(exception)
        }
    }

    private fun String.toPseudonymousUserId(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }

        return "liftrix-${digest.take(32)}"
    }
} 
