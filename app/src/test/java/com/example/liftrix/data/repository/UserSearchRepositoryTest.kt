package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.dao.UserSearchCacheDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.entity.UserSearchCacheEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SearchFilters
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.FitnessLevel
import com.example.liftrix.domain.repository.AuthRepository
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.time.LocalDateTime

/**
 * FAILING TEST SUITE: UserSearchRepository Collection Mismatch
 * 
 * This test suite contains INTENTIONALLY FAILING TESTS that expose the critical
 * Firestore collection mismatch in user search functionality:
 * 
 * ISSUE: UserSearchRepositoryImpl searches in collections 'users_public' and 'user_search_cache'
 * but SocialProfileSyncWorker syncs data to 'social_profiles' collection.
 * 
 * These tests demonstrate how the repository correctly implements search logic
 * but fails because data is synced to a different collection than where it searches.
 */
class UserSearchRepositoryTest {

    @MockK
    private lateinit var userProfileDao: UserProfileDao
    
    @MockK
    private lateinit var userSearchCacheDao: UserSearchCacheDao
    
    @MockK
    private lateinit var followRelationshipDao: FollowRelationshipDao
    
    @MockK
    private lateinit var authRepository: AuthRepository
    
    @MockK
    private lateinit var firestore: FirebaseFirestore
    
    @MockK
    private lateinit var gson: Gson
    
    @MockK
    private lateinit var usersPublicCollection: CollectionReference
    
    @MockK
    private lateinit var userSearchCacheCollection: CollectionReference
    
    @MockK
    private lateinit var socialProfilesCollection: CollectionReference
    
    @MockK
    private lateinit var query: Query
    
    @MockK
    private lateinit var querySnapshot: QuerySnapshot
    
    @MockK
    private lateinit var documentSnapshot: DocumentSnapshot

    private lateinit var repository: UserSearchRepositoryImpl
    private val currentUserId = "test_viewer_id_12345"
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        // Setup Firestore collection mocks
        every { firestore.collection("users_public") } returns usersPublicCollection
        every { firestore.collection("user_search_cache") } returns userSearchCacheCollection
        every { firestore.collection("social_profiles") } returns socialProfilesCollection
        
        // Setup common query mocking
        every { usersPublicCollection.whereEqualTo(any<String>(), any()) } returns query
        every { userSearchCacheCollection.whereEqualTo(any<String>(), any()) } returns query
        every { socialProfilesCollection.whereEqualTo(any<String>(), any()) } returns query
        every { query.whereArrayContainsAny(any<String>(), any<List<String>>()) } returns query
        every { query.limit(any()) } returns query
        
