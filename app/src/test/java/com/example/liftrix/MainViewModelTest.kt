package com.example.liftrix

import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.admin.CheckAdminPermissionsUseCase
import com.example.liftrix.domain.usecase.profile.ProfileQueryUseCase
import com.example.liftrix.test.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: AuthRepository
    private lateinit var profileQueryUseCase: ProfileQueryUseCase
    private lateinit var checkAdminPermissionsUseCase: CheckAdminPermissionsUseCase
    private lateinit var currentUser: MutableStateFlow<User?>

    @Before
    fun setUp() {
        authRepository = mockk(relaxed = true)
        profileQueryUseCase = mockk()
        checkAdminPermissionsUseCase = mockk()
        currentUser = MutableStateFlow(null)

        every { authRepository.currentUser } returns currentUser
        coEvery { authRepository.getCurrentUser() } coAnswers { currentUser.value }
        coEvery { authRepository.createUserProfile(any()) } returns Result.success(Unit)
    }

    @Test
    fun `authenticated shell and profile become ready while AI check remains pending`() = runTest {
        val pendingAdminCheck = CompletableDeferred<Result<Boolean>>()
        coEvery { profileQueryUseCase.hasProfile(USER_A.uid) } returns Result.success(true)
        coEvery { checkAdminPermissionsUseCase(USER_A.uid) } coAnswers {
            pendingAdminCheck.await()
        }
        val viewModel = createViewModel()

        currentUser.value = USER_A
        runCurrent()

        assertEquals(
            MainViewModel.AuthenticationState.Authenticated(USER_A),
            viewModel.authState.value
        )
        assertEquals(
            MainViewModel.ProfileReadiness.Ready(USER_A.uid),
            viewModel.profileReadiness.value
        )
        assertEquals(
            MainViewModel.AiAccessEligibility.Loading,
            viewModel.aiAccessEligibility.value
        )

        pendingAdminCheck.cancel()
    }

    @Test
    fun `account switch rejects stale profile and AI completion from prior user`() = runTest {
        val userAProfile = CompletableDeferred<Result<Boolean>>()
        val userAAdmin = CompletableDeferred<Result<Boolean>>()
        coEvery { profileQueryUseCase.hasProfile(any()) } coAnswers {
            when (firstArg<String>()) {
                USER_A.uid -> withContext(NonCancellable) { userAProfile.await() }
                USER_B.uid -> Result.success(true)
                else -> error("Unexpected user")
            }
        }
        coEvery { checkAdminPermissionsUseCase(any()) } coAnswers {
            when (firstArg<String>()) {
                USER_A.uid -> withContext(NonCancellable) { userAAdmin.await() }
                USER_B.uid -> Result.success(false)
                else -> error("Unexpected user")
            }
        }
        val viewModel = createViewModel()

        currentUser.value = USER_A
        runCurrent()
        currentUser.value = USER_B
        runCurrent()

        userAProfile.complete(Result.success(true))
        userAAdmin.complete(Result.success(true))
        advanceUntilIdle()

        assertEquals(
            MainViewModel.AuthenticationState.Authenticated(USER_B),
            viewModel.authState.value
        )
        assertEquals(
            MainViewModel.ProfileReadiness.Ready(USER_B.uid),
            viewModel.profileReadiness.value
        )
        assertEquals(
            MainViewModel.AiAccessEligibility.Ineligible,
            viewModel.aiAccessEligibility.value
        )
    }

    @Test
    fun `sign out cancels user jobs and stale completions cannot republish state`() = runTest {
        val pendingProfile = CompletableDeferred<Result<Boolean>>()
        val pendingAdmin = CompletableDeferred<Result<Boolean>>()
        coEvery { profileQueryUseCase.hasProfile(USER_A.uid) } coAnswers {
            withContext(NonCancellable) { pendingProfile.await() }
        }
        coEvery { checkAdminPermissionsUseCase(USER_A.uid) } coAnswers {
            withContext(NonCancellable) { pendingAdmin.await() }
        }
        val viewModel = createViewModel()

        currentUser.value = USER_A
        runCurrent()
        currentUser.value = null
        runCurrent()
        pendingProfile.complete(Result.success(true))
        pendingAdmin.complete(Result.success(true))
        advanceUntilIdle()

        assertEquals(MainViewModel.AuthenticationState.Unauthenticated, viewModel.authState.value)
        assertEquals(MainViewModel.ProfileReadiness.NotRequired, viewModel.profileReadiness.value)
        assertEquals(
            MainViewModel.AiAccessEligibility.Ineligible,
            viewModel.aiAccessEligibility.value
        )
    }

    @Test
    fun `profile cancellation is not converted to a readiness error`() = runTest {
        coEvery { profileQueryUseCase.hasProfile(USER_A.uid) } throws kotlinx.coroutines.CancellationException()
        coEvery { checkAdminPermissionsUseCase(USER_A.uid) } returns Result.success(false)
        val viewModel = createViewModel()

        currentUser.value = USER_A
        runCurrent()

        assertTrue(viewModel.profileReadiness.value is MainViewModel.ProfileReadiness.Checking)
    }

    private fun createViewModel(): MainViewModel = MainViewModel(
        authRepository = authRepository,
        profileQueryUseCase = profileQueryUseCase,
        checkAdminPermissionsUseCase = checkAdminPermissionsUseCase
    )

    private companion object {
        val USER_A = User.forAuthentication(
            uid = "authenticated-user-a",
            email = "a@example.test"
        )
        val USER_B = User.forAuthentication(
            uid = "authenticated-user-b",
            email = "b@example.test"
        )
    }
}
