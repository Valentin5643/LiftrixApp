# 🔄 Unified Workout Session Rollback Guide

## 🚨 Emergency Rollback Instructions

If the new unified workout session system causes issues, follow these steps to quickly rollback to the previous architecture.

## 📋 **Quick Rollback Checklist**

### **Step 1: Restore Legacy Files**
All legacy files are backed up in the `legacy_backup/` directory. Copy them back:

```bash
# Restore legacy service files
cp legacy_backup/service/LiveWorkoutSessionManager.kt app/src/main/java/com/example/liftrix/service/
cp legacy_backup/service/PersistentSessionStorage.kt app/src/main/java/com/example/liftrix/service/

# Restore legacy UI files
cp legacy_backup/ui/workout/active/ActiveWorkoutScreen.kt app/src/main/java/com/example/liftrix/ui/workout/active/
cp legacy_backup/ui/workout/active/ActiveWorkoutViewModel.kt app/src/main/java/com/example/liftrix/ui/workout/active/
cp legacy_backup/ui/common/WorkoutNowBar.kt app/src/main/java/com/example/liftrix/ui/common/

# Restore legacy navigation
cp legacy_backup/ui/navigation/MainNavigationContainer.kt app/src/main/java/com/example/liftrix/ui/navigation/
```

### **Step 2: Update Dependency Injection**
Restore the old DI modules:

```kotlin
// In your DI module, replace:
@Provides
@Singleton
fun provideUnifiedWorkoutSessionManager(...): UnifiedWorkoutSessionManager

// With:
@Provides
@Singleton
fun provideLiveWorkoutSessionManager(...): LiveWorkoutSessionManager
```

### **Step 3: Update Navigation**
Replace the new navigation container with the old one:

```kotlin
// In your main activity/navigation setup, replace:
UnifiedMainNavigationContainer(...)

// With:
MainNavigationContainer(...)
```

### **Step 4: Clean Build**
```bash
./gradlew clean
./gradlew build
```

## 🔍 **Rollback Validation**

After rollback, verify these work:
- [ ] Start workout from template
- [ ] Add exercises to workout
- [ ] Pause/resume workout
- [ ] Complete workout
- [ ] Create template from workout
- [ ] App restart with active session

## 📁 **Files to Remove During Rollback**

If rolling back, delete these new files:
- `UnifiedWorkoutSession.kt`
- `UnifiedWorkoutSessionManager.kt`
- `LiveSessionBar.kt`
- `UnifiedActiveWorkoutScreen.kt`
- `UnifiedActiveWorkoutViewModel.kt`
- `UnifiedMainNavigationContainer.kt`
- All `domain/usecase/session/` files

## 🐛 **Common Rollback Issues**

### **Issue 1: Compilation Errors**
- **Problem**: New code references old classes
- **Solution**: Check for any remaining references to `UnifiedWorkoutSession`

### **Issue 2: DI Errors**
- **Problem**: Hilt can't find old providers
- **Solution**: Ensure all old `@Provides` methods are restored

### **Issue 3: Navigation Errors**
- **Problem**: Old navigation routes not found
- **Solution**: Restore old navigation graphs and route definitions

## 📞 **Support**

If rollback fails, check:
1. Are all legacy files restored?
2. Are DI modules updated?
3. Is the build clean?
4. Are there any remaining references to new classes?

## 🎯 **Post-Rollback**

After successful rollback:
1. File a detailed issue report
2. Include error logs and stack traces
3. Document what specifically failed
4. Keep the new architecture files for future reference

---

## ⚠️ **Important Notes**

- **Backup First**: Always backup your current state before rollback
- **Test Thoroughly**: Verify all functionality after rollback
- **Clean Build**: Always run clean build after rollback
- **Data Loss**: Active sessions may be lost during rollback

## 🔄 **Re-attempt Strategy**

If you want to try the new architecture again:
1. Fix the identified issues
2. Test in a development environment
3. Gradual rollout to subset of users
4. Monitor for issues before full deployment