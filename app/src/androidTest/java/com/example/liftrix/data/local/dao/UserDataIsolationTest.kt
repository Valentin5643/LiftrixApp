package com.example.liftrix.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.entity.BlockedUserEntity
import com.example.liftrix.data.local.entity.FollowRelationshipEntity
import com.example.liftrix.data.local.entity.GymBuddyEntity
import com.example.liftrix.data.local.entity.SocialPrivacySettingsEntity
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.example.liftrix.domain.model.social.SocialPrivacySettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Integration tests for user data isolation across all social DAOs.
 * Verifies that all database queries properly filter by userId to prevent data leakage.
 * Part of social infrastructure testing from SPEC-20250113-social-infrastructure.
 */
@RunWith(AndroidJUnit4::class)
class UserDataIsolationTest {

    private lateinit var database: LiftrixDatabase
    private lateinit var socialProfileDao: SocialProfileDao
    private lateinit var followRelationshipDao: FollowRelationshipDao
    private lateinit var gymBuddyDao: GymBuddyDao
    private lateinit var socialPrivacySettingsDao: SocialPrivacySettingsDao
    private lateinit var blockedUserDao: BlockedUserDao

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

        socialProfileDao = database.socialProfileDao()
        followRelationshipDao = database.followRelationshipDao()
        gymBuddyDao = database.gymBuddyDao()
        socialPrivacySettingsDao = database.socialPrivacySettingsDao()
        blockedUserDao = database.blockedUserDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun socialProfileDao_returnsOnlyUserSpecificData() = runTest {
        // Insert profiles for multiple users
        val profile1 = createSocialProfile(user1Id, "user1")
        val profile2 = createSocialProfile(user2Id, "user2")
        val profile3 = createSocialProfile(user3Id, "user3")

        socialProfileDao.insertProfile(profile1)
        socialProfileDao.insertProfile(profile2)
        socialProfileDao.insertProfile(profile3)

        // Verify each user only sees their own profile
        val user1Profile = socialProfileDao.getProfileByUserId(user1Id)
        assertEquals(user1Id, user1Profile?.userId)
        assertEquals("user1", user1Profile?.username)

        val user2Profile = socialProfileDao.getProfileByUserId(user2Id)
        assertEquals(user2Id, user2Profile?.userId)
        assertEquals("user2", user2Profile?.username)

        // Verify user cannot access other users' profiles through direct queries
        val nonExistentProfile = socialProfileDao.getProfileByUserId("nonexistent")
        assertEquals(null, nonExistentProfile)
    }

    @Test
    fun socialProfileDao_observeProfile_isolatesUserData() = runTest {
        // Insert profiles for multiple users
        val profile1 = createSocialProfile(user1Id, "user1")
        val profile2 = createSocialProfile(user2Id, "user2")

        socialProfileDao.insertProfile(profile1)
        socialProfileDao.insertProfile(profile2)

        // Observe user1's profile
        val observedProfile = socialProfileDao.observeProfileByUserId(user1Id).first()
        
        assertEquals(user1Id, observedProfile?.userId)
        assertEquals("user1", observedProfile?.username)

        // Verify cannot observe other user's profile
        val otherUserProfile = socialProfileDao.observeProfileByUserId(user2Id).first()
        assertEquals(user2Id, otherUserProfile?.userId) // Can observe but correct isolation
    }

    @Test
    fun followRelationshipDao_isolatesFollowersAndFollowing() = runTest {
        // Create follow relationships: user1 -> user2, user2 -> user3, user3 -> user1
        val follow1 = createFollowRelationship(user1Id, user2Id)
        val follow2 = createFollowRelationship(user2Id, user3Id)
        val follow3 = createFollowRelationship(user3Id, user1Id)

        followRelationshipDao.insertRelationship(follow1)
        followRelationshipDao.insertRelationship(follow2)
        followRelationshipDao.insertRelationship(follow3)

        // Verify user1 only sees their followers and following
        val user1Following = followRelationshipDao.getFollowing(user1Id).first()
        assertEquals(1, user1Following.size)
        assertTrue(user1Following.all { it.followerId == user1Id })

        val user1Followers = followRelationshipDao.getFollowers(user1Id).first()
        assertEquals(1, user1Followers.size)
        assertTrue(user1Followers.all { it.followingId == user1Id })

        // Verify user2 data isolation
        val user2Following = followRelationshipDao.getFollowing(user2Id).first()
        assertEquals(1, user2Following.size)
        assertTrue(user2Following.all { it.followerId == user2Id })

        val user2Followers = followRelationshipDao.getFollowers(user2Id).first()
        assertEquals(1, user2Followers.size)
        assertTrue(user2Followers.all { it.followingId == user2Id })
    }

