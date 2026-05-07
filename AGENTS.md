# Repository Agent Guide

Last source audit: 2026-05-01. When this guide conflicts with source, trust the source and update the docs.

This root file is intentionally short. Use it for global rules, essential commands, and routing to deeper agent docs.

## Start Here

1. Inspect source before changing behavior. Do not rely on older docs when source differs.
2. Check the routed doc for the area you are touching.
3. Keep changes scoped. Prefer editing existing files over creating new abstractions.
4. Run the smallest verification set that matches the risk, and include any skipped verification in the handoff.

## Essential Commands

```bash
./gradlew assembleDebug
./gradlew compileDebugKotlin
./gradlew build
./gradlew validateRoomQueries
./gradlew lintFull
```

Common tasks:

- Gradle sync issues: run `./gradlew --stop`, then rebuild.
- Compilation errors: start with `./gradlew compileDebugKotlin`.
- Run on device/emulator: `./gradlew installDebug` after building.
- Social/UI commits: `./gradlew compileDebugKotlin` must pass before commit.
- Sync queue inspection: query `SELECT * FROM sync_queue WHERE user_id = ?`.

## Doc Routing

| Task area | Read first |
| --- | --- |
| Architecture, data flow, startup, navigation, DI, known source inconsistencies | `docs/architecture.md` |
| Gradle modules, package ownership, feature maps, registration files | `docs/module-map.md` |
| Sync, offline-first, Firebase/Storage, WorkManager, notification delivery | `patterns/sync.md` |
| Social feed, engagement, privacy, gym buddy, PR notifications | `patterns/social.md` |
| AI chat, quota, abuse prevention, usage accounting | `patterns/ai-chat.md` |
| Progress dashboard, widgets, chart/detail routes | `patterns/progress.md` |
| Verification commands, test locations, risk matrix | `patterns/testing.md` |
| Design system details | `docs/design-system.md`, `docs/component-library.md`, `docs/component-usage-guidelines.md` |
| Modularization planning | `docs/module-map.md`, `docs/modularization-target-graph.md`, `docs/modularization/inventory/*` |
| Older product/social planning | `docs/social.md` |

## Global Laws

- User-owned database access must be scoped by `userId` or `user_id`. Never add unscoped user data queries.
- Room is the app source of truth. UI must not read directly from Firebase; remote state enters through repositories, services, realtime bridges, or workers and is reconciled locally.
- Domain/data operations use `LiftrixResult<T>`, currently a typealias to Kotlin `Result<T>`. Use `fold`, `onSuccess`, `onFailure`, `getOrElse`, `exceptionOrNull`, and helpers such as `liftrixCatching`, `liftrixSuccess`, and `liftrixFailure`.
- Use correct `LiftrixError` constructor shapes. `ValidationError` takes `field` and `violations`; `BusinessLogicError` takes `code` and `errorMessage`.
- Use type-safe `LiftrixRoute` navigation and verify active registration in `UnifiedNavigationContainer` before adding links.
- Use `ModernBaseViewModel<S>` when the shared StateFlow helper fits; use AndroidX `ViewModel` directly when the feature needs a custom pattern.
- New UI should use `LiftrixColorsV2`, existing design system components, and Material 3 Compose patterns.
- Active workout session state belongs to `UnifiedWorkoutSessionManager`.
- Synced entities need sync metadata. Verify whether the local source pattern uses `SyncableEntity` or metadata fields only before adding an interface.
- Trigger sync through `SyncCoordinator` and queue/local write paths, not direct Firebase writes from UI.
- Social interactions should use optimistic updates and revert on failure.
- Batch Firestore operations in sync workers using configured batch sizes.
- Use emulator-based UI/instrumented tests; do not rely on physical-device-only behavior.

## Error and Import Gotchas

Use current imports:

```kotlin
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.common.liftrixFailure
```

Do not use old sealed `Success`/`Error` branches for `LiftrixResult<T>`. It is Kotlin `Result<T>`.

For `Flow<Result<T>>`, return the flow directly and emit `Result.failure(...)` from `catch`; do not wrap a flow-returning repository call in `liftrixCatching`.

## Known Global Risk Areas

- `UnifiedNavigationContainer` has known route-registration drift. Verify active graph registration before exposing any destination.
- `fallbackToDestructiveMigration()` is configured while Room is at database version 9. Schema changes require explicit migration review.
- Some older docs and code comments mention 22 or more Hilt modules; the source-audited docs describe the current consolidated module state.
- Some test files use a `.disabled` suffix and are not part of normal Gradle discovery.
- Generated/vendor/build-output folders exist in the tree. Do not treat them as source modules unless specifically auditing them.
