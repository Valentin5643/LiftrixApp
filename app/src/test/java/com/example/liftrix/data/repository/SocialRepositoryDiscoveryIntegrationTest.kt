package com.example.liftrix.data.repository

import com.example.liftrix.data.cache.RecommendationCache
import com.example.liftrix.data.local.dao.FriendDao
import com.example.liftrix.data.local.dao.PrivacySettingsDao
import com.example.liftrix.data.local.entity.FriendEntity
import com.example.liftrix.data.mapper.FriendMapper
import com.example.liftrix.domain.model.Friend
import com.example.liftrix.domain.model.FriendStatus
import com.example.liftrix.domain.model.RecommendedUser
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.SubscriptionTier
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.repository.AuthRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for SocialRepository discovery functionality  
 * Tests REPO-002 implementation: social discovery methods for user recommendations
 */
@RunWith(RobolectricTestRunner::class)
class SocialRepositoryDiscoveryIntegrationTest {

    @MockK
    private lateinit var friendDao: FriendDao
    
    @MockK
    private lateinit var privacySettingsDao: PrivacySettingsDao
    
    @MockK
    private lateinit var friendMapper: FriendMapper
    
    @MockK
    private lateinit var authRepository: AuthRepository
    
    @MockK
    private lateinit var firestore: FirebaseFirestore
    
    @MockK
    private lateinit var recommendationCache: RecommendationCache

    private lateinit var socialRepository: SocialRepositoryImpl

    private val testUserId = "test-user-123"
    private val friendUserId = "friend-user-456" 
    private val recommendedUserId = "recommended-user-789"
    
    private val testUser = User(
        uid = testUserId,
        email = "test@example.com",
        displayName = "Test User",
        photoUrl = null,
        isAnonymous = false,
        subscriptionTier = SubscriptionTier.FREE,
        subscriptionStatus = SubscriptionStatus.ACTIVE,
        subscriptionExpiresAt = null,
        premiumFeaturesEnabled = false,
        onboardingCompleted = true,
        profileVersion = 1L,
        createdAt = LocalDateTime.now().minusDays(30),
        lastSignInAt = LocalDateTime.now().minusHours(1),
        updatedAt = LocalDateTime.now().minusHours(1)
    )
    
    private val recommendedUser = User(
        uid = recommendedUserId,
        email = "recommended@example.com",
        displayName = "Recommended User",
        photoUrl = "https://example.com/avatar.jpg",
        isAnonymous = false,
        subscriptionTier = SubscriptionTier.FREE,
        subscriptionStatus = SubscriptionStatus.ACTIVE,
        subscriptionExpiresAt = null,
        premiumFeaturesEnabled = false,
        onboardingCompleted = true,
        profileVersion = 1L,
        createdAt = LocalDateTime.now().minusDays(15),
        lastSignInAt = LocalDateTime.now().minusMinutes(30),
        updatedAt = LocalDateTime.now().minusMinutes(30)
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        socialRepository = SocialRepositoryImpl(
            friendDao = friendDao,
            privacySettingsDao = privacySettingsDao,
            friendMapper = friendMapper,
            authRepository = authRepository,
            firestore = firestore,
            recommendationCache = recommendationCache
        )
    }

    @Test
    fun `searchUsers returns matching users by display name`() = runTest {
        // Given
        val query = "Test"
        val mockCollectionRef = mockk<com.google.firebase.firestore.CollectionReference>()
        val mockQuery = mockk<Query>()
        val mockQuerySnapshot = mockk<QuerySnapshot>()
        val mockDocument = mockk<DocumentSnapshot>()
        val mockTask = mockk<Task<QuerySnapshot>>()
        
        every { authRepository.getCurrentUserId() } returns testUserId
        every { firestore.collection("users") } returns mockCollectionRef
        every { 
            mockCollectionRef.whereGreaterThanOrEqualTo("displayName", query) 
        } returns mockQuery
        every { 
            mockQuery.whereLessThanOrEqualTo("displayName", query + "\uf8ff") 
        } returns mockQuery
        every { mockQuery.limit(20) } returns mockQuery
        every { mockQuery.get() } returns mockTask
        every { mockTask.isSuccessful } returns true
        every { mockTask.result } returns mockQuerySnapshot
        
        // Mock email query
        val mockEmailQuery = mockk<Query>()
        val mockEmailTask = mockk<Task<QuerySnapshot>>()
        val mockEmailSnapshot = mockk<QuerySnapshot>()
        
        every { 
            mockCollectionRef.whereEqualTo("email", query.lowercase()) 
        } returns mockEmailQuery
        every { mockEmailQuery.limit(5) } returns mockEmailQuery
        every { mockEmailQuery.get() } returns mockEmailTask
        every { mockEmailTask.isSuccessful } returns true
        every { mockEmailTask.result } returns mockEmailSnapshot
        
        // Mock document results
        every { mockQuerySnapshot.documents } returns listOf(mockDocument)
        every { mockEmailSnapshot.documents } returns emptyList()
        every { mockDocument.id } returns recommendedUserId
        every { mockDocument.data } returns mapOf(
            "email" to "recommended@example.com",
            "displayName" to "Test Recommended User",
            "photoUrl" to "https://example.com/avatar.jpg",
            "isAnonymous" to false,
            "onboardingCompleted" to true,
            "profileVersion" to 1L
        )
        
        // Mock await extension
        coEvery { mockTask.await() } returns mockQuerySnapshot
        coEvery { mockEmailTask.await() } returns mockEmailSnapshot

        // When
        val result = socialRepository.searchUsers(query).toList()

        // Then
        assertEquals(1, result.size)
        val users = result.first()
        assertEquals(1, users.size)
        assertEquals(recommendedUserId, users.first().uid)
        assertEquals("Test Recommended User", users.first().displayName)
        
        verify { authRepository.getCurrentUserId() }
        verify { firestore.collection("users") }
        coVerify { mockTask.await() }
        coVerify { mockEmailTask.await() }
    }