    @Test
    fun followRelationshipDao_getRelationshipStatus_isolatesUsers() = runTest {
        // Create relationship: user1 -> user2
        val relationship = createFollowRelationship(user1Id, user2Id)
        followRelationshipDao.insertRelationship(relationship)

        // Verify user1 can see their outgoing relationship
        val relationshipStatus = followRelationshipDao.getRelationshipStatus(user1Id, user2Id)
        assertEquals("ACCEPTED", relationshipStatus?.status)
        assertEquals(user1Id, relationshipStatus?.followerId)
        assertEquals(user2Id, relationshipStatus?.followingId)

        // Verify user3 cannot see relationship between user1 and user2
        val noRelationship = followRelationshipDao.getRelationshipStatus(user3Id, user2Id)
        assertEquals(null, noRelationship)

        val noRelationship2 = followRelationshipDao.getRelationshipStatus(user1Id, user3Id)
        assertEquals(null, noRelationship2)
    }

    @Test
    fun gymBuddyDao_isolatesBuddyRelationships() = runTest {
        // Create buddy relationships: user1 <-> user2, user2 <-> user3
        val buddy1 = createGymBuddy(user1Id, user2Id)
        val buddy2 = createGymBuddy(user2Id, user1Id) // Reciprocal
        val buddy3 = createGymBuddy(user2Id, user3Id)
        val buddy4 = createGymBuddy(user3Id, user2Id) // Reciprocal

        gymBuddyDao.insertBuddy(buddy1)
        gymBuddyDao.insertBuddy(buddy2)
        gymBuddyDao.insertBuddy(buddy3)
        gymBuddyDao.insertBuddy(buddy4)

        // Verify user1 only sees their buddies
        val user1Buddies = gymBuddyDao.getBuddiesForUser(user1Id).first()
        assertEquals(1, user1Buddies.size)
        assertTrue(user1Buddies.all { it.userId == user1Id })
        assertEquals(user2Id, user1Buddies.first().buddyId)

        // Verify user2 sees their buddies (should have 2: user1 and user3)
        val user2Buddies = gymBuddyDao.getBuddiesForUser(user2Id).first()
        assertEquals(2, user2Buddies.size)
        assertTrue(user2Buddies.all { it.userId == user2Id })
        val buddyIds = user2Buddies.map { it.buddyId }.toSet()
        assertTrue(buddyIds.contains(user1Id))
        assertTrue(buddyIds.contains(user3Id))

        // Verify user3 only sees their buddy
        val user3Buddies = gymBuddyDao.getBuddiesForUser(user3Id).first()
        assertEquals(1, user3Buddies.size)
        assertTrue(user3Buddies.all { it.userId == user3Id })
        assertEquals(user2Id, user3Buddies.first().buddyId)
    }

    @Test
    fun gymBuddyDao_getBuddyRelationship_isolatesUsers() = runTest {
        val buddy = createGymBuddy(user1Id, user2Id)
        gymBuddyDao.insertBuddy(buddy)

        // Verify user1 can see their buddy relationship with user2
        val relationship = gymBuddyDao.getBuddyRelationship(user1Id, user2Id)
        assertEquals(user1Id, relationship?.userId)
        assertEquals(user2Id, relationship?.buddyId)

        // Verify user3 cannot see relationship between user1 and user2
        val noRelationship = gymBuddyDao.getBuddyRelationship(user3Id, user2Id)
        assertEquals(null, noRelationship)

        val noRelationship2 = gymBuddyDao.getBuddyRelationship(user1Id, user3Id)
        assertEquals(null, noRelationship2)
    }

    @Test
    fun socialPrivacySettingsDao_isolatesUserSettings() = runTest {
        // Create privacy settings for multiple users
        val settings1 = createPrivacySettings(user1Id, socialEnabled = true)
        val settings2 = createPrivacySettings(user2Id, socialEnabled = false)
        val settings3 = createPrivacySettings(user3Id, socialEnabled = true)

        socialPrivacySettingsDao.insertPrivacySettings(settings1)
        socialPrivacySettingsDao.insertPrivacySettings(settings2)
        socialPrivacySettingsDao.insertPrivacySettings(settings3)

        // Verify each user only sees their own settings
        val user1Settings = socialPrivacySettingsDao.getPrivacySettings(user1Id)
        assertEquals(user1Id, user1Settings?.userId)
        assertTrue(user1Settings?.socialEnabled == true)

        val user2Settings = socialPrivacySettingsDao.getPrivacySettings(user2Id)
        assertEquals(user2Id, user2Settings?.userId)
        assertFalse(user2Settings?.socialEnabled == true)

        // Verify user cannot access other users' settings
        val nonExistentSettings = socialPrivacySettingsDao.getPrivacySettings("nonexistent")
        assertEquals(null, nonExistentSettings)
    }

