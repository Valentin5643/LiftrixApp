# Firebase Remote Config Debug Override

## Summary

Modified Firebase Remote Config setup to enable **immediate fetching in DEBUG builds only** while preserving production behavior.

---

## Changes Made

### 1. **RemoteConfigManager.kt** (`app/src/main/java/com/example/liftrix/data/remote/config/RemoteConfigManager.kt`)

#### Added Import
```kotlin
import com.example.liftrix.BuildConfig
```

#### Added Debug Override Constant
```kotlin
// TODO: TEMPORARY DEBUG OVERRIDE
// WHY: Enables immediate Remote Config fetching in debug builds for testing/debugging
// WHEN TO REMOVE: After Remote Config is fully tested and Firebase Console shows fetch activity
// PRODUCTION IMPACT: None - only affects debug builds (BuildConfig.DEBUG)
private const val DEBUG_MINIMUM_FETCH_INTERVAL_SECONDS = 0L // Immediate fetching for debug
```

#### Modified `initialize()` Method
- **Added conditional fetch interval selection**:
  ```kotlin
  val fetchInterval = if (BuildConfig.DEBUG) {
      DEBUG_MINIMUM_FETCH_INTERVAL_SECONDS  // 0 seconds
  } else {
      MINIMUM_FETCH_INTERVAL_SECONDS  // 3600 seconds (1 hour)
  }
  ```

- **Added automatic fetch in debug builds**:
  ```kotlin
  if (BuildConfig.DEBUG) {
      Timber.i("🔧 DEBUG MODE: Remote Config initialized with IMMEDIATE fetching (interval = 0s)")
      Timber.i("🔧 DEBUG MODE: Calling fetchAndActivate() now...")

      val fetchSuccess = remoteConfig.fetchAndActivate().await()
      if (fetchSuccess) {
          Timber.i("✅ DEBUG MODE: Remote Config fetch SUCCESS - values are now active")
      } else {
          Timber.w("⚠️ DEBUG MODE: Remote Config fetch returned false - using cached/default values")
      }
  }
  ```

---

### 2. **FeatureFlagServiceImpl.kt** (`app/src/main/java/com/example/liftrix/service/FeatureFlagServiceImpl.kt`)

#### Added Import
```kotlin
import com.example.liftrix.BuildConfig
```

#### Added Debug Override Constant
```kotlin
// TODO: TEMPORARY DEBUG OVERRIDE
// WHY: Enables immediate Remote Config fetching in debug builds for testing/debugging
// WHEN TO REMOVE: After Remote Config is fully tested and Firebase Console shows fetch activity
// PRODUCTION IMPACT: None - only affects debug builds (BuildConfig.DEBUG)
private const val DEBUG_CACHE_EXPIRATION_SECONDS = 0L // Immediate fetching for debug
```

#### Modified `initializeRemoteConfig()` Method
- **Added conditional fetch interval selection**:
  ```kotlin
  val fetchInterval = if (BuildConfig.DEBUG) {
      DEBUG_CACHE_EXPIRATION_SECONDS  // 0 seconds
  } else {
      CACHE_EXPIRATION_SECONDS  // 3600 seconds (1 hour)
  }
  ```

- **Added debug logging**:
  ```kotlin
  if (BuildConfig.DEBUG) {
      Timber.i("🔧 DEBUG MODE: FeatureFlagService initialized with IMMEDIATE fetching (interval = 0s)")
  }
  ```

---

## How It Works

### Debug Builds (`BuildConfig.DEBUG = true`)
1. **Fetch Interval**: Set to **0 seconds** (immediate)
2. **Auto-Fetch**: `RemoteConfigManager.initialize()` automatically calls `fetchAndActivate()`
3. **Logging**: Clear emoji-prefixed logs show:
   - `🔧 DEBUG MODE: Remote Config initialized with IMMEDIATE fetching (interval = 0s)`
   - `✅ DEBUG MODE: Remote Config fetch SUCCESS` (on success)
   - `⚠️ DEBUG MODE: Remote Config fetch returned false` (if cached)
   - `❌ DEBUG MODE: Remote Config fetch FAILED` (on error)

### Production/Release Builds (`BuildConfig.DEBUG = false`)
1. **Fetch Interval**: Standard **3600 seconds (1 hour)**
2. **Auto-Fetch**: No automatic fetching on initialization
3. **Logging**: Standard production logs (no debug emojis)
4. **Behavior**: **Completely unchanged** from before

