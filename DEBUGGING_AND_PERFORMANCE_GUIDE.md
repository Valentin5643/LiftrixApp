# Debugging And Performance Guide

This guide documents the shared debugging and observability foundation for Liftrix. Source code remains authoritative when behavior changes.

## Build Gates

The app exposes BuildConfig gates for debug tooling and release safety:

- `ENABLE_VERBOSE_LOGGING`: allows verbose Timber logging only where the build type permits it.
- `ENABLE_DEBUG_TOOLS`: enables debug-only runtime diagnostics.
- `ENABLE_STRICT_MODE`: enables StrictMode through the debug source set.
- `ENABLE_FIREBASE_PERFORMANCE`: controls Firebase Performance collection.
- `ENABLE_CRASHLYTICS`: controls Crashlytics collection.
- `ENVIRONMENT`, `APP_VERSION_NAME`, `APP_VERSION_CODE`, and `API_BASE_URL`: provide safe build context.

Release builds must keep verbose logging, debug tooling, and StrictMode disabled.

## Logging Rules

Use Timber as the underlying logging system. New observability code can use `LiftrixLogger` for typed contexts and helpers:

- Use context labels such as app, screen, repository, network, database, sync, and performance.
- Do not log raw user IDs, emails, auth tokens, secrets, access tokens, refresh tokens, or debug secrets.
- Use pseudonymous IDs or `LiftrixLogger.safeId(...)` when an identifier is necessary for correlation.
- Use `LiftrixLogger.redact(...)` for sensitive values and `LiftrixLogger.safeKey(...)` for custom key names.
- Release logging accepts warning and error severity only.

Existing Timber call sites do not need a broad rewrite. Update them only when touching nearby behavior.

## Crashlytics And Performance

Crashlytics and Firebase Performance collection are configured in Firebase providers. Crash reports include safe build metadata such as build type, environment, app version, and debug tooling flags.

Crashlytics user context must remain pseudonymous. `AnalyticsServiceImpl` hashes user IDs before setting Crashlytics user identifiers. Custom keys should describe state, route, or phase without raw account identifiers.

Use custom keys for short-lived diagnostic context:

- current screen or route name
- workflow phase
- feature flag state
- sync source or operation name
- safe build/debug flags

Avoid storing request payloads, full URLs with query parameters, user-generated content, email addresses, tokens, or raw Firebase IDs.

## Debug Tooling

Debug builds initialize `DebugToolingInitializer` from `LiftrixApp`. It enables StrictMode when the build gate allows it and includes LeakCanary as a debug-only dependency.

StrictMode findings should be treated as startup and interaction performance signals. Fix disk, network, and heavy parsing work by moving it away from the main thread or deferring it until after first content.

LeakCanary is available only in debug builds. Do not add release references to LeakCanary APIs.

## Database And Sync Inspection

Room is the app source of truth. UI should not read directly from Firebase. Remote state enters through repositories, services, realtime bridges, or workers and is reconciled locally.

For sync queue inspection, query:

```sql
SELECT * FROM sync_queue WHERE user_id = ?;
```

Always scope user-owned database access by `userId` or `user_id`.

## Compose Compiler Reports

App builds write Compose compiler outputs under:

- `app/build/compose_compiler/reports`
- `app/build/compose_compiler/metrics`

Use these generated files to inspect recomposition and skippability while working on UI responsiveness. Do not commit generated report or metric outputs.

## Startup Measurement

Use `reportFullyDrawn()` as the startup milestone for first-content readiness. Pair it with startup traces and Firebase Performance data so cold start work can be separated from post-content background initialization.

Startup work should be staged:

- initialize required app infrastructure
- render first meaningful content
- report fully drawn
- warm caches and start non-critical sync after the UI is responsive

## Image Loading And Feed Performance

For feeds and social surfaces, use Coil and `AsyncImage` patterns already present in the app. Keep placeholders stable so list items do not resize during image loading. Prefer cached thumbnails or constrained image sizes for scrolling surfaces.

Avoid decoding large original images in scrolling rows. Keep network and image loading diagnostics free of raw URLs when they contain user identifiers or access tokens.

## Verification

Use the smallest command that matches the risk while developing, then run the full verification set before handoff:

```bash
./gradlew compileDebugKotlin --no-daemon --max-workers=1 --console=plain
./gradlew testDebugUnitTest --no-daemon --max-workers=1 --console=plain
./gradlew lintFull --no-daemon --max-workers=1 --console=plain
```

If Gradle fails before compilation because Firebase config is missing or mismatched, restore `app/google-services.json` for the active `applicationId`.
