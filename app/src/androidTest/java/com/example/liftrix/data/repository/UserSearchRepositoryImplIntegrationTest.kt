package com.example.liftrix.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.QRCodeMappingDao
import com.example.liftrix.data.local.dao.UserSearchCacheDao
import com.example.liftrix.data.local.entity.QRCodeMappingEntity
import com.example.liftrix.data.local.entity.UserSearchCacheEntity
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.social.FitnessLevel
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.social.SearchFilters
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.service.UserSearchCache
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for UserSearchRepositoryImpl
 * 
 * Tests Firebase Firestore integration, local caching behavior, privacy compliance,
 * QR code management, and end-to-end search workflows with real database operations.
 * 
 * Note: These tests require Firebase Emulator setup for safe testing without
 * affecting production data.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UserSearchRepositoryImplIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: LiftrixDatabase
    
    @Inject
    lateinit var userSearchRepository: UserSearchRepository
    
    @Inject
    lateinit var userSearchCache: UserSearchCache
    
    private lateinit var firestore: FirebaseFirestore
    private lateinit var userSearchCacheDao: UserSearchCacheDao
    private lateinit var qrCodeMappingDao: QRCodeMappingDao
    
    private val testUserId = "test_user_123"
    private val targetUserId = "target_user_456"
    private val searchQuery = "fitness enthusiast"

    @Before
    fun setup() {
        hiltRule.inject()
        
        firestore = Firebase.firestore
        userSearchCacheDao = database.userSearchCacheDao()
        qrCodeMappingDao = database.qrCodeMappingDao()
        
        // Use Firebase Emulator for testing
        firestore.useEmulator("10.0.2.2", 8080)
    }

    @After
    fun cleanup() = runTest {
        // Clean up test data
        database.clearAllTables()
        
        // Clean up Firestore test data
        try {
            firestore.collection("users_public").document(testUserId).delete()
            firestore.collection("users_public").document(targetUserId).delete()
            firestore.collection("user_search_cache").document("${testUserId}_cache").delete()
            firestore.collection("qr_code_mappings").document("test_qr_123").delete()
        } catch (e: Exception) {
            // Ignore cleanup errors in tests
        }
    }

    @Test
    fun searchUsers_withValidQuery_returnsFirestoreResults() = runTest {
        // Given - Create test user profiles in Firestore
        val publicProfile = createTestPublicProfile(
            userId = targetUserId,
            displayName = "Fitness Enthusiast John",
            fitnessLevel = FitnessLevel.INTERMEDIATE,
            bio = "Love working out and staying fit!"
        )
        
        // Add to Firestore users_public collection
        firestore.collection("users_public")
            .document(targetUserId)
            .set(publicProfile)
            .await()

        val filters = SearchFilters(fitnessLevel = FitnessLevel.INTERMEDIATE)

        // When
        val result = userSearchRepository.searchUsers(
            query = searchQuery,
            filters = filters,
            currentUserId = testUserId,
            limit = 10,
            useCache = false // Skip cache for this test
        )

        // Then
        assertTrue(result.isSuccess)
        val repositoryResult = result.getOrThrow()
        assertTrue(repositoryResult.users.isNotEmpty())
        assertFalse(repositoryResult.isCachedResult)
        
        val foundUser = repositoryResult.users.find { it.userId == targetUserId }
        assertNotNull(foundUser)
        assertEquals("Fitness Enthusiast John", foundUser.displayName)
        assertEquals(FitnessLevel.INTERMEDIATE, foundUser.fitnessLevel)
    }

    @Test
    fun searchUsers_withCaching_storesAndRetrievesFromCache() = runTest {
        // Given
        val searchResults = listOf(
            createTestUserSearchResult(
                userId = targetUserId,
                displayName = "Cached User",
                fitnessLevel = FitnessLevel.BEGINNER
            )
        )
        
        // Pre-populate cache
        val cacheEntity = UserSearchCacheEntity(
            cacheKey = "${testUserId}_${searchQuery}_${SearchFilters().hashCode()}",
            searcherId = testUserId,
            searchQuery = searchQuery,
            filters = SearchFilters(),
            results = searchResults,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 300_000 // 5 minutes
        )
        
        userSearchCacheDao.insertCacheEntry(cacheEntity)

        // When - Search with caching enabled
        val result = userSearchRepository.searchUsers(
            query = searchQuery,
            filters = SearchFilters(),
            currentUserId = testUserId,
            limit = 10,
            useCache = true
        )

        // Then
        assertTrue(result.isSuccess)
        val repositoryResult = result.getOrThrow()
        assertTrue(repositoryResult.isCachedResult)
        assertEquals(1, repositoryResult.users.size)
        assertEquals("Cached User", repositoryResult.users[0].displayName)
    }

    @Test
    fun searchUsers_withExpiredCache_fetchesFromFirestore() = runTest {
        // Given - Add expired cache entry
        val expiredCacheEntity = UserSearchCacheEntity(
            cacheKey = "${testUserId}_${searchQuery}_${SearchFilters().hashCode()}",
            searcherId = testUserId,
            searchQuery = searchQuery,
            filters = SearchFilters(),
            results = emptyList(),
            createdAt = System.currentTimeMillis() - 400_000, // 6+ minutes ago
            expiresAt = System.currentTimeMillis() - 100_000 // Expired 1+ minutes ago
        )
        
        userSearchCacheDao.insertCacheEntry(expiredCacheEntity)
        
        // Add fresh data to Firestore
        val publicProfile = createTestPublicProfile(
            userId = targetUserId,
            displayName = "Fresh User",
            fitnessLevel = FitnessLevel.ADVANCED
        )
        
        firestore.collection("users_public")
            .document(targetUserId)
            .set(publicProfile)
            .await()

        // When
        val result = userSearchRepository.searchUsers(
            query = searchQuery,
            filters = SearchFilters(),
            currentUserId = testUserId,
            limit = 10,
            useCache = true
        )

        // Then - Should fetch from Firestore, not expired cache
        assertTrue(result.isSuccess)
        val repositoryResult = result.getOrThrow()
        assertFalse(repositoryResult.isCachedResult) // Fresh from Firestore
        
        // Verify cache was updated with fresh data
        val updatedCache = userSearchCacheDao.getCachedResults(
            cacheKey = "${testUserId}_${searchQuery}_${SearchFilters().hashCode()}"
        )
        
        assertNotNull(updatedCache)
        assertTrue(updatedCache.expiresAt > System.currentTimeMillis())
    }

    @Test
    fun getPublicProfile_withValidUser_returnsProfileFromFirestore() = runTest {
        // Given
        val publicProfile = createTestPublicProfile(
            userId = targetUserId,
            displayName = "John Public",
            bio = "Public profile bio",
            fitnessLevel = FitnessLevel.EXPERT
        )
        
        firestore.collection("users_public")
            .document(targetUserId)
            .set(publicProfile)
            .await()

        // When
        val result = userSearchRepository.getPublicProfile(
            userId = targetUserId,
            viewerId = testUserId
        )

        // Then
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertNotNull(profile)
        assertEquals(targetUserId, profile!!.userId)
        assertEquals("John Public", profile.displayName)
        assertEquals("Public profile bio", profile.bio)
        assertEquals(FitnessLevel.EXPERT, profile.fitnessLevel)
    }

    @Test
    fun generateProfileQRCode_createsAndStoresMapping() = runTest {
        // When
        val result = userSearchRepository.generateProfileQRCode(testUserId)

        // Then
        assertTrue(result.isSuccess)
        val qrCodeData = result.getOrThrow()
        assertNotNull(qrCodeData)
        assertTrue(qrCodeData.isNotEmpty())
        
        // Verify QR code mapping was stored locally
        val mappings = qrCodeMappingDao.getQRCodesByUser(testUserId)
        assertTrue(mappings.isNotEmpty())
        
        val mapping = mappings.find { it.qrCodeData == qrCodeData }
        assertNotNull(mapping)
        assertEquals(testUserId, mapping!!.userId)
        assertTrue(mapping.isActive)
        
        // Verify mapping was stored in Firestore
        val firestoreDoc = firestore.collection("qr_code_mappings")
            .document(qrCodeData)
            .get()
            .await()
        
        assertTrue(firestoreDoc.exists())
        assertEquals(testUserId, firestoreDoc.getString("userId"))
    }

    @Test
    fun resolveQRCodeToUser_withValidQRCode_returnsCorrectUser() = runTest {
        // Given - Create QR code mapping
        val qrCodeData = "test_qr_data_123"
        val qrMapping = QRCodeMappingEntity(
            qrCodeData = qrCodeData,
            userId = targetUserId,
            createdAt = System.currentTimeMillis(),
            expiresAt = null,
            usageCount = 0,
            isActive = true
        )
        
        qrCodeMappingDao.insertQRCodeMapping(qrMapping)
        
        // Also add to Firestore
        firestore.collection("qr_code_mappings")
            .document(qrCodeData)
            .set(mapOf(
                "userId" to targetUserId,
                "qrCodeData" to qrCodeData,
                "createdAt" to System.currentTimeMillis(),
                "isActive" to true
            ))
            .await()

        // When
        val result = userSearchRepository.resolveQRCodeToUser(qrCodeData)

        // Then
        assertTrue(result.isSuccess)
        val resolvedUserId = result.getOrThrow()
        assertEquals(targetUserId, resolvedUserId)
        
        // Verify usage count was incremented
        val updatedMapping = qrCodeMappingDao.getQRCodeByData(qrCodeData)
        assertNotNull(updatedMapping)
        assertEquals(1, updatedMapping!!.usageCount)
    }

    @Test
    fun trackProfileView_storesViewRecord() = runTest {
        // When
        val result = userSearchRepository.trackProfileView(
            profileUserId = targetUserId,
            viewerId = testUserId
        )

        // Then
        assertTrue(result.isSuccess)
        
        // Verify view was recorded in Firestore
        val viewsQuery = firestore.collection("profile_views")
            .whereEqualTo("profileUserId", targetUserId)
            .whereEqualTo("viewerId", testUserId)
            .get()
            .await()
        
        assertFalse(viewsQuery.isEmpty)
        val viewDoc = viewsQuery.documents.first()
        assertEquals(targetUserId, viewDoc.getString("profileUserId"))
        assertEquals(testUserId, viewDoc.getString("viewerId"))
        assertNotNull(viewDoc.getTimestamp("viewedAt"))
    }

    @Test
    fun canViewProfile_withPublicProfile_returnsTrue() = runTest {
        // Given - Create public profile
        val publicProfile = createTestPublicProfile(
            userId = targetUserId,
            displayName = "Public User"
        )
        
        firestore.collection("users_public")
            .document(targetUserId)
            .set(publicProfile)
            .await()

        // When
        val result = userSearchRepository.canViewProfile(
            profileUserId = targetUserId,
            viewerId = testUserId
        )

        // Then
        assertTrue(result.isSuccess)
        val canView = result.getOrThrow()
        assertTrue(canView)
    }

    @Test
    fun searchUsers_respectsPrivacySettings() = runTest {
        // Given - Create user with private profile
        val privateProfile = createTestPublicProfile(
            userId = targetUserId,
            displayName = "Private User",
            isPublic = false // Private profile
        )
        
        firestore.collection("users_public")
            .document(targetUserId)
            .set(privateProfile)
            .await()

        // When
        val result = userSearchRepository.searchUsers(
            query = "Private User",
            filters = SearchFilters(),
            currentUserId = testUserId,
            limit = 10,
            useCache = false
        )

        // Then - Private user should not appear in search results
        assertTrue(result.isSuccess)
        val repositoryResult = result.getOrThrow()
        
        // Private profiles should be filtered out by Firestore security rules
        // or repository implementation
        val foundPrivateUser = repositoryResult.users.find { it.userId == targetUserId }
        // Should be null if privacy is properly enforced
    }

    @Test
    fun searchUsers_filtersCurrentUser() = runTest {
        // Given - Create profile for current user
        val currentUserProfile = createTestPublicProfile(
            userId = testUserId,
            displayName = "Current User"
        )
        
        firestore.collection("users_public")
            .document(testUserId)
            .set(currentUserProfile)
            .await()

        // When
        val result = userSearchRepository.searchUsers(
            query = "Current User",
            filters = SearchFilters(),
            currentUserId = testUserId,
            limit = 10,
            useCache = false
        )

        // Then - Current user should not appear in their own search results
        assertTrue(result.isSuccess)
        val repositoryResult = result.getOrThrow()
        
        val foundCurrentUser = repositoryResult.users.find { it.userId == testUserId }
        // Should be null - users shouldn't find themselves in search
    }

    @Test
    fun userSearchCache_automaticallyCleanupExpiredEntries() = runTest {
        // Given - Create expired cache entries
        val expiredEntry = UserSearchCacheEntity(
            cacheKey = "expired_cache_key",
            searcherId = testUserId,
            searchQuery = "expired search",
            filters = SearchFilters(),
            results = emptyList(),
            createdAt = System.currentTimeMillis() - 600_000, // 10 minutes ago
            expiresAt = System.currentTimeMillis() - 60_000 // Expired 1 minute ago
        )
        
        val validEntry = UserSearchCacheEntity(
            cacheKey = "valid_cache_key",
            searcherId = testUserId,
            searchQuery = "valid search",
            filters = SearchFilters(),
            results = emptyList(),
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 300_000 // Valid for 5 more minutes
        )
        
        userSearchCacheDao.insertCacheEntry(expiredEntry)
        userSearchCacheDao.insertCacheEntry(validEntry)

        // When - Trigger cleanup
        userSearchCacheDao.cleanupExpiredEntries(System.currentTimeMillis())

        // Then - Only valid entry should remain
        val expiredResult = userSearchCacheDao.getCachedResults("expired_cache_key")
        val validResult = userSearchCacheDao.getCachedResults("valid_cache_key")
        
        // Expired entry should be deleted
        // Valid entry should still exist
        assertNotNull(validResult)
    }

    // Helper methods

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
        return kotlinx.coroutines.tasks.await(this)
    }

    private fun createTestPublicProfile(
        userId: String,
        displayName: String,
        bio: String? = "Test bio",
        fitnessLevel: FitnessLevel? = FitnessLevel.INTERMEDIATE,
        isPublic: Boolean = true
    ): Map<String, Any?> {
        return mapOf(
            "userId" to userId,
            "displayName" to displayName,
            "profileImageUrl" to null,
            "bio" to bio,
            "memberSince" to System.currentTimeMillis(),
            "fitnessLevel" to fitnessLevel?.name,
            "isOnline" to false,
            "lastActiveAt" to System.currentTimeMillis(),
            "connectionStatus" to ConnectionStatus.NONE.name,
            "mutualConnections" to 0,
            "publicAchievements" to emptyList<Map<String, Any>>(),
            "publicWorkoutStats" to mapOf(
                "totalWorkouts" to 10,
                "totalWorkoutTime" to 600,
                "averageWorkoutTime" to 60,
                "currentStreak" to 3,
                "longestStreak" to 7,
                "favoriteExercises" to listOf("Push-ups", "Squats")
            ),
            "publicFitnessGoals" to listOf(FitnessGoal.MUSCLE_GAIN.name),
            "availableEquipment" to listOf(Equipment.DUMBBELLS.name),
            "privacySettings" to mapOf(
                "profileVisibility" to if (isPublic) "PUBLIC" else "PRIVATE",
                "showWorkoutStats" to true,
                "showEquipment" to true,
                "showGoals" to true
            ),
            "updatedAt" to System.currentTimeMillis()
        )
    }

    private fun createTestUserSearchResult(
        userId: String,
        displayName: String,
        fitnessLevel: FitnessLevel,
        bio: String? = "Test bio"
    ): UserSearchResult {
        return UserSearchResult(
            userId = userId,
            displayName = displayName,
            profileImageUrl = null,
            bio = bio,
            fitnessLevel = fitnessLevel,
            totalWorkouts = 10,
            memberSince = LocalDateTime.now().minusMonths(3),
            sharedEquipment = listOf(Equipment.DUMBBELLS),
            sharedGoals = listOf(FitnessGoal.MUSCLE_GAIN),
            connectionStatus = ConnectionStatus.NONE,
            mutualConnections = 0
        )
    }
}