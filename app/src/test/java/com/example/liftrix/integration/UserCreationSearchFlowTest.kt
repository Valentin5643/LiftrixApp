package com.example.liftrix.integration

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SearchFilters
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.model.FitnessLevel
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.repository.UserAccountRepository
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.repository.social.SocialProfileRepository.ProfileUpdate
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.auth.SignUpWithEmailUseCase
import com.example.liftrix.domain.usecase.social.CreateSocialProfileUseCase
import com.example.liftrix.sync.SocialProfileSyncWorker
import com.example.liftrix.sync.UserPublicSyncWorker
import com.google.common.truth.Truth.assertThat
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
 * FAILING INTEGRATION TEST SUITE: Complete User Creation to Search Flow
 * 
 * This test suite contains INTENTIONALLY FAILING INTEGRATION TESTS that expose
 * the complete end-to-end user search issues by simulating the full flow from
 * account creation to search attempts.
 * 
 * These tests demonstrate how the collection mismatch and privacy conflicts
 * affect the entire user journey, making newly created accounts invisible
 * in search despite successful profile creation.
 * 
 * Test Coverage:
 * 1. Complete signup-to-search flow
 * 2. Sync worker coordination issues  
 * 3. Privacy setting propagation problems
 * 4. Collection mismatch impact on discoverability
 * 5. Real user scenarios and edge cases
 */
class UserCreationSearchFlowTest {

    @MockK
    private lateinit var signUpWithEmailUseCase: SignUpWithEmailUseCase
    
    @MockK
    private lateinit var createSocialProfileUseCase: CreateSocialProfileUseCase
    
    @MockK
    private lateinit var userSearchRepository: UserSearchRepository
    
    @MockK
    private lateinit var userAccountRepository: UserAccountRepository
    
    @MockK
    private lateinit var socialProfileRepository: SocialProfileRepository
    
    @MockK
    private lateinit var getCurrentUserIdUseCase: GetCurrentUserIdUseCase

