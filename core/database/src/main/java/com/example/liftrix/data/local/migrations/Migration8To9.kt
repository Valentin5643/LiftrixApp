package com.example.liftrix.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `template_share_events` (
                `id` TEXT NOT NULL,
                `sender_id` TEXT NOT NULL,
                `receiver_id` TEXT,
                `template_id` TEXT NOT NULL,
                `delivery_mode` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `expires_at` INTEGER NOT NULL,
                `accepted_at` INTEGER,
                `is_synced` INTEGER NOT NULL DEFAULT 0,
                `is_dirty` INTEGER NOT NULL DEFAULT 1,
                `last_modified` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `idx_template_share_sender_receiver_status`
            ON `template_share_events` (`sender_id`, `receiver_id`, `status`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `idx_template_share_sender_status`
            ON `template_share_events` (`sender_id`, `status`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `idx_template_share_receiver_status`
            ON `template_share_events` (`receiver_id`, `status`)
            """.trimIndent()
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_workout_templates_name_user_id`")
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_workout_templates_name_user_id`
            ON `workout_templates` (`name`, `user_id`)
            """.trimIndent()
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIEW IF NOT EXISTS `completed_workout_metrics_view` AS
            SELECT
                w.id AS workout_id,
                w.user_id AS user_id,
                w.date AS workout_date,
                w.start_time AS start_time,
                w.end_time AS end_time,
                w.exercises_json AS exercises_json,
                CASE
                    WHEN w.end_time IS NOT NULL AND w.start_time IS NOT NULL
                    THEN MAX(0, (strftime('%s', w.end_time) - strftime('%s', w.start_time)) / 60)
                    ELSE 0
                END AS duration_minutes,
                COALESCE(SUM(
                    CASE
                        WHEN (el.equipment IS NULL OR el.equipment != 'BODYWEIGHT_ONLY')
                         AND es.weight_kg > 0
                         AND es.reps > 0
                        THEN es.weight_kg * es.reps
                        ELSE 0
                    END
                ), 0) AS total_volume,
                COALESCE(SUM(CASE WHEN es.reps > 0 THEN es.reps ELSE 0 END), 0) AS total_reps,
                COUNT(es.id) AS total_sets,
                COUNT(DISTINCT e.exercise_library_id) AS exercise_count,
                w.created_at AS created_at,
                w.updated_at AS updated_at
            FROM workouts w
            LEFT JOIN exercises e
                ON w.id = e.workout_id
               AND w.user_id = e.user_id
            LEFT JOIN exercise_sets es
                ON e.id = es.exercise_id
               AND e.user_id = es.user_id
            LEFT JOIN exercise_library el
                ON e.exercise_library_id = el.id
            WHERE w.status = 'COMPLETED'
            GROUP BY
                w.id,
                w.user_id,
                w.date,
                w.start_time,
                w.end_time,
                w.exercises_json,
                w.created_at,
                w.updated_at
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE VIEW IF NOT EXISTS `exercise_set_performance_view` AS
            SELECT
                es.id AS set_id,
                es.user_id AS user_id,
                e.id AS exercise_id,
                e.workout_id AS workout_id,
                w.date AS workout_date,
                COALESCE(DATE(es.completed_at / 1000, 'unixepoch'), w.date) AS activity_date,
                e.exercise_library_id AS exercise_library_id,
                el.name AS exercise_name,
                el.primary_muscle_group AS primary_muscle_group,
                el.equipment AS equipment,
                es.weight_kg AS weight_kg,
                es.reps AS reps,
                es.completed_at AS completed_at,
                CASE
                    WHEN (el.equipment IS NULL OR el.equipment != 'BODYWEIGHT_ONLY')
                     AND es.weight_kg > 0
                     AND es.reps > 0
                    THEN es.weight_kg * es.reps
                    ELSE 0
                END AS set_volume,
                CASE
                    WHEN (el.equipment IS NULL OR el.equipment != 'BODYWEIGHT_ONLY')
                     AND es.weight_kg > 0
                     AND es.reps BETWEEN 1 AND 10
                    THEN es.weight_kg * (1.0 + CAST(es.reps AS REAL) / 30.0)
                    ELSE NULL
                END AS estimated_one_rm,
                CASE
                    WHEN es.reps > 0 THEN es.reps
                    ELSE 0
                END AS rep_count
            FROM exercise_sets es
            JOIN exercises e
                ON es.exercise_id = e.id
               AND es.user_id = e.user_id
            JOIN workouts w
                ON e.workout_id = w.id
               AND e.user_id = w.user_id
            LEFT JOIN exercise_library el
                ON e.exercise_library_id = el.id
            WHERE w.status = 'COMPLETED'
            """.trimIndent()
        )
    }
}
