# Room-First Offline Architecture - Code Review Checklist

**SPEC**: SPEC-20241228-offline-first-architecture-fix.md
**Implementation Date**: 2025-12-28
**Reviewer**: _________________
**Date**: _________________

## Purpose
This checklist validates that the Room-First Offline Architecture has been correctly implemented across all layers of the codebase. Use this during code reviews to ensure architectural compliance and prevent regressions.

---

## Phase 1: Database Layer (Foundation)

### Entity Schema
- [ ] All 37 syncable entities have `isDirty: Boolean` field
- [ ] All 37 syncable entities have `lastModified: Long` field
- [ ] Default values: `isDirty = false`, `lastModified = 0L`
- [ ] No entities use `SyncSource` enum (optional field, not required)

**Files to Check:**
- `app/src/main/java/com/example/liftrix/data/local/entity/*Entity.kt`

### DAO Methods - Origin-Aware Writes
- [ ] All 36 syncable DAOs have `suspend fun upsertLocal(entity: *Entity)`
- [ ] `upsertLocal` sets `isDirty = true` and `lastModified = System.currentTimeMillis()`
- [ ] All 36 syncable DAOs have `suspend fun upsertFromRemote(entity: *Entity)`
- [ ] `upsertFromRemote` checks timestamps (`remote.lastModified > local.lastModified`)
- [ ] `upsertFromRemote` sets `isDirty = false` and `isSynced = true`
- [ ] All DAOs have internal `@Insert private suspend fun _insert(entity: *Entity)` helper

**Files to Check:**
- `app/src/main/java/com/example/liftrix/data/local/dao/*Dao.kt`

**Validation Command:**
```bash
rg "suspend fun upsertLocal" app/src/main/java/com/example/liftrix/data/local/dao/ | wc -l
# Expected: ~36 matches (one per syncable DAO)

rg "suspend fun upsertFromRemote" app/src/main/java/com/example/liftrix/data/local/dao/ | wc -l
# Expected: ~36 matches
```

### DAO Methods - Sync Queries
- [ ] All 36 syncable DAOs have `getDirty*` queries (e.g., `getDirtyWorkouts`)
- [ ] All `getDirty*` queries filter by `is_dirty = 1` AND `user_id = :userId`
- [ ] All 36 syncable DAOs have `markAsClean(ids: List<String>, userId: String)` methods
- [ ] `markAsClean` updates: `is_dirty = 0`, `is_synced = 1`, `sync_version = :version`
- [ ] All legacy `markAsSynced` methods removed from DAOs (validation: `rg "markAsSynced"` returns 0 matches)

**Files to Check:**
- `app/src/main/java/com/example/liftrix/data/local/dao/*Dao.kt`

**Validation Command:**
```bash
rg "getDirty\w+" app/src/main/java/com/example/liftrix/data/local/dao/ | wc -l
# Expected: ~36 matches

rg "markAsClean" app/src/main/java/com/example/liftrix/data/local/dao/ | wc -l
# Expected: ~36 matches

rg "markAsSynced" app/src/main/java/com/example/liftrix/data/local/dao/
# Expected: 0 matches (all removed)
```

---

## Phase 2: Sync Infrastructure

### Feature Flags
- [ ] `OfflineArchitectureFlags.kt` exists with `ROOM_FIRST_ENABLED` master switch
- [ ] All granular flags present: `DISABLE_FIRESTORE_PERSISTENCE`, `USE_DIRTY_FLAG_GATING`, etc.
- [ ] All flags default to `true` (Room-first mode active)
- [ ] `getConfigSummary()` method exists for debugging

**File to Check:**
- `app/src/main/java/com/example/liftrix/config/OfflineArchitectureFlags.kt`

### Firebase Module
- [ ] `provideFirebaseFirestore()` conditionally disables persistence based on `DISABLE_FIRESTORE_PERSISTENCE`
- [ ] Logging present: "✅ ROOM-FIRST MODE" or "⚠️ LEGACY MODE"
- [ ] Cache size set to 0 when persistence disabled

