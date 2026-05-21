package com.example.liftrix.domain.interactor.auth

import com.example.liftrix.core.identity.UserId
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.auth.AuthCommandUseCase
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import javax.inject.Inject

class AuthInteractor @Inject constructor(
    private val authQueryUseCase: AuthQueryUseCase,
    private val authCommandUseCase: AuthCommandUseCase
) {
    suspend fun currentUser(waitForAuth: Boolean = false): LiftrixResult<UserId> =
        authQueryUseCase(waitForAuth = waitForAuth)

    suspend fun signInWithEmail(email: String, password: String): LiftrixResult<User> =
        authCommandUseCase.signInWithEmail(email, password)

    suspend fun signInWithGoogle(idToken: String): LiftrixResult<User> =
        authCommandUseCase.signInWithGoogle(idToken)

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String
    ): LiftrixResult<User> = authCommandUseCase.signUpWithEmail(email, password, username)

    suspend fun signOut(): LiftrixResult<Unit> =
        authCommandUseCase.signOut()

    suspend fun signOutEnhanced(): LiftrixResult<Unit> =
        authCommandUseCase.signOutEnhanced()

    suspend fun resetPassword(email: String): LiftrixResult<Unit> =
        authCommandUseCase.resetPassword(email)
}
