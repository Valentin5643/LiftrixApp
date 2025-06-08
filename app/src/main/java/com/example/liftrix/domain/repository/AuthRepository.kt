package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    
    val currentUser: Flow<User?>
    
    suspend fun signInWithEmail(email: String, password: String): Result<User>
    
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User>
    
    suspend fun signInWithGoogle(idToken: String): Result<User>
    
    suspend fun signInAnonymously(): Result<User>
    
    suspend fun signOut(): Result<Unit>
    
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    
    suspend fun getCurrentUser(): User?
    
    suspend fun getCurrentUserId(): String?
    
    suspend fun createUserProfile(user: User): Result<Unit>
    
    suspend fun getUserProfile(uid: String): Result<User?>
} 