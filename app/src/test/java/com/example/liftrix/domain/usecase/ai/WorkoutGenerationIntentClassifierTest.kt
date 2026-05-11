package com.example.liftrix.domain.usecase.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutGenerationIntentClassifierTest {

    private val classifier = WorkoutGenerationIntentClassifier()

    @Test
    fun `classifies direct generation requests`() {
        assertEquals(
            ChatIntent.GenerateWorkout,
            classifier.classify("Create a 3-day beginner workout using dumbbells only")
        )
    }

    @Test
    fun `classifies common split names`() {
        assertEquals(ChatIntent.GenerateWorkout, classifier.classify("I need a push/pull split"))
        assertEquals(ChatIntent.GenerateWorkout, classifier.classify("Plan me an upper lower workout"))
    }

    @Test
    fun `classifies home training phrasing`() {
        assertEquals(ChatIntent.GenerateWorkout, classifier.classify("Help me plan workouts for 3 days at home"))
    }

    @Test
    fun `routes coaching questions to general chat`() {
        assertEquals(ChatIntent.GeneralChat, classifier.classify("How is my squat form?"))
        assertEquals(ChatIntent.GeneralChat, classifier.classify("How much protein should I eat?"))
    }

    @Test
    fun `classifies source-aware workout modification requests`() {
        assertEquals(ChatIntent.ModifyWorkout, classifier.classify("Make this workout easier"))
        assertEquals(ChatIntent.ModifyWorkout, classifier.classify("Make my last workout easier"))
        assertEquals(ChatIntent.ModifyWorkout, classifier.classify("Increase intensity in my plan"))
        assertEquals(ChatIntent.ModifyWorkout, classifier.classify("Adjust this routine for hypertrophy"))
    }

    @Test
    fun `classifies adaptive progression requests`() {
        assertEquals(
            ChatIntent.UpdatePlanFromProgress,
            classifier.classify("Update my plan based on progress")
        )
        assertEquals(
            ChatIntent.UpdatePlanFromProgress,
            classifier.classify("Adapt my plan for progressive overload")
        )
    }

    @Test
    fun `asks for clarification when modification source is missing`() {
        assertEquals(ChatIntent.NeedsClarification, classifier.classify("Make it easier"))
        assertEquals(ChatIntent.NeedsClarification, classifier.classify("Adjust for hypertrophy"))
    }
}
