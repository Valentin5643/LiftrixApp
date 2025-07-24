# Active Workout Exercise Addition & Template Update Feature

## Implementation Summary

I've successfully implemented the requested feature to add exercises to active workouts and update the original template with those exercises. Here's what was implemented:

### 1. Update Template Dialog Component
- **File**: `app/src/main/java/com/example/liftrix/ui/workout/components/SaveAsTemplateDialog.kt`
- **Features**:
  - Material 3 AlertDialog with simplified interface
  - Clear message about updating the original template
  - Update/Skip/Cancel actions
  - Uses existing Liftrix design system

### 2. Enhanced ViewModel Functionality
- **File**: `app/src/main/java/com/example/liftrix/ui/workout/active/UnifiedActiveWorkoutViewModel.kt`
- **Added Methods**:
  - `updateOriginalTemplate()` - Updates the existing template with new exercises
  - `skipTemplateUpdate()` - Skips template update
  - `dismissTemplateUpdateDialog()` - Dismisses dialog without completing workout
  - `hasExercisesAddedBeyondTemplate()` - Checks if exercises were added
- **Enhanced State**:
  - Added `showSaveAsTemplateDialog` flag to `Success` state
  - Modified `completeWorkout()` to show dialog ONLY if workout started from template AND exercises were added

### 3. Updated Active Workout Screen
- **File**: `app/src/main/java/com/example/liftrix/ui/workout/active/UnifiedActiveWorkoutScreen.kt`
- **Features**:
  - Added "Add Exercise" button using `SecondaryActionButton`
  - Integrated template update dialog display
  - Connected to existing exercise selection flow
  - Uses same navigation pattern as template creation

## How It Works

### Exercise Addition Flow:
1. User taps "Add Exercise" button in active workout
2. Navigates to exercise selection screen (existing functionality)
3. Selected exercise gets added to current session via existing `addExerciseToSession()` method
4. Screen updates reactively showing new exercise

### Template Update Flow:
1. User starts workout from an existing template
2. User adds exercises during the workout
3. When completing workout, dialog appears asking if they want to update the original template
4. User can:
   - **Update Template**: Original template gets updated with new exercises
   - **Skip**: Complete workout without updating template  
   - **Cancel**: Return to workout (doesn't complete)
5. If updated, next time user starts that template, it includes all the newly added exercises

## Benefits

- **Reuses Existing Architecture**: Leverages existing `WorkoutTemplateRepository.updateTemplate()` and exercise selection flows
- **Follows Design Patterns**: Uses same UI components and navigation patterns as rest of app
- **Non-Breaking Changes**: All existing functionality preserved
- **Smart Detection**: Only shows dialog when workout was started from template AND exercises were added
- **User-Friendly**: Clear prompts and actions, optional template updating

## Key Implementation Details

- **Template Tracking**: Uses `templateId` from session to identify original template
- **Exercise Conversion**: Converts session exercises to template exercises with proper target values
- **Conditional Dialog**: Only appears for template-based workouts with added exercises
- **Template Update**: Updates original template with all current exercises from session

## Testing

- Lint checks pass ✅  
- Build compiles successfully ✅
- Follows existing code patterns ✅
- Uses proper dependency injection ✅
- Template update logic implemented ✅

The feature is ready for user testing and correctly implements the requested template updating functionality.