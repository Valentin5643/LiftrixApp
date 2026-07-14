package com.example.liftrix.ui.auth

import com.example.liftrix.core.identity.UserId
import com.example.liftrix.domain.model.AuthEvent
import com.example.liftrix.domain.model.AuthState
import com.example.liftrix.domain.model.ConsentChoices
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.service.ConsentManagementService
import com.example.liftrix.domain.usecase.auth.AuthCommandUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthViewModelConsentTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `failed consent persistence deletes only the account created by this sign-up`() = runTest(dispatcher) {
        val user = User.forAuthentication(
            uid = "new-user-id",
            email = "new@example.com"
        )
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(
            authCommandUseCase = FakeAuthCommandUseCase(user),
            authRepository = repository,
            consentManagementService = FailingConsentService
        )

        viewModel.handleEvent(
            AuthEvent.EmailPasswordSignUpWithConsent(
                email = user.email,
                password = "valid-password",
                username = "New User",
                consents = ConsentChoices(true, true, true, false)
            )
        )
        advanceUntilIdle()

        assertEquals(1, repository.deleteAccountCalls)
        assertTrue(viewModel.authState.value is AuthState.Error)
    }

    private class FakeAuthCommandUseCase(private val user: User) : AuthCommandUseCase {
        override suspend fun signUpWithEmail(email: String, password: String, username: String) =
            LiftrixResult.success(user)
        override suspend fun signInWithEmail(email: String, password: String) = unsupported<User>()
        override suspend fun signInWithGoogle(idToken: String) = unsupported<User>()
        override suspend fun signOut() = LiftrixResult.success(Unit)
        override suspend fun signOutEnhanced() = LiftrixResult.success(Unit)
        override suspend fun resetPassword(email: String) = unsupported<Unit>()
    }

    private class FakeAuthRepository : AuthRepository {
        override val currentUser = MutableStateFlow<User?>(null)
        var deleteAccountCalls = 0
        override suspend fun deleteAccount(): LiftrixResult<Unit> {
            deleteAccountCalls++
            return LiftrixResult.success(Unit)
        }
        override suspend fun getCurrentUser(): User? = currentUser.value
        override suspend fun getCurrentUserId(): UserId? = null
        override fun observeAuthState(): Flow<UserId?> = MutableStateFlow(null)
        override suspend fun signInWithEmail(email: String, password: String) = unsupported<User>()
        override suspend fun signUpWithEmail(email: String, password: String, displayName: String) = unsupported<User>()
        override suspend fun signInWithGoogle(idToken: String) = unsupported<User>()
        override suspend fun signOut() = LiftrixResult.success(Unit)
        override suspend fun sendPasswordResetEmail(email: String) = unsupported<Unit>()
        override suspend fun createUserProfile(user: User) = unsupported<Unit>()
        override suspend fun getUserProfile(uid: UserId) = unsupported<User?>()
        override suspend fun reauthenticate(password: String) = unsupported<Unit>()
        override suspend fun updateEmail(newEmail: String) = unsupported<Unit>()
        override suspend fun updatePassword(currentPassword: String, newPassword: String) = unsupported<Unit>()
    }

    private object FailingConsentService : ConsentManagementService {
        override suspend fun recordConsent(
            userId: String,
            privacyPolicyVersion: String,
            healthDataConsent: Boolean,
            aiChatConsent: Boolean,
            analyticsConsent: Boolean,
            marketingConsent: Boolean
        ): LiftrixResult<Unit> = LiftrixResult.failure(IllegalStateException("database unavailable"))
    }

    private companion object {
        fun <T> unsupported(): LiftrixResult<T> =
            LiftrixResult.failure(UnsupportedOperationException("not used"))
    }
}
