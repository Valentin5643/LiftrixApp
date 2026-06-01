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

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `completed_workout_metric_read_models` (
                `workout_id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `workout_date` TEXT NOT NULL,
                `duration_minutes` INTEGER NOT NULL,
                `total_volume` REAL NOT NULL,
                `total_reps` INTEGER NOT NULL,
                `total_sets` INTEGER NOT NULL,
                `exercise_count` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`workout_id`, `user_id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `idx_completed_workout_metrics_user_date`
            ON `completed_workout_metric_read_models` (`user_id`, `workout_date`)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_daily_volume_read_models` (
                `user_id` TEXT NOT NULL,
                `workout_date` TEXT NOT NULL,
                `total_volume` REAL NOT NULL,
                `total_reps` INTEGER NOT NULL,
                `total_sets` INTEGER NOT NULL,
                `workout_count` INTEGER NOT NULL,
                `exercise_count` INTEGER NOT NULL,
                `total_duration_minutes` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `workout_date`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `idx_daily_volume_user_date`
            ON `workout_daily_volume_read_models` (`user_id`, `workout_date`)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_weekly_volume_read_models` (
                `user_id` TEXT NOT NULL,
                `week_start_date` TEXT NOT NULL,
                `week_end_date` TEXT NOT NULL,
                `total_volume` REAL NOT NULL,
                `total_reps` INTEGER NOT NULL,
                `total_sets` INTEGER NOT NULL,
                `workout_count` INTEGER NOT NULL,
                `exercise_count` INTEGER NOT NULL,
                `total_duration_minutes` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `week_start_date`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `idx_weekly_volume_user_week`
            ON `workout_weekly_volume_read_models` (`user_id`, `week_start_date`)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exercise_pr_read_models` (
                `user_id` TEXT NOT NULL,
                `exercise_library_id` TEXT NOT NULL,
                `exercise_name` TEXT NOT NULL,
                `primary_muscle_group` TEXT NOT NULL,
                `max_estimated_one_rm` REAL NOT NULL,
                `max_weight_kg` REAL NOT NULL,
                `max_reps` INTEGER NOT NULL,
                `total_volume` REAL NOT NULL,
                `total_sets` INTEGER NOT NULL,
                `last_pr_at` INTEGER,
                `source_workout_id` TEXT,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `exercise_library_id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `idx_exercise_pr_user_one_rm`
            ON `exercise_pr_read_models` (`user_id`, `max_estimated_one_rm`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `idx_exercise_pr_user_muscle`
            ON `exercise_pr_read_models` (`user_id`, `primary_muscle_group`)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `muscle_group_daily_read_models` (
                `user_id` TEXT NOT NULL,
                `workout_date` TEXT NOT NULL,
                `primary_muscle_group` TEXT NOT NULL,
                `total_volume` REAL NOT NULL,
                `total_reps` INTEGER NOT NULL,
                `total_sets` INTEGER NOT NULL,
                `exercise_count` INTEGER NOT NULL,
                `workout_count` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`user_id`, `workout_date`, `primary_muscle_group`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `idx_muscle_daily_user_date`
            ON `muscle_group_daily_read_models` (`user_id`, `workout_date`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `idx_muscle_daily_user_group`
            ON `muscle_group_daily_read_models` (`user_id`, `primary_muscle_group`)
            """.trimIndent()
        )

        val updatedAtExpression = "(strftime('%s', 'now') * 1000)"
        db.execSQL(
            """
            INSERT OR REPLACE INTO completed_workout_metric_read_models (
                workout_id,
                user_id,
                workout_date,
                duration_minutes,
                total_volume,
                total_reps,
                total_sets,
                exercise_count,
                updated_at
            )
            SELECT
                workout_id,
                user_id,
                workout_date,
                duration_minutes,
                total_volume,
                total_reps,
                total_sets,
                exercise_count,
                $updatedAtExpression
            FROM completed_workout_metrics_view
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO workout_daily_volume_read_models (
                user_id,
                workout_date,
                total_volume,
                total_reps,
                total_sets,
                workout_count,
                exercise_count,
                total_duration_minutes,
                updated_at
            )
            SELECT
                user_id,
                workout_date,
                SUM(total_volume),
                SUM(total_reps),
                SUM(total_sets),
                COUNT(workout_id),
                SUM(exercise_count),
                SUM(duration_minutes),
                $updatedAtExpression
            FROM completed_workout_metric_read_models
            GROUP BY user_id, workout_date
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO workout_weekly_volume_read_models (
                user_id,
                week_start_date,
                week_end_date,
                total_volume,
                total_reps,
                total_sets,
                workout_count,
                exercise_count,
                total_duration_minutes,
                updated_at
            )
            SELECT
                user_id,
                date(workout_date, '-' || ((CAST(strftime('%w', workout_date) AS INTEGER) + 6) % 7) || ' days') AS week_start_date,
                date(workout_date, (6 - ((CAST(strftime('%w', workout_date) AS INTEGER) + 6) % 7)) || ' days') AS week_end_date,
                SUM(total_volume),
                SUM(total_reps),
                SUM(total_sets),
                SUM(workout_count),
                SUM(exercise_count),
                SUM(total_duration_minutes),
                $updatedAtExpression
            FROM workout_daily_volume_read_models
            GROUP BY user_id, week_start_date, week_end_date
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO exercise_pr_read_models (
                user_id,
                exercise_library_id,
                exercise_name,
                primary_muscle_group,
                max_estimated_one_rm,
                max_weight_kg,
                max_reps,
                total_volume,
                total_sets,
                last_pr_at,
                source_workout_id,
                updated_at
            )
            SELECT
                user_id,
                exercise_library_id,
                COALESCE(exercise_name, exercise_library_id),
                COALESCE(primary_muscle_group, 'UNKNOWN'),
                COALESCE(MAX(estimated_one_rm), 0),
                COALESCE(MAX(weight_kg), 0),
                COALESCE(MAX(reps), 0),
                COALESCE(SUM(set_volume), 0),
                COUNT(set_id),
                MAX(completed_at),
                (
                    SELECT v2.workout_id
                    FROM exercise_set_performance_view v2
                    WHERE v2.user_id = v.user_id
                    AND v2.exercise_library_id = v.exercise_library_id
                    AND v2.estimated_one_rm IS NOT NULL
                    ORDER BY v2.estimated_one_rm DESC, v2.completed_at DESC
                    LIMIT 1
                ),
                $updatedAtExpression
            FROM exercise_set_performance_view v
            WHERE estimated_one_rm IS NOT NULL
            GROUP BY user_id, exercise_library_id, exercise_name, primary_muscle_group
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO muscle_group_daily_read_models (
                user_id,
                workout_date,
                primary_muscle_group,
                total_volume,
                total_reps,
                total_sets,
                exercise_count,
                workout_count,
                updated_at
            )
            SELECT
                user_id,
                activity_date,
                COALESCE(primary_muscle_group, 'UNKNOWN'),
                SUM(set_volume),
                SUM(rep_count),
                COUNT(set_id),
                COUNT(DISTINCT exercise_library_id),
                COUNT(DISTINCT workout_id),
                $updatedAtExpression
            FROM exercise_set_performance_view
            WHERE rep_count > 0
            GROUP BY user_id, activity_date, COALESCE(primary_muscle_group, 'UNKNOWN')
            """.trimIndent()
        )
    }
}
