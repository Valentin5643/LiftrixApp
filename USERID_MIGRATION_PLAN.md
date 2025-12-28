# Incremental UserId Value Class Migration Plan

This plan reflects the current codebase state and adapts the prompt phases to match actual DAO names and existing UserId implementations.

## Status Tracker (mark completed after each phase)
- [ ] Phase 0: Foundation setup + type consolidation
- [ ] Phase 1: Core workout DAOs
- [ ] Phase 2: Social + profile DAOs
- [ ] Phase 3: Progress + analytics DAOs
- [ ] Phase 4: Sync + notifications + metadata DAOs
- [ ] Phase 5: Remaining DAOs (misc)
- [ ] Phase 6: Cleanup + docs + verification

## Analysis Snapshot (current repo)
- DAO files found: 65 (`app/src/main/java/com/example/liftrix/data/local/dao`)
- DAOs with `userId: String` parameters: 59 (per `rg -l "userId: String"`)
- Existing `UserId` value classes:
  - `app/src/main/java/com/example/liftrix/core/identity/UserId.kt` (rich validation + LiftrixResult factory)
  - `app/src/main/java/com/example/liftrix/domain/model/UserId.kt` (lightweight, likely unused)
- Existing `UserId` usage already references `com.example.liftrix.core.identity.UserId` in multiple places (e.g., AuthRepository, ViewModels).
- No `UserSession` class exists yet.
- Prompt DAO names not present in repo:
  - `SetDao` -> actual `ExerciseSetDao`
  - `WorkoutSessionDao` -> no direct equivalent; candidates: `GuestSessionDao`, `QRCodeSessionDao` (confirm intent)
  - `UserSettingsDao` -> actual `SettingsDao`, `NotificationPreferenceDao`, `SyncPreferencesDao`, `PrivacySettingsDao`
  - `SyncMetadataDao` / `ConflictResolutionDao` / `CacheInvalidationDao` -> not present; closest: `SyncQueueDao`, `DeadLetterQueueDao`, `FeedCacheDao`, `AnalyticsCacheDao`, `UserSearchCacheDao`, `UserProfileCacheDao`

---

## Phase 0: Foundation Setup (Week 1, Day 1-2)
Goal: Establish a single canonical `UserId`, add `UserSession`, and prep auth error reasons.

Checklist:
- [ ] Decide canonical `UserId` type and remove duplication
- [ ] Add `UserSession` provider for current user access
- [ ] Add `USER_NOT_AUTHENTICATED` to auth failure reasons
- [ ] Update references/imports where necessary

Recommended approach based on current code:
- Canonicalize on `com.example.liftrix.core.identity.UserId` (already used in repo).
- Replace `app/src/main/java/com/example/liftrix/domain/model/UserId.kt` with a typealias or deprecation wrapper to avoid dual types.
- Create `app/src/main/java/com/example/liftrix/domain/model/user/UserSession.kt` (or `core/identity/UserSession.kt`) and inject `FirebaseAuth`.
- Add `AuthFailureReason.USER_NOT_AUTHENTICATED` in `LiftrixError` (if no enum exists, create it in the same file).

Validation:
- `./gradlew compileDebugKotlin`
- (Optional) add unit tests for `UserId` if a test module exists

---

## Phase 1: Core Workout DAOs (Week 1, Day 3-5)
Goal: Migrate the most critical workout data flows.

Proposed DAO list (adjusted to actual files):
- `WorkoutDao`
- `ExerciseDao`
- `ExerciseSetDao` (replaces SetDao)
- `WorkoutTemplateDao`
- `WorkoutAnomalyDao` (or `SharedRoutineDao` if that is more critical)

Checklist per DAO:
- [ ] Change DAO signatures from `String` to `UserId`
- [ ] Update repository implementations to inject `UserSession`
- [ ] Update repository interfaces to remove `userId` params where appropriate
- [ ] Update use cases to drop `userId` params
- [ ] Update ViewModels to call use cases without `userId`
- [ ] Fix all compile errors before moving to next DAO

Validation:
- `./gradlew compileDebugKotlin`
- DAO/Repo/ViewModel tests as available

