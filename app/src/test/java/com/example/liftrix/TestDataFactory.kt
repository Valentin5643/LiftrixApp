package com.example.liftrix

import com.example.liftrix.domain.model.*
import com.example.liftrix.sync.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

object TestDataFactory {
    
    // User test data
    val testUser = User(
        uid = "test-user-id",
        email = "test@example.com",
        displayName = "Test User",
        photoUrl = null,
        isAnonymous = false,
        subscriptionTier = SubscriptionTier.FREE,
        subscriptionStatus = SubscriptionStatus.ACTIVE,
        subscriptionExpiresAt = null,
        premiumFeaturesEnabled = false,
        onboardingCompleted = true,
        profileVersion = 1L,
        createdAt = LocalDateTime.now().minusHours(1),
        lastSignInAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )
    
    val anonymousUser = User(
        uid = "anonymous-user-id",
        email = "",
        displayName = null,
        photoUrl = null,
        isAnonymous = true,
        subscriptionTier = SubscriptionTier.FREE,
        subscriptionStatus = SubscriptionStatus.ACTIVE,
        subscriptionExpiresAt = null,
        premiumFeaturesEnabled = false,
        onboardingCompleted = false,
        profileVersion = 1L,
        createdAt = LocalDateTime.now().minusMinutes(30),
        lastSignInAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )
    
    // Exercise test data - Simplified for basic compilation
    // TODO: Update to match current Exercise domain model
    
    // TODO: Update squatExercise to match current Exercise domain model
    
    // Workout test data
    val sampleWorkout = WorkoutExample.createSampleWorkout().copy(
        userId = "test-user-id",
        name = "Upper Body Workout"
    )
    
    // TODO: Update completedWorkout to match current Workout domain model
    
    // Sync status test data
    val idleSyncStatus = SyncStatus.Idle
    val syncingSyncStatus = SyncStatus.Syncing
    val successSyncStatus = SyncStatus.Success(syncedCount = 5)
    val errorSyncStatus = SyncStatus.Error("Network error")
    
    // Workout UI state test data
    // TODO: Update WorkoutUiState usage to match current implementation
    
    // Factory methods for creating variations
    // TODO: Implement createWorkout function with current domain models
    
    fun createUser(
        uid: String = "test-user",
        email: String = "test@example.com",
        isAnonymous: Boolean = false
    ): User {
        return User(
            uid = uid,
            email = if (isAnonymous) "" else email,
            displayName = if (isAnonymous) null else "Test User",
            photoUrl = null,
            isAnonymous = isAnonymous,
            subscriptionTier = SubscriptionTier.FREE,
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            subscriptionExpiresAt = null,
            premiumFeaturesEnabled = false,
            onboardingCompleted = !isAnonymous,
            profileVersion = 1L,
            createdAt = LocalDateTime.now().minusHours(1),
            lastSignInAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
} 