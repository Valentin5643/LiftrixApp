# Database Recovery Guide

## 🚨 Room Migration Crash: "Migration didn't properly handle: workouts"

This document provides step-by-step instructions for recovering from Room database migration crashes and preventing future corruption.

### Quick Fix (Immediate Recovery)

**For Development Builds:**

1. **Uninstall & Reinstall App** (Fastest)
   ```bash
   # If using Android Studio
   # - Stop app
   # - Uninstall from device/emulator
   # - Run app again (fresh database will be created)
   ```

2. **Clear App Data** (Alternative)
   ```bash
   # Using ADB
   adb shell pm clear com.example.liftrix
   
   # Or manually through device Settings > Apps > Liftrix > Storage > Clear Data
   ```

3. **Clean Build Cache**
   ```bash
   ./gradlew clean
   # In Android Studio: Build > Clean Project > Rebuild Project
   ```

### Root Cause Analysis

The error occurs when:
- **Database schema changed** but migration was incomplete
- **Development database file corrupted** during testing
- **Migration chain broken** due to missing or incorrect migration steps
- **Room validation failed** because actual schema ≠ expected schema

### Automatic Recovery (Implemented)

The codebase now includes automatic recovery mechanisms:

#### 1. Corruption Detection Callback
```kotlin
// In DatabaseModule.kt
.addCallback(object : RoomDatabase.Callback() {
    override fun onCorruptionDetected(db: SupportSQLiteDatabase) {
        // Automatically clears corrupted development databases
    }
})
```

#### 2. Enhanced Error Handling
```kotlin
// Database initialization with recovery
val dbVersion = try {
    database.openHelper.readableDatabase.version
} catch (e: Exception) {
    // Attempts recovery by clearing corrupted database
}
```

#### 3. Development Reset Utility
```kotlin
// Use DatabaseResetUtil for manual recovery
DatabaseResetUtil.resetDatabase(context)
```

### Manual Recovery Steps

#### Step 1: Verify Current State
```kotlin
// Check database status
val info = DatabaseResetUtil.getDatabaseInfo(context)
Timber.d(info.toString())
```

#### Step 2: Reset Database (Development Only)
```kotlin
// Safely reset corrupted database
if (DatabaseResetUtil.resetDatabase(context)) {
    Timber.i("Database reset successful - restart app")
} else {
    Timber.e("Database reset failed - manual intervention required")
}
```

#### Step 3: Validate Migration Chain
```bash
# Run migration validation tests
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.liftrix.data.local.migration.MigrationValidationTest
```

### Prevention Measures

#### 1. WAL Mode Enabled
- **Write-Ahead Logging** reduces corruption risk
- **Better concurrency** for database operations
- **Atomic transactions** protect data integrity

#### 2. Migration Testing
- **Comprehensive test suite** validates all migrations
- **Data preservation** ensures user data survives migrations
- **Schema validation** catches corruption early

#### 3. Development Best Practices
- **Never modify existing migrations** - create new ones instead
- **Test migration chain** from oldest to newest version
- **Use DatabaseResetUtil** for development database issues
- **Monitor Timber logs** for corruption warnings

### Production Considerations

#### Safe Migration Strategy
```kotlin
// Production databases use fallback only on downgrade
.fallbackToDestructiveMigrationOnDowngrade()

// Never use destructive migration in production
// This preserves user data even if migrations fail
```

#### Error Reporting
- **Corruption events logged** to Firebase Crashlytics
- **Migration failures tracked** for debugging
- **User data protected** with conservative fallback strategies

### Troubleshooting Common Issues

#### "sync_version column missing"
- **Cause**: Migration didn't add sync_version column
- **Fix**: Check Migration_37_38.kt for proper ALTER TABLE statements

#### "Primary key position incorrect"
- **Cause**: Column metadata corruption in SQLite
- **Fix**: Use DatabaseResetUtil.resetDatabase() in development

#### "Migration path not found"
- **Cause**: Missing migration file in chain
- **Fix**: Ensure all migrations 27→38 are registered in DatabaseModule

#### "Schema validation failed"
- **Cause**: Actual database schema doesn't match expected
- **Fix**: Clear corrupted database and let Room recreate from current schema

### Contact & Support

For persistent issues:
1. **Check Timber logs** for detailed error messages
2. **Run MigrationValidationTest** to isolate migration issues
3. **Use DatabaseResetUtil.getDatabaseInfo()** to inspect database state
4. **Clear app data** as last resort to force fresh database creation

### File Locations

- **DatabaseModule.kt**: `app/src/main/java/com/example/liftrix/di/DatabaseModule.kt`
- **DatabaseResetUtil.kt**: `app/src/main/java/com/example/liftrix/debug/DatabaseResetUtil.kt`
- **MigrationValidationTest.kt**: `app/src/androidTest/java/com/example/liftrix/data/local/migration/MigrationValidationTest.kt`
- **Migration Files**: `app/src/main/java/com/example/liftrix/data/local/migration/`

---

*This guide is automatically updated as the database schema evolves. Last updated: January 2025*