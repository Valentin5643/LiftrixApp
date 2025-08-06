package com.example.liftrix.ui.home

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.FeedWorkout
import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * Sealed class representing all possible events from the Home screen UI.
 * 
 * These events are handled by HomeViewModel following the MVI (Model-View-Intent) pattern.
 * Each event represents a user intention or system event that can modify the home screen state.
 */
sealed class HomeEvent : ViewModelEvent {
    
    /**
     * User requested to refresh all home screen data.
     */
    object RefreshData : HomeEvent()
    
    /**
     * User requested to load more workouts in the feed (pagination).
     */
    object LoadMoreWorkouts : HomeEvent()
    
    /**
     * User requested to load more user recommendations (pagination).
     */
    object LoadMoreRecommendations : HomeEvent()
    
    /**
     * User requested to refresh the social feed.
     */
    object RefreshFeed : HomeEvent()
    
    /**
     * User wants to follow another user.
     */
    data class FollowUser(val userId: String) : HomeEvent()
    
    /**
     * User wants to unfollow another user.
     */
    data class UnfollowUser(val userId: String) : HomeEvent()
    
    /**
     * User opened a workout from the home screen.
     */
    data class WorkoutOpened(val workout: Workout) : HomeEvent()
    
    /**
     * User opened a workout from the social feed.
     */
    data class FeedWorkoutOpened(val feedWorkout: FeedWorkout) : HomeEvent()
    
    /**
     * User dismissed a general error.
     */
    object ErrorDismissed : HomeEvent()
    
    /**
     * User dismissed a feed-specific error.
     */
    object FeedErrorDismissed : HomeEvent()
    
    /**
     * User dismissed a recommendations-specific error.
     */
    object RecommendationsErrorDismissed : HomeEvent()
}