**File to Check:**
- `app/src/main/java/com/example/liftrix/di/FirebaseModule.kt:120-145`

### Sync Workers
- [ ] All 13+ sync workers use `getDirty*()` queries instead of `getUnsynced*()`
- [ ] All workers call `markAsClean()` after successful Firestore upload
- [ ] All workers use `upsertFromRemote()` for download/restore paths
- [ ] All workers have timestamp conflict detection (skip upload if remote is newer)
- [ ] Feature flag logging present (ROOM-FIRST vs LEGACY mode)
- [ ] Legacy code paths preserved with feature flag gating

**Files to Check:**
- `app/src/main/java/com/example/liftrix/sync/WorkoutSyncWorker.kt`
- `app/src/main/java/com/example/liftrix/sync/ProfileSyncWorker.kt`
- `app/src/main/java/com/example/liftrix/sync/TemplateSyncWorker.kt`
- `app/src/main/java/com/example/liftrix/sync/AchievementSyncWorker.kt`
- `app/src/main/java/com/example/liftrix/sync/FollowRelationshipSyncWorker.kt`
- `app/src/main/java/com/example/liftrix/sync/GymBuddySyncWorker.kt`
- `app/src/main/java/com/example/liftrix/sync/WorkoutPostSyncWorker.kt`
- `app/src/main/java/com/example/liftrix/sync/SocialProfileSyncWorker.kt`
- `app/src/main/java/com/example/liftrix/sync/ChatSyncWorker.kt`
- `app/src/main/java/com/example/liftrix/sync/SettingsSyncWorkerV2.kt`
- Plus 3 more workers

**Validation Command:**
```bash
rg "getDirty\w+\(" app/src/main/java/com/example/liftrix/sync/ | wc -l
# Expected: 13+ matches (one per worker)

rg "markAsClean\(" app/src/main/java/com/example/liftrix/sync/ | wc -l
# Expected: 13+ matches

rg "ROOM-FIRST|LEGACY MODE" app/src/main/java/com/example/liftrix/sync/ | wc -l
# Expected: 26+ matches (logging in each worker)
```

### Sync Infrastructure Components
- [ ] `SyncOperationManager` updated with dirty-flag gating for workouts/templates/social/achievements
- [ ] `SyncOperationManager.markAsClean()` called after successful Firestore batch commit
- [ ] `SyncManager` returns 0 in dirty-gating mode (validation: check `countUnsyncedWorkouts`)
- [ ] No references to `markAsSynced()` in sync infrastructure

**Files to Check:**
- `app/src/main/java/com/example/liftrix/sync/SyncOperationManager.kt`
- `app/src/main/java/com/example/liftrix/sync/SyncManager.kt`

---

## Phase 3: Repository Layer

### Repository Firestore Bypass Elimination
- [ ] `AuthRepositoryImpl.createUserProfile()` uses Room-first pattern with `FIX_AUTH_REPOSITORY` flag
- [ ] `AuthRepositoryImpl.updateLastSignInTime()` uses `userAccountDao.upsertLocal()`
- [ ] `UserSearchRepositoryImpl` QR operations use `qrCodeDao.upsertLocal()`
- [ ] `UserSearchRepositoryImpl` search cache uses `userSearchCacheDao.upsertLocal()`
- [ ] `UserSearchRepositoryImpl` profile views use `profileViewDao.upsertLocal()`
- [ ] `ProfileRepositoryImpl.deleteProfile()` queues sync operation (no soft delete)
- [ ] `CustomExerciseRepositoryImpl` uses `customExerciseDao.upsertLocal()` with `FIX_CUSTOM_EXERCISE_REPOSITORY`
- [ ] `BlockRepositoryImpl` uses `blockedUserDao.upsertLocal()` with `FIX_BLOCK_REPOSITORY`
- [ ] `ReportRepositoryImpl` uses `contentReportsDao.upsertLocal()` with `FIX_REPORT_REPOSITORY`
- [ ] `AchievementRepositoryImpl.deleteAchievement()` queues sync operation with `FIX_ACHIEVEMENT_REPOSITORY`
- [ ] `FollowRepositoryImpl` uses `followRelationshipDao.upsertLocal()` with `FIX_FOLLOW_REPOSITORY`
- [ ] `ProfileSearchRepositoryImpl` uses Room DAOs with `FIX_PROFILE_SEARCH_REPOSITORY`
- [ ] `SocialRepositoryImpl` uses Room-first pattern with `FIX_SOCIAL_REPOSITORY`
- [ ] All repositories have legacy Firestore paths preserved with feature flag gating
- [ ] No direct `firestore.set()`, `firestore.update()`, `firestore.batch().set()` calls in repositories