    @Test
    fun `searchUsers filters out current user from results`() = runTest {
        // Given
        val query = "User"
        val mockCollectionRef = mockk<com.google.firebase.firestore.CollectionReference>()
        val mockQuery = mockk<Query>()
        val mockQuerySnapshot = mockk<QuerySnapshot>()
        val mockCurrentUserDoc = mockk<DocumentSnapshot>()
        val mockOtherUserDoc = mockk<DocumentSnapshot>()
        val mockTask = mockk<Task<QuerySnapshot>>()
        
        every { authRepository.getCurrentUserId() } returns testUserId
        every { firestore.collection("users") } returns mockCollectionRef
        every { 
            mockCollectionRef.whereGreaterThanOrEqualTo("displayName", query) 
        } returns mockQuery
        every { 
            mockQuery.whereLessThanOrEqualTo("displayName", query + "\uf8ff") 
        } returns mockQuery
        every { mockQuery.limit(20) } returns mockQuery
        every { mockQuery.get() } returns mockTask
        
        // Mock email query (empty results)
        val mockEmailQuery = mockk<Query>()
        val mockEmailTask = mockk<Task<QuerySnapshot>>()
        val mockEmailSnapshot = mockk<QuerySnapshot>()
        
        every { 
            mockCollectionRef.whereEqualTo("email", query.lowercase()) 
        } returns mockEmailQuery
        every { mockEmailQuery.limit(5) } returns mockEmailQuery
        every { mockEmailQuery.get() } returns mockEmailTask
        every { mockEmailSnapshot.documents } returns emptyList()
        
        // Setup documents - current user and another user
        every { mockQuerySnapshot.documents } returns listOf(mockCurrentUserDoc, mockOtherUserDoc)
        every { mockCurrentUserDoc.id } returns testUserId // Should be filtered out
        every { mockOtherUserDoc.id } returns recommendedUserId
        every { mockOtherUserDoc.data } returns mapOf(
            "email" to "other@example.com",
            "displayName" to "Other User",
            "isAnonymous" to false,
            "onboardingCompleted" to true
        )
        
        coEvery { mockTask.await() } returns mockQuerySnapshot
        coEvery { mockEmailTask.await() } returns mockEmailSnapshot

        // When
        val result = socialRepository.searchUsers(query).toList()

        // Then
        assertEquals(1, result.size)
        val users = result.first()
        assertEquals(1, users.size) // Only other user, current user filtered out
        assertEquals(recommendedUserId, users.first().uid)
    }

    @Test
    fun `searchUsers returns empty list for unauthenticated user`() = runTest {
        // Given
        val query = "Test"
        every { authRepository.getCurrentUserId() } returns null

        // When
        val result = socialRepository.searchUsers(query).toList()

        // Then
        assertEquals(1, result.size)
        val users = result.first()
        assertEquals(0, users.size)
        
        verify { authRepository.getCurrentUserId() }
    }

    @Test
    fun `searchUsers returns empty list for blank query`() = runTest {
        // Given
        val blankQuery = "   "

        // When
        val result = socialRepository.searchUsers(blankQuery).toList()

        // Then
        assertEquals(1, result.size)
        val users = result.first()
        assertEquals(0, users.size)
    }

