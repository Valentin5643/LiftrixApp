# Progress Dashboard Patterns

Last moved from root `AGENTS.md`: 2026-05-05. Source audit baseline: 2026-05-01.

Use this when touching progress dashboard, analytics widgets, charts, metric cards, detail screens, widget preferences, or progress navigation.

## Core Shape

```text
ProgressDashboardScreen
  -> ProgressDashboardCoordinator
  -> ProgressSummaryCards
  -> GlobalTimeRangeSelector
  -> ResponsiveDashboardLayout
  -> AdaptiveWidgetGrid
  -> WidgetContainer
  -> analytics/chart/metric widgets
```

## Coordination Pattern

- `ProgressDashboardCoordinator` broadcasts coordinator events through `SharedFlow`.
- Specialized ViewModels observe coordinator events and load widget-specific data.
- Keep global time range and auth/preferences coordination centralized.
- Use `Dispatchers.IO` or existing async use-case patterns for expensive analytics work.

Specialized ViewModels preserved from the prior root guide:

- `AnalyticsWidgetViewModel`
- `ProgressChartsViewModel`
- `ProgressSummaryViewModel`

## Active Widgets

Active widgets returned by `AnalyticsWidget.getAllWidgets()` in the prior root guide:

- `strength_analytics`
- `volume_analytics`
- `frequency_chart`
- `progress_chart`
- `monthly_summary`
- `recovery_metrics`
- `muscle_group_distribution`
- `exercise_ranking`
- `workout_duration`
- `recent_achievements`
- `consistency_score`
- `progressive_overload`

Deprecated/hidden widgets should not be newly surfaced. Removed IDs are centralized in `AnalyticsWidget.DEPRECATED_WIDGET_IDS`.

## Native Home Screen Widgets

Android launcher widgets live in `app/src/main/java/com/example/liftrix/widget` because Glance rendering and app widget receivers are platform entry points owned by `:app`.

- Native widgets render compact display-ready snapshots from local Room-backed workout analytics data.
- Reads must remain scoped by `userId` / `user_id`; widgets must not read Firebase directly.
- `LiftrixWidgetUpdateScheduler` owns immediate, post-workout, periodic, and logout clear/update refreshes.
- `CompleteWorkoutSessionUseCase` notifies native widgets through the `HomeWidgetUpdateNotifier` domain boundary so domain code does not import Android widget APIs.
- Widget taps should use existing typed workout navigation through `MainActivity` and `UnifiedNavigationContainer`; do not add a route unless the active graph cannot express the target.

## Progress Report Export

- Progress-report export actions must declare API-level behavior for file save, share, and open paths.
- Direct Downloads save below API 29 requires an explicit reviewed legacy permission path or a user-mediated document create flow.

## Detail Routes

Use type-safe navigation:

```kotlin
navController.navigate(LiftrixRoute.OneRmDetail)
```

Preserved detail routes:

- `VolumeAnalysisDetail`
- `OneRmDetail`
- `MuscleGroupDetail`
- `WorkoutFrequencyDetail`
- `ExerciseRankingDetail`

Check active registration in `UnifiedNavigationContainer` before adding new links. `DashboardCustomization` was documented as needing verification because it is navigated from the active dashboard but was not found in the active container during the source audit.

## Chart Standards

Follow the existing chart signature style:

```kotlin
@Composable
fun ModernChart(
    data: List<DataPoint>,
    timeRange: TimeRangeType,
    modifier: Modifier = Modifier,
    onDataPointSelected: ((DataPoint) -> Unit)? = null,
    showPersonalRecords: Boolean = true,
    animationDuration: Int = 300
)
```

Performance rules:

- Use `remember(rawData, timeRange)` for expensive chart calculations.
- Avoid recomputing chart data on every recomposition.
- Keep chart rendering responsive and avoid main-thread analytics calculations.
- Preserve adaptive layout behavior: 2 columns mobile, 3 tablet, 4 desktop where the existing layout supports it.
- Widget virtualization may limit visible widgets under memory pressure.

## Debug Hot Zones

- `GetWidgetDataUseCase`, analytics services, and chart/widget ViewModels.
- Per-widget calculation cost and caching behavior.
- Time range propagation through `ProgressDashboardCoordinator`.
- Detail route registration drift in `UnifiedNavigationContainer`.
- Deprecated/hidden widget IDs being surfaced after migrations.
