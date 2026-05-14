package com.example.liftrix.data.mapper

import com.example.liftrix.domain.model.social.MediaItem
import com.example.liftrix.domain.model.social.MediaType

/**
 * Centralized fallback image mapper for workout posts with no user-selected media.
 */
object WorkoutPostDefaultImageMapper {
    private const val APP_PACKAGE = "com.liftrix.app"
    const val GYM_DEFAULT_IMAGE_URI: String =
        "android.resource://$APP_PACKAGE/drawable/workout_card_default_gym"
    const val BODYWEIGHT_DEFAULT_IMAGE_URI: String =
        "android.resource://$APP_PACKAGE/drawable/workout_card_default_bodyweight"

    fun mediaItemsForPost(
        userMediaItems: List<MediaItem>,
        exercises: List<DefaultImageExercise>
    ): List<MediaItem> {
        if (userMediaItems.isNotEmpty()) return userMediaItems

        val defaultUri = if (isBodyweightOnly(exercises)) {
            BODYWEIGHT_DEFAULT_IMAGE_URI
        } else {
            GYM_DEFAULT_IMAGE_URI
        }

        return listOf(
            MediaItem(
                id = "default_workout_card_image",
                type = MediaType.IMAGE,
                originalUrl = defaultUri,
                thumbnailUrl = null,
                compressedUrl = null,
                width = null,
                height = null,
                fileSizeBytes = 0L
            )
        )
    }

    private fun isBodyweightOnly(exercises: List<DefaultImageExercise>): Boolean {
        return exercises.isNotEmpty() && exercises.all { it.isBodyweightExercise() }
    }

    private fun DefaultImageExercise.isBodyweightExercise(): Boolean {
        val normalizedEquipment = equipment?.trim()?.uppercase()
        if (normalizedEquipment == "BODYWEIGHT_ONLY") return true

        val normalizedName = name.lowercase()
        if (normalizedEquipment != null) {
            return normalizedEquipment in setOf("PULL_UP_BAR", "BENCH") && normalizedName.isBodyweightName()
        }

        return normalizedName.isBodyweightName()
    }

    private fun String.isBodyweightName(): Boolean {
        val weightedTerms = listOf(
            "barbell",
            "dumbbell",
            "kettlebell",
            "machine",
            "cable",
            "bench press",
            "leg press",
            "weighted",
            "goblet",
            "thruster",
            "curl"
        )
        if (weightedTerms.any { contains(it) }) return false

        return listOf(
            "bodyweight",
            "pushup",
            "push-up",
            "push up",
            "pullup",
            "pull-up",
            "pull up",
            "chinup",
            "chin-up",
            "dip",
            "plank",
            "situp",
            "sit-up",
            "crunch",
            "mountain climber",
            "burpee",
            "jumping jack",
            "high knees",
            "air squat",
            "squat",
            "lunge",
            "calf raise",
            "box jump",
            "pistol squat",
            "bird dog",
            "flutter kick",
            "glute bridge",
            "inverted row"
        ).any { contains(it) }
    }
}

data class DefaultImageExercise(
    val name: String,
    val equipment: String?
)