    @Test
    fun `getRecommendedUsers returns cached recommendations when available`() = runTest {
        // Given
        val limit = 10
        val offset = 0
        val cachedRecommendations = listOf(
            RecommendedUser.fromUser(recommendedUser, isFollowing = false)
        )
        
        every { authRepository.getCurrentUserId() } returns testUserId
        coEvery { recommendationCache.getCachedRecommendations(testUserId) } returns cachedRecommendations

        // When
        val result = socialRepository.getRecommendedUsers(limit, offset).toList()

        // Then
        assertEquals(1, result.size)
        val recommendations = result.first()
        assertEquals(1, recommendations.size)
        assertEquals(recommendedUserId, recommendations.first().userId)
        assertFalse(recommendations.first().isFollowing)
        
        verify { authRepository.getCurrentUserId() }
        coVerify { recommendationCache.getCachedRecommendations(testUserId) }
    }

    @Test
    fun `getRecommendedUsers generates fresh recommendations when cache miss`() = runTest {
        // Given
        val limit = 6 // Will split: 3 mutual friends + 3 general
        val offset = 0
        val existingFriendIds = setOf(friendUserId)
        
        // Mock entities for friends list
        val friendEntity = mockk<FriendEntity> {
            every { friendUserId } returns this@SocialRepositoryDiscoveryIntegrationTest.friendUserId
            every { status } returns FriendStatus.ACCEPTED.name
        }
        
        every { authRepository.getCurrentUserId() } returns testUserId
        coEvery { recommendationCache.getCachedRecommendations(testUserId) } returns null
        every { friendDao.getFriends(testUserId) } returns flowOf(listOf(friendEntity))
        
        // Mock mutual friends query
        val mockFriendshipsCollection = mockk<com.google.firebase.firestore.CollectionReference>()
        val mockMutualQuery = mockk<Query>()
        val mockMutualTask = mockk<Task<QuerySnapshot>>()
        val mockMutualSnapshot = mockk<QuerySnapshot>()
        
        every { firestore.collection("friendships") } returns mockFriendshipsCollection
        every { 
            mockFriendshipsCollection.whereIn("senderId", listOf(friendUserId)) 
        } returns mockMutualQuery
        every { mockMutualQuery.whereEqualTo("status", "accepted") } returns mockMutualQuery
        every { mockMutualQuery.limit(3) } returns mockMutualQuery
        every { mockMutualQuery.get() } returns mockMutualTask
        every { mockMutualSnapshot.documents } returns emptyList()
        
        // Mock general recommendations query
        val mockUsersCollection = mockk<com.google.firebase.firestore.CollectionReference>()
        val mockGeneralQuery = mockk<Query>()
        val mockGeneralTask = mockk<Task<QuerySnapshot>>()
        val mockGeneralSnapshot = mockk<QuerySnapshot>()
        val mockGeneralDoc = mockk<DocumentSnapshot>()
        
        every { firestore.collection("users") } returns mockUsersCollection
        every { 
            mockUsersCollection.whereEqualTo("onboardingCompleted", true) 
        } returns mockGeneralQuery
        every { mockGeneralQuery.limit(9) } returns mockGeneralQuery // limit * 2 + offset
        every { mockGeneralQuery.get() } returns mockGeneralTask
        every { mockGeneralSnapshot.documents } returns listOf(mockGeneralDoc)
        
        every { mockGeneralDoc.id } returns recommendedUserId
        every { mockGeneralDoc.data } returns mapOf(
            "email" to "recommended@example.com",
            "displayName" to "Recommended User",
            "photoUrl" to "https://example.com/avatar.jpg",
            "isAnonymous" to false,
            "onboardingCompleted" to true,
            "profileVersion" to 1L
        )
        
        coEvery { mockMutualTask.await() } returns mockMutualSnapshot
        coEvery { mockGeneralTask.await() } returns mockGeneralSnapshot
        coEvery { recommendationCache.cacheRecommendations(testUserId, any()) } returns Unit

        // When
        val result = socialRepository.getRecommendedUsers(limit, offset).toList()

        // Then
        assertEquals(1, result.size)
        val recommendations = result.first()
        assertEquals(1, recommendations.size)
        assertEquals(recommendedUserId, recommendations.first().userId)
        
        verify { authRepository.getCurrentUserId() }
        coVerify { recommendationCache.getCachedRecommendations(testUserId) }
        coVerify { recommendationCache.cacheRecommendations(testUserId, any()) }
    }

