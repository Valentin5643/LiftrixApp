package com.example.liftrix.domain.session

import com.example.liftrix.domain.model.UserId
import com.example.liftrix.domain.model.error.LiftrixError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Minimal unit tests for UserSession.
 */
class UserSessionTest {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var userSession: UserSession

    @Before
    fun setup() {
        firebaseAuth = mockk(relaxed = true)
        userSession = UserSession(firebaseAuth)
    }

    @Test
    fun `currentUserId returns UserId when authenticated`() {
        // Given
        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "test-uid-123"
        every { firebaseAuth.currentUser } returns mockUser

        // When
        val userId = userSession.currentUserId

        // Then
        assertNotNull(userId)
        assertEquals("test-uid-123", userId?.value)
    }

    @Test
    fun `currentUserId returns null when not authenticated`() {
        // Given
        every { firebaseAuth.currentUser } returns null

        // When
        val userId = userSession.currentUserId

        // Then
        assertNull(userId)
    }

    @Test
    fun `requireUserId returns UserId when authenticated`() = runTest {
        // Given
        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "test-uid-456"
        every { firebaseAuth.currentUser } returns mockUser

        // When
        val userId = userSession.requireUserId()

        // Then
        assertEquals("test-uid-456", userId.value)
    }

    @Test
    fun `requireUserId throws UnauthenticatedError when not authenticated`() = runTest {
        // Given
        every { firebaseAuth.currentUser } returns null

        // When/Then
        try {
            userSession.requireUserId()
            throw AssertionError("Expected UnauthenticatedError")
        } catch (e: LiftrixError.UnauthenticatedError) {
            assertTrue(e.errorMessage.contains("authenticated"))
        }
    }

    @Test
    fun `isAuthenticated returns true when user is authenticated`() {
        // Given
        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "test-uid-789"
        every { firebaseAuth.currentUser } returns mockUser

        // When
        val isAuthenticated = userSession.isAuthenticated()

        // Then
        assertTrue(isAuthenticated)
    }

    @Test
    fun `isAuthenticated returns false when user is not authenticated`() {
        // Given
        every { firebaseAuth.currentUser } returns null

        // When
        val isAuthenticated = userSession.isAuthenticated()

        // Then
        assertFalse(isAuthenticated)
    }
}
