# Sync, Offline, Firebase, and Storage Patterns

Last moved from root `AGENTS.md`: 2026-05-05. Source audit baseline: 2026-05-01.

Use this when touching Room/Firebase sync, storage uploads, WorkManager, FCM, offline queues, restore gates, or remote/local reconciliation.

## Core Rules

- Room is the source of truth. UI reads local state through ViewModels/use cases/repositories.
- Writes go local first, then queue or trigger background sync.
- Do not call Firebase directly from UI.
- Always preserve `userId`/`user_id` ownership checks for user data.
- `SyncCoordinator.triggerImmediateSync(userId)` is a no-op success while `StartupRestoreGate` has not completed.
- Add sync metadata for synced entities: `isSynced`, `syncVersion`, `lastModified`, and dirty/failure fields as the local entity pattern requires.
- Verify per entity whether it implements `SyncableEntity`; source currently mixes interface-based and field-based metadata.

## Main Flow

```text
Room database
  -> SyncQueueEntity / OfflineQueueManager
  -> WorkManager
  -> UnifiedSyncWorker or startup-specialized workers
  -> FirebaseDataSource / Firestore / Storage
  -> local clean, failed, or dead-letter state
```

Typical mutation path:

```text
Composable event
  -> ViewModel
  -> use case validation/auth checks
  -> repository implementation
  -> Room DAO local upsert/update
  -> OfflineQueueManager and/or SyncCoordinator
  -> worker remote write
```

## Key Components

- `SyncCoordinator`: schedules periodic, immediate, startup, entity-specific, and realtime sync.
- `UnifiedSyncWorker`: current default for periodic, immediate, and entity-specific sync when `USE_UNIFIED_SYNC` is true.
- `MasterSyncWorker`: legacy/fallback orchestrator, not the primary periodic path while unified sync is enabled.
- `BaseSyncWorker`: shared worker behavior and default batch sizing.
- `SyncOperationManager`: entity-specific priority and batch sizes.
- `OfflineQueueManager`: sync queue insert/process/retry/dead-letter behavior.
- `StartupRestoreGate`: blocks immediate sync until restore completes.
- `RealtimeSyncService`: active-session realtime listener bridge into Room.
- `domain/sync/ConflictResolver.kt`: `SyncableEntity` contract and last-write-wins resolver.

## Workers and Scheduling

- Periodic sync runs every 15 minutes and uses `UnifiedSyncWorker` when enabled.
- Immediate sync uses `UnifiedSyncWorker`, except it exits as a no-op while startup restore is incomplete.
- Entity-specific sync uses `UnifiedSyncWorker` when enabled and falls back to specialized workers otherwise.
- Startup/login sync uses a specialized chain: workout restore, template restore, then profile, user public, follow restore, workout post, and achievement workers.
- Entity workers include workout, template, achievement, profile, social profile, follow relationship, workout post, gym buddy, and settings/unit preferences paths.

## Firestore Shape

```text
/users/{userId}
  /workouts/{workoutId}
  /templates/{templateId}
  /achievements/{achievementId}
  /gym_buddies/{buddyId}
  /settings/preferences
/social_profiles/{userId}
/follow_relationships/{id}
/workout_posts/{postId}
```

Treat backend deployment freshness as `Needs verification` unless the task verifies Firebase/GCP state.

## Conflict and Retry Rules

- Last-write-wins compares `lastModified` when both sides have changes.
- Failed sync operations must keep enough metadata for retry/dead-letter diagnosis.
- Use configured worker batch sizes. Root guidance notes `BaseSyncWorker` default 20 and `SyncOperationManager` entity-specific sizes.
- Do not mark local data clean until remote success is confirmed.

## Storage and Media

- Profile/media upload flows go through storage/media services, not UI Firebase calls.
- Profile image upload timeout guidance from existing docs is 30 seconds.
- Media upload pipeline is service-owned: compression/thumbnails, Firebase Storage, URL persistence, background progress where applicable.

## Notifications and FCM

- `LiftrixFirebaseMessagingService.onNewToken` must update token storage on refresh.
- Notification routing should respect priority, privacy filters, quiet hours, user mutes/preferences, and batching.
- Batch similar social notifications instead of sending many single notifications.
- Notification actions such as PR celebration and follow acceptance should route through service/use-case paths, not UI-only state.

## Debug Checklist

- Pending work: inspect `sync_queue` by `user_id`.
- Restore gate: confirm `StartupRestoreGate` state when immediate sync appears skipped.
- Worker status: inspect WorkManager unique names/tags and constraints.
- Data not appearing: check `isSynced`, `syncVersion`, dirty flags, and remote/local upsert path.
- Conflict issues: inspect `lastModified` and `LastWriteWinsResolver`.
- Realtime issues: verify listener lifecycle and Room idempotent remote upserts.
- Partial sync: check batch size, retry class, and dead-letter state.

## Unclear but Preserved Guidance

- Some source/docs refer to both interface-based `SyncableEntity` and field-only metadata. Do not normalize this without a source audit and migration plan.
- `fallbackToDestructiveMigration()` is configured with database version 9. Treat schema changes as production-risk work requiring explicit migration review.
