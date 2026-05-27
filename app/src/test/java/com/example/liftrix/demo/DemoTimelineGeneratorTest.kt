package com.example.liftrix.demo

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.Test

class DemoTimelineGeneratorTest {
    private val generator = DemoTimelineGenerator()
    private val anchorDate = LocalDate(2026, 5, 25)

    @Test
    fun `same seed and anchor generate identical timeline`() {
        val first = generator.generate(sessionSeed = 42L, anchorDate = anchorDate)
        val second = generator.generate(sessionSeed = 42L, anchorDate = anchorDate)

        assertThat(second.workouts).isEqualTo(first.workouts)
        assertThat(second.personalRecords).isEqualTo(first.personalRecords)
        assertThat(first.workouts).hasSizeAtLeast(75)
    }

    @Test
    fun `generated totals match workout set aggregation`() {
        val timeline = generator.generate(sessionSeed = 99L, anchorDate = anchorDate)

        val workoutVolume = timeline.workouts.sumOf { workout ->
            workout.exercises.sumOf { exercise ->
                exercise.sets.sumOf { set -> set.weightKg * set.reps }
            }
        }

        assertThat(timeline.totalVolumeKg).isWithin(0.01).of(workoutVolume)
        assertThat(timeline.workouts.all { it.totalSets > 0 && it.durationMinutes > 0 }).isTrue()
    }

    @Test
    fun `recent activity references generated workouts`() {
        val timeline = generator.generate(sessionSeed = 123L, anchorDate = anchorDate)
        val workoutIds = timeline.workouts.map { it.id }.toSet()

        assertThat(timeline.activityEvents).isNotEmpty()
        assertThat(timeline.activityEvents.all { it.workoutId in workoutIds }).isTrue()
        assertThat(timeline.achievements.all { it.workoutId in workoutIds }).isTrue()
    }

    @Test
    fun `timeline only generates the demo user's own identity`() {
        val timeline = generator.generate(sessionSeed = 20260525L, anchorDate = anchorDate)

        assertThat(timeline.people).containsExactly(timeline.user)
        assertThat(timeline.activityEvents.map { it.person.userId }.toSet()).containsExactly(timeline.user.userId)
    }
}