**Files to Check:**
- `app/src/main/java/com/example/liftrix/data/repository/AuthRepositoryImpl.kt`
- `app/src/main/java/com/example/liftrix/data/repository/UserSearchRepositoryImpl.kt`
- `app/src/main/java/com/example/liftrix/data/repository/ProfileRepositoryImpl.kt`
- `app/src/main/java/com/example/liftrix/data/repository/CustomExerciseRepositoryImpl.kt`
- `app/src/main/java/com/example/liftrix/data/repository/social/BlockRepositoryImpl.kt`
- `app/src/main/java/com/example/liftrix/data/repository/social/ReportRepositoryImpl.kt`
- `app/src/main/java/com/example/liftrix/data/repository/AchievementRepositoryImpl.kt`
- `app/src/main/java/com/example/liftrix/data/repository/social/FollowRepositoryImpl.kt`
- `app/src/main/java/com/example/liftrix/data/repository/social/ProfileSearchRepositoryImpl.kt`
- `app/src/main/java/com/example/liftrix/data/repository/SocialRepositoryImpl.kt`

**Validation Command:**
```bash
# Should return 0 matches (no direct Firestore writes)
rg "firestore\.(set|update|delete|batch)\(" app/src/main/java/com/example/liftrix/data/repository/ --type kotlin

# Should return matches for legacy encapsulation
rg "LegacySocialFirestoreDataSource" app/src/main/java/com/example/liftrix/data/repository/
```

---

## Phase 4: Real-Time Listeners

### Idempotent Listener Pattern
- [ ] `EngagementRealtimeSyncService.updateEngagementFromRemote()` uses `upsertEngagementFromRemote()`
- [ ] `FollowRealtimeService` uses `followRelationshipDao.upsertFromRemote()`
- [ ] `CommentSyncService` uses `commentDao.upsertFromRemote()` for ADDED/MODIFIED events
- [ ] `PostEngagementListener` uses `postLikeDao.upsertFromRemote()`
- [ ] `RealtimeSyncService.processWorkoutUpdate()` uses `workoutDao.upsertFromRemote()`
- [ ] All listeners check `USE_IDEMPOTENT_LISTENERS` flag
- [ ] No sync operations triggered after listener writes (validation: no `syncCoordinator.trigger*` calls)

**Files to Check:**
- `app/src/main/java/com/example/liftrix/sync/EngagementRealtimeSyncService.kt`
- `app/src/main/java/com/example/liftrix/sync/FollowRealtimeService.kt`
- `app/src/main/java/com/example/liftrix/data/remote/realtime/CommentSyncService.kt`
- `app/src/main/java/com/example/liftrix/data/remote/realtime/PostEngagementListener.kt`
- `app/src/main/java/com/example/liftrix/data/sync/RealtimeSyncService.kt`

