package com.example.liftrix.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive security test suite for UserIdValidator.
 * 
 * This test suite validates:
 * - Cross-user data isolation
 * - User context validation across all major workflows
 * - Firebase authentication integration
 * - Widget user scoping security
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UserIdSecurityTest {
    
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var userIdValidator: UserIdValidator
    
    private lateinit var mockFirebaseAuth: FirebaseAuth
    private lateinit var mockGetCurrentUserIdUseCase: GetCurrentUserIdUseCase
    private lateinit var testValidator: UserIdValidator
    
    companion object {
        private const val VALID_USER_ID_1 = "firebase_uid_1234567890abcdef"
        private const val VALID_USER_ID_2 = "firebase_uid_0987654321fedcba"
        private const val INVALID_SHORT_ID = "short"
        private const val HARDCODED_PLACEHOLDER = "current_user"
    }
    
    @Before
    fun setup() {
        hiltRule.inject()
        
        // Create mocks for isolated testing
        mockFirebaseAuth = mockk()
        mockGetCurrentUserIdUseCase = mockk()
        
        testValidator = UserIdValidator(
            firebaseAuth = mockFirebaseAuth,
            getCurrentUserIdUseCase = mockGetCurrentUserIdUseCase
        )
    }
    
    @Test
    fun validateCurrentUser_withAuthenticatedUser_returnsSuccess() = runTest {
        // Given
        val mockFirebaseUser = mockk<FirebaseUser>()
        every { mockFirebaseUser.uid } returns VALID_USER_ID_1
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        coEvery { mockGetCurrentUserIdUseCase() } returns VALID_USER_ID_1
        
        // When
        val result = testValidator.validateCurrentUser()
        
        // Then
        assertIs<Result.success<String>>(result)
        assertEquals(VALID_USER_ID_1, result.data)
    }
    
    @Test
    fun validateCurrentUser_withUnauthenticatedUser_returnsError() = runTest {
        // Given
        every { mockFirebaseAuth.currentUser } returns null
        coEvery { mockGetCurrentUserIdUseCase() } returns null
        
        // When
        val result = testValidator.validateCurrentUser()
        
        // Then
        assertIs<Result.failure>(result)
        assertIs<LiftrixError.UserNotAuthenticated>(result.error)
        assertTrue(result.error.errorMessage.contains("not authenticated"))
    }
    
    @Test
    fun validateCurrentUser_withInconsistentAuthState_returnsError() = runTest {
        // Given
        val mockFirebaseUser = mockk<FirebaseUser>()
        every { mockFirebaseUser.uid } returns VALID_USER_ID_1
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        coEvery { mockGetCurrentUserIdUseCase() } returns VALID_USER_ID_2 // Different ID!
        
        // When
        val result = testValidator.validateCurrentUser()
        
        // Then
        assertIs<Result.failure>(result)
        assertIs<LiftrixError.AuthenticationError>(result.error)
        assertTrue(result.error.errorMessage.contains("inconsistent"))
    }
    
    @Test
    fun validateUserContext_withMatchingUserId_returnsSuccess() = runTest {
        // Given
        val mockFirebaseUser = mockk<FirebaseUser>()
        every { mockFirebaseUser.uid } returns VALID_USER_ID_1
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        coEvery { mockGetCurrentUserIdUseCase() } returns VALID_USER_ID_1
        
        // When
        val result = testValidator.validateUserContext(VALID_USER_ID_1, "test_operation")
        
        // Then
        assertIs<Result.success<Unit>>(result)
    }
    
    @Test
    fun validateUserContext_withDifferentUserId_returnsUnauthorizedError() = runTest {
        // Given
        val mockFirebaseUser = mockk<FirebaseUser>()
        every { mockFirebaseUser.uid } returns VALID_USER_ID_1
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        coEvery { mockGetCurrentUserIdUseCase() } returns VALID_USER_ID_1
        
        // When
        val result = testValidator.validateUserContext(VALID_USER_ID_2, "test_operation")
        
        // Then
        assertIs<Result.failure>(result)
        assertIs<LiftrixError.UnauthorizedAccess>(result.error)
        assertTrue(result.error.errorMessage.contains("cannot access data"))
        assertTrue(result.error.errorMessage.contains("test_operation"))
    }
    
    @Test
    fun validateUserIdFormat_withValidId_returnsSuccess() {
        // When
        val result = testValidator.validateUserIdFormat(VALID_USER_ID_1)
        
        // Then
        assertIs<Result.success<Unit>>(result)
    }
    
    @Test
    fun validateUserIdFormat_withBlankId_returnsValidationError() {
        // When
        val result = testValidator.validateUserIdFormat("")
        
        // Then
        assertIs<Result.failure>(result)
        assertIs<LiftrixError.ValidationError>(result.error)
        assertTrue(result.error.errorMessage.contains("blank"))
    }
    
    @Test
    fun validateUserIdFormat_withHardcodedPlaceholder_returnsValidationError() {
        // When
        val result = testValidator.validateUserIdFormat(HARDCODED_PLACEHOLDER)
        
        // Then
        assertIs<Result.failure>(result)
        assertIs<LiftrixError.ValidationError>(result.error)
        assertTrue(result.error.errorMessage.contains("Hardcoded 'current_user' placeholder"))
    }
    
    @Test
    fun validateUserIdFormat_withShortId_returnsValidationError() {
        // When
        val result = testValidator.validateUserIdFormat(INVALID_SHORT_ID)
        
        // Then
        assertIs<Result.failure>(result)
        assertIs<LiftrixError.ValidationError>(result.error)
        assertTrue(result.error.errorMessage.contains("too short"))
    }
    
    @Test
    fun getCurrentValidatedUserId_withAuthenticatedUser_returnsUserId() = runTest {
        // Given
        val mockFirebaseUser = mockk<FirebaseUser>()
        every { mockFirebaseUser.uid } returns VALID_USER_ID_1
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        coEvery { mockGetCurrentUserIdUseCase() } returns VALID_USER_ID_1
        
        // When
        val userId = testValidator.getCurrentValidatedUserId()
        
        // Then
        assertEquals(VALID_USER_ID_1, userId)
    }
    
    @Test
    fun getCurrentValidatedUserId_withUnauthenticatedUser_returnsNull() = runTest {
        // Given
        every { mockFirebaseAuth.currentUser } returns null
        coEvery { mockGetCurrentUserIdUseCase() } returns null
        
        // When
        val userId = testValidator.getCurrentValidatedUserId()
        
        // Then
        assertNull(userId)
    }
    
    @Test
    fun isCurrentUserAuthenticated_withAuthenticatedUser_returnsTrue() = runTest {
        // Given
        val mockFirebaseUser = mockk<FirebaseUser>()
        every { mockFirebaseUser.uid } returns VALID_USER_ID_1
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        coEvery { mockGetCurrentUserIdUseCase() } returns VALID_USER_ID_1
        
        // When
        val isAuthenticated = testValidator.isCurrentUserAuthenticated()
        
        // Then
        assertTrue(isAuthenticated)
    }
    
    @Test
    fun isCurrentUserAuthenticated_withUnauthenticatedUser_returnsFalse() = runTest {
        // Given
        every { mockFirebaseAuth.currentUser } returns null
        coEvery { mockGetCurrentUserIdUseCase() } returns null
        
        // When
        val isAuthenticated = testValidator.isCurrentUserAuthenticated()
        
        // Then
        assertEquals(false, isAuthenticated)
    }
    
    /**
     * Cross-user data isolation test - simulates attempting to access 
     * another user's data and verifies it's blocked.
     */
    @Test
    fun crossUserDataIsolationTest_preventsUnauthorizedAccess() = runTest {
        // Given - User 1 is authenticated
        val mockFirebaseUser = mockk<FirebaseUser>()
        every { mockFirebaseUser.uid } returns VALID_USER_ID_1
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        coEvery { mockGetCurrentUserIdUseCase() } returns VALID_USER_ID_1
        
        // When - User 1 tries to access User 2's data
        val workoutAccessResult = testValidator.validateUserContext(VALID_USER_ID_2, "access_workout_data")
        val settingsAccessResult = testValidator.validateUserContext(VALID_USER_ID_2, "access_user_settings")
        val analyticsAccessResult = testValidator.validateUserContext(VALID_USER_ID_2, "access_analytics_data")
        
        // Then - All access attempts should be blocked
        assertIs<Result.failure>(workoutAccessResult)
        assertIs<LiftrixError.UnauthorizedAccess>(workoutAccessResult.error)
        
        assertIs<Result.failure>(settingsAccessResult)
        assertIs<LiftrixError.UnauthorizedAccess>(settingsAccessResult.error)
        
        assertIs<Result.failure>(analyticsAccessResult)
        assertIs<LiftrixError.UnauthorizedAccess>(analyticsAccessResult.error)
    }
    
    /**
     * Widget user scoping security test - ensures widgets can only access
     * data for the authenticated user.
     */
    @Test
    fun widgetUserScopingTest_ensuresProperDataIsolation() = runTest {
        // Given - User is authenticated
        val mockFirebaseUser = mockk<FirebaseUser>()
        every { mockFirebaseUser.uid } returns VALID_USER_ID_1
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        coEvery { mockGetCurrentUserIdUseCase() } returns VALID_USER_ID_1
        
        // When - Widget tries to access correct user data
        val validWidgetAccess = testValidator.validateUserContext(VALID_USER_ID_1, "widget_data_access")
        
        // When - Widget tries to access wrong user data
        val invalidWidgetAccess = testValidator.validateUserContext(VALID_USER_ID_2, "widget_data_access")
        
        // Then
        assertIs<Result.success<Unit>>(validWidgetAccess)
        assertIs<Result.failure>(invalidWidgetAccess)
        assertIs<LiftrixError.UnauthorizedAccess>(invalidWidgetAccess.error)
    }
}