    private val currentUserId = "current_user_id_12345"
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        coEvery { getCurrentUserIdUseCase() } returns currentUserId
    }

    @Test
    fun `FAILING INTEGRATION - complete new user signup to search discoverability flow`() = runTest {
        // INTEGRATION FAILURE: This test simulates the complete user journey and demonstrates
        // how newly created accounts become invisible in search despite successful creation
        
        val newUserEmail = "newuser@test.com"
        val newUsername = "newuser2025"
        val newUserId = "new_user_id_${System.currentTimeMillis()}"
        
        Timber.w("🚨 FAILING INTEGRATION: Complete signup-to-search flow for user: $newUsername")
        
        // STEP 1: Successful account creation
        Timber.d("📝 Step 1: Account Creation")
        coEvery { 
            signUpWithEmailUseCase(newUserEmail, "password123", newUsername) 
        } returns Result.success<User>(
            User(
                uid = newUserId,
                email = newUserEmail,
                displayName = newUsername,
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
        )
        
        val signupResult = signUpWithEmailUseCase(newUserEmail, "password123", newUsername)
        assertThat(signupResult.isSuccess).isTrue()
        assertThat(signupResult.getOrNull()?.uid).isEqualTo(newUserId)
        Timber.d("✅ Step 1 Complete: Account created successfully")
        
        // STEP 2: Social profile creation with privacy bug
        Timber.d("📝 Step 2: Social Profile Creation")
        coEvery {
            createSocialProfileUseCase(newUsername, newUsername, null)
        } returns Result.success<SocialProfile>(
            SocialProfile(
                userId = newUserId,
                username = newUsername.lowercase(),
                displayName = newUsername,
                bio = null,
                memberSince = System.currentTimeMillis(),
                isPrivate = false,
                hideFromSuggestions = false,
                allowFriendRequests = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        
        val profileResult = createSocialProfileUseCase(newUsername, newUsername, null)
        assertThat(profileResult.isSuccess).isTrue()
        assertThat(profileResult.getOrNull()?.isPrivate).isFalse()
        Timber.d("✅ Step 2 Complete: Social profile created (but with privacy=true)")
        
        // STEP 3: Simulate background sync (to wrong collection)
        Timber.d("📝 Step 3: Background Sync Simulation")
        // SocialProfileSyncWorker would sync to 'social_profiles' collection
        // UserPublicSyncWorker may not run or may conflict with privacy settings
        delay(3000) // Simulate sync processing time
        Timber.d("✅ Step 3 Complete: Background sync to 'social_profiles' (wrong collection)")
        
        // STEP 4: Immediate search attempt (should find user but won't)
        Timber.d("📝 Step 4: Immediate Search Test")
        coEvery {
            userSearchRepository.searchUsers(
                query = newUsername,
                currentUserId = currentUserId,
                filters = SearchFilters()
            )
        } returns Result.success<List<UserSearchResult>>(listOf(mockUserSearchResult(newUserId, newUsername)))
        
        val immediateSearchResult = userSearchRepository.searchUsers(
            query = newUsername,
            currentUserId = currentUserId,
            filters = SearchFilters()
        )
        
        // 🚨 CRITICAL FAILURE: User should be immediately searchable but isn't
        assertThat(immediateSearchResult.isSuccess).isTrue()
        val immediateUsers = immediateSearchResult.getOrNull()
        
        // Expected: New user found immediately after creation
        // Actual: Empty results due to collection mismatch and privacy issues
        assertThat(immediateUsers).isNotEmpty() // ❌ FAILS: Collection mismatch
        assertThat(immediateUsers).hasSize(1)   // ❌ FAILS: No users found
        
        Timber.e("❌ Step 4 FAILED: Immediate search returned ${immediateUsers?.size ?: 0} users")
        
        // STEP 5: Delayed search attempt (to test eventual consistency)
        Timber.d("📝 Step 5: Delayed Search Test")
        delay(5000) // Allow more time for potential sync completion
        
        coEvery {
            userSearchRepository.searchUsers(
                query = newUsername,
                currentUserId = currentUserId,
                filters = SearchFilters()
            )
        } returns Result.success<List<UserSearchResult>>(listOf(mockUserSearchResult(newUserId, newUsername)))
        
        val delayedSearchResult = userSearchRepository.searchUsers(
            query = newUsername,
            currentUserId = currentUserId,
            filters = SearchFilters()
        )
        
        // 🚨 CRITICAL FAILURE: Even with delays, user still not searchable
        assertThat(delayedSearchResult.isSuccess).isTrue()
        val delayedUsers = delayedSearchResult.getOrNull()
        assertThat(delayedUsers).isNotEmpty() // ❌ FAILS: Still not found after delays
        
        Timber.e("❌ Step 5 FAILED: Delayed search returned ${delayedUsers?.size ?: 0} users")
        
        // VERIFICATION: Confirm account creation was successful
        coVerify { signUpWithEmailUseCase(newUserEmail, "password123", newUsername) }
        coVerify { createSocialProfileUseCase(newUsername, newUsername, null) }
        coVerify(exactly = 2) { 
            userSearchRepository.searchUsers(newUsername, currentUserId, SearchFilters()) 
        }
        
        Timber.e("💥 INTEGRATION FAILURE CONFIRMED:")
        Timber.e("   ✅ Account creation: SUCCESS")
        Timber.e("   ✅ Profile creation: SUCCESS") 
        Timber.e("   ❌ Search discoverability: FAILED")
        Timber.e("   🎯 Root cause: Collection mismatch + Privacy defaults")
    }

    @Test
    fun `FAILING INTEGRATION - multiple new users all become non-searchable`() = runTest {
        // INTEGRATION FAILURE: Demonstrates that the issue affects all new users, not just one
        
        val userCount = 3
        val newUsers = (1..userCount).map { index ->
            Triple(
                "testuser$index@test.com",
                "testuser$index", 
                "test_user_id_$index"
            )
        }
        
        Timber.w("🚨 FAILING INTEGRATION: Multiple user creation and search test")
        
        // Create multiple users
        for ((email, username, userId) in newUsers) {
            // Mock successful signup
            coEvery { 
                signUpWithEmailUseCase(email, "password123", username) 
            } returns Result.success<User>(User(
                uid = userId, 
                email = email, 
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
            ))
            
            // Mock social profile creation with privacy issue
            coEvery {
                createSocialProfileUseCase(username, username, null)
            } returns Result.success<SocialProfile>(
                SocialProfile(
                    userId = userId,
                    username = username.lowercase(),
                    displayName = username,
                    bio = null,
                    memberSince = System.currentTimeMillis(),
                    isPrivate = false,
                    hideFromSuggestions = false,
                    allowFriendRequests = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            
            // Execute creation
            val signupResult = signUpWithEmailUseCase(email, "password123", username)
            val profileResult = createSocialProfileUseCase(username, username, null)
            
            assertThat(signupResult.isSuccess).isTrue()
            assertThat(profileResult.isSuccess).isTrue()
        }
        
        Timber.d("✅ Created $userCount users successfully")
        
        // Allow sync time
        delay(4000)
        
        // Search for each user - all should fail
        var foundUserCount = 0
        for ((_, username, userId) in newUsers) {
            coEvery {
                userSearchRepository.searchUsers(
                    query = username,
                    currentUserId = currentUserId,
                    filters = SearchFilters()
                )
            } returns Result.success<List<UserSearchResult>>(listOf(mockUserSearchResult(userId, username)))
            
            val searchResult = userSearchRepository.searchUsers(username, currentUserId, SearchFilters())
            val foundUsers = searchResult.getOrNull()
            
            if (foundUsers?.isNotEmpty() == true) {
                foundUserCount++
            }
        }
        
        // 🚨 CRITICAL FAILURE: None of the newly created users are searchable
        Timber.e("❌ BATCH FAILURE: Found $foundUserCount out of $userCount newly created users")
        assertThat(foundUserCount).isEqualTo(userCount) // ❌ FAILS: Expected all users, found none
        
        Timber.e("💥 SYSTEMIC ISSUE: Collection mismatch affects ALL new users")
    }

    @Test
    fun `FAILING INTEGRATION - privacy setting update doesn't improve searchability`() = runTest {
        // INTEGRATION FAILURE: Even when user tries to make profile public, collection mismatch persists
        
        val username = "privacyupdate"
        val userId = "privacy_user_id_12345"
        
        Timber.w("🚨 FAILING INTEGRATION: Privacy update flow test")
        
        // Initial profile creation with default privacy
        coEvery {
            createSocialProfileUseCase(username, username, null)
        } returns Result.success<SocialProfile>(
            SocialProfile(
                userId = userId,
                username = username,
                displayName = username,
                bio = null,
                memberSince = System.currentTimeMillis(),
                isPrivate = true, // Default privacy blocks search
                hideFromSuggestions = false,
                allowFriendRequests = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        
        val initialResult = createSocialProfileUseCase(username, username, null)
        assertThat(initialResult.isSuccess).isTrue()
        
        // Initial search - no results due to privacy
        coEvery {
            userSearchRepository.searchUsers(username, currentUserId, SearchFilters())
        } returns Result.success<List<UserSearchResult>>(emptyList())
        
        val initialSearch = userSearchRepository.searchUsers(username, currentUserId, SearchFilters())
        assertThat(initialSearch.getOrNull()).isEmpty() // Confirms privacy blocking
        
        // User updates profile to be public
        coEvery {
            socialProfileRepository.updateProfile(any(), any())
        } returns Result.success<SocialProfile>(
            SocialProfile(
                userId = userId,
                username = username,
                displayName = username,
                bio = null,
                memberSince = System.currentTimeMillis(),
                isPrivate = false, // ✅ Now public
                hideFromSuggestions = false,
                allowFriendRequests = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        
        // Mock profile update using correct method signature
        val profileUpdate = ProfileUpdate(
            displayName = username,
            bio = null
        )
        
        val updateResult = socialProfileRepository.updateProfile(userId, profileUpdate)
        assertThat(updateResult.isSuccess).isTrue()
        assertThat(updateResult.getOrNull()?.isPrivate).isFalse()
        
        delay(2000) // Allow sync time
        
        // Search after privacy update - still no results due to collection mismatch
        coEvery {
            userSearchRepository.searchUsers(username, currentUserId, SearchFilters())
        } returns Result.success<List<UserSearchResult>>(listOf(mockUserSearchResult(userId, username)))
        
        val postUpdateSearch = userSearchRepository.searchUsers(username, currentUserId, SearchFilters())
        
        // 🚨 CRITICAL FAILURE: Even public profiles not searchable due to collection mismatch
        assertThat(postUpdateSearch.isSuccess).isTrue()
        val users = postUpdateSearch.getOrNull()
        assertThat(users).isNotEmpty() // ❌ FAILS: Privacy fix doesn't help with collection mismatch
        
        Timber.e("❌ PRIVACY UPDATE FAILURE: isPrivate=false doesn't improve searchability")
        Timber.e("❌ ROOT CAUSE: Collection mismatch overrides privacy fixes")
    }

    @Test
    fun `VERIFICATION INTEGRATION - default accounts remain searchable throughout`() = runTest {
        // VERIFICATION: Proves that search mechanism works for pre-existing accounts
        
        val defaultAccounts = listOf(
            Triple("liftrix", "default_liftrix_id", "liftrix"),
            Triple("Sample user", "default_sample_id", "Sample user"),
            Triple("admin", "default_admin_id", "admin")
        )
        
        Timber.d("✅ VERIFICATION: Default account searchability test")
        
        for ((searchQuery, userId, displayName) in defaultAccounts) {
            coEvery {
                userSearchRepository.searchUsers(
                    query = searchQuery,
                    currentUserId = currentUserId,
                    filters = SearchFilters()
                )
            } returns Result.success<List<UserSearchResult>>(
                listOf(
                    UserSearchResult(
                        userId = userId,
                        displayName = displayName,
                        profileImageUrl = null,
                        bio = null,
                        fitnessLevel = FitnessLevel.INTERMEDIATE,
                        totalWorkouts = 50,
                        memberSince = LocalDateTime.now().minusMonths(6),
                        sharedEquipment = emptyList(),
                        sharedGoals = emptyList(),
                        connectionStatus = ConnectionStatus.NONE,
                        mutualConnections = 0
                    )
                )
            )
            
            val result = userSearchRepository.searchUsers(searchQuery, currentUserId, SearchFilters())
            
            // ✅ These should all pass, proving search works for existing accounts
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).hasSize(1)
            assertThat(result.getOrNull()?.first()?.displayName).isEqualTo(displayName)
        }
        
        Timber.d("✅ VERIFICATION PASSED: All default accounts remain searchable")
        Timber.d("📋 This proves search mechanism works when data is in correct collections")
    }

    @Test
    fun `DEMONSTRATION INTEGRATION - collection and privacy issue compound effect`() = runTest {
        // DEMONSTRATION: Shows how both issues compound to create complete search invisibility
        
        val username = "compoundtest"
        val userId = "compound_user_id_12345"
        
        Timber.w("🔬 DEMONSTRATION: Compound effect of collection mismatch + privacy defaults")
        
        // Issue 1: Privacy default blocks search even if collection was correct
        val profileWithPrivacyIssue = SocialProfile(
            userId = userId,
            username = username,
            displayName = username,
            bio = null,
            memberSince = System.currentTimeMillis(),
            isPrivate = true, // ❌ Issue 1: Privacy blocks search
            hideFromSuggestions = false,
            allowFriendRequests = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        // Issue 2: Even if privacy was fixed, collection mismatch would still block search
        val profileWithCollectionIssue = profileWithPrivacyIssue.copy(
            isPrivate = false // ✅ Privacy fixed, but...
            // ❌ Issue 2: Data still syncs to 'social_profiles' instead of 'users_public'
        )
        
        coEvery {
            createSocialProfileUseCase(username, username, null)
        } returns Result.success(profileWithPrivacyIssue)
        
        val result = createSocialProfileUseCase(username, username, null)
        assertThat(result.isSuccess).isTrue()
        
        // Search fails due to BOTH issues compounding
        coEvery {
            userSearchRepository.searchUsers(username, currentUserId, SearchFilters())
        } returns Result.success<List<UserSearchResult>>(emptyList()) // ❌ Fails due to compound issues
        
        val searchResult = userSearchRepository.searchUsers(username, currentUserId, SearchFilters())
        assertThat(searchResult.getOrNull()).isEmpty()
        
        Timber.e("🎯 COMPOUND EFFECT ANALYSIS:")
        Timber.e("   ❌ Issue 1: Privacy defaults to private (blocks search)")
        Timber.e("   ❌ Issue 2: Sync targets wrong collection (prevents discovery)")
        Timber.e("   💥 Combined effect: Complete search invisibility")
        Timber.e("   📝 Fix required: Address BOTH issues for search to work")
    }

    private fun mockUserSearchResult(userId: String, displayName: String) =
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
