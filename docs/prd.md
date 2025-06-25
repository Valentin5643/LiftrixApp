# Product Requirements Document: Workout Creation Flow

## Product Specifications

### Elevator Pitch
Create an intuitive workout creation system that reduces setup time by 75% through equipment-based exercise filtering and reusable templates, enabling users to start their first workout within 2 minutes of login.

### Target Users
- **Primary User**: New fitness app users (0-30 days) who need guided workout creation
- **Secondary User**: Experienced users (30+ days) who want efficient workout setup 
- **Use Cases**: 
  - First-time users creating their initial workout routine
  - Regular users creating new workouts 2-3 times per week
  - Users adapting workouts based on available equipment
- **Pain Points**: 
  - Complex exercise selection overwhelming new users (avg 8+ minutes to create first workout)
  - Equipment mismatch reducing workout completion by 40%
  - Manual workout recreation increasing setup time by 5x

### Core Goals (6 Measurable Outcomes)
- **Goal 1**: 70% of new users create a workout within their first session
- **Goal 2**: Average 2+ workouts created per user per week
- **Goal 3**: Median time from signup to first workout completion <10 minutes
- **Goal 4**: 50% of users utilize search/filter when selecting exercises
- **Goal 5**: 60% of users reuse a saved workout template at least once
- **Goal 6**: Drop-off rate at exercise selection screen <20%

### Functional Requirements with Acceptance Criteria

