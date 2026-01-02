# LiftrixApp Module Dependency Graph

## Current State (Single Module)

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│                      :app module                        │
│                     (1,300 files)                       │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐             │
│  │    UI    │  │   Data   │  │  Domain  │             │
│  │ 90k LOC  │  │ 8.8k LOC │  │ 18k LOC  │             │
│  └──────────┘  └──────────┘  └──────────┘             │
│       │              │              │                  │
│       └──────────────┴──────────────┘                  │
│              All compiled together                     │
│         528 Hilt + 144 Room annotations                │
│                                                         │
└─────────────────────────────────────────────────────────┘
           │
           ├── :lint-rules
           ├── :user-scoping-annotations
           └── :user-scoping-processor

BUILD CHARACTERISTICS:
- Single KSP/Hilt pass over entire codebase
- UI changes trigger full recompilation
- Model changes trigger ViewModel recompilation
- No parallel module compilation
- Clean build: ~180-240 seconds
- Incremental build: ~45-60 seconds
```

---

## After Phase 1 (Foundation Modules)

```
                    ┌─────────────────┐
                    │   :app module   │
                    │  (reduced size) │
                    │                 │
                    │  - ViewModels   │
                    │  - Screens      │
                    │  - Navigation   │
                    │  - DI config    │
                    │  - Repositories │
                    │  - DAOs/Entities│
                    │  - Sync Workers │
                    └────────┬────────┘
                             │
           ┌─────────────────┼─────────────────┐
           │                 │                 │
    ┌──────▼──────┐   ┌──────▼──────┐   ┌─────▼─────┐
    │   :design-  │   │  :domain-   │   │   :core   │
    │   system    │   │   models    │   │           │
    │             │   │             │   │           │
    │ - Components│   │ - Entities  │   │ - Cache   │
    │ - Theme     │   │ - Results   │   │ - Extensions
    │ - Animations│   │ - Errors    │   │ - Formatters
    │             │   │             │   │ - Utils   │
    └─────────────┘   └──────┬──────┘   └───────────┘
                             │
                             └────────────────┐
                                              │
                                       ┌──────▼──────┐
                                       │   :core     │
                                       │  (shared)   │
                                       └─────────────┘

DEPENDENCY RULES:
┌──────────────────┬─────────────────────────────────────┐
│ Module           │ Can Depend On                       │
├──────────────────┼─────────────────────────────────────┤
│ :app             │ :design-system, :domain-models,     │
│                  │ :core, Firebase, Room, Hilt         │
├──────────────────┼─────────────────────────────────────┤
│ :design-system   │ :core, Compose, Coil                │
├──────────────────┼─────────────────────────────────────┤
│ :domain-models   │ :core, Kotlin stdlib, Serialization │
├──────────────────┼─────────────────────────────────────┤
│ :core            │ Kotlin stdlib, Coroutines, Timber   │
└──────────────────┴─────────────────────────────────────┘

BUILD CHARACTERISTICS:
✅ Parallel compilation of 4 modules
✅ Design system compiles independently
✅ Domain models isolated from UI/Data
✅ Core utilities no annotation processing
- Clean build: ~140-180 seconds (22% faster)
- Incremental UI: ~25-35 seconds (44% faster)
- Incremental model: ~15-20 seconds (50% faster)
```

---

## After Phase 2 (Feature Modules)

```
                         ┌─────────────────┐
                         │   :app module   │
                         │   (core only)   │
                         │                 │
                         │  - MainActivity │
                         │  - Navigation   │
                         │  - App DI       │
                         │  - Database     │
                         └────────┬────────┘
                                  │
      ┌───────────────────────────┼───────────────────────────┐
      │                           │                           │
┌─────▼─────┐             ┌───────▼────────┐         ┌───────▼────────┐
│ :feature- │             │   :feature-    │         │   :feature-    │
│  social   │             │   analytics    │         │   workout      │
│           │             │                │         │                │
│ - Feed    │             │ - Dashboard    │         │ - Active       │
│ - Profile │             │ - Widgets      │         │ - Templates    │
│ - Follow  │             │ - Charts       │         │ - History      │
│ - Posts   │             │ - Insights     │         │ - Exercises    │
└─────┬─────┘             └────────┬───────┘         └────────┬───────┘
      │                            │                          │
      │    ┌───────────────────────┼──────────────────────────┘
      │    │                       │
      │    │    ┌──────────────────┼──────────────────┐
      │    │    │                  │                  │
      ▼    ▼    ▼                  ▼                  ▼
