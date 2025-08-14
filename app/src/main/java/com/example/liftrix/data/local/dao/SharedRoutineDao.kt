package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.SharedRoutineEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for shared workout routines.
 * All queries MUST include userId filtering to prevent data leakage.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
@Dao
interface SharedRoutineDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSharedRoutine(routine: SharedRoutineEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSharedRoutines(routines: List<SharedRoutineEntity>)
    
    @Update
    suspend fun updateSharedRoutine(routine: SharedRoutineEntity)
    
    @Delete
    suspend fun deleteSharedRoutine(routine: SharedRoutineEntity)
    
    @Query("DELETE FROM shared_routines WHERE id = :routineId AND user_id = :userId")
    suspend fun deleteSharedRoutineById(routineId: String, userId: String)
    
    @Query("SELECT * FROM shared_routines WHERE id = :routineId")
    suspend fun getSharedRoutineById(routineId: String): SharedRoutineEntity?
    
    @Query("SELECT * FROM shared_routines WHERE share_token = :shareToken")
    suspend fun getSharedRoutineByToken(shareToken: String): SharedRoutineEntity?
    
    @Query("SELECT * FROM shared_routines WHERE user_id = :userId ORDER BY created_at DESC")
    fun getUserSharedRoutines(userId: String): Flow<List<SharedRoutineEntity>>
    
    @Query("""
        SELECT * FROM shared_routines 
        WHERE user_id = :userId 
        AND is_active = 1 
        ORDER BY created_at DESC
    """)
    fun getUserActiveSharedRoutines(userId: String): Flow<List<SharedRoutineEntity>>
    
    @Query("""
        SELECT * FROM shared_routines 
        WHERE is_featured = 1 
        AND is_active = 1 
        ORDER BY import_count DESC, created_at DESC 
        LIMIT :limit
    """)
    suspend fun getFeaturedRoutines(limit: Int = 20): List<SharedRoutineEntity>
    
    @Query("""
        SELECT * FROM shared_routines 
        WHERE is_active = 1 
        AND user_id != :excludeUserId
        ORDER BY import_count DESC, view_count DESC, created_at DESC 
        LIMIT :limit
    """)
    suspend fun getPopularRoutines(excludeUserId: String, limit: Int = 20): List<SharedRoutineEntity>
    
    @Query("""
        UPDATE shared_routines 
        SET view_count = view_count + 1 
        WHERE id = :routineId
    """)
    suspend fun incrementViewCount(routineId: String)
    
    @Query("""
        UPDATE shared_routines 
        SET import_count = import_count + 1 
        WHERE id = :routineId
    """)
    suspend fun incrementImportCount(routineId: String)
    
    @Query("""
        UPDATE shared_routines 
        SET like_count = like_count + :increment 
        WHERE id = :routineId
    """)
    suspend fun updateLikeCount(routineId: String, increment: Int)
    
    @Query("""
        UPDATE shared_routines 
        SET is_active = :isActive, updated_at = :updatedAt
        WHERE id = :routineId AND user_id = :userId
    """)
    suspend fun updateActiveStatus(routineId: String, userId: String, isActive: Boolean, updatedAt: Long)
    
    @Query("""
        UPDATE shared_routines 
        SET is_featured = :isFeatured 
        WHERE id = :routineId
    """)
    suspend fun updateFeaturedStatus(routineId: String, isFeatured: Boolean)
    
    @Query("""
        SELECT * FROM shared_routines 
        WHERE routine_name LIKE '%' || :query || '%' 
        OR description LIKE '%' || :query || '%'
        AND is_active = 1
        ORDER BY import_count DESC, view_count DESC 
        LIMIT :limit
    """)
    suspend fun searchRoutines(query: String, limit: Int = 20): List<SharedRoutineEntity>
    
    @Query("SELECT COUNT(*) FROM shared_routines WHERE user_id = :userId AND is_active = 1")
    suspend fun getUserActiveRoutineCount(userId: String): Int
    
    @Query("DELETE FROM shared_routines WHERE user_id = :userId")
    suspend fun deleteAllUserSharedRoutines(userId: String)
    
    @Query("""
        SELECT * FROM shared_routines 
        WHERE user_id = :userId 
        AND is_synced = 0
        ORDER BY created_at ASC
    """)
    suspend fun getUnsyncedRoutines(userId: String): List<SharedRoutineEntity>
    
    @Query("""
        UPDATE shared_routines 
        SET is_synced = 1, sync_version = :syncVersion
        WHERE id = :routineId
    """)
    suspend fun markAsSynced(routineId: String, syncVersion: Int)
    
    @Query("""
        SELECT * FROM shared_routines 
        WHERE parent_routine_id = :parentId 
        ORDER BY version DESC, created_at DESC
    """)
    suspend fun getRoutineVersions(parentId: String): List<SharedRoutineEntity>
}