package com.example.liftrix.service

import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.SessionSet
import com.example.liftrix.domain.model.UnifiedWorkoutSession

interface WorkoutSessionManagerPort {
    fun startSession(session: UnifiedWorkoutSession)
    fun resumeSession()
    fun hasActiveSession(): Boolean
    fun getCurrentSession(): UnifiedWorkoutSession?
    fun addExerciseToSession(exercise: SessionExercise)
    fun updateSetInSession(exerciseId: ExerciseId, setNumber: Int, updatedSet: SessionSet)
    fun refreshSessionState()
}
