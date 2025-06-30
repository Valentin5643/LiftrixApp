package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.FriendDao
import com.example.liftrix.data.local.dao.PrivacySettingsDao
import com.example.liftrix.data.local.entity.FriendEntity
import com.example.liftrix.data.local.entity.PrivacySettingsEntity
import com.example.liftrix.data.mapper.FriendMapper
import com.example.liftrix.data.cache.RecommendationCache
import com.example.liftrix.domain.model.Friend
import com.example.liftrix.domain.model.FriendStatus
import com.example.liftrix.domain.model.RecommendedUser
import com.example.liftrix.domain.model.SharedWorkout
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.UserPresence
import com.example.liftrix.domain.repository.AuthRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit and integration tests for SocialRepositoryImpl
 * Tests friend management, privacy controls, and workout sharing functionality
 * Following task SOCIAL-TEST-001 requirements
 */
class SocialRepositoryImplTest {

    private lateinit var friendDao: FriendDao
    private lateinit var privacySettingsDao: PrivacySettingsDao
    private lateinit var friendMapper: FriendMapper
    private lateinit var authRepository: AuthRepository
    private lateinit var firestore: FirebaseFirestore
    private lateinit var recommendationCache: RecommendationCache
    private lateinit var repository: SocialRepositoryImpl

    // Test data
    private val testUserId = "test-user-123"
    private val testFriendId = "friend-user-456"
    private val testFriendId2 = "friend-user-789"
    
    private val testFriendEntity = FriendEntity(
        userId = testUserId,
        friendUserId = testFriendId,
        status = "ACCEPTED",
        createdAt = Instant.now().minusSeconds(3600),
        updatedAt = Instant.now().minusSeconds(3600),
        isSynced = true
    )
    
    private val testPendingEntity = FriendEntity(
        userId = testFriendId2,
        friendUserId = testUserId,
        status = "PENDING",
        createdAt = Instant.now().minusSeconds(1800),
        updatedAt = Instant.now().minusSeconds(1800),
        isSynced = false
    )
    
    private val testFriend = Friend(
        userId = testFriendId,
        displayName = "Test Friend",
        email = "test.friend@example.com",
        avatarUrl = null,
        status = FriendStatus.ACCEPTED,
        presence = null,
        friendSince = testFriendEntity.createdAt
    )
    
    private val testPrivacySettings = PrivacySettingsEntity(
        userId = testFriendId,
        allowFriendRequests = true,
        workoutVisibility = "FRIENDS",
        profileVisibility = "PUBLIC",
        createdAt = Instant.now().minusSeconds(7200),
        updatedAt = Instant.now().minusSeconds(7200)
    )

    @Before
    fun setup() {
        friendDao = mockk()
        privacySettingsDao = mockk()
        friendMapper = mockk()
        authRepository = mockk()
        firestore = mockk()
        recommendationCache = mockk()
        
        repository = SocialRepositoryImpl(
            friendDao = friendDao,
            privacySettingsDao = privacySettingsDao,
            friendMapper = friendMapper,
            authRepository = authRepository,
            firestore = firestore,
            recommendationCache = recommendationCache
        )
        
        // Default auth behavior
        every { authRepository.getCurrentUserId() } returns testUserId
    }

    // Friend Request Tests

    @Test
    fun `sendFriendRequest creates pending friend relationship`() = runTest {
        // Given
        every { friendDao.getFriendRelationship(testUserId, testFriendId) } returns null
        coEvery { privacySettingsDao.getPrivacySettingsOnce(testFriendId) } returns testPrivacySettings
        every { friendMapper.createFriendRequest(testUserId, testFriendId, false) } returns testPendingEntity
        coEvery { friendDao.insertFriend(testPendingEntity) } returns 1L
        coEvery { firestore.collection("friendships").document(any()).set(any()) } returns Tasks.forResult(null)

        // When
        val result = repository.sendFriendRequest(testFriendId)

        // Then
        assertTrue(result.isSuccess)
        coVerify { friendDao.insertFriend(testPendingEntity) }
        verify { friendMapper.createFriendRequest(testUserId, testFriendId, false) }
    }

