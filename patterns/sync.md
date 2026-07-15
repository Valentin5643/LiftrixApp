# Sync, Offline, Firebase, Storage, and Notification Patterns

Last moved from root AGENTS.md: 2026-05-05. Source refresh: 2026-07-14.

Use this document when touching Room/Firebase reconciliation, sync queues, WorkManager workers, restore gates, realtime listeners, storage uploads, FCM, or notification delivery.

## Non-Negotiable Rules

- Room is the source of truth for UI state.
- UI must not call Firebase directly.
- User-owned reads, writes, queue rows, remote paths, and cleanup operations must be scoped by userId/user_id.
- Writes are local first; remote work is scheduled through SyncScheduler/SyncCoordinator and durable queue paths.
- Do not mark local data clean until remote success is confirmed.
- Use configured batch sizes and existing retry/dead-letter behavior.
- Do not add destructive Room fallback.
- Verify per entity whether sync metadata is interface-based or field-based; source intentionally contains both patterns.
- New default sync scheduling must use UnifiedSyncWorker.

## Current Persistence Baseline

| Item | Current source |
| --- | --- |
| Database module | :core:database |
| Database | LiftrixDatabase |
| File name | liftrix_database_encrypted_v14 |
| Room version | 14 |
| Registered entities | 77 |
| Registered views | 2 |
| DAO accessors | 70 |
| Schema snapshots | 1 through 14 |
| Registered migrations | None |
| Supported migration floor | None |
| Destructive fallback | None |

CoreDatabaseModule builds the SQLCipher database. App DatabaseModule provides the encryption/passphrase boundary.

Version 14 adds the durable `ai_usage` ledger and uses a versioned encrypted filename. No in-place migration chain is registered. Any schema change requires:

- a new exported schema;
- a new versioned database filename or a separately approved complete migration strategy;
- explicit review of the unsynced local-data replacement tradeoff and remote restore path;
- `validateRoomQueries`, whose source scanner fails on zero entities, plus Room/KSP compilation for DAO/query validation.

## Canonical Flow

~~~text
UI event
  -> ViewModel
  -> command use case/interactor
  -> repository
  -> user-scoped Room transaction
  -> dirty/sync metadata and/or SyncQueueEntity
  -> OfflineQueueManager
  -> SyncScheduler
  -> SyncCoordinator
  -> UnifiedSyncWorker
  -> SyncOperationManager/FirebaseDataSource
  -> local clean, retry, or dead-letter state
~~~

Remote reads/realtime events follow:

~~~text
Firebase integration boundary
  -> repository/service/realtime/worker
  -> idempotent user-scoped Room upsert
  -> Room Flow
  -> ViewModel/UI
~~~

## Ownership

