package com.example.liftrix

import com.example.liftrix.domain.model.AuthEvent
import com.example.liftrix.domain.model.AuthState
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.SubscriptionTier
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.usecase.auth.ForgotPasswordUseCase
import com.example.liftrix.domain.usecase.auth.SignInAnonymouslyUseCase
import com.example.liftrix.domain.usecase.auth.SignInWithEmailUseCase
import com.example.liftrix.domain.usecase.auth.SignInWithGoogleUseCase
import com.example.liftrix.domain.usecase.auth.SignOutUseCase
import com.example.liftrix.domain.usecase.auth.SignUpWithEmailUseCase
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.ui.auth.AuthViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var signInWithEmailUseCase: SignInWithEmailUseCase
    private lateinit var signUpWithEmailUseCase: SignUpWithEmailUseCase
    private lateinit var signInWithGoogleUseCase: SignInWithGoogleUseCase
    private lateinit var signInAnonymouslyUseCase: SignInAnonymouslyUseCase
    private lateinit var signOutUseCase: SignOutUseCase
    private lateinit var forgotPasswordUseCase: ForgotPasswordUseCase
    private lateinit var authRepository: AuthRepository
    private lateinit var authViewModel: AuthViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        signInWithEmailUseCase = mockk()
        signUpWithEmailUseCase = mockk()
        signInWithGoogleUseCase = mockk()
        signInAnonymouslyUseCase = mockk()
        signOutUseCase = mockk()
        forgotPasswordUseCase = mockk()
        authRepository = mockk()
        
        authViewModel = AuthViewModel(
            signInWithEmailUseCase = signInWithEmailUseCase,
            signUpWithEmailUseCase = signUpWithEmailUseCase,
            signInWithGoogleUseCase = signInWithGoogleUseCase,
            signInAnonymouslyUseCase = signInAnonymouslyUseCase,
            signOutUseCase = signOutUseCase,
            forgotPasswordUseCase = forgotPasswordUseCase,
            authRepository = authRepository
        )
    }

    @Test
    fun `initial state should be Initial`() {
        assertEquals(AuthState.Initial, authViewModel.authState.value)
    }

    @Test
    fun `handleEvent with EmailPasswordSignIn should call signInWithEmailUseCase and update state on success`() = runTest {
        // Given
        val email = "test@example.com"
        val password = "password123"
        val mockUser = User(
            uid = "test-uid",
            email = email,
            displayName = "Test User",
            photoUrl = null,
            isAnonymous = false,
            subscriptionTier = SubscriptionTier.FREE,
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            subscriptionExpiresAt = null,
            premiumFeaturesEnabled = false,
            onboardingCompleted = true,
            profileVersion = 1L,
            createdAt = LocalDateTime.now(),
            lastSignInAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        coEvery { signInWithEmailUseCase(email, password) } returns Result.success(mockUser)

        // When
        authViewModel.handleEvent(AuthEvent.EmailPasswordSignIn(email, password))
        advanceUntilIdle()

        // Then
        val finalState = authViewModel.authState.value
        assertTrue(finalState is AuthState.Authenticated)
        assertEquals(mockUser, (finalState as AuthState.Authenticated).user)
    }

    @Test
    fun `handleEvent with EmailPasswordSignIn should update state to Error on failure`() = runTest {
        // Given
        val email = "test@example.com"
        val password = "wrongpassword"
        val exception = Exception("Invalid credentials")
        
        coEvery { signInWithEmailUseCase(email, password) } returns Result.failure(exception)

        // When
        authViewModel.handleEvent(AuthEvent.EmailPasswordSignIn(email, password))
        advanceUntilIdle()

        // Then
        val finalState = authViewModel.authState.value
        assertTrue(finalState is AuthState.Error)
        assertTrue((finalState as AuthState.Error).message.isNotEmpty())
    }

    @Test
    fun `handleEvent with AnonymousSignIn should call signInAnonymouslyUseCase and update state on success`() = runTest {
        // Given
        val mockUser = User(
            uid = "anonymous-uid",
            email = "",
            displayName = null,
            photoUrl = null,
            isAnonymous = true,
            subscriptionTier = SubscriptionTier.FREE,
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            subscriptionExpiresAt = null,
            premiumFeaturesEnabled = false,
            onboardingCompleted = false,
            profileVersion = 1L,
            createdAt = LocalDateTime.now(),
            lastSignInAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        coEvery { signInAnonymouslyUseCase() } returns Result.success(mockUser)

        // When
        authViewModel.handleEvent(AuthEvent.AnonymousSignIn)
        advanceUntilIdle()

        // Then
        val finalState = authViewModel.authState.value
        assertTrue(finalState is AuthState.Authenticated)
        assertEquals(mockUser, (finalState as AuthState.Authenticated).user)
    }

    @Test
    fun `handleEvent with SignOut should call signOutUseCase and update state to Unauthenticated on success`() = runTest {
        // Given
        coEvery { signOutUseCase() } returns Result.success(Unit)

        // When
        authViewModel.handleEvent(AuthEvent.SignOut)
        advanceUntilIdle()

        // Then
        assertEquals(AuthState.Unauthenticated, authViewModel.authState.value)
    }
} 