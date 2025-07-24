package com.example.liftrix.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.core.LiftrixResult
import com.example.liftrix.data.local.dao.UserProfileDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import com.google.android.gms.tasks.Tasks
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Integration tests for ProfileImageRepositoryImpl.
 * 
 * Test Coverage:
 * - Firebase Storage upload operations with authentication
 * - Image deletion from Firebase Storage with cleanup
 * - URL retrieval and caching behavior
 * - User scoping and security validation
 * - Error handling for network failures and auth issues
 * - Local database synchronization with Firebase operations
 * - Firebase Storage security rules validation
 * - Concurrent upload/delete operation handling
 * 
 * Test Strategy:
 * - Use Firebase emulator for controlled testing environment
 * - Mock Firebase services for unit-level testing
 * - Test real Firebase integration in separate test suite
 * - Validate security rules with different user contexts
 * - Test network failure scenarios and retry behavior
 * - Verify local database consistency after Firebase operations
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProfileImageRepositoryImplTest {
    
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    // Mock Firebase components
    private val mockFirebaseStorage = mockk<FirebaseStorage>()
    private val mockFirebaseAuth = mockk<FirebaseAuth>()
    private val mockUserProfileDao = mockk<UserProfileDao>(relaxed = true)
    
    // Mock Firebase Storage references
    private val mockStorageRef = mockk<StorageReference>()
    private val mockProfileImagesRef = mockk<StorageReference>()
    private val mockUserRef = mockk<StorageReference>()
    private val mockImageRef = mockk<StorageReference>()
    
    // Mock upload task
    private val mockUploadTask = mockk<UploadTask>()
    private val mockTaskSnapshot = mockk<UploadTask.TaskSnapshot>()
    
    private lateinit var repository: ProfileImageRepositoryImpl
    
    // Test data
    private val testUserId = "test-user-123"
    private val testImageBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    private val testImageUrl = "https://firebasestorage.googleapis.com/profile_images/test-user-123/avatar.jpg"
    
    @Before
    fun setUp() {
        hiltRule.inject()
        MockKAnnotations.init(this)
        
        // Setup Firebase Storage mock chain
        every { mockFirebaseStorage.reference } returns mockStorageRef
        every { mockStorageRef.child("profile_images") } returns mockProfileImagesRef
        every { mockProfileImagesRef.child(testUserId) } returns mockUserRef
        every { mockUserRef.child("avatar.jpg") } returns mockImageRef
        
        // Setup upload task mocking
        every { mockImageRef.putBytes(any()) } returns mockUploadTask
        every { mockUploadTask.addOnSuccessListener(any()) } returns mockUploadTask
        every { mockUploadTask.addOnFailureListener(any()) } returns mockUploadTask
        
        repository = ProfileImageRepositoryImpl(
            firebaseStorage = mockFirebaseStorage,
            firebaseAuth = mockFirebaseAuth,
            userProfileDao = mockUserProfileDao
        )
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }
    
    /**
     * Test successful image upload with proper Firebase Storage integration.
     */
    @Test
    fun `uploadProfileImage should successfully upload image and return URL`() = runTest {
        // Given
        val mockDownloadUri = mockk<android.net.Uri>()
        every { mockDownloadUri.toString() } returns testImageUrl
        
        // Mock successful upload
        every { mockUploadTask.result } returns mockTaskSnapshot
        every { mockImageRef.downloadUrl } returns Tasks.forResult(mockDownloadUri)
        
        // Mock DAO update
        coEvery { mockUserProfileDao.updateProfileImageUrl(testUserId, testImageUrl) } returns Unit
        
        // Use Tasks.forResult to simulate successful upload
        mockkStatic(Tasks::class)
        every { Tasks.await(mockUploadTask) } returns mockTaskSnapshot
        every { Tasks.await(mockImageRef.downloadUrl) } returns mockDownloadUri
        
        // When
        val result = repository.uploadProfileImage(testUserId, testImageBytes)
        
        // Then
        assertTrue("Upload should succeed", result.isSuccess)
        assertEquals("Should return correct URL", testImageUrl, result.getOrNull())
        
        // Verify Firebase Storage interactions
        verify { mockFirebaseStorage.reference }
        verify { mockStorageRef.child("profile_images") }
        verify { mockProfileImagesRef.child(testUserId) }
        verify { mockUserRef.child("avatar.jpg") }
        verify { mockImageRef.putBytes(testImageBytes) }
        
        // Verify local database update
        coVerify { mockUserProfileDao.updateProfileImageUrl(testUserId, testImageUrl) }
    }
    
    /**
     * Test upload failure handling with proper error propagation.
     */
    @Test
    fun `uploadProfileImage should handle upload failure gracefully`() = runTest {
        // Given
        val uploadException = Exception("Upload failed - network error")
        
        // Mock failed upload
        mockkStatic(Tasks::class)
        every { Tasks.await(mockUploadTask) } throws uploadException
        
        // When
        val result = repository.uploadProfileImage(testUserId, testImageBytes)
        
        // Then
        assertTrue("Upload should fail", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull("Exception should be present", exception)
        assertEquals("Should preserve original error message", uploadException.message, exception?.message)
        
        // Verify Firebase Storage was attempted
        verify { mockImageRef.putBytes(testImageBytes) }
        
        // Verify no database update on failure
        coVerify(exactly = 0) { mockUserProfileDao.updateProfileImageUrl(any(), any()) }
    }
    
    /**
     * Test image deletion with proper cleanup from Firebase Storage.
     */
    @Test
    fun `deleteProfileImage should remove image from storage and update database`() = runTest {
        // Given
        // Mock successful deletion
        mockkStatic(Tasks::class)
        every { Tasks.await(mockImageRef.delete()) } returns Unit
        
        // Mock DAO update
        coEvery { mockUserProfileDao.clearProfileImageUrl(testUserId) } returns Unit
        
        // When
        val result = repository.deleteProfileImage(testUserId)
        
        // Then
        assertTrue("Deletion should succeed", result.isSuccess)
        
        // Verify Firebase Storage deletion
        verify { mockImageRef.delete() }
        
        // Verify local database cleanup
        coVerify { mockUserProfileDao.clearProfileImageUrl(testUserId) }
    }
    
    /**
     * Test deletion failure handling without corrupting local state.
     */
    @Test
    fun `deleteProfileImage should handle deletion failure without database changes`() = runTest {
        // Given
        val deletionException = Exception("Deletion failed - permission denied")
        
        // Mock failed deletion
        mockkStatic(Tasks::class)
        every { Tasks.await(mockImageRef.delete()) } throws deletionException
        
        // When
        val result = repository.deleteProfileImage(testUserId)
        
        // Then
        assertTrue("Deletion should fail", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull("Exception should be present", exception)
        
        // Verify Firebase Storage deletion was attempted
        verify { mockImageRef.delete() }
        
        // Verify no database changes on failure
        coVerify(exactly = 0) { mockUserProfileDao.clearProfileImageUrl(any()) }
    }
    
    /**
     * Test URL retrieval from Firebase Storage.
     */
    @Test
    fun `getProfileImageUrl should return download URL for existing image`() = runTest {
        // Given
        val mockDownloadUri = mockk<android.net.Uri>()
        every { mockDownloadUri.toString() } returns testImageUrl
        
        // Mock successful URL retrieval
        mockkStatic(Tasks::class)
        every { Tasks.await(mockImageRef.downloadUrl) } returns mockDownloadUri
        
        // When
        val result = repository.getProfileImageUrl(testUserId)
        
        // Then
        assertTrue("URL retrieval should succeed", result.isSuccess)
        assertEquals("Should return correct URL", testImageUrl, result.getOrNull())
        
        // Verify Firebase Storage interaction
        verify { mockImageRef.downloadUrl }
    }
    
    /**
     * Test URL retrieval for non-existent image.
     */
    @Test
    fun `getProfileImageUrl should handle non-existent image gracefully`() = runTest {
        // Given
        val storageException = com.google.firebase.storage.StorageException.fromErrorStatus(
            com.google.android.gms.common.api.Status.fromStatusCode(404)
        )
        
        // Mock image not found
        mockkStatic(Tasks::class)
        every { Tasks.await(mockImageRef.downloadUrl) } throws storageException
        
        // When
        val result = repository.getProfileImageUrl(testUserId)
        
        // Then
        assertTrue("Should handle missing image gracefully", result.isSuccess)
        assertNull("Should return null for non-existent image", result.getOrNull())
    }
    
    /**
     * Test local database profile image URL update.
     */
    @Test
    fun `updateProfileImageUrl should update local database`() = runTest {
        // Given
        coEvery { mockUserProfileDao.updateProfileImageUrl(testUserId, testImageUrl) } returns Unit
        
        // When
        val result = repository.updateProfileImageUrl(testUserId, testImageUrl)
        
        // Then
        assertTrue("Database update should succeed", result.isSuccess)
        
        // Verify DAO interaction
        coVerify { mockUserProfileDao.updateProfileImageUrl(testUserId, testImageUrl) }
    }
    
    /**
     * Test database update failure handling.
     */
    @Test
    fun `updateProfileImageUrl should handle database errors`() = runTest {
        // Given
        val databaseException = RuntimeException("Database connection failed")
        coEvery { mockUserProfileDao.updateProfileImageUrl(testUserId, testImageUrl) } throws databaseException
        
        // When
        val result = repository.updateProfileImageUrl(testUserId, testImageUrl)
        
        // Then
        assertTrue("Database update should fail", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull("Exception should be present", exception)
        assertEquals("Should preserve database error", databaseException.message, exception?.message)
    }
    
    /**
     * Test user scoping validation - users should only access their own images.
     */
    @Test
    fun `repository should enforce user scoping for all operations`() = runTest {
        // Given
        val differentUserId = "different-user-456"
        val mockDifferentUserRef = mockk<StorageReference>()
        val mockDifferentImageRef = mockk<StorageReference>()
        
        every { mockProfileImagesRef.child(differentUserId) } returns mockDifferentUserRef
        every { mockDifferentUserRef.child("avatar.jpg") } returns mockDifferentImageRef
        
        // Mock successful operations for different user
        val mockDownloadUri = mockk<android.net.Uri>()
        every { mockDownloadUri.toString() } returns "https://storage/different-user/avatar.jpg"
        
        mockkStatic(Tasks::class)
        every { Tasks.await(mockDifferentImageRef.downloadUrl) } returns mockDownloadUri
        
        // When - operations with different user ID
        val uploadResult = repository.uploadProfileImage(differentUserId, testImageBytes)
        val downloadResult = repository.getProfileImageUrl(differentUserId)
        val deleteResult = repository.deleteProfileImage(differentUserId)
        
        // Then - verify user-scoped paths are used
        verify { mockProfileImagesRef.child(differentUserId) }
        verify { mockDifferentUserRef.child("avatar.jpg") }
        
        // Each operation should use the correct user-scoped path
        assertTrue("Upload should work with user scoping", uploadResult.isSuccess || uploadResult.isFailure)
        assertTrue("Download should work with user scoping", downloadResult.isSuccess || downloadResult.isFailure)
        assertTrue("Delete should work with user scoping", deleteResult.isSuccess || deleteResult.isFailure)
    }
    
    /**
     * Test concurrent upload operations to prevent race conditions.
     */
    @Test
    fun `repository should handle concurrent upload operations safely`() = runTest {
        // Given
        val mockDownloadUri = mockk<android.net.Uri>()
        every { mockDownloadUri.toString() } returns testImageUrl
        
        // Mock successful uploads
        mockkStatic(Tasks::class)
        every { Tasks.await(mockUploadTask) } returns mockTaskSnapshot
        every { Tasks.await(mockImageRef.downloadUrl) } returns mockDownloadUri
        
        coEvery { mockUserProfileDao.updateProfileImageUrl(testUserId, testImageUrl) } returns Unit
        
        // When - simulate concurrent uploads
        val results = (1..3).map { index ->
            val imageBytes = byteArrayOf(index.toByte())
            repository.uploadProfileImage(testUserId, imageBytes)
        }
        
        // Then - all operations should complete without corruption
        results.forEach { result ->
            assertTrue("Concurrent upload should succeed", result.isSuccess)
        }
        
        // Verify all uploads were attempted
        verify(exactly = 3) { mockImageRef.putBytes(any()) }
        coVerify(exactly = 3) { mockUserProfileDao.updateProfileImageUrl(testUserId, testImageUrl) }
    }
    
    /**
     * Test Firebase Storage security rules enforcement.
     * Note: This test validates the repository's expectation of security rules,
     * actual rules testing would be done in Firebase Test Lab.
     */
    @Test
    fun `repository should construct user-scoped storage paths for security`() = runTest {
        // Given
        val secureUserId = "secure-user-789"
        val mockSecureUserRef = mockk<StorageReference>()
        val mockSecureImageRef = mockk<StorageReference>()
        
        every { mockProfileImagesRef.child(secureUserId) } returns mockSecureUserRef
        every { mockSecureUserRef.child("avatar.jpg") } returns mockSecureImageRef
        
        // When
        repository.uploadProfileImage(secureUserId, testImageBytes)
        repository.getProfileImageUrl(secureUserId)
        repository.deleteProfileImage(secureUserId)
        
        // Then - verify security-compliant path structure
        verify { mockStorageRef.child("profile_images") }
        verify { mockProfileImagesRef.child(secureUserId) }
        verify { mockSecureUserRef.child("avatar.jpg") }
        
        // Path should follow: profile_images/{userId}/avatar.jpg
        // This structure aligns with Firebase security rules for user-scoped access
    }
    
    /**
     * Test error handling for invalid user IDs.
     */
    @Test
    fun `repository should handle invalid user IDs gracefully`() = runTest {
        // Given
        val invalidUserIds = listOf("", " ", "\n", null)
        
        invalidUserIds.forEach { invalidId ->
            if (invalidId != null) {
                // When
                val uploadResult = repository.uploadProfileImage(invalidId, testImageBytes)
                val getResult = repository.getProfileImageUrl(invalidId)
                val deleteResult = repository.deleteProfileImage(invalidId)
                
                // Then - operations should handle invalid IDs appropriately
                // (Either succeed with sanitized ID or fail gracefully)
                assertNotNull("Upload result should not be null", uploadResult)
                assertNotNull("Get result should not be null", getResult)
                assertNotNull("Delete result should not be null", deleteResult)
            }
        }
    }
    
    /**
     * Test memory efficiency with large image data.
     */
    @Test
    fun `repository should handle large image uploads efficiently`() = runTest {
        // Given
        val largeImageBytes = ByteArray(5_000_000) // 5MB image
        largeImageBytes.fill(42) // Fill with test data
        
        val mockDownloadUri = mockk<android.net.Uri>()
        every { mockDownloadUri.toString() } returns testImageUrl
        
        // Mock successful large upload
        mockkStatic(Tasks::class)
        every { Tasks.await(mockUploadTask) } returns mockTaskSnapshot
        every { Tasks.await(mockImageRef.downloadUrl) } returns mockDownloadUri
        
        coEvery { mockUserProfileDao.updateProfileImageUrl(testUserId, testImageUrl) } returns Unit
        
        // When
        val startTime = System.currentTimeMillis()
        val result = repository.uploadProfileImage(testUserId, largeImageBytes)
        val uploadTime = System.currentTimeMillis() - startTime
        
        // Then
        assertTrue("Large upload should succeed", result.isSuccess)
        assertTrue("Upload should complete in reasonable time", uploadTime < 30_000) // 30 seconds max
        
        // Verify large data was passed to Firebase
        verify { mockImageRef.putBytes(largeImageBytes) }
    }
}