- **FR-001**: Workout Entry Point Display
  - **AC-001.1**: Display "Create Your First Workout" button for template creation (reusable workouts)
  - **AC-001.2**: Display floating action button for "Quick Workout" (today's workout only)
  - **AC-001.3**: Template button prominent on main screen, FAB accessible from corner
  - **AC-001.4**: Both buttons have 48dp minimum touch targets with proper accessibility
  - **AC-001.5**: Clear visual distinction between template creation and daily workout

- **FR-002**: Exercise Library with Search and Filtering
  - **AC-002.1**: Provide 50+ pre-built exercises covering all major muscle groups
  - **AC-002.2**: Support intelligent search showing exercise variations (e.g., "lateral raises" shows both band and dumbbell versions)
  - **AC-002.3**: Filter exercises by user's selected equipment from onboarding/settings
  - **AC-002.4**: Search results display within 200ms with fuzzy matching (typo tolerance)
  - **AC-002.5**: Exercise cards show name, category, equipment requirements, and targeted muscles
  - **AC-002.6**: Support pagination and infinite scroll for large exercise libraries

- **FR-003**: Custom Exercise Creation
  - **AC-003.1**: Allow users to create custom exercises with required fields: name, primary muscle group, equipment
  - **AC-003.2**: Support optional fields: secondary muscles, difficulty level, exercise notes
  - **AC-003.3**: Validate exercise name uniqueness per user (max 100 characters)
  - **AC-003.4**: Provide muscle group selection from predefined list (Chest, Back, Shoulders, Arms, Legs, Core, Cardio, Full Body)
  - **AC-003.5**: Custom exercises appear in search results alongside pre-built exercises
  - **AC-003.6**: Support editing and deletion of user-created exercises

- **FR-004**: Exercise Selection and Workout Assembly
  - **AC-004.1**: Allow multi-select of exercises with visual selection indicators
  - **AC-004.2**: Support exercise reordering within workout via drag-and-drop
  - **AC-004.3**: Preview selected exercises with total exercise count
  - **AC-004.4**: Enforce maximum 20 exercises per workout with clear validation
  - **AC-004.5**: Validate workout has minimum 1 exercise before saving
  - **AC-004.6**: Display "Add Custom Exercise" option prominently in exercise selection

- **FR-005**: Workout Template Creation and Reuse
  - **AC-005.1**: Save created workouts as reusable templates with custom names
  - **AC-005.2**: Template names must be unique per user with 100 character limit
  - **AC-005.3**: Templates preserve exercise order and target sets/reps/weight
  - **AC-005.4**: Support template editing (add/remove/reorder exercises)
  - **AC-005.5**: Allow template deletion with confirmation dialog
  - **AC-005.6**: Distinguish between templates and daily workouts in UI

- **FR-006**: Pre-built Workout Templates
  - **AC-006.1**: Provide 20+ curated workouts categorized by equipment availability
  - **AC-006.2**: Filter pre-built workouts by user's equipment selection
  - **AC-006.3**: Display workout difficulty, duration estimate, and required equipment
  - **AC-006.4**: When users add pre-built workouts, they become owned custom templates
  - **AC-006.5**: Allow full customization of pre-built workouts before saving
  - **AC-006.6**: Track usage analytics for popular pre-built workouts

- **FR-007**: Daily Workout Creation
  - **AC-007.1**: Quick workout creation for immediate use (today's workout only)
  - **AC-007.2**: Option to start from template or create from scratch
  - **AC-007.3**: Auto-populate with previous weights/reps when using templates
  - **AC-007.4**: Save daily workout instance separate from templates
  - **AC-007.5**: Option to convert daily workout to template after completion

- **FR-008**: Workout Execution Interface
  - **AC-008.1**: Support weight, reps, RPE (1-10 scale), and rest time input
  - **AC-008.2**: Validate weight range (0.5-500kg) and reps range (1-200)
  - **AC-008.3**: Auto-populate previous workout data when using templates
  - **AC-008.4**: Save workout progress automatically every 30 seconds
  - **AC-008.5**: Handle offline workout completion with sync on reconnection

### User Stories with Precision
- **US-001**: As a new user, I want to create my first workout template within 10 minutes of signup so that I can establish my routine quickly
- **US-002**: As a user with specific equipment, I want to search "lateral raises" and see both band and dumbbell variations so that I can choose based on my available equipment
- **US-003**: As a regular user, I want to create custom exercises with my own name and muscle targeting so that I can track specialized movements in my routine
- **US-004**: As a user wanting to work out today, I want a quick workout button that lets me start immediately without creating a template
- **US-005**: As a user browsing pre-built workouts, I want to modify them and save as my own templates so that I can customize proven routines
- **US-006**: As a user searching exercises, I want results within 200ms with intelligent matching so that I can find what I need without frustration
- **US-007**: As a template user, I want my previous weights and reps auto-populated so that I can track progression effortlessly

### Non-Goals with Explicit Boundaries
- Exercise video content or form guidance (deferred to v2.0)
- Social workout sharing or community features (separate epic)
- Advanced workout programming (periodization, auto-progression) 
- Integration with wearable devices during workout creation
- Custom exercise creation by users (admin-only in v1.0)

## User Specs

### User Flows

**Flow 1: Template Creation (First Workout)**
1. User logs in → sees main dashboard
2. Clicks "Create Your First Workout" button (template creation)
3. Navigates to Exercise Selection screen with 50+ pre-built exercises
4. Searches for exercises (e.g., "lateral raises" shows band and dumbbell variations)
5. Optionally creates custom exercise via "Add Custom Exercise" button
6. Selects multiple exercises with visual feedback
7. Reviews and reorders selected exercises
8. Saves as reusable template with custom name
9. Returns to dashboard with template available for future use

**Flow 2: Quick Daily Workout**
1. User clicks floating action button "Quick Workout"
2. Chooses: Start from template OR Create from scratch
3. If template: Auto-populates previous weights/reps for progression
4. If from scratch: Selects exercises (including custom ones)
5. Begins workout immediately (not saved as template)
6. Option to convert to template after completion

**Flow 3: Custom Exercise Creation**
1. User in exercise selection screen
2. Clicks "Add Custom Exercise" button
3. Fills required fields: name, primary muscle group, equipment
4. Optionally adds: secondary muscles, difficulty, notes
5. Validates name uniqueness and saves
6. Custom exercise appears in search results immediately

**Flow 4: Pre-built Workout Adoption**
1. User accesses workout library from main menu
2. Filters pre-built workouts by available equipment
3. Previews workout details (exercises, duration, difficulty)
4. Selects "Use This Workout"
5. Customizes exercises (add/remove/modify)
6. Saves as personal template (becomes owned template)
7. Can use immediately or save for later

### User Interface & Experience
- **Design Language**: Material 3 with consistent spacing (4dp grid)
- **Navigation**: Bottom navigation with workout tab, floating action button for quick workout start
- **Interaction Patterns**: Swipe gestures for exercise selection, tap-to-select with visual feedback
- **Accessibility**: Screen reader support, high contrast mode, minimum 44dp touch targets
- **Error Handling**: Inline validation with clear recovery actions, offline mode indicators

### Layout Structure
- **Main Dashboard**: Prominent workout creation buttons, recent workouts list, progress summary
- **Exercise Selection**: Search bar, filter chips, grid layout with exercise cards
- **Workout Builder**: Selected exercises list, drag-to-reorder, save/start action buttons
- **Template Library**: Categorized list view, search, filter by equipment/difficulty

### Core Components
- **EquipmentFilterChips**: Multi-select chips with equipment icons
- **ExerciseCard**: Name, category, equipment icons, muscle groups, selection state, custom indicator
- **CustomExerciseForm**: Modal form for creating custom exercises with validation
- **WorkoutBuilderList**: Draggable exercise items with remove actions and exercise source indicator
- **TemplateCard**: Workout name, exercise count, last used date, quick start button, custom/pre-built indicator
- **QuickWorkoutFAB**: Floating action button for immediate workout start
- **ExerciseVariationList**: Shows multiple equipment variations for same exercise movement

### Visual Design Elements & Color Scheme
- **Primary Actions**: Material 3 primary color (workout creation, save buttons)
- **Exercise Categories**: Color-coded chips (Chest: red, Legs: blue, etc.)
- **Equipment Icons**: Consistent icon library with recognition patterns
- **Typography**: Roboto font family, clear hierarchy with accessible contrast ratios
- **Loading States**: Shimmer effects for exercise loading, progress indicators for saves

## Technical Specifications

### System Architecture
- **System Patterns**: Clean Architecture with MVI pattern, Repository pattern for data management
- **Frontend Tech**: Jetpack Compose UI, Navigation Component, Hilt dependency injection
- **Database, Backend and APIs**: Room local database, Firestore cloud sync, Firebase Analytics
- **Component Relationships**: UI → ViewModel → UseCase → Repository → (Room + Firestore)
- **Integration Points**: Firebase Auth for user context, Equipment settings sync
- **Data Flow**: Local-first with background sync, offline-capable with conflict resolution

### Technology Stack with Versions
- **Language/Runtime**: Kotlin 1.9.20 with coroutines for reactive patterns
- **Framework**: Jetpack Compose@1.5.4 with Material 3 components
- **Database**: Room@2.6.0 for local storage, Firestore for cloud sync
- **Authentication**: Firebase Auth with user-scoped data isolation

### Application Outline (File Structure)

```
app/src/main/java/com/example/liftrix/
├── domain/
│   ├── model/
│   │   ├── WorkoutTemplate.kt
│   │   ├── DailyWorkout.kt
│   │   ├── ExerciseLibrary.kt
│   │   ├── CustomExercise.kt
│   │   ├── PrebuiltWorkout.kt
│   │   └── ExerciseVariation.kt
│   ├── usecase/
│   │   ├── workout/
│   │   │   ├── CreateWorkoutTemplateUseCase.kt
│   │   │   ├── CreateDailyWorkoutUseCase.kt
│   │   │   ├── GetWorkoutTemplatesUseCase.kt
│   │   │   ├── ConvertDailyWorkoutToTemplateUseCase.kt
│   │   │   └── AdoptPrebuiltWorkoutUseCase.kt
│   │   └── exercise/
│   │       ├── CreateCustomExerciseUseCase.kt
│   │       ├── SearchExercisesUseCase.kt
│   │       ├── FilterExercisesByEquipmentUseCase.kt
│   │       ├── GetExerciseVariationsUseCase.kt
│   │       ├── GetExerciseLibraryUseCase.kt
│   │       └── GetPrebuiltWorkoutsUseCase.kt
│   └── repository/
│       ├── ExerciseLibraryRepository.kt
│       ├── CustomExerciseRepository.kt
│       └── WorkoutTemplateRepository.kt
├── data/
│   ├── local/
│   │   ├── entity/
│   │   │   ├── WorkoutTemplateEntity.kt
│   │   │   ├── DailyWorkoutEntity.kt
│   │   │   ├── ExerciseLibraryEntity.kt
│   │   │   ├── CustomExerciseEntity.kt
│   │   │   └── PrebuiltWorkoutEntity.kt
│   │   └── dao/
│   │       ├── WorkoutTemplateDao.kt
│   │       ├── DailyWorkoutDao.kt
│   │       ├── CustomExerciseDao.kt
│   │       └── ExerciseLibraryDao.kt
│   ├── remote/
│   │   └── dto/
│   │       ├── WorkoutTemplateDto.kt
│   │       ├── CustomExerciseDto.kt
│   │       └── ExerciseLibraryDto.kt
│   └── repository/
│       ├── ExerciseLibraryRepositoryImpl.kt
│       ├── CustomExerciseRepositoryImpl.kt
│       └── WorkoutTemplateRepositoryImpl.kt
├── ui/
│   ├── workout/
│   │   ├── creation/
│   │   │   ├── WorkoutCreationScreen.kt
│   │   │   ├── ExerciseSelectionScreen.kt
│   │   │   ├── CustomExerciseCreationScreen.kt
│   │   │   ├── WorkoutBuilderScreen.kt
│   │   │   └── WorkoutCreationViewModel.kt
│   │   ├── daily/
│   │   │   ├── QuickWorkoutScreen.kt
│   │   │   ├── DailyWorkoutViewModel.kt
│   │   │   └── WorkoutConversionDialog.kt
│   │   ├── templates/
│   │   │   ├── WorkoutTemplateScreen.kt
│   │   │   ├── TemplateDetailScreen.kt
│   │   │   └── WorkoutTemplateViewModel.kt
│   │   └── components/
│   │       ├── ExerciseLibraryComponents.kt
│   │       ├── CustomExerciseComponents.kt
│   │       ├── ExerciseVariationComponents.kt
│   │       ├── WorkoutBuilderComponents.kt
│   │       └── TemplateListComponents.kt
│   └── components/
│       ├── EquipmentFilterChips.kt
│       ├── ExerciseSearchBar.kt
│       ├── QuickWorkoutFab.kt
│       └── WorkoutCreationFab.kt
```

### Implementation Approach
- **File Structure**: Feature-based modules with Clean Architecture layers
- **Code Patterns**: MVI state management, sealed classes for UI states, Flow for reactive streams
- **Testing Strategy**: Unit tests (ViewModels, UseCases), Integration tests (Repository), UI tests (Compose)
- **Deployment**: CI/CD with automated testing, staged rollout with feature flags

### Quality Standards
- **Performance**: <200ms search response, <1s workout save, support 50+ pre-built + unlimited custom exercises
- **Security**: User-scoped data isolation for custom exercises, offline data encryption, Firestore security rules
- **Reliability**: 99.5% uptime, graceful offline handling, automatic sync with conflict resolution
- **Data Quality**: 50+ pre-built exercises covering all major muscle groups with proper equipment categorization
- **Search Quality**: Intelligent exercise variations matching (e.g., "lateral raises" returns multiple equipment options)

## Validation Gates (Must Achieve 95%+ Confidence)
- ✅ All requirements implementable with current Kotlin/Compose tech stack
- ✅ Exercise and workout models extend existing domain architecture
- ✅ Equipment filtering integrates with current onboarding flow
- ✅ Performance targets achievable with Room + Firestore architecture
- ✅ User experience flows validated through existing UI component patterns
- ✅ Technical approach aligns with Clean Architecture and MVI patterns
- ✅ Testing strategy compatible with existing test structure and CI pipeline