---

## Phase 2: Social + Profile DAOs (Week 2, Day 1-3)
Goal: Migrate social graph, profile, and engagement flows.

Proposed DAO list (adjusted to actual files):
- `SocialProfileDao`
- `FollowRelationshipDao`
- `FollowRequestDao`
- `WorkoutPostDao`
- `PostLikeDao`
- `PostCommentDao`
- `GymBuddyDao`
- `ProfileViewDao`
- `UserProfileDao`
- `UserAccountDao`
- `PrivacySettingsDao` + `SocialPrivacySettingsDao` (if tied to social)

Validation:
- `./gradlew compileDebugKotlin`
- Manual: social feed, profile view/edit, follow/unfollow, buddy flow

---

## Phase 3: Progress + Analytics DAOs (Week 2, Day 4-5)
Goal: Migrate progress tracking and analytics.

Proposed DAO list:
- `PersonalRecordDao`
- `ProgressPhotoDao`
- `ExerciseHistoryDao`
- `ExerciseUsageHistoryDao`
- `ExerciseWeightMemoryDao`
- `AnalyticsCacheDao`
- `WidgetPreferencesDao`

Validation:
- `./gradlew compileDebugKotlin`
- Manual: progress dashboard, analytics charts

---

## Phase 4: Sync + Notifications + Metadata DAOs (Week 3, Day 1-2)
Goal: Migrate sync queues, notifications, and user-scoped metadata.

Proposed DAO list:
- `SyncQueueDao`
- `SyncPreferencesDao`
- `DeadLetterQueueDao`
- `NotificationQueueDao`
- `NotificationHistoryDao`
- `NotificationPreferenceDao`
- `NotificationMuteDao`
- `FCMTokenDao`
- `PRNotificationDao`
- `PRNotificationPreferencesDao`
- `PRReactionDao`

Validation:
- `./gradlew compileDebugKotlin`
- Manual: sync triggers, notification settings, notification history

---

## Phase 5: Remaining DAOs (Week 3-4)
Goal: Finish all remaining user-scoped DAOs not covered above.

Remaining DAO candidates:
- `CustomExerciseDao`
- `ExerciseLibraryDao`
- `FolderDao`
- `SavedPostDao`
- `SharedRoutineDao`
- `FeedCacheDao`
- `UserSearchCacheDao`
- `UserProfileCacheDao`
- `ChatHistoryDao`
- `ChatPreferencesDao`
- `BlockedUserDao`
- `ContentReportsDao`
- `GymBuddyActivityDao`
- `QRCodeMappingDao`
- `QRCodeSessionDao`
- `GuestSessionDao`
- `SettingsDao`
- `SettingsAuditDao`
- `AppConfigDao`
- `SubscriptionDao`
- `DataImportDao`
- `DataExportDao`
- `ExternalShareDao`
- `HelpArticleDao`
- `MetDataDao`
- `SupportTicketDao`
- `MediaItemDao`
- `FriendDao`
- `AnomalyDetectionSettingsDao`

Validation:
- `./gradlew compileDebugKotlin`
- `./gradlew test` (or targeted tests)

---

## Phase 6: Cleanup + Docs + Verification (Week 4, Day 1-2)
Goal: Remove legacy `String` patterns and document the new approach.

Checklist:
- [ ] Eliminate remaining `userId: String` usage in DAO/repo/usecase layers
- [ ] Convert stragglers to `UserId` and `UserSession`
- [ ] Update/author docs (`README.md`, `docs/`, and existing `USERID_*.md` files)
- [ ] Add/adjust lint checks or scripts if desired

Verification:
- `rg "userId: String" app/src/main/java/com/example/liftrix/data/local/dao`
- `./gradlew compileDebugKotlin`
- `./gradlew test`

---

## Notes / Decisions To Confirm Before Phase 1
- Confirm which session DAO (if any) should be in Phase 1.
- Decide if `WorkoutAnomalyDao` or `SharedRoutineDao` is more critical for early migration.
- Confirm canonical `UserId` package (recommended: `core.identity.UserId`).
