package com.example.liftrix.domain.repository

import com.example.liftrix.core.identity.UserId
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    
    val currentUser: Flow<User?>
    
    suspend fun signInWithEmail(email: String, password: String): LiftrixResult<User>
    
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): LiftrixResult<User>
    
    suspend fun signInWithGoogle(idToken: String): LiftrixResult<User>
    
    suspend fun signInAnonymously(): LiftrixResult<User>
    
    suspend fun signOut(): LiftrixResult<Unit>
    
    suspend fun sendPasswordResetEmail(email: String): LiftrixResult<Unit>
    
    suspend fun getCurrentUser(): User?
    
    suspend fun getCurrentUserId(): UserId?

    fun observeAuthState(): Flow<UserId?>
    
    suspend fun createUserProfile(user: User): LiftrixResult<Unit>
    
    suspend fun getUserProfile(uid: UserId): LiftrixResult<User?>
    
    // Account Management Methods
    suspend fun reauthenticate(password: String): LiftrixResult<Unit>
    
    suspend fun updateEmail(newEmail: String): LiftrixResult<Unit>
    
    suspend fun updatePassword(currentPassword: String, newPassword: String): LiftrixResult<Unit>
    
    suspend fun deleteAccount(): LiftrixResult<Unit>
} 