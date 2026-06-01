# Sync, Offline, Firebase, and Storage Patterns

Last moved from root `AGENTS.md`: 2026-05-05. Source refresh: 2026-05-31.

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
Room database (:core:database)
  -> SyncQueueEntity / OfflineQueueManager (:core:data)
  -> SyncCoordinator (:core:sync)
  -> WorkManager
  -> UnifiedSyncWorker (:core:sync)
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

Current persistence baseline:

- `LiftrixDatabase` lives in `:core:database`, is Room version 10, and exports schemas to `core/database/schemas/com.example.liftrix.data.local.LiftrixDatabase/`.
- The active database registration contains 69 entities, 68 DAO accessors, and 2 database views: `CompletedWorkoutMetricsView` and `ExerciseSetPerformanceView`.
- `DatabaseModule` creates the encrypted SQLCipher database named `liftrix_database_encrypted` and registers `MIGRATION_7_8`, `MIGRATION_8_9`, and `MIGRATION_9_10`.

## Key Components

- `core/sync/.../SyncCoordinator`: the only public entry point for periodic, immediate, startup, entity-specific, and realtime-triggered sync scheduling.
- `core/sync/.../UnifiedSyncWorker`: the only default WorkManager sync worker. It drains `SyncQueueEntity` items first, then runs internal entity processors through `SyncOperationManager`.
- `core/sync/.../MasterSyncWorker`: compatibility wrapper only; any entity work it schedules must map back to `UnifiedSyncWorker`.
- `core/sync/.../BaseSyncWorker`: shared worker behavior and default batch sizing.
- `core/sync/.../SyncOperationManager`: internal entity processors, priority ordering, batch sizes, and startup remote restore.
- `core/data/.../OfflineQueueManager`: sync queue insert/process/retry/dead-letter behavior.
- `core/sync/.../StartupRestoreGate`: blocks immediate sync until restore completes.
- `core/sync/.../RealtimeSyncService`: active-session realtime listener bridge into Room.
- Conflict contracts/resolvers live in domain/sync-oriented packages; verify the current package before editing because sync code has been modularized.

## Workers and Scheduling

- Periodic sync runs every 15 minutes through `SyncCoordinator.schedulePeriodicSync(userId)` and uses `UnifiedSyncWorker`.
- Immediate sync runs through `SyncCoordinator.triggerImmediateSync(userId)`, except it exits as a no-op while startup restore is incomplete.
- Entity-specific sync runs through `SyncCoordinator.triggerEntitySync(userId, entityType)` or the non-suspending `enqueueEntitySync(...)` adapter, both targeting `UnifiedSyncWorker`.
- Startup/login sync inserts prioritized `FETCH` queue records and schedules one startup `UnifiedSyncWorker`; it no longer launches a worker chain.
- Realtime listeners remain listener-owned, but any background retry or analytics recalculation request routes through `SyncCoordinator`.
- Legacy entity workers remain loadable only for already-enqueued WorkManager compatibility. New default scheduling must not enqueue them directly.

## Sync Entry Point Inventory

- App startup/login: `SyncScheduler.triggerStartupSync(...)` -> `SyncCoordinator.triggerStartupSync(...)` -> startup queue rows + startup `UnifiedSyncWorker`.
- Login/account recovery: auth repositories and onboarding use cases call `SyncScheduler.triggerImmediateSync(...)`, `triggerEntitySync("profile")`, or profile repository queue methods; these now route through `WorkManagerSyncScheduler` to `SyncCoordinator`.
- Logout: `SyncScheduler.cancelSyncForUser(...)` -> `SyncCoordinator.cancelSyncForUser(...)`, including cancellation of old unique work names for migration cleanup.
- Workout save/profile change/social actions/settings change: repository writes stay Room-first, enqueue/update local dirty state or `SyncQueueEntity`, then call `SyncScheduler` entity/immediate methods backed by `SyncCoordinator`.
- Network restored/periodic/manual refresh: `SyncCoordinator` schedules or enqueues `UnifiedSyncWorker`; `SyncManager` status/sync APIs delegate scheduling back to `SyncCoordinator`.
- Realtime sync: `RealtimeSyncManager` owns Firestore listeners only; scheduling requests are delegated to `SyncCoordinator`.

## Queue Contract

`sync_queue` is the durable contract for offline actions:

- Scope every row by `user_id`.
- Logical de-duplication key is `user_id + entity_type + entity_id`; newer writes coalesce onto an existing pending item instead of creating duplicate work.
- `entity_type` and `operation` are normalized to uppercase.
- Priority is ascending: `1` critical, `2` high, `3` medium, `4` low.
- Supported operations are `CREATE`, `UPDATE`, `DELETE`, and `FETCH`.
- Worker processing order is priority first, then creation time.
- Success marks the item processed; retryable failure increments retry state; terminal failure moves through the existing dead-letter path.

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

## Migration Notes

- Already-enqueued legacy entity workers may still run until WorkManager drains or cancels them; new scheduling code cancels known old unique work names during user/all-sync cancellation.
- The legacy worker classes remain present so old WorkManager specs can deserialize safely after an app update.
- `MasterSyncWorker`, `SyncManager`, `WorkManagerSyncScheduler`, and realtime scheduling paths should route any new work to `SyncCoordinator`/`UnifiedSyncWorker`.
- Cleanup, export, widget refresh, notification action, database integrity, and dead-letter maintenance workers are not user data sync paths; do not use them as entity sync fallbacks.

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
- Room is at database version 10. Current app DI registers migrations `7->8`, `8->9`, and `9->10`, and no `fallbackToDestructiveMigration()` call was found during the 2026-05-31 refresh. Treat schema changes and older-version upgrade support as production-risk work requiring explicit migration review.
