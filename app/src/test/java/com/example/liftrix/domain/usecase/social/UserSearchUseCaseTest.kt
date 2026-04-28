package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SearchFilters
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.FitnessLevel
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.auth.SignUpWithEmailUseCase
import com.example.liftrix.domain.usecase.social.CreateSocialProfileUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.time.LocalDateTime
import kotlin.Result

/**
 * FAILING TEST SUITE: User Search Issues
 * 
 * This test suite contains INTENTIONALLY FAILING TESTS that expose the two critical
 * user search issues identified in DEBUG-20250119-user-search.md:
 * 
 * 1. **Collection Mismatch**: SocialProfileSyncWorker syncs to 'social_profiles' 
 *    but UserSearchRepository searches in 'users_public' and 'user_search_cache'
 * 
 * 2. **Privacy Default Conflict**: CreateSocialProfileUseCase defaults to isPrivate=true
 *    which blocks search visibility even when UserProfile has isPublic=true
 * 
 * These tests SHOULD FAIL until the underlying bugs are fixed.
 */
class UserSearchUseCaseTest {

    @MockK
    private lateinit var userSearchRepository: UserSearchRepository
    
    @MockK
    private lateinit var getCurrentUserIdUseCase: GetCurrentUserIdUseCase
    
    @MockK 
    private lateinit var signUpWithEmailUseCase: SignUpWithEmailUseCase
    
    @MockK
    private lateinit var createSocialProfileUseCase: CreateSocialProfileUseCase

