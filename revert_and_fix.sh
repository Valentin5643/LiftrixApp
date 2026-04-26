#!/bin/bash

# Revert bad sed changes and apply correct fixes for all ViewModel files

echo "🔧 Reverting and fixing ViewModel files..."

files=(
    "app/src/main/java/com/example/liftrix/ui/workout/custom/CustomExerciseListViewModel.kt"
    "app/src/main/java/com/example/liftrix/ui/workout/details/WorkoutDetailsViewModel.kt"
    "app/src/main/java/com/example/liftrix/ui/workout/edit/EditSessionViewModel.kt"
    "app/src/main/java/com/example/liftrix/ui/workout/edit/EditWorkoutViewModel.kt"
    "app/src/main/java/com/example/liftrix/ui/workouts/UserWorkoutsViewModel.kt"
)

for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        echo "  Fixing: $(basename $file)"

        # Fix Pattern 1: if (userId.value == null) → if (userId == null)
        sed -i 's/if (userId\.value == null)/if (userId == null)/g' "$file"
        sed -i 's/if (userId\.value != null)/if (userId != null)/g' "$file"

        # Fix Pattern 2: userId.value, → userId, (when followed by parameter)
        sed -i 's/userId\.value,/userId,/g' "$file"

        # Fix Pattern 3: userId.value) → userId)
        sed -i 's/userId\.value)/userId)/g' "$file"

        # Fix Pattern 4: userId.value!! → userId!!
        sed -i 's/userId\.value!!/userId!!/g' "$file"

        # Fix Pattern 5: userId?.value → userId (for already-dereferenced nullable)
        sed -i 's/userId?\.value/userId/g' "$file"
    fi
done

echo "✓ All ViewModels processed"
echo ""
echo "Running compilation check..."

cd "$(dirname "$0")"
./gradlew compileDebugKotlin --no-daemon 2>&1 | tail -60

echo ""
echo "Done!"
