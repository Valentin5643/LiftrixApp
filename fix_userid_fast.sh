#!/bin/bash

# Fast targeted fix for UserId errors
# Only processes files that have compilation errors

echo "🔧 Fast UserId error fix..."

# Files with errors (from compilation output)
files=(
    "app/src/main/java/com/example/liftrix/data/repository/AuthRepositoryImpl.kt"
    "app/src/main/java/com/example/liftrix/data/repository/ChatRepositoryImpl.kt"
    "app/src/main/java/com/example/liftrix/data/repository/CustomExerciseRepositoryImpl.kt"
    "app/src/main/java/com/example/liftrix/data/repository/ExerciseLibraryRepositoryImpl.kt"
    "app/src/main/java/com/example/liftrix/data/repository/FCMTokenRepositoryImpl.kt"
    "app/src/main/java/com/example/liftrix/data/repository/FolderRepositoryImpl.kt"
    "app/src/main/java/com/example/liftrix/data/repository/PRNotificationRepositoryImpl.kt"
    "app/src/main/java/com/example/liftrix/data/repository/PersonalRecordRepositoryImpl.kt"
    "app/src/main/java/com/example/liftrix/data/repository/ProfileImageRepositoryImpl.kt"
    "app/src/main/java/com/example/liftrix/data/repository/ProfileRepositoryImpl.kt"
    "app/src/main/java/com/example/liftrix/data/repository/ProgressStatsRepositoryImpl.kt"
    "app/src/main/java/com/example/liftrix/data/repository/SocialRepositoryImpl.kt"
    "app/src/main/java/com/example/liftrix/data/repository/SocialRepositoryImplEnhanced.kt"
    "app/src/main/java/com/example/liftrix/data/repository/SubscriptionRepositoryImpl.kt"
)

for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        echo "  Processing: $(basename $file)"

        # Fix 1: .document(userId) → .document(userId.value)
        sed -i 's/\.document(userId)/\.document(userId.value)/g' "$file"

        # Fix 2: userId != comparison → userId.value !=
        sed -i 's/userId != /userId.value != /g' "$file"

        # Fix 3: userId == comparison
        sed -i 's/userId == /userId.value == /g' "$file"

        # Fix 4: Comparisons where userId is on right side
        sed -i 's/ != userId$/ != userId.value/g' "$file"
        sed -i 's/ == userId$/ == userId.value/g' "$file"

        # Fix 5: Firebase update/set calls - add .value to userId references in path building
        sed -i 's/\.update(\(.*\)userId/\.update(\1userId.value/g' "$file"
    fi
done

echo "✓ All files processed"
echo ""
echo "Running compilation check..."

cd "$(dirname "$0")"
./gradlew compileDebugKotlin --no-daemon 2>&1 | tail -50

echo ""
echo "Done!"
