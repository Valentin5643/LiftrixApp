package com.example.liftrix.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.*
import com.example.liftrix.data.local.entity.*
import com.example.liftrix.data.remote.realtime.CommentSyncService
import com.example.liftrix.data.remote.realtime.PostEngagementListener
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import javax.inject.Inject

/**
 * Idempotency tests for real-time listeners (SPEC-20241228 RT-007).
 *
 * Verifies that applying the same remote update multiple times results in identical state.
 * Tests all 5 real-time services with feature-flag gating.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RealTimeListenerIdempotencyTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: LiftrixDatabase

    @Inject
    lateinit var workoutDao: WorkoutDao

    @Inject
    lateinit var workoutPostDao: WorkoutPostDao

    @Inject
    lateinit var postCommentDao: PostCommentDao

    @Inject
    lateinit var postLikeDao: PostLikeDao

    @Inject
    lateinit var socialProfileDao: SocialProfileDao

    @Inject
    lateinit var firestore: FirebaseFirestore

    @Before
    fun setup() {
        hiltRule.inject()
        // Enable Room-first mode for testing
        OfflineArchitectureFlags.USE_IDEMPOTENT_LISTENERS = true
        Timber.plant(Timber.DebugTree())
    }

    @After
    fun tearDown() {
        database.clearAllTables()
        OfflineArchitectureFlags.USE_IDEMPOTENT_LISTENERS = false
    }

    /**
     * TS-005: Verify applying same remote update 10x results in identical state.
     * Tests WorkoutDao.upsertFromRemote() idempotency.
     */
    @Test
    fun testWorkoutListenerIdempotency() = runBlocking {
        // Given: Remote workout data
        val remoteWorkout = WorkoutEntity(
            id = "w1",
            userId = "u1",
            name = "Idempotent Test Workout",
            exercises = "[{\"id\":\"1\",\"name\":\"Bench Press\"}]",
            date = java.time.Instant.now(),
            duration = 3600,
            notes = "Test",
            totalVolume = 1000.0,
            isDirty = false,
            isSynced = true,
            syncVersion = 1L,
            lastModified = 1000L
        )

        // When: Apply same update 10 times
        repeat(10) {
            workoutDao.upsertFromRemote(remoteWorkout)
        }

        // Then: State is identical
        val saved = workoutDao.getWorkoutById("w1", "u1")
        assertNotNull("Workout should exist", saved)
        assertEquals("Idempotent Test Workout", saved?.name)
        assertEquals(1000L, saved?.lastModified)
        assertFalse("Should not be dirty", saved?.isDirty ?: true)
        assertTrue("Should be synced", saved?.isSynced ?: false)

        // Verify only 1 entity exists (not 10 duplicates)
        val all = workoutDao.getAllWorkoutsForUser("u1")
        assertEquals("Should have exactly 1 workout", 1, all.size)
    }

    /**
     * TS-005: Verify engagement metrics idempotency.
     * Tests WorkoutPostDao.upsertEngagementFromRemote() idempotency.
     */
    @Test
    fun testEngagementListenerIdempotency() = runBlocking {
        // Given: Create a post first
        val post = WorkoutPostEntity(
            id = "p1",
            userId = "u1",
            workoutId = "w1",
            caption = "Test Post",
            likeCount = 0,
            commentCount = 0,
            shareCount = 0,
            saveCount = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastModified = 500L,
            isDirty = false,
            isSynced = true
        )
        workoutPostDao.upsertLocal(post)

        // When: Apply same engagement update 10 times
        repeat(10) {
            workoutPostDao.upsertEngagementFromRemote(
                postId = "p1",
                likeCount = 10,
                commentCount = 5,
                shareCount = 2,
                saveCount = 1,
                lastModified = 1000L
            )
        }

        // Then: State is identical
        val saved = workoutPostDao.getWorkoutPostById("p1")
        assertNotNull("Post should exist", saved)
        assertEquals(10, saved?.likeCount)
        assertEquals(5, saved?.commentCount)
        assertEquals(2, saved?.shareCount)
        assertEquals(1, saved?.saveCount)
        assertEquals(1000L, saved?.lastModified)
        assertFalse("Should not be dirty", saved?.isDirty ?: true)

        // Verify no duplicate posts
        val allPosts = workoutPostDao.getAllPostsForUser("u1")
        assertEquals("Should have exactly 1 post", 1, allPosts.size)
    }

    /**
     * TS-005: Verify comment listener idempotency.
     * Tests PostCommentDao.upsertFromRemote() idempotency.
     */
    @Test
    fun testCommentListenerIdempotency() = runBlocking {
        // Given: Remote comment data
        val remoteComment = PostCommentEntity(
            id = "c1",
            postId = "p1",
            userId = "u1",
            content = "Idempotent Comment Test",
            replyToCommentId = null,
            likeCount = 3,
            isEdited = false,
            createdAt = 1000L,
            editedAt = null,
            updatedAt = 1000L,
            isSynced = true,
            syncVersion = 1,
            lastModified = 1000L,
            isDirty = false
        )

        // When: Apply same update 10 times
        repeat(10) {
            postCommentDao.upsertFromRemote(remoteComment)
        }

        // Then: State is identical
        val saved = postCommentDao.getCommentById("c1")
        assertNotNull("Comment should exist", saved)
        assertEquals("Idempotent Comment Test", saved?.content)
        assertEquals(3, saved?.likeCount)
        assertEquals(1000L, saved?.lastModified)
        assertFalse("Should not be dirty", saved?.isDirty ?: true)

        // Verify no duplicates
        val allComments = postCommentDao.getAllCommentsForPost("p1")
        assertEquals("Should have exactly 1 comment", 1, allComments.size)
    }

    /**
     * TS-005: Verify like listener idempotency.
     * Tests PostLikeDao.upsertFromRemote() idempotency.
     */
    @Test
    fun testLikeListenerIdempotency() = runBlocking {
        // Given: Remote like data
        val remoteLike = PostLikeEntity(
            id = "l1",
            postId = "p1",
            userId = "u1",
            createdAt = 1000L,
            isSynced = true,
            lastModified = 1000L,
            isDirty = false
        )

        // When: Apply same update 10 times
        repeat(10) {
            postLikeDao.upsertFromRemote(remoteLike)
        }

        // Then: State is identical
        val saved = postLikeDao.getLikeById("l1")
        assertNotNull("Like should exist", saved)
        assertEquals("p1", saved?.postId)
        assertEquals("u1", saved?.userId)
        assertEquals(1000L, saved?.lastModified)
        assertFalse("Should not be dirty", saved?.isDirty ?: true)

        // Verify no duplicates
        val allLikes = postLikeDao.getLikesForPost("p1")
        assertEquals("Should have exactly 1 like", 1, allLikes.size)
    }

    /**
     * TS-005: Verify social stats listener idempotency.
     * Tests SocialProfileDao.updateStatsFromRemote() idempotency.
     */
    @Test
    fun testFollowListenerIdempotency() = runBlocking {
        // Given: Create social profile first
        val profile = SocialProfileEntity(
            userId = "u1",
            username = "testuser",
            bio = "Test Bio",
            followerCount = 0,
            followingCount = 0,
            workoutCount = 0,
            isPrivate = false,
            createdAt = 500L,
            updatedAt = 500L,
            lastModified = 500L,
            isDirty = false,
            isSynced = true
        )
        socialProfileDao.upsertLocal(profile)

        // When: Apply same stats update 10 times
        repeat(10) {
            socialProfileDao.updateStatsFromRemote(
                userId = "u1",
                followerCount = 42,
                followingCount = 13,
                workoutCount = 100,
                remoteModified = 1000L
            )
        }

        // Then: State is identical
        val saved = socialProfileDao.getSocialProfileForSync("u1")
        assertNotNull("Profile should exist", saved)
        assertEquals(42, saved?.followerCount)
        assertEquals(13, saved?.followingCount)
        assertEquals(100, saved?.workoutCount)
        assertEquals(1000L, saved?.lastModified)
        assertFalse("Should not be dirty", saved?.isDirty ?: true)

        // Verify no duplicates
        val allProfiles = socialProfileDao.getAllSocialProfiles()
        assertEquals("Should have exactly 1 profile", 1, allProfiles.size)
    }

    /**
     * TS-004: Verify real-time listener update does not create feedback loop.
     * Tests that remote updates don't trigger outbound sync.
     */
    @Test
    fun testNoFeedbackLoopFromListenerUpdate() = runBlocking {
        // Given: Remote workout update
        val remoteWorkout = WorkoutEntity(
            id = "w2",
            userId = "u1",
            name = "Feedback Test",
            exercises = "[]",
            date = java.time.Instant.now(),
            duration = 0,
            notes = null,
            totalVolume = 0.0,
            isDirty = false,
            isSynced = true,
            syncVersion = 1L,
            lastModified = 1000L
        )

        // When: Apply remote update
        workoutDao.upsertFromRemote(remoteWorkout)

        // Then: Workout is NOT dirty (no sync trigger)
        val dirtyWorkouts = workoutDao.getDirtyWorkouts("u1")
        assertEquals("No workouts should be dirty", 0, dirtyWorkouts.size)

        val saved = workoutDao.getWorkoutById("w2", "u1")
        assertFalse("Workout should not be dirty", saved?.isDirty ?: true)
        assertTrue("Workout should be synced", saved?.isSynced ?: false)
    }

    /**
     * TS-002: Verify older remote updates are discarded (timestamp deduplication).
     */
    @Test
    fun testOlderRemoteUpdateDiscarded() = runBlocking {
        // Given: Local workout with newer timestamp
        val localWorkout = WorkoutEntity(
            id = "w3",
            userId = "u1",
            name = "Local V2",
            exercises = "[]",
            date = java.time.Instant.now(),
            duration = 0,
            notes = null,
            totalVolume = 0.0,
            isDirty = true,
            isSynced = false,
            syncVersion = 2L,
            lastModified = 2000L
        )
        workoutDao.upsertLocal(localWorkout)

        // When: Remote update with older timestamp arrives
        val olderRemote = WorkoutEntity(
            id = "w3",
            userId = "u1",
            name = "Remote V1",
            exercises = "[]",
            date = java.time.Instant.now(),
            duration = 0,
            notes = null,
            totalVolume = 0.0,
            isDirty = false,
            isSynced = true,
            syncVersion = 1L,
            lastModified = 1000L
        )
        workoutDao.upsertFromRemote(olderRemote)

        // Then: Local is preserved (newer)
        val saved = workoutDao.getWorkoutById("w3", "u1")
        assertEquals("Local V2", saved?.name)
        assertEquals(2000L, saved?.lastModified)
        assertTrue("Workout should still be dirty", saved?.isDirty ?: false)
    }
}