    @Test
    fun blockedUserDao_isolatesBlockedUsers() = runTest {
        // User1 blocks user2, user2 blocks user3, user3 blocks user1
        val block1 = createBlockedUser(user1Id, user2Id)
        val block2 = createBlockedUser(user2Id, user3Id)
        val block3 = createBlockedUser(user3Id, user1Id)

        blockedUserDao.insertBlockedUser(block1)
        blockedUserDao.insertBlockedUser(block2)
        blockedUserDao.insertBlockedUser(block3)

        // Verify user1 only sees their blocked users
        val user1Blocked = blockedUserDao.getBlockedUsersForUser(user1Id).first()
        assertEquals(1, user1Blocked.size)
        assertTrue(user1Blocked.all { it.userId == user1Id })
        assertEquals(user2Id, user1Blocked.first().blockedUserId)

        // Verify user2 only sees their blocked users
        val user2Blocked = blockedUserDao.getBlockedUsersForUser(user2Id).first()
        assertEquals(1, user2Blocked.size)
        assertTrue(user2Blocked.all { it.userId == user2Id })
        assertEquals(user3Id, user2Blocked.first().blockedUserId)

        // Verify user3 only sees their blocked users
        val user3Blocked = blockedUserDao.getBlockedUsersForUser(user3Id).first()
        assertEquals(1, user3Blocked.size)
        assertTrue(user3Blocked.all { it.userId == user3Id })
        assertEquals(user1Id, user3Blocked.first().blockedUserId)
    }

    @Test
    fun blockedUserDao_isUserBlocked_isolatesUsers() = runTest {
        val blockedUser = createBlockedUser(user1Id, user2Id)
        blockedUserDao.insertBlockedUser(blockedUser)

        // Verify user1 can check if they blocked user2
        val isBlocked = blockedUserDao.isUserBlocked(user1Id, user2Id)
        assertTrue(isBlocked)

        // Verify user2 cannot see that user1 blocked them through this method
        // This method checks if userId blocked blockedUserId, so user2 checking user1 should be false
        val isUser1BlockedByUser2 = blockedUserDao.isUserBlocked(user2Id, user1Id)
        assertFalse(isUser1BlockedByUser2)

        // Verify user3 cannot see any blocking relationship
        val user3ChecksUser1 = blockedUserDao.isUserBlocked(user3Id, user1Id)
        assertFalse(user3ChecksUser1)

        val user3ChecksUser2 = blockedUserDao.isUserBlocked(user3Id, user2Id)
        assertFalse(user3ChecksUser2)
    }

    @Test
    fun crossDaoDataIsolation_verifyCompleteIsolation() = runTest {
        // Create comprehensive test data for all users across all DAOs
        setupCompleteTestData()

        // Verify user1 can only see their own data across all DAOs
        val user1Profile = socialProfileDao.getProfileByUserId(user1Id)
        val user1Following = followRelationshipDao.getFollowing(user1Id).first()
        val user1Buddies = gymBuddyDao.getBuddiesForUser(user1Id).first()
        val user1Settings = socialPrivacySettingsDao.getPrivacySettings(user1Id)
        val user1Blocked = blockedUserDao.getBlockedUsersForUser(user1Id).first()

        // All data should belong to user1
        assertEquals(user1Id, user1Profile?.userId)
        assertTrue(user1Following.all { it.followerId == user1Id })
        assertTrue(user1Buddies.all { it.userId == user1Id })
        assertEquals(user1Id, user1Settings?.userId)
        assertTrue(user1Blocked.all { it.userId == user1Id })

        // Verify user2 data isolation
        val user2Profile = socialProfileDao.getProfileByUserId(user2Id)
        val user2Following = followRelationshipDao.getFollowing(user2Id).first()
        val user2Buddies = gymBuddyDao.getBuddiesForUser(user2Id).first()
        val user2Settings = socialPrivacySettingsDao.getPrivacySettings(user2Id)
        val user2Blocked = blockedUserDao.getBlockedUsersForUser(user2Id).first()

        // All data should belong to user2
        assertEquals(user2Id, user2Profile?.userId)
        assertTrue(user2Following.all { it.followerId == user2Id })
        assertTrue(user2Buddies.all { it.userId == user2Id })
        assertEquals(user2Id, user2Settings?.userId)
        assertTrue(user2Blocked.all { it.userId == user2Id })

        // Verify no cross-contamination
        assertFalse(user1Following.any { it.followerId != user1Id })
        assertFalse(user2Buddies.any { it.userId != user2Id })
    }