┌──────────────────┐        ┌──────────────┐   ┌─────────────┐
│  :design-system  │        │ :domain-     │   │   :core     │
│                  │        │  models      │   │             │
└──────────────────┘        └──────┬───────┘   └─────────────┘
                                   │
                                   └───────────────┐
                                                   ▼
                                            ┌─────────────┐
                                            │   :core     │
                                            └─────────────┘

PARALLEL COMPILATION GRAPH:
┌─────────────────────────────────────────────────────────┐
│ Time →                                                  │
├─────────────────────────────────────────────────────────┤
│ T0    [core] [domain-models]                           │
│ T1           [design-system] [feature-social]          │
│ T2                            [feature-analytics]       │
│ T3                            [feature-workout]         │
│ T4                            [app]                     │
└─────────────────────────────────────────────────────────┘

FEATURE MODULE CHARACTERISTICS:
┌─────────────────┬───────────┬──────────────────────────┐
│ Feature Module  │ Size      │ Key Components           │
├─────────────────┼───────────┼──────────────────────────┤
│ :feature-social │ ~120 files│ Feed, Profile, Follow    │
│                 │           │ Paging, Privacy          │
├─────────────────┼───────────┼──────────────────────────┤
│ :feature-       │ ~80 files │ Dashboard, Widgets,      │
│  analytics      │           │ Charts, Calculations     │
├─────────────────┼───────────┼──────────────────────────┤
│ :feature-       │ ~150 files│ Active, Templates,       │
│  workout        │           │ History, Exercises       │
└─────────────────┴───────────┴──────────────────────────┘