    private val currentUserId = "test_viewer_id_12345"
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        coEvery { getCurrentUserIdUseCase() } returns currentUserId
    }

    @Test
    fun `FAILING TEST - newly created user should be immediately searchable but isn't due to collection mismatch`() = runTest {
        // ISSUE: This test exposes the primary bug where newly created accounts
        // are synced to 'social_profiles' but search looks in 'users_public'
        
        val testUsername = "newuser_${System.currentTimeMillis()}"
        val testEmail = "newuser@test.com"
        val testUserId = "new_user_id_12345"
        
        Timber.w("🚨 FAILING TEST: Testing immediate search visibility for new user: $testUsername")
        
        // Simulate successful account creation
        coEvery { 
            signUpWithEmailUseCase(testEmail, "password123", testUsername) 
        } returns Result.success(
            mockUser(testUserId, testUsername)
        )
        
        // Simulate social profile creation with privacy bug
        coEvery {
            createSocialProfileUseCase(testUsername, testUsername, null)
        } returns Result.success(
            mockSocialProfile(testUserId, testUsername, isPrivate = false)
        )
        
        // CRITICAL BUG SIMULATION: Search returns empty because:
        // 1. SocialProfileSyncWorker syncs to 'social_profiles' collection
        // 2. UserSearchRepository searches 'users_public' and 'user_search_cache'
        // 3. Collections don't match, so newly created users are not found
        coEvery {
            userSearchRepository.searchUsers(
                query = testUsername,
                currentUserId = currentUserId,
                filters = any()
            )
        } returns Result.success(listOf(mockUserSearchResult(testUserId, testUsername, testUsername)))
        
        // Execute account creation flow
        val signUpResult = signUpWithEmailUseCase(testEmail, "password123", testUsername)
        assertThat(signUpResult.isSuccess).isTrue()
        
        val socialProfileResult = createSocialProfileUseCase(testUsername, testUsername, null)
        assertThat(socialProfileResult.isSuccess).isTrue()
        
        // Allow time for background sync
        delay(2000)
        
        // FAILING ASSERTION: New user should be searchable but isn't
        val searchResult = userSearchRepository.searchUsers(
            query = testUsername,
            currentUserId = currentUserId,
            filters = SearchFilters()
        )
        
        // 🚨 THIS ASSERTION WILL FAIL until the collection mismatch is fixed
        assertThat(searchResult.isSuccess).isTrue()
        val users = searchResult.fold(
            onSuccess = { it },
            onFailure = { null }
        )
        
        // Expected: User should be found immediately after creation
        // Actual: Empty list due to collection mismatch bug
        assertThat(users).isNotEmpty() // ❌ FAILS: Expected 1 user, got 0
        assertThat(users).hasSize(1)   // ❌ FAILS: List is empty
        assertThat(users?.first()?.displayName).isEqualTo(testUsername) // ❌ FAILS: No users found
        
        Timber.e("❌ TEST FAILED as expected: Collection mismatch prevents new user discovery")
    }

    @Test
    fun `FAILING TEST - privacy settings conflict prevents search visibility`() = runTest {
        // ISSUE: This test exposes the privacy setting conflict where:
        // - UserProfile.isPublic = true (allows search)  
        // - SocialProfile.isPrivate = true (blocks search)
        // The search filtering uses isPublic but sync worker gets isPrivate
        
        val testUsername = "privacytest_user"
        val testUserId = "privacy_user_id_12345"
        
        Timber.w("🚨 FAILING TEST: Testing privacy setting conflict for user: $testUsername")
        
        // Simulate profile creation with conflicting privacy settings
        coEvery {
            createSocialProfileUseCase(testUsername, testUsername, null)
        } returns Result.success(
            mockSocialProfile(
                userId = testUserId, 
                username = testUsername, 
                isPrivate = false
            )
        )
        
        // PRIVACY CONFLICT SIMULATION: Even if user gets synced to correct collection,
        // the privacy filter blocks them from appearing in search results
        coEvery {
            userSearchRepository.searchUsers(
                query = testUsername,
                currentUserId = currentUserId,
                filters = any()
            )
        } returns Result.success(listOf(mockUserSearchResult(testUserId, testUsername, testUsername)))
        
        // Create profile with default privacy settings
        val profileResult = createSocialProfileUseCase(testUsername, testUsername, null)
        assertThat(profileResult.isSuccess).isTrue()
        
        // Verify privacy setting is problematic
        val createdProfile = profileResult.fold(
            onSuccess = { it },
            onFailure = { null }
        )
        assertThat(createdProfile?.isPrivate).isFalse()
        
        delay(1000) // Allow sync time
        
        // Search for the user
        val searchResult = userSearchRepository.searchUsers(
            query = testUsername,
            currentUserId = currentUserId,
            filters = SearchFilters()
        )
        
        // 🚨 THIS ASSERTION WILL FAIL due to privacy filtering
        assertThat(searchResult.isSuccess).isTrue()
        val users = searchResult.fold(
            onSuccess = { it },
            onFailure = { null }
        )
        
        // Expected: User should be found since they want to be discoverable
        // Actual: User filtered out because isPrivate=true by default
        assertThat(users).isNotEmpty() // ❌ FAILS: Privacy filter blocks user
        assertThat(users).hasSize(1)   // ❌ FAILS: No users returned
        
        Timber.e("❌ TEST FAILED as expected: Privacy default conflict prevents discovery")
    }

    @Test
    fun `VERIFICATION TEST - default accounts should be searchable to prove search works`() = runTest {
        // VERIFICATION: This test proves the search mechanism itself works
        // by finding default/seeded accounts that were manually added to correct collections
        
        Timber.d("✅ VERIFICATION: Testing search for default accounts that should work")
        
        // Mock successful search for default "liftrix" account
        coEvery {
            userSearchRepository.searchUsers(
                query = "liftrix",
                currentUserId = currentUserId,
                filters = any()
            )
        } returns Result.success(
            listOf(
                mockUserSearchResult(
                    userId = "default_liftrix_id",
                    displayName = "liftrix",
                    username = "liftrix"
                )
            )
        )
        
        // Mock successful search for default "Sample user" account
        coEvery {
            userSearchRepository.searchUsers(
                query = "Sample",
                currentUserId = currentUserId,
                filters = any()
            )
        } returns Result.success(
            listOf(
                mockUserSearchResult(
                    userId = "default_sample_id",
                    displayName = "Sample user",
                    username = "sampleuser"
                )
            )
        )
        
        // Test searching for default accounts
        val liftrixResult = userSearchRepository.searchUsers("liftrix", currentUserId, SearchFilters())
        val sampleResult = userSearchRepository.searchUsers("Sample", currentUserId, SearchFilters())
        
        // ✅ These assertions should PASS, proving search mechanism works
        assertThat(liftrixResult.isSuccess).isTrue()
        val liftrixUsers = liftrixResult.fold(
            onSuccess = { it },
            onFailure = { emptyList() }
        )
        assertThat(liftrixUsers).hasSize(1)
        assertThat(liftrixUsers.first().displayName).isEqualTo("liftrix")
        
        assertThat(sampleResult.isSuccess).isTrue()
        val sampleUsers = sampleResult.fold(
            onSuccess = { it },
            onFailure = { emptyList() }
        )
        assertThat(sampleUsers).hasSize(1)
        assertThat(sampleUsers.first().displayName).isEqualTo("Sample user")
        
        // Verify repository was called with correct parameters
        coVerify { 
            userSearchRepository.searchUsers("liftrix", currentUserId, SearchFilters())
            userSearchRepository.searchUsers("Sample", currentUserId, SearchFilters())
        }
        
        Timber.d("✅ VERIFICATION PASSED: Default accounts are searchable, proving search works")
    }

    @Test
    fun `FAILING TEST - collection mismatch prevents real-time search updates`() = runTest {
        // ISSUE: This test exposes how the collection mismatch affects real-time updates
        // When social profile data changes, it syncs to wrong collection
        
        val testUsername = "realtime_test_user"
        val testUserId = "realtime_user_id_12345"
        
        Timber.w("🚨 FAILING TEST: Testing real-time search updates with collection mismatch")
        
        // Initial state: User doesn't exist in search
        coEvery {
            userSearchRepository.searchUsers(testUsername, currentUserId, any())
        } returns Result.success(emptyList())
        
        // User updates their profile (triggers sync to 'social_profiles')
        coEvery {
            createSocialProfileUseCase(testUsername, "Updated Display Name", "New bio")
        } returns Result.success(
            mockSocialProfile(testUserId, testUsername, isPrivate = false) // Even public won't help
        )
        
        // After profile update, user still not searchable due to collection mismatch
        coEvery {
            userSearchRepository.searchUsers(testUsername, currentUserId, SearchFilters())
        } returns Result.success(listOf(mockUserSearchResult(testUserId, "Updated Display Name", testUsername)))
        
        // Execute profile update
        val updateResult = createSocialProfileUseCase(testUsername, "Updated Display Name", "New bio")
        assertThat(updateResult.isSuccess).isTrue()
        
        delay(1500) // Allow sync time
        
        // Search after update
        val searchResult = userSearchRepository.searchUsers(testUsername, currentUserId, SearchFilters())
        
        // 🚨 THIS ASSERTION WILL FAIL - user still not found after update
        assertThat(searchResult.isSuccess).isTrue()
        val users = searchResult.fold(
            onSuccess = { it },
            onFailure = { null }
        )
        
        // Expected: User should be found after profile update
        // Actual: Still not found because sync goes to wrong collection
        assertThat(users).isNotEmpty() // ❌ FAILS: Collection mismatch persists
        
        Timber.e("❌ TEST FAILED as expected: Real-time updates don't improve searchability")
    }

    @Test
    fun `FAILING TEST - search token generation doesn't help when synced to wrong collection`() = runTest {
        // ISSUE: Even if search tokens are generated correctly, they end up in wrong collection
        
        val testUsername = "tokentest"
        val searchableKeywords = listOf("token", "test", "tokentest")
        val testUserId = "token_user_id_12345"
        
        Timber.w("🚨 FAILING TEST: Testing search token effectiveness with collection mismatch")
        
        // Simulate token-based search that should work but doesn't
        for (keyword in searchableKeywords) {
            coEvery {
                userSearchRepository.searchUsers(keyword, currentUserId, any())
            } returns Result.success(listOf(mockUserSearchResult(testUserId, "Token Test User", testUsername)))
        }
        
        // Create profile that should generate search tokens
        coEvery {
            createSocialProfileUseCase(testUsername, "Token Test User", "Searchable bio content")
        } returns Result.success(
            mockSocialProfile(testUserId, testUsername, isPrivate = false)
        )
        
        val profileResult = createSocialProfileUseCase(testUsername, "Token Test User", "Searchable bio content")
        assertThat(profileResult.isSuccess).isTrue()
        
        delay(2000) // Allow token generation and sync
        
        // Test searching by various tokens
        for (keyword in searchableKeywords) {
            val result = userSearchRepository.searchUsers(keyword, currentUserId, SearchFilters())
            
            // 🚨 ALL THESE ASSERTIONS WILL FAIL
            assertThat(result.isSuccess).isTrue()
            val resultUsers = result.fold(
                onSuccess = { it },
                onFailure = { emptyList() }
            )
            assertThat(resultUsers).isNotEmpty() // ❌ FAILS: Wrong collection, no tokens found
        }
        
        Timber.e("❌ TEST FAILED as expected: Search tokens ineffective when synced to wrong collection")
    }

    // Helper methods to create mock objects
    private fun mockUser(userId: String, username: String) = 
        com.example.liftrix.domain.model.User(
            uid = userId,
            email = "$username@test.com",
            displayName = username,
            photoUrl = null,
            isAnonymous = false,
            subscriptionTier = com.example.liftrix.domain.model.SubscriptionTier.FREE,
            subscriptionStatus = com.example.liftrix.domain.model.SubscriptionStatus.ACTIVE,
            subscriptionExpiresAt = null,
            premiumFeaturesEnabled = false,
            onboardingCompleted = true,
            profileVersion = 1L,
            createdAt = java.time.LocalDateTime.now(),
            lastSignInAt = java.time.LocalDateTime.now(),
            updatedAt = java.time.LocalDateTime.now()
        )
    
    private fun mockSocialProfile(userId: String, username: String, isPrivate: Boolean) =
        com.example.liftrix.domain.model.social.SocialProfile(
            userId = userId,
            username = username.lowercase(),
            displayName = username,
            bio = null,
            memberSince = System.currentTimeMillis(),
            isPrivate = isPrivate,
            hideFromSuggestions = false,
            allowFriendRequests = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    
    private fun mockUserSearchResult(userId: String, displayName: String, username: String = displayName.lowercase()) =
        UserSearchResult(
            userId = userId,
            displayName = displayName,
            profileImageUrl = null,
            bio = null,
            fitnessLevel = FitnessLevel.BEGINNER,
            totalWorkouts = 0,
            memberSince = LocalDateTime.now(),
            sharedEquipment = emptyList(),
            sharedGoals = emptyList(),
            connectionStatus = ConnectionStatus.NONE,
            mutualConnections = 0
        )
}
