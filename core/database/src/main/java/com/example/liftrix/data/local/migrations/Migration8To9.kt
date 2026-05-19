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
