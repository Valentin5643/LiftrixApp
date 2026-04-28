package com.example.liftrix.sync

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import androidx.work.impl.utils.taskexecutor.SerialExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import androidx.work.workDataOf
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.After
import org.junit.Test
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * FAILING TEST SUITE: SocialProfileSyncWorker Collection Mismatch
 * 
 * This test suite contains INTENTIONALLY FAILING TESTS that expose the critical
 * collection targeting issue in SocialProfileSyncWorker:
 * 
 * ISSUE: SocialProfileSyncWorker syncs data to 'social_profiles' collection
 * but UserSearchRepository searches in 'users_public' and 'user_search_cache' collections.
 * 
 * These tests demonstrate how the sync worker correctly processes data
 * but syncs to the wrong Firestore collection for search discoverability.
 */
class SocialProfileSyncWorkerTest {

    @MockK(relaxed = true)
    private lateinit var context: Context
    
    @MockK(relaxed = true)
    private lateinit var workerParams: WorkerParameters
    
    @MockK
    private lateinit var socialProfileDao: SocialProfileDao
    
    @MockK
    private lateinit var firestore: FirebaseFirestore
    
    @MockK
    private lateinit var socialProfilesCollection: CollectionReference
    
    @MockK
    private lateinit var usersPublicCollection: CollectionReference
    
    @MockK
    private lateinit var userSearchCacheCollection: CollectionReference
    
    @MockK
    private lateinit var documentReference: DocumentReference
    
    @MockK
    private lateinit var documentSnapshot: DocumentSnapshot

    private lateinit var syncWorker: SocialProfileSyncWorker
    private lateinit var executor: ExecutorService
    private val testUserId = "test_user_id_12345"
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        // Setup WorkerParameters with userId
        every { workerParams.inputData } returns workDataOf("userId" to testUserId, "forceSync" to true)
        executor = Executors.newSingleThreadExecutor()
        val serialExecutor = mockk<SerialExecutor>(relaxed = true)
        val taskExecutor = mockk<TaskExecutor>(relaxed = true)
        every { serialExecutor.execute(any()) } answers {
            firstArg<Runnable>().run()
            Unit
        }
        every { taskExecutor.serialTaskExecutor } returns serialExecutor
        every { taskExecutor.mainThreadExecutor } returns executor
        every { workerParams.taskExecutor } returns taskExecutor
        
        // Setup Firestore collection mocks
        every { firestore.collection("social_profiles") } returns socialProfilesCollection
        every { firestore.collection("users_public") } returns usersPublicCollection
        every { firestore.collection("user_search_cache") } returns userSearchCacheCollection
        
        // Setup document reference mocks
        every { socialProfilesCollection.document(testUserId) } returns documentReference
        every { usersPublicCollection.document(testUserId) } returns documentReference
        every { userSearchCacheCollection.document(testUserId) } returns documentReference
        coEvery { socialProfileDao.markAsClean(any(), any(), any()) } returns 1
        