    @Test
    fun `getRecommendedUsers returns empty list for unauthenticated user`() = runTest {
        // Given
        val limit = 10
        val offset = 0
        every { authRepository.getCurrentUserId() } returns null

        // When
        val result = socialRepository.getRecommendedUsers(limit, offset).toList()

        // Then
        assertEquals(1, result.size)
        val recommendations = result.first()
        assertEquals(0, recommendations.size)
        
        verify { authRepository.getCurrentUserId() }
    }

    @Test
    fun `followUser sends friend request for new user`() = runTest {
        // Given
        every { authRepository.getCurrentUserId() } returns testUserId
        every { friendDao.getFriendRelationship(recommendedUserId, testUserId) } returns null
        every { friendDao.getFriendRelationship(testUserId, recommendedUserId) } returns null
        every { privacySettingsDao.getPrivacySettingsOnce(recommendedUserId) } returns null
        every { 
            friendMapper.createFriendRequest(testUserId, recommendedUserId, false) 
        } returns mockk<FriendEntity>(relaxed = true)
        every { friendDao.insertFriend(any()) } returns Unit
        coEvery { recommendationCache.invalidateCacheForUser(testUserId) } returns Unit
        
        // Mock Firebase sync (just verify it's called)
        val mockFriendshipsCollection = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
        val mockDocument = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
        val mockTask = mockk<Task<Void>>(relaxed = true)
        
        every { firestore.collection("friendships") } returns mockFriendshipsCollection
        every { mockFriendshipsCollection.document(any()) } returns mockDocument
        every { mockDocument.set(any()) } returns mockTask
        coEvery { mockTask.await() } returns mockk()

        // When
        val result = socialRepository.followUser(recommendedUserId)

        // Then
        assertTrue(result.isSuccess)
        
        verify { authRepository.getCurrentUserId() }
        verify { friendDao.getFriendRelationship(recommendedUserId, testUserId) }
        verify { friendDao.insertFriend(any()) }
        coVerify { recommendationCache.invalidateCacheForUser(testUserId) }
    }

    @Test
    fun `followUser accepts existing pending request`() = runTest {
        // Given
        val existingRequest = mockk<FriendEntity> {
            every { status } returns FriendStatus.PENDING.name
            every { userId } returns recommendedUserId
            every { friendUserId } returns testUserId
        }
        
        every { authRepository.getCurrentUserId() } returns testUserId
        every { friendDao.getFriendRelationship(recommendedUserId, testUserId) } returns existingRequest
        every { friendDao.updateFriendStatus(recommendedUserId, testUserId, FriendStatus.ACCEPTED.name, any()) } returns Unit
        every { 
            friendMapper.createFriendRequest(testUserId, recommendedUserId, false) 
        } returns mockk<FriendEntity>(relaxed = true) {
            every { copy(status = FriendStatus.ACCEPTED.name) } returns mockk(relaxed = true)
        }
        every { friendDao.insertFriend(any()) } returns Unit
        coEvery { recommendationCache.invalidateCacheForUser(testUserId) } returns Unit
        coEvery { recommendationCache.invalidateCacheForUser(recommendedUserId) } returns Unit
        
        // Mock Firebase sync
        val mockFriendshipsCollection = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
        val mockDocument = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
        val mockTask = mockk<Task<Void>>(relaxed = true)
        
        every { firestore.collection("friendships") } returns mockFriendshipsCollection
        every { mockFriendshipsCollection.document(any()) } returns mockDocument
        every { mockDocument.update(any<Map<String, Any>>()) } returns mockTask
        coEvery { mockTask.await() } returns mockk()

        // When
        val result = socialRepository.followUser(recommendedUserId)

        // Then
        assertTrue(result.isSuccess)
        
        verify { authRepository.getCurrentUserId() }
        verify { friendDao.getFriendRelationship(recommendedUserId, testUserId) }
        verify { friendDao.updateFriendStatus(recommendedUserId, testUserId, FriendStatus.ACCEPTED.name, any()) }
        coVerify { recommendationCache.invalidateCacheForUser(testUserId) }
        coVerify { recommendationCache.invalidateCacheForUser(recommendedUserId) }
    }

    @Test
    fun `refreshDiscoveryCache clears cache for authenticated user`() = runTest {
        // Given
        every { authRepository.getCurrentUserId() } returns testUserId
        coEvery { recommendationCache.invalidateCacheForUser(testUserId) } returns Unit

        // When
        val result = socialRepository.refreshDiscoveryCache()

        // Then
        assertTrue(result.isSuccess)
        
        verify { authRepository.getCurrentUserId() }
        coVerify { recommendationCache.invalidateCacheForUser(testUserId) }
    }