| Owner | Responsibility |
| --- | --- |
| core/domain/.../SyncScheduler.kt | Public scheduling contract outside :core:sync. |
| core/sync/.../WorkManagerSyncScheduler.kt | Adapter from domain scheduling calls to SyncCoordinator. |
| core/sync/.../SyncCoordinator.kt | Periodic, immediate, startup, entity-specific, cancellation, and realtime scheduling. |
| core/sync/.../UnifiedSyncWorker.kt | Default worker for periodic, immediate, entity, and startup sync. |
| core/sync/.../BaseSyncWorker.kt | Shared validation, cancellation, retry, and worker result behavior. |
| core/sync/.../SyncOperationManager.kt | Priority/entity processing, startup restore, remote/local reconciliation, and batch sizing. |
| core/data/.../OfflineQueueManager.kt | Durable queue insertion, de-duplication, processing, retry, and dead-letter behavior. |
| core/sync/.../StartupRestoreGate.kt | Per-user restore state. |
| core/sync/.../TemplateRestoreNotifier.kt | Template restore completion events. |
| core/data/.../RealtimeSyncService.kt | Active workout Firestore listener bridge into Room. |
| core/sync/.../*SyncWorker.kt | Specialized legacy/compatibility workers. |

There is no current USE_UNIFIED_SYNC flag. SyncCoordinator directly schedules UnifiedSyncWorker for all default paths.

## Scheduling

### Periodic

- Interval: 15 minutes.
- Worker: UnifiedSyncWorker.
- Unique work: liftrix_unified_sync_periodic_{userId}.
- Policy: KEEP.
- Constraints: connected network and battery not low.
- Additional schedules: DeadLetterCleanupWorker and DatabaseIntegrityWorker.

### Immediate

- Entry: SyncCoordinator.triggerImmediateSync(userId).
- If StartupRestoreGate is incomplete, it does not enqueue work and returns a
  `RESTORE_IN_PROGRESS` business-logic failure.
- Worker: one immediate UnifiedSyncWorker.
- Policy: KEEP.

Callers must receive either an accepted enqueue result or an explicit failure. A scheduling path that
declines to enqueue must never report success.

### Entity-Specific

- Entries: triggerEntitySync or enqueueEntitySync.
- Entity names are mapped to sync type/priority in SyncCoordinator.
- Unknown entity types are rejected/ignored rather than routed to a fallback worker.
- Worker: one UnifiedSyncWorker request.

### Startup/Login

Startup is queue-driven and unified:

1. Suppress duplicate requests through the restore gate, in-memory guard, and existing unique-work state.
2. Transition the gate to RESTORING_FROM_FIREBASE.
3. Perform best-effort profile cleanup.
4. Queue FETCH rows for:
   - PROFILE;
   - USER_PUBLIC;
   - WORKOUT;
   - TEMPLATE;
   - FOLLOW_RELATIONSHIP;
   - WORKOUT_POST;
   - ACHIEVEMENT.
5. Enqueue one startup UnifiedSyncWorker under startup_sync_{userId}.
6. Use ExistingWorkPolicy.KEEP.
7. UnifiedSyncWorker/SyncOperationManager performs restore/reconciliation.
8. Observe the durable request without imposing a work deadline. ENQUEUED, BLOCKED, RUNNING, and
   WorkManager retry remain pending beyond any in-process observation interval.
9. Transition the gate to RESTORE_COMPLETE only on successful startup work; FAILED and CANCELLED
   terminal work transition it to RESTORE_FAILED.

Do not document or reintroduce the retired specialized startup worker chain.

Local monitoring does not own durable work lifetime. Replacing or detaching an in-process observer may
cancel only that observer job; it must not call `cancelUniqueWork` or otherwise mutate WorkManager work.
Monitor cleanup is generation-safe so an older observer cannot release the guard for a newer request.
Explicit account/logout cleanup remains authorized to cancel user-scoped work.

### Legacy Compatibility

MasterSyncWorker and specialized entity workers remain loadable so already-persisted WorkManager specifications can instantiate after an update. New SyncCoordinator scheduling does not select them.

Cancellation clears current unified names/tags and known legacy unique names.

## Queue Contract

Social mutation queue entity types are `POST_LIKE`, `SAVED_POST`, `POST_COMMENT`, `BLOCKED_USER`, and `CONTENT_REPORT`. They are intentionally deferred by `OfflineQueueManager`'s generic payload processor and completed by the social batch handlers in `SyncOperationManager`; queue rows are removed only after the corresponding remote batch commits.

sync_queue is the durable offline contract:

- every row includes user_id;
- logical de-duplication is user_id + entity_type + entity_id;
- entity_type and operation are normalized;
- supported operations are CREATE, UPDATE, DELETE, and FETCH;
- priority is ascending: 1 critical, 2 high, 3 medium, 4 low;
- processing order is priority then creation time;
- retryable failure increments retry metadata;
- terminal failure follows the dead-letter path;
- successful remote work marks the local entity/queue state clean/processed.

Inspect pending work with:

~~~sql
SELECT * FROM sync_queue WHERE user_id = ?;
~~~

## Firestore Shape

Representative paths:

~~~text
/users/{userId}
  /workouts/{workoutId}
  /templates/{templateId}
  /achievements/{achievementId}
  /gym_buddies/{buddyId}
  /settings/preferences
  /chat_conversations/{conversationId}
  /chat_history/{messageId}
  /chat_preferences/settings
/social_profiles/{userId}
/follow_relationships/{id}
/workout_posts/{postId}
~~~

Backend/deployment freshness is not proven by local source. Treat functions/, cloud-functions/, rules, and indexes as Needs verification until deployment state is checked.

### Firebase Authorization Perimeter

The active deployment sources are the root `firestore.rules`, `storage.rules`,
`database.rules.json`, `firebase.json`, and `functions/index.js`. Files under
`docs/firebase` are reference copies and must not be deployed as substitutes.

- `/users/{userId}` is a private record boundary. Collection list is denied;
  direct get is owner-only unless the record explicitly has `isPublic == true`.
- Discovery uses projection-only `/users_public` or `/social_profiles` data.
  Application-layer filtering is not authorization and must never be used to
  compensate for a permissive private collection rule.
- Profile-image originals and thumbnails use `/users_public/{userId}` as the
  single Storage authorization projection. Owners retain access. An
  authenticated non-owner is allowed only when the projection exists and
  `isPublic` is exactly true; a missing or stale projection fails closed.
- Client subscription creation is limited to the exact own-user free/default
  registration shape. Paid/custom entitlement writes and every subscription
  update/delete are server-owned. Claim derivation allowlists tiers, statuses,
  and features and preserves only explicitly named non-subscription claims.
- Every v2 callable in `functions/index.js` uses App Check enforcement plus one
  authorization policy: caller-owned, server-derived relationship, or admin.
  Caller-supplied foreign UIDs are never sufficient authorization.
- `setAdminClaim` always requires an existing authenticated admin. First-admin
  provisioning is an offline project-owner operation; there is no zero-admin
  callable bootstrap path.
- Realtime Database is selected by `firebase.json` and denied at the root for
  authenticated and unauthenticated clients until a separate owner-specific
  review approves a data model.

### AI Conversation Durability

`ChatSyncWorker` is the per-user compatibility worker for AI conversation lifecycle data. Its ordering is part of the privacy contract:

1. Reconcile `chat_conversations` metadata and tombstones before handling messages.
2. Upload a deletion tombstone before deleting matching remote messages, using the worker's configured batch size.
3. Only treat the metadata operation as complete after remote message deletion succeeds.
4. Upload dirty preferences and messages, then mark their Room rows clean after Firestore confirms the write.
5. On restore, download metadata before messages and suppress messages covered by a tombstone.

Local `AI_RESPONSE` maps to remote `ASSISTANT`; download maps `ASSISTANT` back to `AI_RESPONSE`. `USER` and `SYSTEM` are unchanged. Unknown values fail synchronization instead of being silently stored.

Deleting one conversation creates a tombstone for that conversation ID and suppresses all messages for it. Clear-all uses the reserved `ChatConversationDeletionPolicy.ALL_HISTORY_CONVERSATION_ID` row: its `deletedAt` is a cutoff, so messages created at or before the cutoff remain suppressed while later messages may sync normally.

Retention runs inside the authenticated user's `ChatSyncWorker`. It queries only that user's expired Room rows, deletes their Firestore documents first, and deletes the local rows only after remote success. Do not reintroduce a global or unscoped chat cleanup worker.

## Conflict and Retry Rules

- Last-write-wins comparisons use lastModified where both sides changed.
- Remote upserts must be idempotent and user-scoped.
- Failed operations retain enough metadata for retry/dead-letter diagnosis.
- Use BaseSyncWorker and SyncOperationManager batch/retry conventions.
- Do not clear dirty state before remote confirmation.
- Offline queue item/processing failures are required unified-worker failures. Startup operation failures
  are also required, so partial startup success retries under the existing WorkManager attempt policy and
  becomes terminal failure when attempts are exhausted. Terminal output preserves successful, failed,
  conflict, required-failure, and failure-category metadata.
- A worker that fails to instantiate is an explicit sync failure. The coordinator must not delay, simulate in-process work, or report success when no durable work ran.

## Realtime Sync

- RealtimeSyncService owns active-workout listener lifecycle.
- Listener callbacks reconcile into Room rather than driving UI directly.
- Background retry/recalculation requests route through SyncCoordinator.
- callbackFlow-based listeners must close registrations in awaitClose.
- Realtime listener state is not a replacement for durable queue/sync metadata.

## Restore Signals

Canonical mutable owners are StartupRestoreGate and TemplateRestoreNotifier in :core:sync.

Feature modules consume restore state and completion events through read-only contracts in :core:domain; restore mutation remains owned by :core:sync.

## Storage and Media

- UI delegates profile/media upload to services/use cases.
- Compression, thumbnails, Firebase Storage, URL persistence, and progress belong to media/storage services.
- Preserve ownership/user validation for uploaded paths and saved URLs.
- Existing profile upload timeout guidance is 30 seconds.

## Notifications and FCM

- LiftrixFirebaseMessagingService lives in :core:notifications and is registered as com.example.liftrix.service.LiftrixFirebaseMessagingService through the app manifest.
- onNewToken must update token storage on refresh.
- Delivery must respect priority, privacy, quiet hours, mutes/preferences, and batching.
- Notification actions route through service/use-case/repository paths, not UI-only state.
- Prefer the Room-backed NotificationPreferencesRepository and NotificationMuteRepository paths.
- NotificationRepositoryImpl in :core:data is a placeholder legacy path; do not add new callers.

## Debug Checklist

- Queue: query sync_queue by user_id.
- Restore: inspect StartupRestoreGate state and startup_sync_{userId} WorkInfo.
- Startup: confirm the seven FETCH entity types were queued.
- Worker: inspect unified_sync/startup_sync/user tags and unique names.
- Missing data: inspect dirty/isSynced/syncVersion/lastModified plus remote-upsert code.
- Partial sync: inspect entity result, queue retry state, and dead-letter state.
- Observer recovery: if an in-process observer detaches, inspect WorkManager by the user-scoped unique
  name/request ID. Do not cancel constrained or retrying work merely to clear presentation state; a later
  startup request may reattach or enqueue after terminal failure.
- Realtime: confirm listener registration/awaitClose and Room upsert.
- Database: confirm current schema 14, `liftrix_database_encrypted_v14`, no registered migration floor, and no destructive fallback.

## Known Source Risks

- Some repository/DAO mutations remain ID-only despite user-scoping law.
- KSP/lint scoping coverage is partial.
- WorkerModule comments are stale and should be removed/refreshed under the existing DI cleanup.
- Specialized workers contain legacy behavior and diagnostics; do not treat their presence as default scheduling.

## Verification

Use patterns/testing.md. Typical sync/database checks:

~~~bash
./gradlew compileDebugKotlin
./gradlew validateRoomQueries
./gradlew lintFull
~~~

Add relevant module/unit/instrumented checks proportionate to the change. Report anything skipped.
