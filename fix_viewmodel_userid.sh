#!/bin/bash

# Fix UserId errors in ViewModel files
# These are String → UserId wrapper issues

echo "🔧 Fixing ViewModel UserId type mismatches..."

vm_files=(
    "app/src/main/java/com/example/liftrix/ui/workout/active/UnifiedActiveWorkoutViewModel.kt"
    "app/src/main/java/com/example/liftrix/ui/workout/completion/PostCreationViewModel.kt"
    "app/src/main/java/com/example/liftrix/ui/workout/completion/PostWorkoutSummaryViewModel.kt"
    "app/src/main/java/com/example/liftrix/ui/workout/create/WorkoutTemplateCreationViewModel.kt"
    "app/src/main/java/com/example/liftrix/ui/workout/custom/CustomExerciseEditViewModel.kt"
    "app/src/main/java/com/example/liftrix/ui/workout/custom/CustomExerciseListViewModel.kt"
    "app/src/main/java/com/example/liftrix/ui/workout/details/WorkoutDetailsViewModel.kt"
    "app/src/main/java/com/example/liftrix/ui/workout/edit/EditSessionViewModel.kt"
    "app/src/main/java/com/example/liftrix/ui/workout/edit/EditWorkoutViewModel.kt"
    "app/src/main/java/com/example/liftrix/ui/workouts/UserWorkoutsViewModel.kt"
)

for file in "${vm_files[@]}"; do
    if [ -f "$file" ]; then
        echo "  Fixing: $(basename $file)"

        # Pattern 1: val userId = ... followed by method call
        # This is trickier - need to check the actual error lines
        # For now, we'll do simple wrapping patterns

        # Pattern: useCase(..., userId) → useCase(..., UserId(userId))
        sed -i 's/useCase(\([^)]*\), userId)/useCase(\1, UserId(userId))/g' "$file"

        # Pattern: repository.method(userId) → repository.method(UserId(userId))
        sed -i 's/repository\.\([a-zA-Z]*\)(userId)/repository.\1(UserId(userId))/g' "$file"

        # Pattern: service.method(userId) → service.method(UserId(userId))
        sed -i 's/service\.\([a-zA-Z]*\)(userId)/service.\1(UserId(userId))/g' "$file"

        # Pattern: createFolder(userId = userId)
        sed -i 's/userId = userId)/userId = UserId(userId))/g' "$file"

        # Pattern: Copy/create operations with userId parameter
        sed -i 's/copyWorkoutFromPost(\([^)]*\)userId)/copyWorkoutFromPost(\1UserId(userId))/g' "$file"
        sed -i 's/createWorkoutPost(\([^)]*\)userId)/createWorkoutPost(\1UserId(userId))/g' "$file"
    fi
done

echo "✓ ViewModels processed"
echo ""
echo "Running compilation check..."

cd "$(dirname "$0")"
./gradlew compileDebugKotlin --no-daemon 2>&1 | tail -50

echo ""
echo "Done!"
