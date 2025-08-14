package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.GymBuddyActivityEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Gym Buddy Activity tracking
 * Handles engagement metrics between gym buddies
 */
@Dao
interface GymBuddyActivityDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: GymBuddyActivityEntity)
    
    @Update
    suspend fun updateActivity(activity: GymBuddyActivityEntity)
    
    @Query("SELECT * FROM gym_buddy_activities WHERE user_id = :userId AND buddy_id = :buddyId")
    suspend fun getActivityBetween(userId: String, buddyId: String): GymBuddyActivityEntity?
    
    @Query("SELECT * FROM gym_buddy_activities WHERE user_id = :userId")
    fun getUserActivities(userId: String): Flow<List<GymBuddyActivityEntity>>
    
    @Query("""
        UPDATE gym_buddy_activities 
        SET workouts_together_count = workouts_together_count + 1,
            last_workout_together = :workoutTime
        WHERE user_id = :userId AND buddy_id = :buddyId
    """)
    suspend fun incrementWorkoutsTogether(userId: String, buddyId: String, workoutTime: Long)
    
    @Query("""
        UPDATE gym_buddy_activities 
        SET total_prs_celebrated = total_prs_celebrated + 1
        WHERE user_id = :userId AND buddy_id = :buddyId
    """)
    suspend fun incrementPrsCelebrated(userId: String, buddyId: String)
    
    @Query("""
        UPDATE gym_buddy_activities 
        SET encouragement_messages_sent = encouragement_messages_sent + 1
        WHERE user_id = :userId AND buddy_id = :buddyId
    """)
    suspend fun incrementEncouragementMessages(userId: String, buddyId: String)
    
    @Query("""
        UPDATE gym_buddy_activities 
        SET workout_templates_shared = workout_templates_shared + 1
        WHERE user_id = :userId AND buddy_id = :buddyId
    """)
    suspend fun incrementTemplatesShared(userId: String, buddyId: String)
    
    @Query("DELETE FROM gym_buddy_activities WHERE user_id = :userId AND buddy_id = :buddyId")
    suspend fun deleteActivity(userId: String, buddyId: String)
}