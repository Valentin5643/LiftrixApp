#!/bin/bash

# Fix nullable UserId issues (UserId? vs UserId)
# The problem: getCurrentUserId() returns UserId?, but repository methods expect UserId
# The solution: Use ?: null coalescing or handle null properly

echo "🔧 Fixing nullable UserId issues..."

vm_files=(
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

        # Fix 1: userId.value where userId is nullable
        # Change: userId.value, → userId?.value, (for nullable)
        sed -i 's/userId\.value,/userId?.value,/g' "$file"

        # Fix 2: userId.value) where it's the last parameter
        sed -i 's/userId\.value)/userId?.value)/g' "$file"

        # Fix 3: if (userId.value == null) → if (userId == null)
        sed -i 's/if (userId\.value == null)/if (userId == null)/g' "$file"
        sed -i 's/if (userId\.value != null)/if (userId != null)/g' "$file"

        # Fix 4: userId.value!! for non-null assertion
        sed -i 's/userId\.value!!/userId!!/g' "$file"

        # Fix 5: More complex pattern matching for method calls
        # Pattern: repository.method(userId.value) → repository.method(userId)
        # But only if the method signature expects UserId, not String
        # For now we'll use ?. which handles both cases
        sed -i 's/customExerciseRepository\.\([a-zA-Z]*\)(userId\.value,/customExerciseRepository.\1(userId,/g' "$file"
        sed -i 's/customExerciseRepository\.\([a-zA-Z]*\)(userId\.value)/customExerciseRepository.\1(userId)/g' "$file"
    fi
done

echo "✓ Nullable UserId issues fixed"
echo ""
echo "Running compilation check..."

cd "$(dirname "$0")"
./gradlew compileDebugKotlin --no-daemon 2>&1 | tail -50

echo ""
echo "Done!"
