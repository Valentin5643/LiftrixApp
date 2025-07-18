package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migration from version 29 to 30
 * 
 * Adds support for guest login tracking and workout anomaly detection features.
 * 
 * Changes:
 * - Creates guest_sessions table for tracking guest user limits and nudging
 * - Creates workout_anomalies table for detecting and storing data entry errors
 * - Creates anomaly_detection_settings table for user-specific detection preferences
 * - Creates exercise_history table for exercise performance pattern learning
 * - Adds proper indices for performance optimization
 */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(database: SupportSQLiteDatabase) {
        
        // Create guest_sessions table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS guest_sessions (
                session_id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT NOT NULL,
                workout_count INTEGER NOT NULL,
                max_workouts INTEGER NOT NULL,
                last_nudge_shown TEXT,
                nudge_count INTEGER NOT NULL,
                significant_interaction_count INTEGER NOT NULL,
                session_started_at TEXT NOT NULL,
                last_activity_at TEXT NOT NULL,
                has_seen_limit_warning INTEGER NOT NULL,
                is_limit_reached INTEGER NOT NULL
            )
        """)
        
        // Create indices for guest_sessions
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS index_guest_sessions_user_id 
            ON guest_sessions(user_id)
        """)
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_guest_sessions_is_limit_reached 
            ON guest_sessions(is_limit_reached)
        """)
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_guest_sessions_last_activity_at 
            ON guest_sessions(last_activity_at)
        """)
        
        // Create workout_anomalies table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS workout_anomalies (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                exercise_id TEXT NOT NULL,
                exercise_name TEXT NOT NULL,
                anomaly_type TEXT NOT NULL,
                current_value_type TEXT NOT NULL,
                current_value_data TEXT NOT NULL,
                previous_value_type TEXT,
                previous_value_data TEXT,
                confidence_score REAL NOT NULL,
                detected_at TEXT NOT NULL,
                resolved_at TEXT,
                user_action TEXT,
                corrected_value_type TEXT,
                corrected_value_data TEXT
            )
        """)
        
        // Create indices for workout_anomalies
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_workout_anomalies_user_id 
            ON workout_anomalies(user_id)
        """)
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_workout_anomalies_session_id 
            ON workout_anomalies(session_id)
        """)
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_workout_anomalies_exercise_id 
            ON workout_anomalies(exercise_id)
        """)
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_workout_anomalies_detected_at 
            ON workout_anomalies(detected_at)
        """)
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_workout_anomalies_user_action 
            ON workout_anomalies(user_action)
        """)
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_workout_anomalies_anomaly_type 
            ON workout_anomalies(anomaly_type)
        """)
        
        // Create anomaly_detection_settings table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS anomaly_detection_settings (
                user_id TEXT NOT NULL PRIMARY KEY,
                weight_spike_threshold REAL NOT NULL,
                weight_drop_threshold REAL NOT NULL,
                reps_spike_threshold REAL NOT NULL,
                reps_drop_threshold REAL NOT NULL,
                duration_spike_threshold REAL NOT NULL,
                duration_drop_threshold REAL NOT NULL,
                min_weight_for_detection REAL NOT NULL,
                min_reps_for_detection INTEGER NOT NULL,
                min_duration_for_detection INTEGER NOT NULL,
                learning_enabled INTEGER NOT NULL,
                last_updated TEXT NOT NULL
            )
        """)
        
        // Create index for anomaly_detection_settings
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS index_anomaly_detection_settings_user_id 
            ON anomaly_detection_settings(user_id)
        """)
        
        // Create exercise_history table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS exercise_history (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT NOT NULL,
                exercise_id TEXT NOT NULL,
                recent_weights TEXT NOT NULL,
                recent_reps TEXT NOT NULL,
                recent_durations TEXT NOT NULL,
                last_performed TEXT,
                average_weight REAL NOT NULL,
                average_reps REAL NOT NULL,
                average_duration REAL NOT NULL,
                max_weight REAL NOT NULL,
                max_reps INTEGER NOT NULL,
                max_duration INTEGER NOT NULL
            )
        """)
        
        // Create indices for exercise_history
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS index_exercise_history_user_id_exercise_id 
            ON exercise_history(user_id, exercise_id)
        """)
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_exercise_history_last_performed 
            ON exercise_history(last_performed)
        """)
    }
}