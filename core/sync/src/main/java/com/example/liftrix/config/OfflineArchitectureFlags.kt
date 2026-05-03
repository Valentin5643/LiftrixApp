package com.example.liftrix.config

/**
 * Feature flags for gradual rollout of Room-First Offline Architecture.
 *
 * SPEC: SPEC-20241228-offline-first-architecture-fix
 *
 * These flags allow granular control over the offline-first architecture migration:
 * - Master switch for complete rollback
 * - Individual feature switches for phased rollout
 * - Can be controlled via BuildConfig or Remote Config
 *
 * Rollback Strategy:
 * - Level 1: Disable Firestore persistence only (DISABLE_FIRESTORE_PERSISTENCE = false)
 * - Level 2: Disable dirty flag gating (USE_DIRTY_FLAG_GATING = false)
 * - Level 3: Disable idempotent listeners (USE_IDEMPOTENT_LISTENERS = false)
 * - Level 4: Full rollback (ROOM_FIRST_ENABLED = false)
 */
object OfflineArchitectureFlags {

    /**
     * Master switch for Room-First architecture.
     * Set to false to completely rollback to legacy dual-authority mode.
     *
     * Default: true (Room-First mode enabled for testing)
     */
    @JvmField
    val ROOM_FIRST_ENABLED: Boolean = true

    /**
     * Disable Firestore offline persistence.
     * When true, Firestore acts as stateless sync target only.
     * When false, reverts to dual authority (Room + Firestore cache).
     *
     * Default: Follows master switch
     */
    @JvmField
    var DISABLE_FIRESTORE_PERSISTENCE: Boolean = ROOM_FIRST_ENABLED

    /**
     * Use dirty flag gating in sync workers.
     * When true, only entities with isDirty=true are uploaded.
     * When false, reverts to old getUnsyncedWorkouts() behavior.
     *
     * Default: Follows master switch
     */
    @JvmField
    var USE_DIRTY_FLAG_GATING: Boolean = ROOM_FIRST_ENABLED

    /**
     * Use idempotent real-time listeners.
     * When true, listeners use upsertFromRemote() with timestamp checks.
     * When false, reverts to direct DAO updates (risk of feedback loops).
     *
     * Default: Follows master switch
     */
    @JvmField
    var USE_IDEMPOTENT_LISTENERS: Boolean = ROOM_FIRST_ENABLED

    /**
     * Fix AuthRepository to use Room-first pattern.
     * When true, createUserProfile() writes to Room first, then queues sync.
     * When false, uses legacy direct Firestore batch writes.
     *
     * Default: Follows master switch
     */
    @JvmField
    var FIX_AUTH_REPOSITORY: Boolean = ROOM_FIRST_ENABLED

    /**
     * Fix UserSearchRepository to use Room-first pattern.
     * When true, QR codes and search cache go through Room.
     * When false, uses legacy direct Firestore writes.
     *
     * Default: Follows master switch
     */
    @JvmField
    var FIX_SEARCH_REPOSITORY: Boolean = ROOM_FIRST_ENABLED

    /**
     * Fix ProfileRepository to use Room-first pattern for deletions.
     * When true, profile deletes become soft deletes with sync queue.
     * When false, uses legacy direct Firestore deletion.
     *
     * Default: Follows master switch
     */
    @JvmField
    var FIX_PROFILE_REPOSITORY: Boolean = ROOM_FIRST_ENABLED

    /**
     * Fix CustomExerciseRepository to use Room-first pattern.
     * When true, custom exercise writes stay local and sync via workers.
     * When false, uses legacy direct Firestore writes.
     *
     * Default: Follows master switch
     */
    @JvmField
    var FIX_CUSTOM_EXERCISE_REPOSITORY: Boolean = ROOM_FIRST_ENABLED

    /**
     * Fix BlockRepository to use Room-first pattern.
     * When true, block operations use Room and sync workers.
     * When false, uses legacy direct Firestore writes.
     *
     * Default: Follows master switch
     */
    @JvmField
    var FIX_BLOCK_REPOSITORY: Boolean = ROOM_FIRST_ENABLED

    /**
     * Fix FollowRepository to use Room-first pattern.
     * When true, follow operations use Room and sync workers.
     * When false, uses legacy direct Firestore writes.
     *
     * Default: Follows master switch
     */
    @JvmField
    var FIX_FOLLOW_REPOSITORY: Boolean = ROOM_FIRST_ENABLED

    /**
     * Fix ReportRepository to use Room-first pattern.
     * When true, reports are stored locally and synced by workers.
     * When false, uses legacy direct Firestore writes.
     *
     * Default: Follows master switch
     */
    @JvmField
    var FIX_REPORT_REPOSITORY: Boolean = ROOM_FIRST_ENABLED

    /**
     * Fix SocialRepository to use Room-first pattern.
     * When true, social writes are local-only with worker sync.
     * When false, uses legacy direct Firestore writes.
     *
     * Default: Follows master switch
     */
    @JvmField
    var FIX_SOCIAL_REPOSITORY: Boolean = ROOM_FIRST_ENABLED

    /**
     * Fix ProfileSearchRepository to use Room-first pattern.
     * When true, analytics and report writes are local-only with worker sync.
     * When false, uses legacy direct Firestore writes.
     *
     * Default: Follows master switch
     */
    @JvmField
    var FIX_PROFILE_SEARCH_REPOSITORY: Boolean = ROOM_FIRST_ENABLED

