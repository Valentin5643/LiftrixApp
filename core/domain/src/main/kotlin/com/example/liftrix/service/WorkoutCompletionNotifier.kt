package com.example.liftrix.service

import com.example.liftrix.domain.model.Workout

interface WorkoutCompletionNotifier {
    suspend fun notifyWorkoutCompleted(workout: Workout)
}