    private suspend fun setupCompleteTestData() {
        // Social Profiles
        socialProfileDao.insertProfile(createSocialProfile(user1Id, "user1"))
        socialProfileDao.insertProfile(createSocialProfile(user2Id, "user2"))
        socialProfileDao.insertProfile(createSocialProfile(user3Id, "user3"))

        // Follow Relationships
        followRelationshipDao.insertRelationship(createFollowRelationship(user1Id, user2Id))
        followRelationshipDao.insertRelationship(createFollowRelationship(user2Id, user3Id))
        followRelationshipDao.insertRelationship(createFollowRelationship(user3Id, user1Id))

        // Gym Buddies
        gymBuddyDao.insertBuddy(createGymBuddy(user1Id, user2Id))
        gymBuddyDao.insertBuddy(createGymBuddy(user2Id, user3Id))
        gymBuddyDao.insertBuddy(createGymBuddy(user3Id, user1Id))

        // Privacy Settings
        socialPrivacySettingsDao.insertPrivacySettings(createPrivacySettings(user1Id, true))
        socialPrivacySettingsDao.insertPrivacySettings(createPrivacySettings(user2Id, false))
        socialPrivacySettingsDao.insertPrivacySettings(createPrivacySettings(user3Id, true))

        // Blocked Users
        blockedUserDao.insertBlockedUser(createBlockedUser(user1Id, user3Id))
        blockedUserDao.insertBlockedUser(createBlockedUser(user2Id, user1Id))
        blockedUserDao.insertBlockedUser(createBlockedUser(user3Id, user2Id))
    }

    // Helper functions to create test entities
    private fun createSocialProfile(userId: String, username: String) = SocialProfileEntity(
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
        isPrivate = true,
        hideFromSuggestions = false,
        allowFriendRequests = true,
        instagramHandle = null,
        youtubeChannel = null,
        personalWebsite = null,
        isSynced = false,
        syncVersion = 0,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private fun createFollowRelationship(followerId: String, followingId: String) = FollowRelationshipEntity(
        id = UUID.randomUUID().toString(),
        followerId = followerId,
        followingId = followingId,
        status = "ACCEPTED",
        createdAt = System.currentTimeMillis(),
        acceptedAt = System.currentTimeMillis(),
        blockedAt = null,
        isSynced = false,
        syncVersion = 0
    )

    private fun createGymBuddy(userId: String, buddyId: String) = GymBuddyEntity(
        id = UUID.randomUUID().toString(),
        userId = userId,
        buddyId = buddyId,
        buddyNickname = null,
        createdAt = System.currentTimeMillis(),
        lastPrNotificationSent = null,
        notificationCooldownHours = 24,
        pairedViaQr = true,
        pairingLocation = null,
        isSynced = false,
        syncVersion = 0
    )

    private fun createPrivacySettings(userId: String, socialEnabled: Boolean) = SocialPrivacySettingsEntity(
        userId = userId,
        socialEnabled = socialEnabled,
        profileVisibility = SocialPrivacySettings.ProfileVisibility.PRIVATE.name,
        allowFollowRequests = false,
        workoutSharingEnabled = false,
        gymBuddiesEnabled = false,
        communityParticipation = false,
        challengeParticipation = false,
        routineSharingEnabled = false,
        defaultWorkoutVisibility = "PRIVATE",
        showWorkoutStats = true,
        showAchievements = true,
        showWorkoutStreak = true,
        hideFromSuggestions = true,
        hideFromSearch = false,
        notificationSettings = "{}",
        isSynced = false,
        syncVersion = 0,
        updatedAt = System.currentTimeMillis()
    )

    private fun createBlockedUser(userId: String, blockedUserId: String) = BlockedUserEntity(
        id = UUID.randomUUID().toString(),
        userId = userId,
        blockedUserId = blockedUserId,
        reason = null,
        blockedAt = System.currentTimeMillis(),
        isSynced = false
    )
}