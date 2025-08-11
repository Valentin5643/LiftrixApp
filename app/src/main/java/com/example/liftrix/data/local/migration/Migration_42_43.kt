package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.sql.SQLException

/**
 * Migration from database version 42 to 43
 * Chart Performance Optimization: Add performance indexes for analytics queries
 * 
 * Background:
 * - Analytics queries for charts were not optimized with proper indexes
 * - Large workout histories (5000+ workouts) causing slow query performance
 * - Chart loading times exceeding 500ms for historical data analysis
 * - Supports performance requirements from SPEC-20250110-chart-performance-optimization
 * 
 * Changes:
 * - Add composite index for workout metrics queries (user_id, workout_date, total_volume)
 * - Add composite index for exercise-specific queries (user_id, exercise_library_id, created_at)
 * - Add composite index for frequency queries (user_id, start_time, duration_millis)
 * - Target <100ms cached query performance and <500ms fresh query performance
 * 
 * Performance Impact:
 * - Volume chart queries: Expected 80% performance improvement
 * - Exercise progression queries: Expected 70% performance improvement
 * - Workout frequency queries: Expected 85% performance improvement
 * - Memory overhead: ~2-3MB for 10,000+ workouts (acceptable)
 */
val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()
        try {
            // ========================================
            // Analytics Performance Indexes
            // ========================================
            
            // Index for volume queries - supports volume chart and trend analysis
            // Covers queries like: SELECT total_volume FROM workout_metrics WHERE user_id = ? AND workout_date BETWEEN ? AND ?
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_workout_metrics_user_date_volume
                ON workout_metrics(user_id, workout_date DESC, total_volume)
            """.trimIndent())
            
            // Index for exercise-specific queries - supports 1RM progression and exercise rankings
            // Covers queries like: SELECT * FROM exercise_sets WHERE user_id = ? AND exercise_library_id = ? ORDER BY created_at DESC
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_exercise_sets_user_exercise_date
                ON exercise_sets(user_id, exercise_library_id, created_at DESC)
            """.trimIndent())
            
            // Index for workout frequency queries - supports frequency heatmap and duration analysis
            // Covers queries like: SELECT COUNT(*) FROM workouts WHERE user_id = ? AND start_time BETWEEN ? AND ?
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_workouts_user_date_duration
                ON workouts(user_id, start_time DESC, duration_millis)
            """.trimIndent())
            
            // Additional index for muscle group analysis - supports muscle group distribution charts
            // Covers queries joining exercises with exercise library for muscle group data
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_exercises_user_workout_library
                ON exercises(user_id, workout_id, exercise_library_id)
            """.trimIndent())
            
            // Index for exercise weight memory - supports weight progression charts
            // Covers queries for latest weights and progression tracking
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_exercise_weight_memory_user_exercise
                ON exercise_weight_memory(user_id, exercise_library_id, last_used_at DESC)
            """.trimIndent())
            
            // ========================================
            // Query Performance Validation
            // ========================================
            
            // Log index creation for performance monitoring
            database.execSQL("""
                INSERT OR REPLACE INTO analytics_cache (
                    id, user_id, calculation_type, result, timestamp
                ) VALUES (
                    'migration_42_43_performance_indexes', 
                    'system', 
                    'performance_optimization', 
                    '{"migration": "42_43", "action": "add_analytics_indexes", "indexes": ["workout_metrics_user_date_volume", "exercise_sets_user_exercise_date", "workouts_user_date_duration", "exercises_user_workout_library", "exercise_weight_memory_user_exercise"], "target_performance": {"cached_queries": "<100ms", "fresh_queries": "<500ms"}, "timestamp": ' || (strftime('%s', 'now') * 1000) || '}',
                    strftime('%s', 'now') * 1000
                )
            """.trimIndent())
            
            database.setTransactionSuccessful()
        } catch (e: SQLException) {
            throw RuntimeException("Migration 42->43 failed: Analytics performance indexes", e)
        } finally {
            database.endTransaction()
        }
    }
}