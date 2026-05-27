package com.example.liftrix.demo

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.Test

class DemoAwareHomeAdaptersTest {
    private val timeline = DemoTimelineGenerator().generate(20260525L, LocalDate(2026, 5, 25))
    private val factory = DemoHomeDataFactory()

    @Test
    fun `home projections share workout ids and totals`() {
        val recent = factory.recentWorkouts(timeline, 10)
        val feed = factory.recentActivityFeed(timeline, includeOthers = true, limit = 10)
        val posts = factory.posts(timeline, 10)
        val stats = factory.stats(timeline)

        assertThat(recent).hasSize(10)
        assertThat(feed.map { it.workout.id }).containsAtLeastElementsIn(recent.map { it.id })
        assertThat(posts.map { it.workoutId }).containsAtLeastElementsIn(recent.map { it.id })
        assertThat(stats.totalWorkouts).isEqualTo(timeline.workouts.size)
    }

    @Test
    fun `recommendations are not generated from demo accounts`() {
        val recommendations = factory.recommendedUsers(timeline, limit = 3, offset = 0)
        val posts = factory.posts(timeline, 12)

        assertThat(recommendations).isEmpty()
        assertThat(posts).hasSize(12)
        assertThat(posts.all { it.totalVolume != null && it.workoutDuration != null }).isTrue()
        assertThat(posts.map { it.userId }.toSet()).containsExactly(timeline.user.userId)
    }
}