**Validation Command:**
```bash
# Should return matches for upsertFromRemote calls
rg "upsertFromRemote|upsertEngagementFromRemote" app/src/main/java/com/example/liftrix/sync/
rg "upsertFromRemote|upsertEngagementFromRemote" app/src/main/java/com/example/liftrix/data/remote/realtime/
rg "upsertFromRemote|upsertEngagementFromRemote" app/src/main/java/com/example/liftrix/data/sync/

# Should return 0 matches (no sync triggers in listeners)
rg "syncCoordinator\.trigger" app/src/main/java/com/example/liftrix/sync/EngagementRealtimeSyncService.kt
rg "syncCoordinator\.trigger" app/src/main/java/com/example/liftrix/data/remote/realtime/CommentSyncService.kt
```

---

## Phase 5: Integration & Testing

### getInstance() Calls Removed
- [ ] `MasterSyncWorker` injects `FirebaseFirestore` and `FirebaseAuth` via constructor
- [ ] `SupportServiceImpl` injects `FirebaseFirestore` via constructor
- [ ] No other direct `getInstance()` calls in sync/repository layers

**Files to Check:**
- `app/src/main/java/com/example/liftrix/sync/MasterSyncWorker.kt`
- `app/src/main/java/com/example/liftrix/data/service/SupportServiceImpl.kt`

**Validation Command:**
```bash
rg "FirebaseFirestore\.getInstance\(\)|FirebaseAuth\.getInstance\(\)" app/src/main/java/com/example/liftrix/sync/
rg "FirebaseFirestore\.getInstance\(\)|FirebaseAuth\.getInstance\(\)" app/src/main/java/com/example/liftrix/data/repository/
# Expected: 0 matches (all use DI)
```

### Logging
- [ ] `LiftrixApp.onCreate()` calls `logOfflineArchitectureMode()`
- [ ] Startup logging shows Room-first mode status and all feature flags
- [ ] `FirebaseModule` logs persistence configuration
- [ ] All sync workers log Room-first vs legacy mode

**Files to Check:**
- `app/src/main/java/com/example/liftrix/LiftrixApp.kt`
- `app/src/main/java/com/example/liftrix/di/FirebaseModule.kt`

### Integration Tests
- [ ] `RoomFirstIntegrationTest.kt` exists with 7 test scenarios
- [ ] `OfflineOnlineScenarioTest.kt` exists with 6 offline-online workflow tests
- [ ] `FeedbackLoopDetectionTest.kt` exists with 7 feedback loop prevention tests
- [ ] All tests pass (run `./gradlew connectedAndroidTest`)

**Files to Check:**
- `app/src/androidTest/java/com/example/liftrix/sync/RoomFirstIntegrationTest.kt`
- `app/src/androidTest/java/com/example/liftrix/sync/OfflineOnlineScenarioTest.kt`
- `app/src/androidTest/java/com/example/liftrix/sync/FeedbackLoopDetectionTest.kt`

### Documentation
- [ ] `CLAUDE.md` updated with Room-First Architecture section
- [ ] DAO pattern examples documented
- [ ] Sync worker pattern examples documented
- [ ] Real-time listener pattern examples documented
- [ ] Repository pattern examples documented
- [ ] Feature flags documented
- [ ] Validation commands documented

**File to Check:**
- `CLAUDE.md` (lines 42-176)

### Code Review Checklist
- [ ] This checklist exists: `ROOM_FIRST_CODE_REVIEW_CHECKLIST.md`
- [ ] Checklist covers all 5 implementation phases
- [ ] Validation commands provided for automated checks

---

## Automated Validation Suite

Run these commands to perform automated architectural validation:

### 1. Verify Origin-Aware DAOs
```bash
# Count upsertLocal methods (should be ~36)
rg "suspend fun upsertLocal" app/src/main/java/com/example/liftrix/data/local/dao/ | wc -l

# Count upsertFromRemote methods (should be ~36)
rg "suspend fun upsertFromRemote" app/src/main/java/com/example/liftrix/data/local/dao/ | wc -l

# Count getDirty* queries (should be ~36)
rg "getDirty\w+" app/src/main/java/com/example/liftrix/data/local/dao/ | wc -l

# Count markAsClean methods (should be ~36)
rg "markAsClean" app/src/main/java/com/example/liftrix/data/local/dao/ | wc -l

# Verify no legacy markAsSynced (should be 0)
rg "markAsSynced" app/src/main/java/com/example/liftrix/data/local/dao/
```

