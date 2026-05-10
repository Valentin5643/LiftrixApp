package com.example.liftrix.domain.usecase.auth

import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.common.LiftrixResult

interface AuthCommandUseCase {
    suspend fun signInWithEmail(email: String, password: String): LiftrixResult<User>

    suspend fun signInWithGoogle(idToken: String): LiftrixResult<User>

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String
    ): LiftrixResult<User>

    suspend fun signOut(): LiftrixResult<Unit>

    suspend fun signOutEnhanced(): LiftrixResult<Unit>

    suspend fun resetPassword(email: String): LiftrixResult<Unit>
}
