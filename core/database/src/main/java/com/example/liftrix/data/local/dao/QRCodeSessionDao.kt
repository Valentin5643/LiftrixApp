package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.QRCodeSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for QR Code Session operations
 * Supports creation, expiration, and usage tracking of QR codes
 */
@Dao
interface QRCodeSessionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQRCodeSession(session: QRCodeSessionEntity)
    
    @Update
    suspend fun updateQRCodeSession(session: QRCodeSessionEntity)
    
    @Query("SELECT * FROM qr_code_sessions WHERE code_token = :token AND expires_at > :currentTime AND is_used = 0")
    suspend fun getValidSessionByToken(token: String, currentTime: Long): QRCodeSessionEntity?
    
    @Query("SELECT * FROM qr_code_sessions WHERE user_id = :userId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestSessionForUser(userId: String): QRCodeSessionEntity?
    
    @Query("UPDATE qr_code_sessions SET is_used = 1, used_by_user_id = :usedByUserId WHERE code_token = :token")
    suspend fun markAsUsed(token: String, usedByUserId: String)
    
    @Query("DELETE FROM qr_code_sessions WHERE expires_at <= :currentTime")
    suspend fun deleteExpiredSessions(currentTime: Long)
    
    @Query("DELETE FROM qr_code_sessions WHERE user_id = :userId AND is_used = 0")
    suspend fun deleteUnusedSessionsForUser(userId: String)
    
    @Query("SELECT COUNT(*) FROM qr_code_sessions WHERE user_id = :userId AND created_at > :sinceTime")
    suspend fun getGenerationCountSince(userId: String, sinceTime: Long): Int
    
    @Query("SELECT * FROM qr_code_sessions WHERE user_id = :userId ORDER BY created_at DESC")
    fun getUserQRHistory(userId: String): Flow<List<QRCodeSessionEntity>>
}