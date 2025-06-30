package com.example.liftrix.data.service

import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.WorkoutMetrics
import com.example.liftrix.domain.service.AnalyticsService
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.tasks.await
import timber.log.Timber
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
        
        // User property names
        private const val USER_PROP_SUBSCRIPTION_TIER = "subscription_tier"
        private const val USER_PROP_IS_PREMIUM = "is_premium"
        private const val USER_PROP_IS_ANONYMOUS = "is_anonymous"
        private const val USER_PROP_ONBOARDING_COMPLETED = "onboarding_completed"
        
        // Crashlytics custom keys
        private const val CRASHLYTICS_USER_ID = "user_id"
        private const val CRASHLYTICS_SUBSCRIPTION_TIER = "subscription_tier"
        private const val CRASHLYTICS_APP_VERSION = "app_version"
    }

    override suspend fun setUserProperties(user: User): Result<Unit> {
        return try {
            // Set Firebase Analytics user properties
            firebaseAnalytics.setUserId(user.uid)
            firebaseAnalytics.setUserProperty(USER_PROP_SUBSCRIPTION_TIER, user.subscriptionTier.name.lowercase())
            firebaseAnalytics.setUserProperty(USER_PROP_IS_PREMIUM, user.isPremium.toString())
            firebaseAnalytics.setUserProperty(USER_PROP_IS_ANONYMOUS, user.isAnonymous.toString())
            firebaseAnalytics.setUserProperty(USER_PROP_ONBOARDING_COMPLETED, user.onboardingCompleted.toString())
            
            // Set Crashlytics user properties
            firebaseCrashlytics.setUserId(user.uid)
            firebaseCrashlytics.setCustomKey("user_id", user.uid)
            firebaseCrashlytics.setCustomKey("subscription_tier", user.subscriptionTier.name)
            firebaseCrashlytics.setCustomKey("is_premium", user.isPremium.toString())
            
            Timber.d("User properties set successfully for user: ${user.uid}")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to set user properties for user: ${user.uid}")
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
            
            Timber.d("Workout start logged: $workoutId for user: $userId")
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
        metrics: WorkoutMetrics,
        durationMinutes: Long?
    ): Result<Unit> {
        return try {
            firebaseAnalytics.logEvent(EVENT_WORKOUT_COMPLETED) {
                param(FirebaseAnalytics.Param.ITEM_ID, workoutId)
                param(FirebaseAnalytics.Param.ITEM_NAME, workoutName)
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "workout")
                param(PARAM_EXERCISE_COUNT, metrics.exerciseCount.toLong())
                param(PARAM_TOTAL_SETS, metrics.totalSets.toLong())
                param(PARAM_COMPLETED_SETS, metrics.completedSets.toLong())
                param(PARAM_COMPLETION_PERCENTAGE, metrics.completionPercentage)
                param(PARAM_TOTAL_VOLUME_KG, metrics.totalVolume.kilograms)
                durationMinutes?.let { param(PARAM_DURATION_MINUTES, it) }
            }
            
            // Clear workout context from crashlytics
            firebaseCrashlytics.setCustomKey("current_workout_id", "")
            firebaseCrashlytics.setCustomKey("workout_phase", "completed")
            
            Timber.d("Workout completion logged: $workoutId with ${metrics.exerciseCount} exercises, ${metrics.totalSets} sets")
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
            
            Timber.d("Friend request sent logged: $userId -> $targetUserId")
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
            Timber.d("Friend request $action logged: $userId -> $targetUserId")
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
            firebaseCrashlytics.setCustomKey("user_id", "")
            firebaseCrashlytics.setCustomKey("subscription_tier", "")
            firebaseCrashlytics.setCustomKey("current_workout_id", "")
            firebaseCrashlytics.setCustomKey("workout_phase", "")
            
            Timber.d("User properties cleared")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to clear user properties")
            Result.failure(exception)
        }
    }
} 