---

## Verification Steps

### 1. Check Logcat Output
Run the app in **debug mode** and filter Logcat for Remote Config logs:

```
adb logcat | grep -E "(Remote Config|DEBUG MODE|RemoteConfigManager|FeatureFlagService)"
```

**Expected Output:**
```
I/RemoteConfigManager: 🔧 DEBUG MODE: Remote Config initialized with IMMEDIATE fetching (interval = 0s)
I/RemoteConfigManager: 🔧 DEBUG MODE: Calling fetchAndActivate() now...
I/RemoteConfigManager: ✅ DEBUG MODE: Remote Config fetch SUCCESS - values are now active
I/FeatureFlagServiceImpl: 🔧 DEBUG MODE: FeatureFlagService initialized with IMMEDIATE fetching (interval = 0s)
```

### 2. Check Firebase Console
1. Go to **Firebase Console** → **Remote Config**
2. Navigate to the **"Fetch %"** metric
3. You should see **increased fetch activity** when running debug builds

### 3. Test Immediate Fetching
1. Set a Remote Config value in Firebase Console
2. Run the debug app
3. The new value should be **immediately available** (no 1-hour wait)

### 4. Verify Production Safety
Build a **release APK** and verify:
```bash
./gradlew assembleRelease
```
- Logs should **NOT** contain debug emoji logs
- Fetch interval should be **3600 seconds**
- Behavior should be **identical** to before the change

---

## Removal Instructions

When Remote Config is fully tested and you want to remove the debug override:

### Option 1: Complete Removal
Remove the debug override entirely:
1. Delete `DEBUG_MINIMUM_FETCH_INTERVAL_SECONDS` and `DEBUG_CACHE_EXPIRATION_SECONDS` constants
2. Replace conditional logic with hardcoded production values
3. Remove debug logging blocks

### Option 2: Keep Debug Logging Only
Keep the helpful debug logs but remove the 0-second override:
1. Set `DEBUG_MINIMUM_FETCH_INTERVAL_SECONDS = 60L` (1 minute instead of 0)
2. Keep the debug logging for troubleshooting
3. This provides faster testing without being too aggressive

---

## Files Modified

1. **`app/src/main/java/com/example/liftrix/data/remote/config/RemoteConfigManager.kt`**
   - Added `BuildConfig` import
   - Added `DEBUG_MINIMUM_FETCH_INTERVAL_SECONDS` constant
   - Modified `initialize()` method with conditional logic
   - Added debug logging and auto-fetch

2. **`app/src/main/java/com/example/liftrix/service/FeatureFlagServiceImpl.kt`**
   - Added `BuildConfig` import
   - Added `DEBUG_CACHE_EXPIRATION_SECONDS` constant
   - Modified `initializeRemoteConfig()` method with conditional logic
   - Added debug logging

---

## Architecture Compliance

✅ **No business logic in UI**: Changes are in data/service layers only
✅ **Respects DI patterns**: Uses existing Hilt injection
✅ **No hardcoded test flags**: Uses `BuildConfig.DEBUG` (build-type aware)
✅ **Safe and reversible**: Clear TODO comments mark removal points
✅ **Production protected**: Zero impact on release builds

---

## Common Issues & Solutions

### Issue: "Cannot resolve symbol BuildConfig"
**Solution**: Clean and rebuild the project
```bash
./gradlew clean
./gradlew compileDebugKotlin
```

### Issue: Still seeing "fetch throttled" in logs
**Solution**:
1. Clear app data: `adb shell pm clear com.example.liftrix`
2. Uninstall and reinstall the app
3. Check that you're running a **debug** build (not release)

### Issue: No logs appearing
**Solution**:
1. Verify Timber is initialized (should be in `LiftrixApp.kt`)
2. Check Logcat filters aren't blocking Remote Config logs
3. Use verbose logging: `adb shell setprop log.tag.RemoteConfigManager VERBOSE`

---

## Next Steps

1. **Build and run the app** in debug mode
2. **Check Logcat** for the debug mode initialization logs
3. **Modify a Remote Config value** in Firebase Console
4. **Verify immediate fetching** works without 1-hour delay
5. **Monitor Firebase Console** for increased fetch percentage
6. **Build release APK** and verify production behavior unchanged

---

**Last Updated**: 2025-12-30
**Author**: Claude Code
**Status**: Ready for testing