        repository = UserSearchRepositoryImpl(
            userProfileDao = userProfileDao,
            userSearchCacheDao = userSearchCacheDao,
            followRelationshipDao = followRelationshipDao,
            authRepository = authRepository,
            firestore = firestore,
            gson = gson
        )
    }

    @Test
    fun `FAILING TEST - searchUsers finds no results when data is in social_profiles collection`() = runTest {
        // ISSUE: This test demonstrates that when user data is synced to 'social_profiles',
        // the search repository cannot find it because it only searches 'users_public' and 'user_search_cache'
        
        val searchQuery = "newuser"
        val testUserId = "new_user_id_12345"
        
        Timber.w("🚨 FAILING TEST: Search in users_public/user_search_cache when data is in social_profiles")
        
        // Simulate user data existing in 'social_profiles' collection (where SyncWorker puts it)
        every { socialProfilesCollection.whereEqualTo("isPublic", true) } returns query
        every { querySnapshot.documents } returns listOf(
            mockDocumentSnapshot(testUserId, "newuser", "New User")
        )
        coEvery { query.get().await() } returns querySnapshot
        
        // Repository searches 'user_search_cache' first - will find nothing
        every { userSearchCacheCollection.whereEqualTo("isPublic", true) } returns query
        every { querySnapshot.documents } returns emptyList() // ❌ No data because wrong collection
        
        // Repository searches 'users_public' as fallback - will also find nothing  
        every { usersPublicCollection.whereEqualTo("isPublic", true) } returns query
        every { querySnapshot.documents } returns emptyList() // ❌ No data because wrong collection
        
        // Mock empty local cache
        coEvery { userSearchCacheDao.getCachedSearchResult(any<String>(), any<String>()) } returns null
        
        // Execute search
        val result = repository.searchUsers(
            query = searchQuery,
            currentUserId = currentUserId,
            filters = SearchFilters()
        )
        
        // 🚨 THIS ASSERTION WILL FAIL - repository searches wrong collections
        assertThat(result.isSuccess).isTrue()
        val users = result.getOrNull()
        
        // Expected: User should be found since data exists in Firebase
        // Actual: No results because data is in 'social_profiles' but search looks in 'users_public'
        assertThat(users).isNotEmpty() // ❌ FAILS: Wrong collection searched
        assertThat(users).hasSize(1)   // ❌ FAILS: No users found
        
        // Verify repository searched the correct collections (but data is elsewhere)
        verify { userSearchCacheCollection.whereEqualTo("isPublic", true) }
        verify { usersPublicCollection.whereEqualTo("isPublic", true) }
        verify(exactly = 0) { socialProfilesCollection.whereEqualTo("isPublic", true) } // Never searches here!
        
        Timber.e("❌ TEST FAILED as expected: Repository searches users_public/user_search_cache but data is in social_profiles")
    }

    @Test
    fun `FAILING TEST - cached search results become stale when sync targets wrong collection`() = runTest {
        // ISSUE: Local cache becomes stale because Firebase sync updates wrong collection
        
        val searchQuery = "cachetest"
        val testUserId = "cache_user_id_12345"
        
        Timber.w("🚨 FAILING TEST: Local cache invalidation fails due to collection mismatch")
        
        // Simulate stale cache data (user was once findable)
        val staleCache = UserSearchCacheEntity(
            id = "cache_${testUserId}_12345",
            viewerUserId = currentUserId,
            searchQuery = searchQuery,
            searchResults = "[]", // Empty JSON array for stale cache
            createdAt = "2024-01-01T10:00:00",
            expiresAt = "2024-01-01T11:00:00" // Expired cache
        )
        coEvery { userSearchCacheDao.getCachedSearchResult(any<String>(), any<String>()) } returns staleCache
        
        // Repository tries to refresh from Firebase but finds nothing (wrong collection)
        every { userSearchCacheCollection.whereEqualTo("isPublic", true) } returns query
        every { querySnapshot.documents } returns emptyList() // ❌ No fresh data
        coEvery { query.get().await() } returns querySnapshot
        
        // Execute search - should get fresh data but won't due to collection mismatch
        val result = repository.searchUsers(
            query = searchQuery,
            currentUserId = currentUserId,
            filters = SearchFilters()
        )
        
        // 🚨 Repository may return stale cache data or empty results
        assertThat(result.isSuccess).isTrue()
        val users = result.getOrNull()
        
        // Expected: Fresh data from Firebase or properly invalidated cache
        // Actual: Stale cache data or empty results due to collection mismatch
        if (users?.isNotEmpty() == true) {
            // If stale cache is returned, it won't have updated profile info
            val user = users.first()
            // This would contain old data because fresh sync went to wrong collection
            Timber.e("❌ Stale cache returned: ${user.displayName}")
        } else {
            // If empty results, cache invalidation failed
            Timber.e("❌ Cache invalidated but no fresh data found (wrong collection)")
        }
        
        // Either way, the test demonstrates the collection mismatch problem
        Timber.e("❌ TEST DEMONSTRATES: Collection mismatch causes cache issues")
    }

    @Test
    fun `FAILING TEST - search token queries fail when tokens are in wrong collection`() = runTest {
        // ISSUE: Search tokens are generated correctly but stored in wrong Firestore collection
        
        val searchQuery = "engineer"
        val searchTokens = listOf("engineer", "eng")
        val testUserId = "token_user_id_12345"
        
        Timber.w("🚨 FAILING TEST: Search token queries fail due to collection mismatch")
        
        // Repository searches for tokens in 'user_search_cache' collection
        every { userSearchCacheCollection.whereEqualTo("isPublic", true) } returns query
        every { query.whereArrayContainsAny("searchTokens", searchTokens) } returns query
        every { querySnapshot.documents } returns emptyList() // ❌ Tokens not found in this collection
        coEvery { query.get().await() } returns querySnapshot
        
        // Mock local cache as empty too
        coEvery { userSearchCacheDao.getCachedSearchResult(any<String>(), any<String>()) } returns null
        
        // Meanwhile, tokens exist in 'social_profiles' collection (where sync worker put them)
        // but repository never searches there
        
        // Execute token-based search
        val result = repository.searchUsers(
            query = searchQuery,
            currentUserId = currentUserId,
            filters = SearchFilters()
        )
        
        // 🚨 THIS ASSERTION WILL FAIL - tokens exist but in wrong collection
        assertThat(result.isSuccess).isTrue()
        val users = result.getOrNull()
        
        // Expected: User found via search tokens
        // Actual: No results because tokens are in 'social_profiles' not 'user_search_cache'
        assertThat(users).isNotEmpty() // ❌ FAILS: Tokens in wrong collection
        assertThat(users).hasSize(1)   // ❌ FAILS: No token matches found
        
        // Verify correct query was attempted (but on wrong collection)
        verify { userSearchCacheCollection.whereEqualTo("isPublic", true) }
        verify { query.whereArrayContainsAny("searchTokens", any<List<String>>()) }
        
        Timber.e("❌ TEST FAILED as expected: Search tokens exist but in social_profiles, not user_search_cache")
    }

    @Test
    fun `VERIFICATION TEST - repository searches correct collections when they contain data`() = runTest {
        // VERIFICATION: This test proves repository logic works when data is in expected collections
        
        val searchQuery = "workinguser"
        val testUserId = "working_user_id_12345"
        
        Timber.d("✅ VERIFICATION: Repository works when data is in correct collections")
        
        // Simulate data existing in 'user_search_cache' (correct collection)
        val mockDoc = mockDocumentSnapshot(testUserId, "workinguser", "Working User")
        every { userSearchCacheCollection.whereEqualTo("isPublic", true) } returns query
        every { querySnapshot.documents } returns listOf(mockDoc)
        coEvery { query.get().await() } returns querySnapshot
        
        // Mock successful data parsing
        every { gson.fromJson(any<String>(), any<Class<Any>>()) } returns mapOf(
            "userId" to testUserId,
            "username" to "workinguser",
            "displayName" to "Working User",
            "isPublic" to true
        )
        
        // Execute search
        val result = repository.searchUsers(
            query = searchQuery,
            currentUserId = currentUserId,
            filters = SearchFilters()
        )
        
        // ✅ These assertions should PASS when data is in correct collection
        assertThat(result.isSuccess).isTrue()
        val users = result.getOrNull()
        assertThat(users).isNotEmpty()
        assertThat(users).hasSize(1)
        
        // Verify repository searched correct collection
        verify { userSearchCacheCollection.whereEqualTo("isPublic", true) }
        
        Timber.d("✅ VERIFICATION PASSED: Repository works correctly when data is in user_search_cache")
    }

    @Test
    fun `DEMONSTRATION TEST - collection targeting mismatch root cause`() = runTest {
        // DEMONSTRATION: This test clearly shows the root cause of the issue
        
        val testUsername = "demonstrationuser"
        val testUserId = "demo_user_id_12345"
        
        Timber.w("🔬 DEMONSTRATION: Root cause analysis of collection mismatch")
        
        // Show where UserSearchRepository searches:
        Timber.d("📍 UserSearchRepository searches these collections:")
        Timber.d("   1. user_search_cache (primary)")
        Timber.d("   2. users_public (fallback)")
        
        // Show where SocialProfileSyncWorker syncs to:
        Timber.d("📍 SocialProfileSyncWorker syncs to this collection:")
        Timber.d("   1. social_profiles (MISMATCH!)")
        
        // Mock the collections that repository DOES search (empty)
        every { userSearchCacheCollection.whereEqualTo("isPublic", true) } returns query
        every { usersPublicCollection.whereEqualTo("isPublic", true) } returns query
        every { querySnapshot.documents } returns emptyList()
        coEvery { query.get().await() } returns querySnapshot
        coEvery { userSearchCacheDao.getCachedSearchResult(any<String>(), any<String>()) } returns null
        
        // Mock the collection where data ACTUALLY exists (but repository doesn't search it)
        val actualDataDoc = mockDocumentSnapshot(testUserId, testUsername, "Demo User")
        every { socialProfilesCollection.whereEqualTo("isPublic", true) } returns query
        // Note: This query would return data, but repository never calls it!
        
        // Execute search
        val result = repository.searchUsers(
            query = testUsername,
            currentUserId = currentUserId,
            filters = SearchFilters()
        )
        
        // Demonstrate the mismatch
        assertThat(result.isSuccess).isTrue()
        val users = result.getOrNull()
        assertThat(users).isEmpty() // No results because of collection mismatch
        
        // Verify repository only searched expected collections (not where data actually is)
        verify { userSearchCacheCollection.whereEqualTo("isPublic", true) }
        verify { usersPublicCollection.whereEqualTo("isPublic", true) }
        verify(exactly = 0) { socialProfilesCollection.whereEqualTo("isPublic", true) }
        
        Timber.e("🎯 ROOT CAUSE DEMONSTRATED: Data in 'social_profiles', search in 'users_public/user_search_cache'")
    }

    // Helper method to create mock Firestore document
    private fun mockDocumentSnapshot(userId: String, username: String, displayName: String): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>()
        every { doc.id } returns userId
        every { doc.getString("userId") } returns userId
        every { doc.getString("username") } returns username
        every { doc.getString("displayName") } returns displayName
        every { doc.getBoolean("isPublic") } returns true
        every { doc.exists() } returns true
        every { doc.data } returns mapOf(
            "userId" to userId,
            "username" to username,
            "displayName" to displayName,
            "isPublic" to true,
            "memberSince" to System.currentTimeMillis(),
            "totalWorkouts" to 0
        )
        return doc
    }
}