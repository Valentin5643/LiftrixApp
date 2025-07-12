#!/bin/bash

# 🔄 Rollback Script for Unified Workout Session Architecture
# This script restores the legacy workout session architecture if needed

echo "🔄 Starting rollback to legacy workout session architecture..."

# Check if backup directory exists
if [ ! -d "legacy_backup" ]; then
    echo "❌ Error: legacy_backup directory not found!"
    echo "Cannot rollback without backup files."
    exit 1
fi

echo "📋 Backing up current unified files..."
mkdir -p unified_backup
cp -r app/src/main/java/com/example/liftrix/domain/model/UnifiedWorkoutSession.kt unified_backup/ 2>/dev/null || true
cp -r app/src/main/java/com/example/liftrix/service/UnifiedWorkoutSessionManager.kt unified_backup/ 2>/dev/null || true
cp -r app/src/main/java/com/example/liftrix/ui/common/LiveSessionBar.kt unified_backup/ 2>/dev/null || true
cp -r app/src/main/java/com/example/liftrix/ui/workout/active/UnifiedActiveWorkoutScreen.kt unified_backup/ 2>/dev/null || true
cp -r app/src/main/java/com/example/liftrix/ui/workout/active/UnifiedActiveWorkoutViewModel.kt unified_backup/ 2>/dev/null || true
cp -r app/src/main/java/com/example/liftrix/ui/navigation/UnifiedMainNavigationContainer.kt unified_backup/ 2>/dev/null || true
cp -r app/src/main/java/com/example/liftrix/domain/usecase/session/ unified_backup/ 2>/dev/null || true
cp -r app/src/main/java/com/example/liftrix/di/UnifiedWorkoutSessionModule.kt unified_backup/ 2>/dev/null || true

echo "🗂️ Restoring legacy files..."

# Restore data layer
cp legacy_backup/data/local/dao/ActiveWorkoutSessionDao.kt app/src/main/java/com/example/liftrix/data/local/dao/
cp legacy_backup/data/local/entity/ActiveWorkoutSessionEntity.kt app/src/main/java/com/example/liftrix/data/local/entity/
cp legacy_backup/data/mapper/ActiveWorkoutSessionMapper.kt app/src/main/java/com/example/liftrix/data/mapper/
cp legacy_backup/data/repository/ActiveWorkoutSessionRepositoryImpl.kt app/src/main/java/com/example/liftrix/data/repository/

# Restore domain layer
cp legacy_backup/domain/model/ActiveWorkoutSession.kt app/src/main/java/com/example/liftrix/domain/model/
cp legacy_backup/domain/repository/ActiveWorkoutSessionRepository.kt app/src/main/java/com/example/liftrix/domain/repository/

# Restore service layer
cp legacy_backup/service/LiveWorkoutSessionManager.kt app/src/main/java/com/example/liftrix/service/
cp legacy_backup/service/PersistentSessionStorage.kt app/src/main/java/com/example/liftrix/service/
cp legacy_backup/service/WorkoutSessionPersistenceService.kt app/src/main/java/com/example/liftrix/service/

# Restore UI layer
cp legacy_backup/ui/common/WorkoutNowBar.kt app/src/main/java/com/example/liftrix/ui/common/
cp legacy_backup/ui/common/WorkoutSessionRecoveryDialog.kt app/src/main/java/com/example/liftrix/ui/common/
cp legacy_backup/ui/navigation/LiveWorkoutSessionViewModel.kt app/src/main/java/com/example/liftrix/ui/navigation/
cp legacy_backup/ui/workout/active/ActiveWorkoutScreen.kt app/src/main/java/com/example/liftrix/ui/workout/active/
cp legacy_backup/ui/workout/active/ActiveWorkoutViewModel.kt app/src/main/java/com/example/liftrix/ui/workout/active/

echo "🗑️ Removing unified files..."

# Remove unified files
rm -f app/src/main/java/com/example/liftrix/domain/model/UnifiedWorkoutSession.kt
rm -f app/src/main/java/com/example/liftrix/service/UnifiedWorkoutSessionManager.kt
rm -f app/src/main/java/com/example/liftrix/ui/common/LiveSessionBar.kt
rm -f app/src/main/java/com/example/liftrix/ui/workout/active/UnifiedActiveWorkoutScreen.kt
rm -f app/src/main/java/com/example/liftrix/ui/workout/active/UnifiedActiveWorkoutViewModel.kt
rm -f app/src/main/java/com/example/liftrix/ui/navigation/UnifiedMainNavigationContainer.kt
rm -f app/src/main/java/com/example/liftrix/di/UnifiedWorkoutSessionModule.kt
rm -rf app/src/main/java/com/example/liftrix/domain/usecase/session/

echo "🧹 Cleaning build..."
./gradlew clean

echo "✅ Rollback completed successfully!"
echo ""
echo "📋 Next steps:"
echo "1. Update your DI modules to use legacy providers"
echo "2. Update navigation to use legacy components"
echo "3. Run './gradlew build' to verify compilation"
echo "4. Test all workout functionality"
echo ""
echo "📁 Your unified files are backed up in: unified_backup/"
echo "📁 Legacy files restored from: legacy_backup/"