    @Test
    fun `refreshDiscoveryCache handles unauthenticated user gracefully`() = runTest {
        // Given
        every { authRepository.getCurrentUserId() } returns null

        // When
        val result = socialRepository.refreshDiscoveryCache()

        // Then
        assertTrue(result.isSuccess) // Should still succeed but not clear cache
        
        verify { authRepository.getCurrentUserId() }
    }

    @Test
    fun `discovery integration end-to-end scenario`() = runTest {
        // Given - Complete discovery workflow
        val searchQuery = "Test"
        val limit = 4
        val offset = 0
        
        // Setup user search
        val mockUsersCollection = mockk<com.google.firebase.firestore.CollectionReference>()
        val mockSearchQuery = mockk<Query>()
        val mockSearchTask = mockk<Task<QuerySnapshot>>()
        val mockSearchSnapshot = mockk<QuerySnapshot>()
        val mockSearchDoc = mockk<DocumentSnapshot>()
        
        every { authRepository.getCurrentUserId() } returns testUserId
        every { firestore.collection("users") } returns mockUsersCollection
        every { 
            mockUsersCollection.whereGreaterThanOrEqualTo("displayName", searchQuery) 
        } returns mockSearchQuery
        every { 
            mockSearchQuery.whereLessThanOrEqualTo("displayName", searchQuery + "\uf8ff") 
        } returns mockSearchQuery
        every { mockSearchQuery.limit(20) } returns mockSearchQuery
        every { mockSearchQuery.get() } returns mockSearchTask
        every { mockSearchSnapshot.documents } returns listOf(mockSearchDoc)
        
        // Mock email search (empty)
        val mockEmailQuery = mockk<Query>()
        val mockEmailTask = mockk<Task<QuerySnapshot>>()
        val mockEmailSnapshot = mockk<QuerySnapshot>()
        every { 
            mockUsersCollection.whereEqualTo("email", searchQuery.lowercase()) 
        } returns mockEmailQuery
        every { mockEmailQuery.limit(5) } returns mockEmailQuery
        every { mockEmailQuery.get() } returns mockEmailTask
        every { mockEmailSnapshot.documents } returns emptyList()
        
        // Setup search document
        every { mockSearchDoc.id } returns recommendedUserId
        every { mockSearchDoc.data } returns mapOf(
            "email" to "test@example.com",
            "displayName" to "Test User Found",
            "isAnonymous" to false,
            "onboardingCompleted" to true
        )
        
        coEvery { mockSearchTask.await() } returns mockSearchSnapshot
        coEvery { mockEmailTask.await() } returns mockEmailSnapshot
        
        // Setup recommendations (cache miss)
        coEvery { recommendationCache.getCachedRecommendations(testUserId) } returns null
        every { friendDao.getFriends(testUserId) } returns flowOf(emptyList<FriendEntity>())
        
        // Mock general recommendations
        val mockGeneralQuery = mockk<Query>()
        val mockGeneralTask = mockk<Task<QuerySnapshot>>()
        val mockGeneralSnapshot = mockk<QuerySnapshot>()
        
        every { 
            mockUsersCollection.whereEqualTo("onboardingCompleted", true) 
        } returns mockGeneralQuery
        every { mockGeneralQuery.limit(8) } returns mockGeneralQuery
        every { mockGeneralQuery.get() } returns mockGeneralTask
        every { mockGeneralSnapshot.documents } returns listOf(mockSearchDoc) // Reuse same doc
        
        coEvery { mockGeneralTask.await() } returns mockGeneralSnapshot
        coEvery { recommendationCache.cacheRecommendations(testUserId, any()) } returns Unit

        // When - Execute discovery operations
        val searchResult = socialRepository.searchUsers(searchQuery).toList()
        val recommendationsResult = socialRepository.getRecommendedUsers(limit, offset).toList()
        val refreshResult = socialRepository.refreshDiscoveryCache()

        // Then - Verify complete discovery functionality
        assertEquals(1, searchResult.size)
        val searchUsers = searchResult.first()
        assertEquals(1, searchUsers.size)
        assertEquals("Test User Found", searchUsers.first().displayName)
        
        assertEquals(1, recommendationsResult.size)
        val recommendations = recommendationsResult.first()
        assertEquals(1, recommendations.size)
        assertEquals(recommendedUserId, recommendations.first().userId)
        
        assertTrue(refreshResult.isSuccess)
        
        // Verify all components were properly integrated
        verify { authRepository.getCurrentUserId() }
        verify { firestore.collection("users") }
        coVerify { recommendationCache.getCachedRecommendations(testUserId) }
        coVerify { recommendationCache.cacheRecommendations(testUserId, any()) }
        coVerify { recommendationCache.invalidateCacheForUser(testUserId) }
    }
}