### 2. Verify Sync Worker Compliance
```bash
# Verify dirty flag usage in workers (should be 13+)
rg "getDirty\w+" app/src/main/java/com/example/liftrix/sync/ | wc -l

# Verify markAsClean calls in workers (should be 13+)
rg "markAsClean" app/src/main/java/com/example/liftrix/sync/ | wc -l

# Verify Room-first logging (should be 26+)
rg "ROOM-FIRST|LEGACY MODE" app/src/main/java/com/example/liftrix/sync/ | wc -l
```

### 3. Verify Repository Compliance
```bash
# Should return 0 matches (no direct Firestore writes)
rg "firestore\.(set|update|delete|batch)\(" app/src/main/java/com/example/liftrix/data/repository/ --type kotlin

# Should return ~10 matches (Room-first feature flags)
rg "if \(OfflineArchitectureFlags\.FIX_\w+_REPOSITORY\)" app/src/main/java/com/example/liftrix/data/repository/
```

### 4. Verify Listener Idempotency
```bash
# Should return matches for idempotent writes
rg "upsertFromRemote|upsertEngagementFromRemote" app/src/main/java/com/example/liftrix/sync/
rg "upsertFromRemote|upsertEngagementFromRemote" app/src/main/java/com/example/liftrix/data/remote/realtime/
```

### 5. Run Integration Tests
```bash
# Run all Room-first integration tests
./gradlew connectedAndroidTest --tests "com.example.liftrix.sync.RoomFirstIntegrationTest"
./gradlew connectedAndroidTest --tests "com.example.liftrix.sync.OfflineOnlineScenarioTest"
./gradlew connectedAndroidTest --tests "com.example.liftrix.sync.FeedbackLoopDetectionTest"
```

---

## Rollback Validation

### Feature Flag Switches
- [ ] Setting `ROOM_FIRST_ENABLED = false` reverts to legacy mode
- [ ] Setting individual flags to `false` reverts specific features
- [ ] Legacy code paths still functional (no regressions)

### Rollback Testing
- [ ] Test app with `DISABLE_FIRESTORE_PERSISTENCE = false` (dual authority)
- [ ] Test app with `USE_DIRTY_FLAG_GATING = false` (old sync)
- [ ] Test app with `USE_IDEMPOTENT_LISTENERS = false` (old listeners)
- [ ] Test app with all repository flags set to `false` (direct Firestore writes)
- [ ] Verify app functions normally in all rollback modes

---

## Sign-Off

### Database Layer
- [ ] All entity schemas validated
- [ ] All DAO methods validated
- [ ] No legacy methods remaining

**Reviewer**: _________________ **Date**: _________

### Sync Infrastructure
- [ ] Feature flags validated
- [ ] Firebase module validated
- [ ] All sync workers validated

**Reviewer**: _________________ **Date**: _________

### Repository Layer
- [ ] All repositories validated
- [ ] No Firestore bypass detected
- [ ] Feature flag gating verified

**Reviewer**: _________________ **Date**: _________

### Real-Time Listeners
- [ ] All listeners validated
- [ ] Idempotency verified
- [ ] No feedback loops detected

**Reviewer**: _________________ **Date**: _________

### Integration & Testing
- [ ] getInstance() removal validated
- [ ] Logging validated
- [ ] All tests passing
- [ ] Documentation updated

**Reviewer**: _________________ **Date**: _________

### Final Approval
- [ ] All automated validation commands executed successfully
- [ ] All manual checks completed
- [ ] All tests passing
- [ ] Documentation complete
- [ ] Rollback paths verified

**Final Approver**: _________________ **Date**: _________

---

## Notes & Issues Found

_Use this section to document any deviations, issues, or special cases discovered during review:_

---

**End of Checklist**