    /**
     * Fix AchievementRepository to use Room-first pattern for deletions.
     * When true, deletes become soft deletes with sync queue.
     * When false, uses legacy direct Firestore deletes.
     *
     * Default: Follows master switch
     */
    @JvmField
    var FIX_ACHIEVEMENT_REPOSITORY: Boolean = ROOM_FIRST_ENABLED

    /**
     * Enable JSON consistency validation between exercises_json and normalized tables.
     */
    @JvmField
    var ENABLE_JSON_CONSISTENCY_VALIDATION: Boolean = ROOM_FIRST_ENABLED

    /**
     * Enable canonical JSON format for exercises_json (guarded for rollout).
     */
    @JvmField
    var ENABLE_CANONICAL_JSON_FORMAT: Boolean = false

    /**
     * Enable asynchronous database integrity checks on background workers.
     */
    @JvmField
    var ENABLE_ASYNC_INTEGRITY_CHECKS: Boolean = ROOM_FIRST_ENABLED

    /**
     * Enable HMAC signature verification for remote data.
     * When true, all upsertFromRemote() methods verify HMAC-SHA256 signatures.
     * When false, accepts remote data without cryptographic integrity checks.
     *
     * Security: Prevents data tampering attacks (SEC-001, SEC-004)
     * Default: false (requires server-side HMAC generation first)
     */
    @JvmField
    var ENABLE_HMAC_VERIFICATION: Boolean = false

    /**
     * Enforce strict user scoping lint rules during build.
     */
    @JvmField
    var STRICT_USER_SCOPING_LINT: Boolean = true

    /**
     * Enable verbose logging for Room-first operations.
     * Useful for debugging sync behavior in development.
     *
     * Default: false (enable manually for debugging)
     */
    @JvmField
    var VERBOSE_SYNC_LOGGING: Boolean = false

    /**
     * Get current configuration summary for logging.
     */
    fun getConfigSummary(): String {
        return """
            Room-First Architecture Configuration:
            - Master Switch: ${if (ROOM_FIRST_ENABLED) "✅ ENABLED" else "⚠️ DISABLED (LEGACY MODE)"}
            - Firestore Persistence: ${if (DISABLE_FIRESTORE_PERSISTENCE) "DISABLED (Room authority)" else "ENABLED (Dual authority)"}
            - Dirty Flag Gating: ${if (USE_DIRTY_FLAG_GATING) "ENABLED" else "DISABLED"}
            - Idempotent Listeners: ${if (USE_IDEMPOTENT_LISTENERS) "ENABLED" else "DISABLED"}
            - Auth Repository Fix: ${if (FIX_AUTH_REPOSITORY) "ENABLED" else "DISABLED"}
            - Search Repository Fix: ${if (FIX_SEARCH_REPOSITORY) "ENABLED" else "DISABLED"}
            - Profile Repository Fix: ${if (FIX_PROFILE_REPOSITORY) "ENABLED" else "DISABLED"}
            - Custom Exercise Repository Fix: ${if (FIX_CUSTOM_EXERCISE_REPOSITORY) "ENABLED" else "DISABLED"}
            - Block Repository Fix: ${if (FIX_BLOCK_REPOSITORY) "ENABLED" else "DISABLED"}
            - Follow Repository Fix: ${if (FIX_FOLLOW_REPOSITORY) "ENABLED" else "DISABLED"}
            - Report Repository Fix: ${if (FIX_REPORT_REPOSITORY) "ENABLED" else "DISABLED"}
            - Social Repository Fix: ${if (FIX_SOCIAL_REPOSITORY) "ENABLED" else "DISABLED"}
            - Profile Search Repository Fix: ${if (FIX_PROFILE_SEARCH_REPOSITORY) "ENABLED" else "DISABLED"}
            - Achievement Repository Fix: ${if (FIX_ACHIEVEMENT_REPOSITORY) "ENABLED" else "DISABLED"}
            - JSON Consistency Validation: ${if (ENABLE_JSON_CONSISTENCY_VALIDATION) "ENABLED" else "DISABLED"}
            - Canonical JSON Format: ${if (ENABLE_CANONICAL_JSON_FORMAT) "ENABLED" else "DISABLED"}
            - Async Integrity Checks: ${if (ENABLE_ASYNC_INTEGRITY_CHECKS) "ENABLED" else "DISABLED"}
            - HMAC Verification: ${if (ENABLE_HMAC_VERIFICATION) "ENABLED" else "DISABLED"}
            - Strict User Scoping Lint: ${if (STRICT_USER_SCOPING_LINT) "ENABLED" else "DISABLED"}
            - Verbose Logging: ${if (VERBOSE_SYNC_LOGGING) "ENABLED" else "DISABLED"}
        """.trimIndent()
    }

    /**
     * Check if running in full Room-First mode.
     */
    fun isFullRoomFirstMode(): Boolean {
        return ROOM_FIRST_ENABLED &&
               DISABLE_FIRESTORE_PERSISTENCE &&
               USE_DIRTY_FLAG_GATING &&
               USE_IDEMPOTENT_LISTENERS
    }

    /**
     * Check if running in legacy dual-authority mode.
     */
    fun isLegacyMode(): Boolean {
        return !ROOM_FIRST_ENABLED ||
               !DISABLE_FIRESTORE_PERSISTENCE
    }
}
