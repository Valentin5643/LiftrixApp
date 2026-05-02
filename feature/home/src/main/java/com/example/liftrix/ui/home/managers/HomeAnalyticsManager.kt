package com.example.liftrix.ui.home.managers

import com.example.liftrix.feature.home.model.HomeFeedWorkout
import com.example.liftrix.feature.home.model.HomeWorkout
import com.example.liftrix.feature.home.ports.HomeAnalyticsPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages analytics tracking for home screen interactions.
 * 
 * Extracts analytics responsibilities from HomeViewModel to focus on:
 * - Home screen event tracking
 * - User interaction analytics
 * - Performance metrics collection
 * - Social interaction tracking
 * 
 * Follows single responsibility principle by handling only analytics operations.
 */
@Singleton
class HomeAnalyticsManager @Inject constructor(
    private val analyticsService: HomeAnalyticsPort
) {
    
    /**
     * Tracks when the home screen is viewed.
     */
    fun trackHomeScreenViewed(scope: CoroutineScope) {
        scope.launch {
            try {
                Timber.d("Analytics: Home screen viewed")
            } catch (e: Exception) {
                Timber.e(e, "Error tracking home screen viewed")
            }
        }
    }
    
    /**
     * Tracks when a workout is opened from the home screen.
     */
    fun trackWorkoutOpened(workout: HomeWorkout, scope: CoroutineScope) {
        scope.launch {
            try {
                Timber.d("Analytics: Workout opened - ${workout.name}")
            } catch (e: Exception) {
                Timber.e(e, "Error tracking workout opened")
            }
        }
    }
    
    /**
     * Tracks when data refresh is performed.
     */
    fun trackRefreshPerformed(scope: CoroutineScope) {
        scope.launch {
            try {
                Timber.d("Analytics: Home data refreshed")
            } catch (e: Exception) {
                Timber.e(e, "Error tracking refresh performed")
            }
        }
    }
    
    /**
     * Tracks when a feed workout is opened.
     */
    fun trackFeedWorkoutOpened(feedWorkout: HomeFeedWorkout, scope: CoroutineScope) {
        scope.launch {
            try {
                Timber.d("Analytics: Feed workout opened - ${feedWorkout.workout.id}")
            } catch (e: Exception) {
                Timber.e(e, "Error tracking feed workout opened")
            }
        }
    }
    
    /**
     * Tracks when feed data is loaded.
     */
    fun trackFeedLoaded(workoutCount: Int, scope: CoroutineScope) {
        scope.launch {
            try {
                Timber.d("Analytics: Feed loaded with $workoutCount workouts")
            } catch (e: Exception) {
                Timber.e(e, "Error tracking feed loaded")
            }
        }
    }
    
    /**
     * Tracks when more workouts are loaded (pagination).
     */
    fun trackMoreWorkoutsLoaded(newWorkoutCount: Int, scope: CoroutineScope) {
        scope.launch {
            try {
                Timber.d("Analytics: More workouts loaded - $newWorkoutCount new workouts")
            } catch (e: Exception) {
                Timber.e(e, "Error tracking more workouts loaded")
            }
        }
    }
    
    /**
     * Tracks when recommendations are loaded.
     */
    fun trackRecommendationsLoaded(userCount: Int, scope: CoroutineScope) {
        scope.launch {
            try {
                Timber.d("Analytics: Recommendations loaded with $userCount users")
            } catch (e: Exception) {
                Timber.e(e, "Error tracking recommendations loaded")
            }
        }
    }
    
    /**
     * Tracks when more recommendations are loaded (pagination).
     */
    fun trackMoreRecommendationsLoaded(newUserCount: Int, scope: CoroutineScope) {
        scope.launch {
            try {
                Timber.d("Analytics: More recommendations loaded - $newUserCount new users")
            } catch (e: Exception) {
                Timber.e(e, "Error tracking more recommendations loaded")
            }
        }
    }
    
    /**
     * Tracks when feed is refreshed.
     */
    fun trackFeedRefreshed(scope: CoroutineScope) {
        scope.launch {
            try {
                Timber.d("Analytics: Feed refreshed")
            } catch (e: Exception) {
                Timber.e(e, "Error tracking feed refreshed")
            }
        }
    }
    
    /**
     * Tracks when a user is followed.
     */
    fun trackUserFollowed(userId: String, scope: CoroutineScope) {
        scope.launch {
            try {
                Timber.d("Analytics: User followed - $userId")
            } catch (e: Exception) {
                Timber.e(e, "Error tracking user followed")
            }
        }
    }
    
    /**
     * Tracks when a user is unfollowed.
     */
    fun trackUserUnfollowed(userId: String, scope: CoroutineScope) {
        scope.launch {
            try {
                Timber.d("Analytics: User unfollowed - $userId")
            } catch (e: Exception) {
                Timber.e(e, "Error tracking user unfollowed")
            }
        }
    }
}