    @Test
    fun `sendFriendRequest fails when user not authenticated`() = runTest {
        // Given
        every { authRepository.getCurrentUserId() } returns null

        // When
        val result = repository.sendFriendRequest(testFriendId)

        // Then
        assertTrue(result.isFailure)
        assertEquals("User not authenticated", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sendFriendRequest fails when trying to add self`() = runTest {
        // When
        val result = repository.sendFriendRequest(testUserId)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Cannot send friend request to yourself", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sendFriendRequest fails when relationship already exists`() = runTest {
        // Given
        coEvery { friendDao.getFriendRelationship(testUserId, testFriendId) } returns testFriendEntity

        // When
        val result = repository.sendFriendRequest(testFriendId)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Friend relationship already exists", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sendFriendRequest fails when target user blocks friend requests`() = runTest {
        // Given
        val blockedPrivacySettings = testPrivacySettings.copy(allowFriendRequests = false)
        coEvery { friendDao.getFriendRelationship(testUserId, testFriendId) } returns null
        coEvery { privacySettingsDao.getPrivacySettingsOnce(testFriendId) } returns blockedPrivacySettings

        // When
        val result = repository.sendFriendRequest(testFriendId)

        // Then
        assertTrue(result.isFailure)
        assertEquals("User is not accepting friend requests", result.exceptionOrNull()?.message)
    }

    @Test
    fun `respondToFriendRequest accepts friend request successfully`() = runTest {
        // Given
        coEvery { friendDao.getFriendRelationship(testFriendId2, testUserId) } returns testPendingEntity
        coEvery { friendDao.updateFriendStatus(testFriendId2, testUserId, "ACCEPTED", any()) } returns 1
        every { friendMapper.createFriendRequest(testUserId, testFriendId2, false) } returns testFriendEntity.copy(friendUserId = testFriendId2)
        coEvery { friendDao.insertFriend(any()) } returns 1L
        coEvery { firestore.collection("friendships").document(any()).update(any()) } returns Tasks.forResult(null)

        // When
        val result = repository.respondToFriendRequest(testFriendId2, accept = true)

        // Then
        assertTrue(result.isSuccess)
        coVerify { friendDao.updateFriendStatus(testFriendId2, testUserId, "ACCEPTED", any()) }
        coVerify { friendDao.insertFriend(any()) }
    }

    @Test
    fun `respondToFriendRequest declines friend request successfully`() = runTest {
        // Given
        coEvery { friendDao.getFriendRelationship(testFriendId2, testUserId) } returns testPendingEntity
        coEvery { friendDao.updateFriendStatus(testFriendId2, testUserId, "DECLINED", any()) } returns 1
        coEvery { firestore.collection("friendships").document(any()).update(any()) } returns Tasks.forResult(null)

        // When
        val result = repository.respondToFriendRequest(testFriendId2, accept = false)

        // Then
        assertTrue(result.isSuccess)
        coVerify { friendDao.updateFriendStatus(testFriendId2, testUserId, "DECLINED", any()) }
        coVerify(exactly = 0) { friendDao.insertFriend(any()) }
    }

    @Test
    fun `respondToFriendRequest fails when no pending request found`() = runTest {
        // Given
        coEvery { friendDao.getFriendRelationship(testFriendId2, testUserId) } returns null

        // When
        val result = repository.respondToFriendRequest(testFriendId2, accept = true)

        // Then
        assertTrue(result.isFailure)
        assertEquals("No pending friend request found", result.exceptionOrNull()?.message)
    }

    @Test
    fun `respondToFriendRequest fails when request not pending`() = runTest {
        // Given
        val acceptedEntity = testPendingEntity.copy(status = "ACCEPTED")
        coEvery { friendDao.getFriendRelationship(testFriendId2, testUserId) } returns acceptedEntity

        // When
        val result = repository.respondToFriendRequest(testFriendId2, accept = true)

        // Then
        assertTrue(result.isFailure)
        assertEquals("No pending friend request found", result.exceptionOrNull()?.message)
    }

    // Friend Management Tests

    @Test
    fun `getFriends returns accepted friends with mapped data`() = runTest {
        // Given
        val friendEntities = listOf(testFriendEntity)
        every { friendDao.getFriends(testUserId) } returns flowOf(friendEntities)
        every { friendMapper.toDomain(testFriendEntity, any(), any(), any(), any()) } returns testFriend

        // When
        val result = repository.getFriends(testUserId).toList()

        // Then
        assertEquals(1, result.size)
        assertEquals(1, result[0].size)
        assertEquals(testFriend, result[0][0])
        verify { friendDao.getFriends(testUserId) }
    }

    @Test
    fun `getFriends handles mapping errors gracefully`() = runTest {
        // Given
        val friendEntities = listOf(testFriendEntity)
        every { friendDao.getFriends(testUserId) } returns flowOf(friendEntities)
        every { friendMapper.toDomain(testFriendEntity, any(), any(), any(), any()) } throws RuntimeException("Mapping failed")

        // When
        val result = repository.getFriends(testUserId).toList()

        // Then
        assertEquals(1, result.size)
        assertEquals(0, result[0].size) // Failed mapping should be filtered out
    }

    @Test
    fun `getPendingFriendRequests returns incoming requests`() = runTest {
        // Given
        val pendingEntities = listOf(testPendingEntity)
        every { friendDao.getIncomingFriendRequests(testUserId) } returns flowOf(pendingEntities)
        every { friendMapper.toDomain(testPendingEntity, any(), any(), any(), any()) } returns testFriend.copy(status = FriendStatus.PENDING)

        // When
        val result = repository.getPendingFriendRequests(testUserId).toList()

        // Then
        assertEquals(1, result.size)
        assertEquals(1, result[0].size)
        assertEquals(FriendStatus.PENDING, result[0][0].status)
        verify { friendDao.getIncomingFriendRequests(testUserId) }
    }

    @Test
    fun `removeFriend deletes bidirectional relationship`() = runTest {
        // Given
        coEvery { friendDao.deleteBidirectionalFriendRelationship(testUserId, testFriendId) } returns 2
        coEvery { firestore.collection("friendships").document(any()).delete() } returns Tasks.forResult(null)

        // When
        val result = repository.removeFriend(testFriendId)

        // Then
        assertTrue(result.isSuccess)
        coVerify { friendDao.deleteBidirectionalFriendRelationship(testUserId, testFriendId) }
        coVerify(exactly = 2) { firestore.collection("friendships").document(any()).delete() }
    }

    @Test
    fun `blockUser creates or updates blocked relationship`() = runTest {
        // Given
        coEvery { friendDao.getFriendRelationship(testUserId, testFriendId) } returns testFriendEntity
        coEvery { friendDao.updateFriendStatus(testUserId, testFriendId, "BLOCKED", any()) } returns 1
        coEvery { friendDao.deleteFriendRelationship(testFriendId, testUserId) } returns 1
        coEvery { firestore.collection("friendships").document(any()).set(any()) } returns Tasks.forResult(null)

        // When
        val result = repository.blockUser(testFriendId)

        // Then
        assertTrue(result.isSuccess)
        coVerify { friendDao.updateFriendStatus(testUserId, testFriendId, "BLOCKED", any()) }
        coVerify { friendDao.deleteFriendRelationship(testFriendId, testUserId) }
    }

    @Test
    fun `blockUser creates new blocked relationship when none exists`() = runTest {
        // Given
        coEvery { friendDao.getFriendRelationship(testUserId, testFriendId) } returns null
        every { friendMapper.createFriendRequest(testUserId, testFriendId) } returns testFriendEntity.copy(status = "BLOCKED")
        coEvery { friendDao.insertFriend(any()) } returns 1L
        coEvery { friendDao.deleteFriendRelationship(testFriendId, testUserId) } returns 0
        coEvery { firestore.collection("friendships").document(any()).set(any()) } returns Tasks.forResult(null)

        // When
        val result = repository.blockUser(testFriendId)

        // Then
        assertTrue(result.isSuccess)
        coVerify { friendDao.insertFriend(any()) }
        verify { friendMapper.createFriendRequest(testUserId, testFriendId) }
    }

    @Test
    fun `unblockUser removes blocked relationship`() = runTest {
        // Given
        coEvery { friendDao.deleteFriendRelationship(testUserId, testFriendId) } returns 1
        coEvery { firestore.collection("friendships").document(any()).delete() } returns Tasks.forResult(null)

        // When
        val result = repository.unblockUser(testFriendId)

        // Then
        assertTrue(result.isSuccess)
        coVerify { friendDao.deleteFriendRelationship(testUserId, testFriendId) }
    }

    // User Search Tests

    @Test
    fun `searchUsers returns empty list for blank query`() = runTest {
        // When
        val result = repository.searchUsers("").toList()

        // Then
        assertEquals(1, result.size)
        assertEquals(0, result[0].size)
    }

    @Test
    fun `searchUsers returns empty list when user not authenticated`() = runTest {
        // Given
        every { authRepository.getCurrentUserId() } returns null

        // When
        val result = repository.searchUsers("test").toList()

        // Then
        assertEquals(1, result.size)
        assertEquals(0, result[0].size)
    }

    @Test
    fun `searchUsers performs Firebase query and returns mapped users`() = runTest {
        // Given
        val query = "Test User"
        val mockCollection: CollectionReference = mockk()
        val mockDisplayNameQuery: Query = mockk()
        val mockEmailQuery: Query = mockk()
        val mockDisplayNameSnapshot: QuerySnapshot = mockk()
        val mockEmailSnapshot: QuerySnapshot = mockk()
        val mockDocument: DocumentSnapshot = mockk()
        
        every { firestore.collection("users") } returns mockCollection
        every { mockCollection.whereGreaterThanOrEqualTo("displayName", query) } returns mockDisplayNameQuery
        every { mockDisplayNameQuery.whereLessThanOrEqualTo("displayName", query + "\uf8ff") } returns mockDisplayNameQuery
        every { mockDisplayNameQuery.limit(20L) } returns mockDisplayNameQuery
        every { mockDisplayNameQuery.get() } returns Tasks.forResult(mockDisplayNameSnapshot)
        
        every { mockCollection.whereEqualTo("email", query.lowercase()) } returns mockEmailQuery
        every { mockEmailQuery.limit(5) } returns mockEmailQuery
        every { mockEmailQuery.get() } returns Tasks.forResult(mockEmailSnapshot)
        
        every { mockDisplayNameSnapshot.documents } returns listOf(mockDocument)
        every { mockEmailSnapshot.documents } returns emptyList()
        every { mockDocument.id } returns testFriendId
        every { mockDocument.data } returns mapOf(
            "email" to "test.user@example.com",
            "displayName" to "Test User",
            "isAnonymous" to false,
            "onboardingCompleted" to true
        )

        // When
        val result = repository.searchUsers(query).toList()

        // Then
        assertEquals(1, result.size)
        assertEquals(1, result[0].size)
        assertEquals("Test User", result[0][0].displayName)
        assertEquals("test.user@example.com", result[0][0].email)
    }

    @Test
    fun `searchUsers excludes current user from results`() = runTest {
        // Given
        val query = "Test"
        val mockCollection: CollectionReference = mockk()
        val mockDisplayNameQuery: Query = mockk()
        val mockEmailQuery: Query = mockk()
        val mockDisplayNameSnapshot: QuerySnapshot = mockk()
        val mockEmailSnapshot: QuerySnapshot = mockk()
        val mockDocument: DocumentSnapshot = mockk()
        
        every { firestore.collection("users") } returns mockCollection
        every { mockCollection.whereGreaterThanOrEqualTo("displayName", query) } returns mockDisplayNameQuery
        every { mockDisplayNameQuery.whereLessThanOrEqualTo("displayName", query + "\uf8ff") } returns mockDisplayNameQuery
        every { mockDisplayNameQuery.limit(20L) } returns mockDisplayNameQuery
        every { mockDisplayNameQuery.get() } returns Tasks.forResult(mockDisplayNameSnapshot)
        
        every { mockCollection.whereEqualTo("email", query.lowercase()) } returns mockEmailQuery
        every { mockEmailQuery.limit(5) } returns mockEmailQuery
        every { mockEmailQuery.get() } returns Tasks.forResult(mockEmailSnapshot)
        
        every { mockDisplayNameSnapshot.documents } returns listOf(mockDocument)
        every { mockEmailSnapshot.documents } returns emptyList()
        every { mockDocument.id } returns testUserId // Current user ID
        every { mockDocument.data } returns mapOf(
            "email" to "current.user@example.com",
            "displayName" to "Current User"
        )

        // When
        val result = repository.searchUsers(query).toList()

        // Then
        assertEquals(1, result.size)
        assertEquals(0, result[0].size) // Current user should be excluded
    }

    // Workout Feed Tests

    @Test
    fun `getFriendWorkoutFeed returns empty list (placeholder implementation)`() = runTest {
        // When
        val result = repository.getFriendWorkoutFeed(testUserId).toList()

        // Then
        assertEquals(1, result.size)
        assertEquals(0, result[0].size)
    }

    // Error Handling Tests

    @Test
    fun `repository methods handle authentication failures`() = runTest {
        // Given
        every { authRepository.getCurrentUserId() } returns null

        // When & Then
        val sendResult = repository.sendFriendRequest(testFriendId)
        val respondResult = repository.respondToFriendRequest(testFriendId, true)
        val blockResult = repository.blockUser(testFriendId)
        val unblockResult = repository.unblockUser(testFriendId)
        val removeResult = repository.removeFriend(testFriendId)

        assertTrue(sendResult.isFailure)
        assertTrue(respondResult.isFailure)
        assertTrue(blockResult.isFailure)
        assertTrue(unblockResult.isFailure)
        assertTrue(removeResult.isFailure)
    }

    @Test
    fun `repository methods handle DAO exceptions gracefully`() = runTest {
        // Given
        coEvery { friendDao.getFriendRelationship(any(), any()) } throws RuntimeException("Database error")

        // When
        val result = repository.sendFriendRequest(testFriendId)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Database error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `Flow-based methods handle errors and emit empty lists`() = runTest {
        // Given
        every { friendDao.getFriends(testUserId) } throws RuntimeException("Database error")

        // When
        val result = repository.getFriends(testUserId).toList()

        // Then
        assertEquals(1, result.size)
        assertEquals(0, result[0].size)
    }

    // Integration Tests

    @Test
    fun `friend request lifecycle integration test`() = runTest {
        // Given - setup for complete friend request flow
        coEvery { friendDao.getFriendRelationship(testUserId, testFriendId) } returns null
        coEvery { privacySettingsDao.getPrivacySettingsOnce(testFriendId) } returns testPrivacySettings
        every { friendMapper.createFriendRequest(testUserId, testFriendId, false) } returns testPendingEntity
        coEvery { friendDao.insertFriend(testPendingEntity) } returns 1L
        coEvery { firestore.collection("friendships").document(any()).set(any()) } returns Tasks.forResult(null)

        // When - send friend request
        val sendResult = repository.sendFriendRequest(testFriendId)

        // Then - verify request was sent
        assertTrue(sendResult.isSuccess)
        coVerify { friendDao.insertFriend(testPendingEntity) }

        // Given - setup for accepting the request (switching perspective)
        coEvery { friendDao.getFriendRelationship(testUserId, testFriendId) } returns testPendingEntity
        coEvery { friendDao.updateFriendStatus(testUserId, testFriendId, "ACCEPTED", any()) } returns 1
        every { friendMapper.createFriendRequest(testFriendId, testUserId, false) } returns testFriendEntity.copy(userId = testFriendId, friendUserId = testUserId)
        coEvery { friendDao.insertFriend(any()) } returns 1L
        coEvery { firestore.collection("friendships").document(any()).update(any()) } returns Tasks.forResult(null)

        // When - accept the request (from friend's perspective)
        every { authRepository.getCurrentUserId() } returns testFriendId
        val acceptResult = repository.respondToFriendRequest(testUserId, accept = true)

        // Then - verify request was accepted
        assertTrue(acceptResult.isSuccess)
        coVerify { friendDao.updateFriendStatus(testUserId, testFriendId, "ACCEPTED", any()) }
        coVerify { friendDao.insertFriend(any()) }
    }

    // ================================================================================
    // DISCOVERY INTEGRATION TESTS (REPO-002)
    // Tests for user discovery and recommendation methods
    // ================================================================================

    private val testRecommendedUser1 = RecommendedUser(
        userId = "rec-user-1",
        username = "athlete1",
        displayName = "Athlete One",
        profileImageUrl = "https://example.com/athlete1.jpg",
        isFollowing = false,
        mutualFriends = 2,
        commonInterests = listOf("powerlifting", "cardio"),
        recommendationReason = "mutual_friends",
        cachedAt = System.currentTimeMillis()
    )

    private val testRecommendedUser2 = RecommendedUser(
        userId = "rec-user-2",
        username = "athlete2",
        displayName = "Athlete Two",
        profileImageUrl = "https://example.com/athlete2.jpg",
        isFollowing = false,
        mutualFriends = 0,
        commonInterests = listOf("yoga", "running"),
        recommendationReason = "general_discovery",
        cachedAt = System.currentTimeMillis()
    )

    private val testSearchUser1 = User(
        userId = "search-user-1",
        username = "searchuser1",
        displayName = "Search User One",
        email = "search1@example.com",
        profileImageUrl = null,
        isVerified = false,
        joinDate = Instant.now().minusSeconds(604800) // 1 week ago
    )

    private val testSearchUser2 = User(
        userId = "search-user-2",
        username = "searchuser2",
        displayName = "Search User Two",
        email = "search2@example.com",
        profileImageUrl = "https://example.com/search2.jpg",
        isVerified = true,
        joinDate = Instant.now().minusSeconds(1209600) // 2 weeks ago
    )

    @Test
    fun getRecommendedUsers_returnsMutualFriendsFirst() = runTest {
        // Given
        val cachedUsers = listOf(testRecommendedUser1, testRecommendedUser2)
        val mutualFriendsUsers = listOf(testRecommendedUser1) // Has mutual friends
        val generalUsers = listOf(testRecommendedUser2) // General discovery
        
        coEvery { recommendationCache.getCachedRecommendations(testUserId) } returns null
        coEvery { repository.getMutualFriendsRecommendations(testUserId, 5) } returns mutualFriendsUsers
        coEvery { repository.getGeneralRecommendations(testUserId, 5, 0) } returns generalUsers
        coEvery { recommendationCache.cacheRecommendations(testUserId, any()) } returns Unit

        // When
        val result = repository.getRecommendedUsers(testUserId, 10, 0).toList()

        // Then
        assertEquals(1, result.size)
        val recommendations = result[0]
        assertEquals(2, recommendations.size)
        
        // Verify mutual friends user comes first
        assertEquals("mutual_friends", recommendations[0].recommendationReason)
        assertEquals(2, recommendations[0].mutualFriends)
        
        // Verify general discovery user comes second
        assertEquals("general_discovery", recommendations[1].recommendationReason)
        assertEquals(0, recommendations[1].mutualFriends)

        coVerify { recommendationCache.getCachedRecommendations(testUserId) }
        coVerify { recommendationCache.cacheRecommendations(testUserId, any()) }
    }

    @Test
    fun getRecommendedUsers_usesCacheWhenValid() = runTest {
        // Given
        val cachedUsers = listOf(testRecommendedUser1, testRecommendedUser2)
        
        coEvery { recommendationCache.getCachedRecommendations(testUserId) } returns cachedUsers

        // When
        val result = repository.getRecommendedUsers(testUserId, 10, 0).toList()

        // Then
        assertEquals(1, result.size)
        val recommendations = result[0]
        assertEquals(2, recommendations.size)
        assertEquals(cachedUsers, recommendations)

        // Should not call discovery methods when cache is valid
        coVerify(exactly = 0) { repository.getMutualFriendsRecommendations(any(), any()) }
        coVerify(exactly = 0) { repository.getGeneralRecommendations(any(), any(), any()) }
        coVerify { recommendationCache.getCachedRecommendations(testUserId) }
    }

    @Test
    fun getRecommendedUsers_respectsPaginationLimits() = runTest {
        // Given
        val firstPageUsers = listOf(testRecommendedUser1)
        val secondPageUsers = listOf(testRecommendedUser2)
        
        coEvery { recommendationCache.getCachedRecommendations(testUserId) } returns null
        coEvery { repository.getMutualFriendsRecommendations(testUserId, 1) } returns firstPageUsers
        coEvery { repository.getGeneralRecommendations(testUserId, 0, 0) } returns emptyList()
        coEvery { recommendationCache.cacheRecommendations(testUserId, any()) } returns Unit

        // When - Request first page
        val firstPage = repository.getRecommendedUsers(testUserId, 1, 0).toList()

        // Set up for second page
        coEvery { repository.getMutualFriendsRecommendations(testUserId, 1) } returns emptyList()
        coEvery { repository.getGeneralRecommendations(testUserId, 1, 1) } returns secondPageUsers

        // When - Request second page
        val secondPage = repository.getRecommendedUsers(testUserId, 1, 1).toList()

        // Then
        assertEquals(1, firstPage.size)
        assertEquals(1, firstPage[0].size)
        assertEquals(testRecommendedUser1.userId, firstPage[0][0].userId)
        
        assertEquals(1, secondPage.size)
        assertEquals(1, secondPage[0].size)
        assertEquals(testRecommendedUser2.userId, secondPage[0][0].userId)
    }

    @Test
    fun searchUsers_returnsFirebaseResults() = runTest {
        // Given
        val searchQuery = "search"
        val usersCollection = mockk<CollectionReference>()
        val query = mockk<Query>()
        val querySnapshot = mockk<QuerySnapshot>()
        val documentSnapshot1 = mockk<DocumentSnapshot>()
        val documentSnapshot2 = mockk<DocumentSnapshot>()
        
        every { firestore.collection("users") } returns usersCollection
        every { usersCollection.whereGreaterThanOrEqualTo("displayName", searchQuery) } returns query
        every { query.whereLessThan("displayName", searchQuery + "\uf8ff") } returns query
        every { query.limit(20) } returns query
        coEvery { query.get() } returns Tasks.forResult(querySnapshot)
        
        every { querySnapshot.documents } returns listOf(documentSnapshot1, documentSnapshot2)
        every { documentSnapshot1.toObject(User::class.java) } returns testSearchUser1
        every { documentSnapshot2.toObject(User::class.java) } returns testSearchUser2

        // When
        val result = repository.searchUsers(searchQuery).toList()

        // Then
        assertEquals(1, result.size)
        val users = result[0]
        assertEquals(2, users.size)
        assertTrue(users.contains(testSearchUser1))
        assertTrue(users.contains(testSearchUser2))

        verify { firestore.collection("users") }
        coVerify { query.get() }
    }

    @Test
    fun followUser_updatesRecommendationState() = runTest {
        // Given
        val targetUserId = "target-user-123"
        val friendshipDoc = mockk<DocumentReference>()
        
        every { firestore.collection("friendships").document(any()) } returns friendshipDoc
        coEvery { friendshipDoc.set(any()) } returns Tasks.forResult(null)
        coEvery { friendDao.insertFriend(any()) } returns 1L
        every { friendMapper.createFriendRequest(testUserId, targetUserId, false) } returns testPendingEntity.copy(friendUserId = targetUserId)

        // When
        val result = repository.followUser(testUserId, targetUserId)

        // Then
        assertTrue(result.isSuccess)
        
        // Verify Firebase friendship was created
        verify { firestore.collection("friendships").document(any()) }
        coVerify { friendshipDoc.set(any()) }
        
        // Verify local friend relationship was created
        coVerify { friendDao.insertFriend(any()) }
    }

    @Test
    fun refreshDiscoveryCache_invalidatesExistingCache() = runTest {
        // Given
        coEvery { recommendationCache.clearCache(testUserId) } returns Unit
        coEvery { repository.getMutualFriendsRecommendations(testUserId, 10) } returns listOf(testRecommendedUser1)
        coEvery { repository.getGeneralRecommendations(testUserId, 5, 0) } returns listOf(testRecommendedUser2)
        coEvery { recommendationCache.cacheRecommendations(testUserId, any()) } returns Unit

        // When
        repository.refreshDiscoveryCache(testUserId)

        // Then
        coVerify { recommendationCache.clearCache(testUserId) }
        coVerify { repository.getMutualFriendsRecommendations(testUserId, 10) }
        coVerify { repository.getGeneralRecommendations(testUserId, 5, 0) }
        coVerify { recommendationCache.cacheRecommendations(testUserId, any()) }
    }

    @Test
    fun searchUsers_handlesFirebaseErrors() = runTest {
        // Given
        val searchQuery = "search"
        val usersCollection = mockk<CollectionReference>()
        val query = mockk<Query>()
        
        every { firestore.collection("users") } returns usersCollection
        every { usersCollection.whereGreaterThanOrEqualTo("displayName", searchQuery) } returns query
        every { query.whereLessThan("displayName", searchQuery + "\uf8ff") } returns query
        every { query.limit(20) } returns query
        coEvery { query.get() } throws RuntimeException("Firebase error")

        // When
        val result = repository.searchUsers(searchQuery).toList()

        // Then - Should return empty list on error
        assertEquals(1, result.size)
        assertEquals(0, result[0].size)
    }

    @Test
    fun getRecommendedUsers_handlesDiscoveryErrors() = runTest {
        // Given
        coEvery { recommendationCache.getCachedRecommendations(testUserId) } returns null
        coEvery { repository.getMutualFriendsRecommendations(testUserId, any()) } throws RuntimeException("Discovery error")
        coEvery { repository.getGeneralRecommendations(testUserId, any(), any()) } returns emptyList()

        // When
        val result = repository.getRecommendedUsers(testUserId, 10, 0).toList()

        // Then - Should handle error gracefully and return available recommendations
        assertEquals(1, result.size)
        assertEquals(0, result[0].size) // Empty list when both discovery methods fail

        coVerify { recommendationCache.getCachedRecommendations(testUserId) }
    }

    @Test
    fun getRecommendedUsers_removesCurrentUserFromResults() = runTest {
        // Given
        val currentUserRecommendation = testRecommendedUser1.copy(userId = testUserId)
        val validRecommendation = testRecommendedUser2
        val recommendations = listOf(currentUserRecommendation, validRecommendation)
        
        coEvery { recommendationCache.getCachedRecommendations(testUserId) } returns recommendations

        // When
        val result = repository.getRecommendedUsers(testUserId, 10, 0).toList()

        // Then
        assertEquals(1, result.size)
        val filteredRecommendations = result[0]
        assertEquals(1, filteredRecommendations.size)
        assertEquals(testRecommendedUser2.userId, filteredRecommendations[0].userId)
        
        // Should not contain current user
        assertFalse(filteredRecommendations.any { it.userId == testUserId })
    }

    @Test
    fun getRecommendedUsers_deduplicatesResults() = runTest {
        // Given
        val duplicateUser = testRecommendedUser1
        val mutualFriendsUsers = listOf(duplicateUser)
        val generalUsers = listOf(duplicateUser, testRecommendedUser2) // Contains duplicate
        
        coEvery { recommendationCache.getCachedRecommendations(testUserId) } returns null
        coEvery { repository.getMutualFriendsRecommendations(testUserId, 5) } returns mutualFriendsUsers
        coEvery { repository.getGeneralRecommendations(testUserId, 5, 0) } returns generalUsers
        coEvery { recommendationCache.cacheRecommendations(testUserId, any()) } returns Unit

        // When
        val result = repository.getRecommendedUsers(testUserId, 10, 0).toList()

        // Then
        assertEquals(1, result.size)
        val recommendations = result[0]
        assertEquals(2, recommendations.size) // Should be deduplicated
        
        val userIds = recommendations.map { it.userId }
        assertEquals(userIds.toSet().size, userIds.size) // No duplicates
        
        assertTrue(userIds.contains(testRecommendedUser1.userId))
        assertTrue(userIds.contains(testRecommendedUser2.userId))
    }

    // ================================================================================
    // FIREBASE REAL-TIME INTEGRATION TESTS (INT-003)
    // Tests for real-time sync and Firebase integration methods
    // ================================================================================

    @Test
    fun syncFriendsWorkouts_updatesLocalDatabase() = runTest {
        // Given
        val friendIds = listOf(friendUserId1, "friend-user-789")
        val workoutsCollection = mockk<CollectionReference>()
        val query = mockk<Query>()
        val querySnapshot = mockk<QuerySnapshot>()
        val workoutDoc1 = mockk<DocumentSnapshot>()
        val workoutDoc2 = mockk<DocumentSnapshot>()
        
        coEvery { friendDao.getFriendIds(testUserId) } returns friendIds
        every { firestore.collection("workouts") } returns workoutsCollection
        every { workoutsCollection.whereIn("userId", friendIds) } returns query
        every { query.whereEqualTo("status", "COMPLETED") } returns query
        every { query.orderBy("completedAt", Query.Direction.DESCENDING) } returns query
        every { query.limit(50) } returns query
        coEvery { query.get() } returns Tasks.forResult(querySnapshot)
        
        every { querySnapshot.documents } returns listOf(workoutDoc1, workoutDoc2)
        every { workoutDoc1.toObject(any<Class<*>>()) } returns mapOf(
            "id" to "workout-1",
            "userId" to friendIds[0],
            "name" to "Friend Workout 1",
            "completedAt" to Instant.now().toEpochMilli()
        )
        every { workoutDoc2.toObject(any<Class<*>>()) } returns mapOf(
            "id" to "workout-2", 
            "userId" to friendIds[1],
            "name" to "Friend Workout 2",
            "completedAt" to Instant.now().minusSeconds(3600).toEpochMilli()
        )

        // When
        repository.syncFriendsWorkouts(testUserId)

        // Then
        verify { firestore.collection("workouts") }
        coVerify { query.get() }
        coVerify { friendDao.getFriendIds(testUserId) }
    }

    @Test
    fun updateUserPresence_syncsToFirebase() = runTest {
        // Given
        val presenceDoc = mockk<DocumentReference>()
        val presenceData = mapOf(
            "userId" to testUserId,
            "status" to "online",
            "lastSeen" to System.currentTimeMillis(),
            "currentWorkout" to null
        )
        
        every { firestore.collection("user_presence").document(testUserId) } returns presenceDoc
        coEvery { presenceDoc.set(presenceData) } returns Tasks.forResult(null)

        // When
        repository.updateUserPresence(testUserId, "online", null)

        // Then
        verify { firestore.collection("user_presence").document(testUserId) }
        coVerify { presenceDoc.set(any()) }
    }

    @Test
    fun setupRealtimeFeedListener_handlesWorkoutUpdates() = runTest {
        // Given
        val friendIds = listOf(friendUserId1)
        val workoutsCollection = mockk<CollectionReference>()
        val query = mockk<Query>()
        
        coEvery { friendDao.getFriendIds(testUserId) } returns friendIds
        every { firestore.collection("workouts") } returns workoutsCollection
        every { workoutsCollection.whereIn("userId", friendIds) } returns query
        every { query.whereNotEqualTo("completedAt", null) } returns query
        every { query.orderBy("completedAt", Query.Direction.DESCENDING) } returns query
        every { query.limit(20) } returns query

        // When
        repository.setupRealtimeFeedListener(testUserId)

        // Then
        verify { firestore.collection("workouts") }
        verify { query.addSnapshotListener(any()) }
        coVerify { friendDao.getFriendIds(testUserId) }
    }
}