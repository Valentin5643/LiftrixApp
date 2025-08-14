package com.example.liftrix.domain.model

/**
 * Domain model representing a progress photo.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
data class ProgressPhoto(
    val id: String,
    val userId: String,
    val mediaId: String,
    val bodyPart: BodyPart? = null,
    val photoType: PhotoType? = null,
    val weightKg: Float? = null,
    val bodyFatPercent: Float? = null,
    val comparisonGroupId: String? = null,
    val isBefore: Boolean? = null,
    val isPrivate: Boolean = true,
    val takenAt: Long,
    val createdAt: Long
)

/**
 * Body parts for progress photo categorization.
 */
enum class BodyPart {
    FULL_BODY,
    UPPER_BODY,
    LOWER_BODY,
    ARMS,
    LEGS,
    CHEST,
    BACK,
    SHOULDERS,
    CORE,
    FACE
}

/**
 * Photo types for different angles and poses.
 */
enum class PhotoType {
    FRONT,
    SIDE,
    BACK,
    FLEX,
    RELAXED,
    POSE
}

/**
 * Progress comparison set containing before/after photos.
 */
data class ProgressComparison(
    val id: String,
    val userId: String,
    val name: String,
    val bodyPart: BodyPart,
    val beforePhoto: ProgressPhoto,
    val afterPhoto: ProgressPhoto,
    val timeDifferenceWeeks: Int,
    val weightChangeKg: Float? = null,
    val bodyFatChange: Float? = null,
    val notes: String? = null,
    val createdAt: Long
)

/**
 * Request for creating a progress comparison.
 */
data class CreateProgressComparisonRequest(
    val name: String,
    val bodyPart: BodyPart,
    val beforePhotoId: String,
    val afterPhotoId: String,
    val notes: String? = null
)

/**
 * Progress photo upload request.
 */
data class ProgressPhotoUploadRequest(
    val mediaId: String,
    val bodyPart: BodyPart? = null,
    val photoType: PhotoType? = null,
    val weightKg: Float? = null,
    val bodyFatPercent: Float? = null,
    val isPrivate: Boolean = true,
    val takenAt: Long = System.currentTimeMillis()
)

/**
 * Progress timeline showing user's journey.
 */
data class ProgressTimeline(
    val userId: String,
    val bodyPart: BodyPart,
    val photos: List<ProgressPhoto>,
    val measurements: List<ProgressMeasurement>,
    val milestones: List<ProgressMilestone>
)

/**
 * Progress measurement at a specific point in time.
 */
data class ProgressMeasurement(
    val date: Long,
    val weightKg: Float? = null,
    val bodyFatPercent: Float? = null,
    val muscleMassKg: Float? = null,
    val bodyMeasurements: Map<String, Float> = emptyMap() // chest, waist, arms, etc.
)

/**
 * Progress milestone achievement.
 */
data class ProgressMilestone(
    val date: Long,
    val type: MilestoneType,
    val description: String,
    val photoId: String? = null
)

/**
 * Types of progress milestones.
 */
enum class MilestoneType {
    WEIGHT_GOAL,
    BODY_FAT_GOAL,
    STRENGTH_GOAL,
    PHYSIQUE_GOAL,
    CONSISTENCY_GOAL
}