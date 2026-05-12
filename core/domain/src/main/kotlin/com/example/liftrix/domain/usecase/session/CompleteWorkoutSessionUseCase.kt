package com.example.liftrix.domain.usecase.session

import com.example.liftrix.core.cache.CacheManager
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.service.CacheInvalidationService
import com.example.liftrix.service.WorkoutCompletionNotifier
import kotlinx.coroutines.delay
import com.example.liftrix.domain.util.DomainLogger as Timber
import javax.inject.Inject

class CompleteWorkoutSessionUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val cacheManager: CacheManager,
    private val cacheInvalidationService: CacheInvalidationService,
    private val workoutCompletionNotifier: WorkoutCompletionNotifier
) {

    suspend operator fun invoke(
        session: UnifiedWorkoutSession
    ): LiftrixResult<CompleteWorkoutSessionResult> {
        val completedWorkout = session.toCompletedWorkout()
        Timber.d(
            "[WORKOUT-DEBUG] CompleteWorkoutSessionUseCase saving workout " +
                "id=${completedWorkout.id.value} userId=${completedWorkout.userId} " +
                "status=${completedWorkout.status} exercises=${completedWorkout.exercises.size}"
        )

        return workoutRepository.createWorkout(completedWorkout).fold(
            onSuccess = { savedWorkout ->
                delay(100)

                val sideEffects = listOf(
                    createAutomaticWorkoutPost(savedWorkout),
                    invalidateWorkoutRelatedCache(savedWorkout),
                    notifyGymBuddiesWorkoutCompleted(savedWorkout)
                )

                Result.success(
                    CompleteWorkoutSessionResult(
                        savedWorkout = savedWorkout,
                        savedWorkoutId = savedWorkout.id.value,
                        sideEffects = sideEffects
                    )
                )
            },
            onFailure = { throwable ->
                Result.failure(throwable)
            }
        )
    }

    private suspend fun createAutomaticWorkoutPost(
        savedWorkout: Workout
    ): CompletionSideEffectStatus {
        Timber.i(
            "[PUBLIC-LOG] Automatic workout post disabled for workout=${savedWorkout.id.value} user=${savedWorkout.userId}"
        )
        return CompletionSideEffectStatus(
            effect = CompletionSideEffect.AUTO_POST,
            state = CompletionSideEffectState.SKIPPED,
            detail = "Manual share flow owns workout post creation"
        )
    }

    private suspend fun invalidateWorkoutRelatedCache(
        workout: Workout
    ): CompletionSideEffectStatus {
        return try {
            Timber.d("Enhanced cache invalidation for workout completion - user: ${workout.userId}")

            val exerciseIds = workout.exercises.map { it.libraryExercise.id }
            val workoutDate = kotlinx.datetime.LocalDate(
                workout.date.year,
                workout.date.monthValue,
                workout.date.dayOfMonth
            )
            val workoutDuration = workout.getDuration()?.toMinutes()?.toInt() ?: 0

            cacheInvalidationService.invalidateWorkoutData(
                userId = workout.userId,
                workoutDate = workoutDate,
                exerciseIds = exerciseIds,
                workoutDuration = workoutDuration
            ).fold(
                onSuccess = {
                    Timber.i(
                        "Enhanced cache invalidation completed successfully for user: ${workout.userId}"
                    )
                    CompletionSideEffectStatus(
                        effect = CompletionSideEffect.CACHE_INVALIDATION,
                        state = CompletionSideEffectState.SUCCESS
                    )
                },
                onFailure = { exception ->
                    Timber.e(
                        exception,
                        "Enhanced cache invalidation failed for user: ${workout.userId}"
                    )
                    if (fallbackCacheInvalidation(workout.userId)) {
                        CompletionSideEffectStatus(
                            effect = CompletionSideEffect.CACHE_INVALIDATION,
                            state = CompletionSideEffectState.SUCCESS,
                            detail = "Fallback invalidation used"
                        )
                    } else {
                        CompletionSideEffectStatus(
                            effect = CompletionSideEffect.CACHE_INVALIDATION,
                            state = CompletionSideEffectState.FAILED,
                            detail = exception.message
                        )
                    }
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error in enhanced cache invalidation for user: ${workout.userId}")
            if (fallbackCacheInvalidation(workout.userId)) {
                CompletionSideEffectStatus(
                    effect = CompletionSideEffect.CACHE_INVALIDATION,
                    state = CompletionSideEffectState.SUCCESS,
                    detail = "Fallback invalidation used after exception"
                )
            } else {
                CompletionSideEffectStatus(
                    effect = CompletionSideEffect.CACHE_INVALIDATION,
                    state = CompletionSideEffectState.FAILED,
                    detail = e.message
                )
            }
        }
    }

    private suspend fun notifyGymBuddiesWorkoutCompleted(
        workout: Workout
    ): CompletionSideEffectStatus {
        return try {
            workoutCompletionNotifier.notifyWorkoutCompleted(workout)
            CompletionSideEffectStatus(
                effect = CompletionSideEffect.GYM_BUDDY_NOTIFICATION,
                state = CompletionSideEffectState.SUCCESS
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to send Gym Buddy workout-completion notification")
            CompletionSideEffectStatus(
                effect = CompletionSideEffect.GYM_BUDDY_NOTIFICATION,
                state = CompletionSideEffectState.FAILED,
                detail = e.message
            )
        }
    }

    private suspend fun fallbackCacheInvalidation(userId: String): Boolean {
        return try {
            Timber.d("Fallback cache invalidation for user: $userId")
            cacheManager.invalidatePattern { cacheKey ->
                val keyString = cacheKey.keyString
                keyString.contains(":$userId:") || keyString.contains("user:$userId")
            }
            Timber.i("Fallback cache invalidation completed for user: $userId")
            true
        } catch (e: Exception) {
            Timber.e(e, "Fallback cache invalidation failed for user: $userId")
            false
        }
    }
}

data class CompleteWorkoutSessionResult(
    val savedWorkout: Workout,
    val savedWorkoutId: String,
    val sideEffects: List<CompletionSideEffectStatus>
)

data class CompletionSideEffectStatus(
    val effect: CompletionSideEffect,
    val state: CompletionSideEffectState,
    val detail: String? = null
)

enum class CompletionSideEffect {
    AUTO_POST,
    CACHE_INVALIDATION,
    GYM_BUDDY_NOTIFICATION
}

enum class CompletionSideEffectState {
    SUCCESS,
    SKIPPED,
    FAILED
}