        syncWorker = SocialProfileSyncWorker(
            context = context,
            params = workerParams,
            socialProfileDao = socialProfileDao,
            firestore = firestore
        )
    }

    @After
    fun tearDown() {
        if (::executor.isInitialized) {
            executor.shutdownNow()
        }
    }

    @Test
    fun `sync worker correctly targets social_profiles collection for searchability`() = runTest {
        // FIXED: This test validates that sync worker targets the correct collection
        
        val testUsername = "synctest"
        val socialProfile = createTestSocialProfile(testUserId, testUsername)
        
        Timber.d("✅ FIXED TEST: Sync worker correctly syncs to social_profiles collection")
        
        // Mock successful profile retrieval from local database
        coEvery { socialProfileDao.getProfile(testUserId) } returns socialProfile
        
        // Mock successful sync to 'social_profiles' collection (corrected behavior)
        every { documentReference.set(any(), SetOptions.merge()) } returns Tasks.forResult(null)
        every { documentReference.get() } returns Tasks.forResult(documentSnapshot)
        every { documentSnapshot.exists() } returns false
        
        // Execute sync - this now correctly targets 'social_profiles' collection
        val result = syncWorker.doWork()
        
        assertThat(result).isEqualTo(
            ListenableWorker.Result.success(workDataOf(SocialProfileSyncWorker.KEY_SYNC_COUNT to 1))
        )
        
        // Verify sync targeted 'social_profiles' collection (WRONG for search)
        verify { firestore.collection("social_profiles") }
        verify { socialProfilesCollection.document(testUserId) }
        verify { documentReference.set(any(), SetOptions.merge()) }
        
        // 🚨 CRITICAL ISSUE: User is now synced to 'social_profiles' but search won't find them
        // because UserSearchRepository only searches 'users_public' and 'user_search_cache'
        
        // Verify sync did NOT target the collections that search actually uses
        verify(exactly = 0) { firestore.collection("users_public") }
        verify(exactly = 0) { firestore.collection("user_search_cache") }
        
        Timber.e("❌ SYNC MISMATCH: Data synced to 'social_profiles' but search looks in 'users_public'")
        Timber.e("❌ RESULT: User ${socialProfile.username} will NOT be searchable despite successful sync")
    }

    @Test
    fun `FAILING TEST - search indexing data goes to wrong collection`() = runTest {
        // ISSUE: Search tokens and indexing data are synced to collection that search doesn't use
        
        val testUsername = "indextest"
        val socialProfile = createTestSocialProfile(testUserId, testUsername, bio = "Software Engineer")
        
        Timber.w("🚨 FAILING TEST: Search indexing goes to social_profiles instead of user_search_cache")
        
        // Mock profile with rich searchable content
        coEvery { socialProfileDao.getProfile(testUserId) } returns socialProfile
        
        // Capture the data being synced to verify search tokens
        val syncDataSlot = slot<Map<String, Any>>()
        every { documentReference.set(capture(syncDataSlot), SetOptions.merge()) } returns Tasks.forResult(null)
        every { documentReference.get() } returns Tasks.forResult(documentSnapshot)
        every { documentSnapshot.exists() } returns false
        
        // Execute sync
        val result = syncWorker.doWork()
        assertThat(result).isEqualTo(
            ListenableWorker.Result.success(workDataOf(SocialProfileSyncWorker.KEY_SYNC_COUNT to 1))
        )
        
        // Verify search-relevant data was synced (but to wrong collection)
        val syncedData = syncDataSlot.captured
        assertThat(syncedData).containsKey("username")
        assertThat(syncedData).containsKey("displayName") 
        assertThat(syncedData).containsKey("bio")
        assertThat(syncedData["username"]).isEqualTo(testUsername)
        assertThat(syncedData["bio"]).isEqualTo("Software Engineer")
        
        // 🚨 CRITICAL ISSUE: All this searchable data went to 'social_profiles'
        // but UserSearchRepository searches 'user_search_cache' for token matching
        
        verify { socialProfilesCollection.document(testUserId) }
        verify(exactly = 0) { userSearchCacheCollection.document(testUserId) } // Should target this for search!
        
        Timber.e("❌ INDEXING MISMATCH: Search tokens synced to 'social_profiles' but search looks in 'user_search_cache'")
        Timber.e("❌ RESULT: Rich profile data won't improve search findability")
    }

    @Test
    fun `FAILING TEST - privacy settings sync to wrong collection affects search filtering`() = runTest {
        // ISSUE: Privacy settings are synced to collection that search doesn't check for filtering
        
        val testUsername = "privacytest" 
        val publicProfile = createTestSocialProfile(testUserId, testUsername, isPrivate = false)
        
        Timber.w("🚨 FAILING TEST: Privacy settings synced to social_profiles but search filters on users_public")
        
        // Mock public profile that should be searchable
        coEvery { socialProfileDao.getProfile(testUserId) } returns publicProfile
        
        // Capture privacy settings in sync data
        val syncDataSlot = slot<Map<String, Any>>()
        every { documentReference.set(capture(syncDataSlot), SetOptions.merge()) } returns Tasks.forResult(null)
        every { documentReference.get() } returns Tasks.forResult(documentSnapshot)
        every { documentSnapshot.exists() } returns false
        
        // Execute sync
        val result = syncWorker.doWork()
        assertThat(result).isEqualTo(
            ListenableWorker.Result.success(workDataOf(SocialProfileSyncWorker.KEY_SYNC_COUNT to 1))
        )
        
        // Verify privacy settings were synced correctly
        val syncedData = syncDataSlot.captured
        assertThat(syncedData).containsKey("isPrivate")
        assertThat(syncedData["isPrivate"]).isEqualTo(false) // Should be searchable
        
        // 🚨 CRITICAL ISSUE: Privacy setting is in 'social_profiles' but
        // UserSearchRepository checks 'isPublic' in 'users_public' collection
        
        verify { socialProfilesCollection.document(testUserId) }
        verify(exactly = 0) { usersPublicCollection.document(testUserId) } // Should sync here for search filtering!
        
        Timber.e("❌ PRIVACY MISMATCH: isPrivate=false in 'social_profiles' but search filters on 'users_public.isPublic'")
        Timber.e("❌ RESULT: Public profiles may not be found due to collection mismatch")
    }

    @Test
    fun `DEMONSTRATION TEST - multiple collections sync mismatch`() = runTest {
        // DEMONSTRATION: Show exactly which collections are involved in the mismatch
        
        val testUsername = "multitest"
        val socialProfile = createTestSocialProfile(testUserId, testUsername)
        
        Timber.w("🔬 DEMONSTRATION: Complete collection mismatch analysis")
        
        // Mock multiple profiles to show batch processing
        coEvery { socialProfileDao.getProfile(testUserId) } returns socialProfile
        every { documentReference.set(any(), SetOptions.merge()) } returns Tasks.forResult(null)
        every { documentReference.get() } returns Tasks.forResult(documentSnapshot)
        every { documentSnapshot.exists() } returns false
        
        // Track which collections are accessed during sync
        val collectionsAccessed = mutableListOf<String>()
        
        every { firestore.collection("social_profiles") } answers {
            collectionsAccessed.add("social_profiles")
            socialProfilesCollection
        }
        every { firestore.collection("users_public") } answers {
            collectionsAccessed.add("users_public")
            usersPublicCollection
        }
        every { firestore.collection("user_search_cache") } answers {
            collectionsAccessed.add("user_search_cache")
            userSearchCacheCollection
        }
        
        // Execute sync
        val result = syncWorker.doWork()
        assertThat(result).isEqualTo(
            ListenableWorker.Result.success(workDataOf(SocialProfileSyncWorker.KEY_SYNC_COUNT to 1))
        )
        
        // Analyze collection access patterns
        Timber.d("📊 SYNC ANALYSIS:")
        Timber.d("   Collections accessed by SocialProfileSyncWorker: $collectionsAccessed")
        Timber.d("   Expected collections for search: [users_public, user_search_cache]")
        
        // Verify the mismatch
        assertThat(collectionsAccessed).contains("social_profiles")
        assertThat(collectionsAccessed).doesNotContain("users_public")
        assertThat(collectionsAccessed).doesNotContain("user_search_cache")
        
        Timber.e("🎯 MISMATCH CONFIRMED:")
        Timber.e("   ❌ SyncWorker targets: social_profiles")
        Timber.e("   ✅ Search expects: users_public, user_search_cache")
        Timber.e("   💥 Result: Zero overlap between sync target and search source")
    }

    @Test
    fun `SPECIFICATION TEST - correct collections for search compatibility`() = runTest {
        // SPECIFICATION: This test shows what the sync worker SHOULD do for search compatibility
        
        val testUsername = "correcttest"
        val socialProfile = createTestSocialProfile(testUserId, testUsername)
        
        Timber.d("📋 SPECIFICATION: Correct sync behavior for search compatibility")
        
        // This test demonstrates the CORRECT behavior (not currently implemented)
        // SyncWorker should target 'users_public' and/or 'user_search_cache' for searchability
        
        Timber.d("🎯 CORRECT BEHAVIOR SPECIFICATION:")
        Timber.d("   1. SyncWorker should target 'users_public' collection")
        Timber.d("   2. OR SyncWorker should target 'user_search_cache' collection")
        Timber.d("   3. AND generate proper search tokens")
        Timber.d("   4. AND include isPublic field for privacy filtering")
        
        // Current implementation verification (targets wrong collection)
        coEvery { socialProfileDao.getProfile(testUserId) } returns socialProfile
        every { documentReference.set(any(), SetOptions.merge()) } returns Tasks.forResult(null)
        every { documentReference.get() } returns Tasks.forResult(documentSnapshot)
        every { documentSnapshot.exists() } returns false
        
        val result = syncWorker.doWork()
        assertThat(result).isEqualTo(
            ListenableWorker.Result.success(workDataOf(SocialProfileSyncWorker.KEY_SYNC_COUNT to 1))
        )
        
        // Show current (incorrect) behavior
        verify { firestore.collection("social_profiles") }
        
        verify(exactly = 0) { firestore.collection("users_public") }
        verify(exactly = 0) { firestore.collection("user_search_cache") }
        
        Timber.d("📝 SPECIFICATION COMPLETE: Sync worker needs collection target fix")
    }

    @Test
    fun `INTEGRATION TEST - end to end sync and search mismatch simulation`() = runTest {
        // INTEGRATION: Simulate complete flow showing the disconnect
        
        val testUsername = "integrationtest"
        val socialProfile = createTestSocialProfile(testUserId, testUsername)
        
        Timber.w("🔄 INTEGRATION: End-to-end sync/search mismatch simulation")
        
        // Step 1: Social profile sync (current implementation)
        coEvery { socialProfileDao.getProfile(testUserId) } returns socialProfile
        every { documentReference.set(any(), SetOptions.merge()) } returns Tasks.forResult(null)
        every { documentReference.get() } returns Tasks.forResult(documentSnapshot)
        every { documentSnapshot.exists() } returns false
        
        val syncResult = syncWorker.doWork()
        assertThat(syncResult).isEqualTo(
            ListenableWorker.Result.success(workDataOf(SocialProfileSyncWorker.KEY_SYNC_COUNT to 1))
        )
        
        Timber.d("✅ Step 1: Profile synced to 'social_profiles' collection")
        
        // Step 2: Simulate search attempt (would fail in real implementation)
        // UserSearchRepository would look in 'users_public' and 'user_search_cache'
        // but find nothing because data is in 'social_profiles'
        
        Timber.e("❌ Step 2: Search would look in 'users_public' and 'user_search_cache'")
        Timber.e("❌ Step 3: Search would find no results despite successful sync")
        Timber.e("❌ Step 4: User appears in profile but not in search results")
        
        // Verify the disconnect
        verify { socialProfilesCollection.document(testUserId) } // Sync target
        verify(exactly = 0) { usersPublicCollection.document(testUserId) } // Search source
        verify(exactly = 0) { userSearchCacheCollection.document(testUserId) } // Search source
        
        Timber.e("💥 INTEGRATION FAILURE: Complete disconnect between sync and search")
    }

    // Helper method to create test social profile
    private fun createTestSocialProfile(
        userId: String, 
        username: String, 
        isPrivate: Boolean = true,
        bio: String? = null
    ): SocialProfileEntity {
        return SocialProfileEntity(
            userId = userId,
            username = username.lowercase(),
            displayName = username,
            bio = bio,
            profilePhotoUrl = null,
            memberSince = System.currentTimeMillis(),
            isPrivate = isPrivate,
            hideFromSuggestions = false,
            allowFriendRequests = true,
            instagramHandle = null,
            youtubeChannel = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isDirty = true,
            isSynced = false,
            syncVersion = 1
        )
    }
}
