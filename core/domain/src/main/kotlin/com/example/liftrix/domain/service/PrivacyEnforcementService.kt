package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.social.WorkoutVisibility
import com.example.liftrix.domain.model.social.WorkoutPost

interface PrivacyEnforcementService {
    suspend fun preloadRelationshipsForViewer(viewerId: String)

    suspend fun canViewProfile(profileUserId: String, viewerId: String?): Boolean

    suspend fun canViewWorkout(
        workoutOwnerId: String,
        viewerId: String?,
        workoutVisibility: WorkoutVisibility? = null
    ): Boolean

    suspend fun canViewPost(viewerId: String?, post: WorkoutPost): Boolean
}