BUILD CHARACTERISTICS:
✅ Parallel compilation of 7 modules
✅ Feature isolation (social changes don't affect analytics)
✅ Reduced Hilt scope per module
✅ Independent feature development
- Clean build: ~110-150 seconds (38% faster)
- Incremental social: ~18-25 seconds (58% faster)
- Incremental analytics: ~15-20 seconds (66% faster)
```

---

## After Phase 3 (Full Modularization)

```
                              ┌─────────────┐
                              │  :app       │
                              │  (minimal)  │
                              │             │
                              │ - Main      │
                              │ - NavGraph  │
                              │ - AppModule │
                              └──────┬──────┘
                                     │
         ┌───────────────────────────┼────────────────────────────┐
         │                           │                            │
    ┌────▼────┐               ┌──────▼─────┐              ┌──────▼──────┐
    │:feature-│               │ :feature-  │              │  :feature-  │
    │ social  │               │ analytics  │              │  workout    │
    └────┬────┘               └──────┬─────┘              └──────┬──────┘
         │                           │                           │
         │    ┌──────────────────────┼───────────────────────────┘
         │    │                      │
         │    │    ┌─────────────────┼────────────────────┐
         │    │    │                 │                    │
    ┌────▼────▼────▼──┐         ┌────▼──────┐       ┌────▼────┐
    │  :data-         │         │ :sync-    │       │:design- │
    │  infrastructure │         │ infra     │       │ system  │
    │                 │         │           │       └────┬────┘
    │ - Database      │         │ - Workers │            │
    │ - Entities      │         │ - Queue   │            │
    │ - DAOs          │         │ - Coord   │            │
    │ - Migrations    │         └─────┬─────┘            │
    └────┬────────────┘               │                  │
         │                            │                  │
         │        ┌───────────────────┼──────────────────┘
         │        │                   │
         │   ┌────▼─────┐        ┌────▼────────┐
         │   │ :domain- │        │   :core     │
         │   │  models  │        │             │
         │   └────┬─────┘        └─────────────┘
         │        │
         └────────┴──────────────────┐
                                     │
                              ┌──────▼──────┐
                              │   :core     │
                              └─────────────┘

MODULE DEPENDENCY MATRIX:
┌──────────────────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐
│ Module           │ app  │social│analyt│workout│data │sync │design│domain│core│
├──────────────────┼──────┼──────┼──────┼──────┼──────┼──────┼──────┼──────┤
│ :app             │  -   │  ✓   │  ✓   │  ✓   │  ✓   │  ✓   │  ✓   │  ✓   │ ✓ │
│ :feature-social  │      │  -   │      │      │  ✓   │      │  ✓   │  ✓   │ ✓ │
│ :feature-analytics│     │      │  -   │      │  ✓   │      │  ✓   │  ✓   │ ✓ │
│ :feature-workout │      │      │      │  -   │  ✓   │      │  ✓   │  ✓   │ ✓ │
│ :data-infrastructure│   │      │      │      │  -   │      │      │  ✓   │ ✓ │
│ :sync-infrastructure│   │      │      │      │  ✓   │  -   │      │  ✓   │ ✓ │
│ :design-system   │      │      │      │      │      │      │  -   │      │ ✓ │
│ :domain-models   │      │      │      │      │      │      │      │  -   │ ✓ │
│ :core            │      │      │      │      │      │      │      │      │ - │
└──────────────────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┴───┘

ANNOTATION PROCESSING DISTRIBUTION:
┌─────────────────────────┬───────┬──────┬────────────────┐
│ Module                  │ Hilt  │ Room │ KSP Time       │
├─────────────────────────┼───────┼──────┼────────────────┤
│ :app                    │  50   │  0   │ ~8s            │
│ :feature-social         │  80   │  15  │ ~12s           │
│ :feature-analytics      │  60   │  8   │ ~10s           │
│ :feature-workout        │  90   │  20  │ ~15s           │
│ :data-infrastructure    │  20   │ 100  │ ~25s (Room)    │
│ :sync-infrastructure    │  40   │  0   │ ~7s            │
│ Other modules           │  0    │  0   │ 0s             │
├─────────────────────────┼───────┼──────┼────────────────┤
│ BEFORE (single :app)    │ 528   │ 144  │ ~60s           │
│ AFTER (distributed)     │ 340   │ 143  │ ~25s (parallel)│
└─────────────────────────┴───────┴──────┴────────────────┘

PARALLEL BUILD TIMELINE:
┌─────────────────────────────────────────────────────────┐
│ Time →                        BEFORE    │    AFTER      │
├───────────────────────────────────────────┼──────────────┤
│ T0    Core libs                           │ [core]       │
│                                           │ [domain]     │
│ T1    KSP processing (60s)                │ [design]     │
│                                           │ [data-infra] │
│ T2    Kotlin compilation                  │ [sync-infra] │
│                                           │              │
│ T3    Final assembly                      │ [feature-*]  │
│                                           │ (parallel)   │
│ T4                                        │ [app]        │
├───────────────────────────────────────────┼──────────────┤
│ TOTAL: ~180s                              │ ~90s         │
└───────────────────────────────────────────┴──────────────┘

BUILD CHARACTERISTICS:
✅ Parallel compilation of 9 modules
✅ KSP processing distributed and parallelized
✅ Maximum incremental build efficiency
✅ Complete feature isolation
- Clean build: ~90-120 seconds (50% faster)
- Incremental any layer: ~8-18 seconds (70-75% faster)
- Parallel task execution: 6-8 modules simultaneously
```

---

## Build Graph Visualization

### Clean Build Task Execution (Phase 1)

```
Parallel Workers: 4

Worker 1          Worker 2          Worker 3          Worker 4
═══════════════════════════════════════════════════════════════
[core:compile]    [domain:compile]  [IDLE]           [IDLE]
                  └─depends on core
[design:compile]  [IDLE]           [IDLE]           [IDLE]
└─depends on core
[app:ksp]         [app:ksp]        [app:ksp]        [app:ksp]
└─depends on all modules (SERIAL - cannot parallelize)
[app:compile]     [IDLE]           [IDLE]           [IDLE]
```

### Clean Build Task Execution (Phase 2)

```
Parallel Workers: 4

Worker 1              Worker 2              Worker 3              Worker 4
═══════════════════════════════════════════════════════════════════════════
[core:compile]        [IDLE]               [IDLE]               [IDLE]
[domain:compile]      [IDLE]               [IDLE]               [IDLE]
└─depends on core
[design:compile]      [sync:compile]       [data:compile]       [IDLE]
                      └─depends on domain  └─depends on domain
[feature-social:ksp]  [feature-analytics:ksp] [feature-workout:ksp] [IDLE]
└─parallel KSP!
[feature-social:compile] [feature-analytics:compile] [feature-workout:compile] [IDLE]
[app:ksp]             [IDLE]               [IDLE]               [IDLE]
[app:compile]         [IDLE]               [IDLE]               [IDLE]
```

---

## Module Size Distribution

### Before Modularization
```
┌─────────────────────────────────────────────────────┐
│ :app module (100%)                                  │
│ ███████████████████████████████████████████████████ │
│ 1,300 files, 15.2MB                                 │
└─────────────────────────────────────────────────────┘
```

### After Phase 1
```
┌─────────────────────────────────────────────────────┐
│ :app module (70%)                                   │
│ ███████████████████████████████████████             │
│ 900 files, 10.6MB                                   │
├─────────────────────────────────────────────────────┤
│ :domain-models (20%)                                │
│ ███████████                                         │
│ 145 files, 3.0MB                                    │
├─────────────────────────────────────────────────────┤
│ :design-system (7%)                                 │
│ ████                                                │
│ 180 files, 1.1MB                                    │
├─────────────────────────────────────────────────────┤
│ :core (3%)                                          │
│ ██                                                  │
│ 75 files, 0.5MB                                     │
└─────────────────────────────────────────────────────┘
```

### After Phase 2
```
┌─────────────────────────────────────────────────────┐
│ :app module (30%)                                   │
│ ████████████████                                    │
│ 390 files, 4.5MB                                    │
├─────────────────────────────────────────────────────┤
│ :feature-workout (18%)                              │
│ ██████████                                          │
│ 230 files, 2.7MB                                    │
├─────────────────────────────────────────────────────┤
│ :domain-models (20%)                                │
│ ███████████                                         │
│ 145 files, 3.0MB                                    │
├─────────────────────────────────────────────────────┤
│ :feature-social (12%)                               │
│ ███████                                             │
│ 155 files, 1.8MB                                    │
├─────────────────────────────────────────────────────┤
│ :feature-analytics (10%)                            │
│ ██████                                              │
│ 130 files, 1.5MB                                    │
├─────────────────────────────────────────────────────┤
│ :design-system (7%)                                 │
│ ████                                                │
│ 180 files, 1.1MB                                    │
├─────────────────────────────────────────────────────┤
│ :core (3%)                                          │
│ ██                                                  │
│ 75 files, 0.5MB                                     │
└─────────────────────────────────────────────────────┘
```

---

## Key Architectural Patterns

### 1. Layer Dependency Rules

```
┌─────────────────────────────────────────────────────┐
│                 LAYER DEPENDENCY RULES              │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌───────────┐                                     │
│  │    UI     │  ← Feature Modules (ViewModels)     │
│  └─────┬─────┘                                     │
│        │                                            │
│        ▼                                            │
│  ┌───────────┐                                     │
│  │  Domain   │  ← Use Cases, Models, Interfaces    │
│  └─────┬─────┘                                     │
│        │                                            │
│        ▼                                            │
│  ┌───────────┐                                     │
│  │   Data    │  ← Repositories, DAOs, Entities     │
│  └─────┬─────┘                                     │
│        │                                            │
│        ▼                                            │
│  ┌───────────┐                                     │
│  │   Core    │  ← Utilities, Extensions            │
│  └───────────┘                                     │
│                                                     │
│  RULE: Lower layers NEVER depend on upper layers   │
│  RULE: Horizontal dependencies only in same layer  │
└─────────────────────────────────────────────────────┘
```

### 2. Feature Module Pattern

```
:feature-<name>/
├── ui/                  # Screens, ViewModels, Components
│   ├── screens/
│   ├── viewmodels/
│   └── components/
├── domain/              # Feature-specific use cases
│   ├── usecases/
│   └── repository/      # Repository interfaces
├── data/                # Feature-specific data layer
│   ├── repository/      # Repository implementations
│   ├── datasource/
│   └── local/           # DAOs, Entities
├── di/                  # Feature DI module
│   └── FeatureModule.kt
└── navigation/          # Navigation graph
    └── FeatureNavGraph.kt

DEPENDENCIES:
- api(project(":domain-models"))     # Shared models
- implementation(project(":design-system"))  # UI components
- implementation(project(":core"))   # Utilities
- implementation(libs.hilt.android)  # DI
- implementation(libs.room.runtime)  # If needed
```

---

## Summary

This dependency graph shows the **evolution from monolithic to modular** architecture:

### Phase 1 Benefits (Foundation)
- ✅ 3 new modules (:core, :domain-models, :design-system)
- ✅ Parallel compilation enabled
- ✅ Reduced annotation processing scope
- ✅ 16-27% build time reduction

### Phase 2 Benefits (Features)
- ✅ 3 feature modules extracted
- ✅ Isolated team development
- ✅ Feature-level testing
- ✅ 38% build time reduction

### Phase 3 Benefits (Infrastructure)
- ✅ Full modularization (9 modules)
- ✅ Maximum parallelization
- ✅ KSP/Hilt distributed processing
- ✅ 50% build time reduction

**Key Principle:** Dependencies always flow downward (UI → Domain → Data → Core), never upward or circular.
