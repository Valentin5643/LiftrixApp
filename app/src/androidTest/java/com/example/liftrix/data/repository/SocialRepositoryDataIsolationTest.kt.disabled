package com.example.liftrix.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.mapper.SocialProfileMapper
import com.example.liftrix.data.repository.social.SocialProfileRepositoryImpl
import com.example.liftrix.domain.model.social.SocialProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for repository-level data isolation in social features.
 * Verifies that repository implementations properly enforce user data isolation
 * and prevent cross-user data access through higher-level APIs.
 * Part of social infrastructure testing from SPEC-20250113-social-infrastructure.
 */
@RunWith(AndroidJUnit4::class)
class SocialRepositoryDataIsolationTest {

    private lateinit var database: LiftrixDatabase
    private lateinit var repository: SocialProfileRepositoryImpl
    private lateinit var mapper: SocialProfileMapper

    // Test users for isolation verification
    private val user1Id = "user1"
    private val user2Id = "user2"
    private val user3Id = "user3"

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LiftrixDatabase::class.java
        ).allowMainThreadQueries().build()

        mapper = SocialProfileMapper()
        repository = SocialProfileRepositoryImpl(
            dao = database.socialProfileDao(),
            mapper = mapper
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun createProfile_isolatesUserData() = runTest {
        // Create profiles for multiple users
        val profile1 = createSocialProfile(user1Id, "user1")
        val profile2 = createSocialProfile(user2Id, "user2")

        val result1 = repository.createProfile(profile1)
        val result2 = repository.createProfile(profile2)

        // Both should succeed
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)

        // Verify each user gets their own profile back
        val returnedProfile1 = result1.getOrNull()!!
        val returnedProfile2 = result2.getOrNull()!!

        assertEquals(user1Id, returnedProfile1.userId)
        assertEquals("user1", returnedProfile1.username)
        assertEquals(user2Id, returnedProfile2.userId)
        assertEquals("user2", returnedProfile2.username)
    }

    @Test
    fun getProfile_withViewerId_respectsPrivacy() = runTest {
        // Create a private profile for user1
        val privateProfile = createSocialProfile(user1Id, "user1", isPrivate = true)
        repository.createProfile(privateProfile)

        // User1 should see their own profile
        val ownProfileResult = repository.getProfile(user1Id, user1Id)
        assertTrue(ownProfileResult.isSuccess)
        assertEquals(user1Id, ownProfileResult.getOrNull()?.userId)

        // User2 should not see user1's private profile
        val otherUserResult = repository.getProfile(user1Id, user2Id)
        assertTrue(otherUserResult.isSuccess)
        assertNull(otherUserResult.getOrNull()) // Should be null due to privacy
    }

    @Test
    fun getProfile_withoutViewerId_returnsNull() = runTest {
        // Create profile for user1
        val profile = createSocialProfile(user1Id, "user1")
        repository.createProfile(profile)

        // Without viewerId, should not return profile data (privacy protection)
        val result = repository.getProfile(user1Id, null)
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun observeProfile_isolatesUserData() = runTest {
        // Create profiles for multiple users
        val profile1 = createSocialProfile(user1Id, "user1")
        val profile2 = createSocialProfile(user2Id, "user2")

        repository.createProfile(profile1)
        repository.createProfile(profile2)

        // Observe user1's profile
        val observedProfile1 = repository.observeProfile(user1Id).first()
        assertEquals(user1Id, observedProfile1?.userId)
        assertEquals("user1", observedProfile1?.username)

        // Observe user2's profile
        val observedProfile2 = repository.observeProfile(user2Id).first()
        assertEquals(user2Id, observedProfile2?.userId)
        assertEquals("user2", observedProfile2?.username)

        // Observing non-existent user should return null
        val nonExistentProfile = repository.observeProfile("nonexistent").first()
        assertNull(nonExistentProfile)
    }

    @Test
    fun updateProfile_onlyUpdatesOwnProfile() = runTest {
        // Create profiles for both users
        val profile1 = createSocialProfile(user1Id, "user1")
        val profile2 = createSocialProfile(user2Id, "user2")

        repository.createProfile(profile1)
        repository.createProfile(profile2)

        // Update user1's profile
        val updateResult = repository.updateProfile(
            userId = user1Id,
            updates = SocialProfileRepositoryImpl.ProfileUpdate(
                displayName = "Updated User 1",
                bio = "Updated bio"
            )
        )

        assertTrue(updateResult.isSuccess)

        // Verify user1's profile was updated
        val updatedProfile = repository.getProfile(user1Id, user1Id).getOrNull()!!
        assertEquals("Updated User 1", updatedProfile.displayName)
        assertEquals("Updated bio", updatedProfile.bio)

        // Verify user2's profile was not affected
        val user2Profile = repository.getProfile(user2Id, user2Id).getOrNull()!!
        assertEquals("Display user2", user2Profile.displayName)
        assertEquals("Bio for user2", user2Profile.bio)
    }

    @Test
    fun deleteProfile_onlyDeletesOwnProfile() = runTest {
        // Create profiles for both users
        val profile1 = createSocialProfile(user1Id, "user1")
        val profile2 = createSocialProfile(user2Id, "user2")

        repository.createProfile(profile1)
        repository.createProfile(profile2)

        // Delete user1's profile
        val deleteResult = repository.deleteProfile(user1Id)
        assertTrue(deleteResult.isSuccess)

        // Verify user1's profile is gone
        val deletedProfile = repository.getProfile(user1Id, user1Id).getOrNull()
        assertNull(deletedProfile)

        // Verify user2's profile still exists
        val remainingProfile = repository.getProfile(user2Id, user2Id).getOrNull()
        assertEquals(user2Id, remainingProfile?.userId)
    }

    @Test
    fun checkUsernameAvailability_preventsUsernameConflicts() = runTest {
        // Create profile with username "testuser"
        val profile = createSocialProfile(user1Id, "testuser")
        repository.createProfile(profile)

        // Username should not be available for other users
        val availabilityResult = repository.checkUsernameAvailability("testuser")
        assertTrue(availabilityResult.isSuccess)
        val isAvailable = availabilityResult.getOrNull()!!
        assertTrue(!isAvailable) // Should be false (not available)

        // Different username should be available
        val otherUsernameResult = repository.checkUsernameAvailability("otherusername")
        assertTrue(otherUsernameResult.isSuccess)
        val otherUsernameAvailable = otherUsernameResult.getOrNull()!!
        assertTrue(otherUsernameAvailable) // Should be true (available)
    }

    @Test
    fun repository_enforcesDataIsolationAcrossOperations() = runTest {
        // Create comprehensive test scenario
        val profile1 = createSocialProfile(user1Id, "user1")
        val profile2 = createSocialProfile(user2Id, "user2")
        val profile3 = createSocialProfile(user3Id, "user3")

        // Create all profiles
        repository.createProfile(profile1)
        repository.createProfile(profile2)
        repository.createProfile(profile3)

        // Each user should only be able to access their own profile
        val user1Profile = repository.getProfile(user1Id, user1Id).getOrNull()
        val user2Profile = repository.getProfile(user2Id, user2Id).getOrNull()
        val user3Profile = repository.getProfile(user3Id, user3Id).getOrNull()

        assertEquals(user1Id, user1Profile?.userId)
        assertEquals(user2Id, user2Profile?.userId)
        assertEquals(user3Id, user3Profile?.userId)

        // Cross-user access should be restricted (depends on privacy settings)
        val user1ViewingUser2 = repository.getProfile(user2Id, user1Id).getOrNull()
        assertNull(user1ViewingUser2) // Should be null due to private profile default

        // Update operations should be isolated
        repository.updateProfile(
            userId = user1Id,
            updates = SocialProfileRepositoryImpl.ProfileUpdate(displayName = "Updated User 1")
        )

        // Only user1's profile should be updated
        val updatedUser1 = repository.getProfile(user1Id, user1Id).getOrNull()
        val unchangedUser2 = repository.getProfile(user2Id, user2Id).getOrNull()

        assertEquals("Updated User 1", updatedUser1?.displayName)
        assertEquals("Display user2", unchangedUser2?.displayName) // Should be unchanged
    }

    @Test
    fun repository_handlesInvalidUserIds() = runTest {
        // Attempt to get profile for non-existent user
        val nonExistentResult = repository.getProfile("nonexistent", user1Id)
        assertTrue(nonExistentResult.isSuccess)
        assertNull(nonExistentResult.getOrNull())

        // Attempt to update non-existent user's profile
        val updateResult = repository.updateProfile(
            userId = "nonexistent",
            updates = SocialProfileRepositoryImpl.ProfileUpdate(displayName = "Should fail")
        )
        assertTrue(updateResult.isFailure) // Should fail gracefully

        // Attempt to delete non-existent user's profile
        val deleteResult = repository.deleteProfile("nonexistent")
        assertTrue(deleteResult.isSuccess) // Should succeed (idempotent)

        // Observe non-existent user
        val observed = repository.observeProfile("nonexistent").first()
        assertNull(observed)
    }

    // Helper function to create test social profiles
    private fun createSocialProfile(
        userId: String, 
        username: String, 
        isPrivate: Boolean = true
    ) = SocialProfile(
        userId = userId,
        username = username,
        displayName = "Display $username",
        bio = "Bio for $username",
        profilePhotoUrl = null,
        coverPhotoUrl = null,
        workoutCount = 0,
        followerCount = 0,
        followingCount = 0,
        memberSince = System.currentTimeMillis(),
        lastActive = null,
        isVerified = false,
        isPrivate = isPrivate,
        hideFromSuggestions = false,
        allowFriendRequests = true,
        instagramHandle = null,
        youtubeChannel = null,
        personalWebsite = null,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}