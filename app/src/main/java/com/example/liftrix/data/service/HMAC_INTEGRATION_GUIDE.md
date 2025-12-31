# HMAC Signature Verification Integration Guide

**Security Task**: SEC-T004
**Status**: Service Implemented, DAO Integration Pending
**Priority**: P1 (High) - Deploy Within 2 Weeks

---

## Overview

This guide explains how to integrate HMAC-SHA256 signature verification into the Room-first sync architecture to prevent data tampering (SEC-001, SEC-004).

## Architecture

```
Firestore (with HMAC signatures)
    ↓ Real-time listeners / Sync workers
HmacSignatureService.verifySignature()
    ↓ If valid
DAO.upsertFromRemote()
    ↓ Room Database
```

---

## Server-Side Implementation (Firestore Functions)

### 1. Add HMAC Generation to Firestore Writes

```typescript
// functions/src/sync/hmacUtil.ts
import * as crypto from 'crypto';

export function generateHmac(userId: string, data: string): string {
  const secretKey = deriveUserSecret(userId); // From secure key management
  const hmac = crypto.createHmac('sha256', secretKey);
  hmac.update(data);
  return hmac.digest('base64');
}

function deriveUserSecret(userId: string): string {
  // Retrieve user-specific secret from Secret Manager
  // For development: const baseSecret = process.env.HMAC_SECRET;
  // return `${baseSecret}:${userId}`.hashCode();
}
```

### 2. Update Firestore Document Schema

```typescript
// functions/src/sync/workout.ts
export async function syncWorkout(userId: string, workout: WorkoutDto) {
  const workoutData = JSON.stringify(workout);
  const hmacSignature = generateHmac(userId, workoutData);

  await admin.firestore()
    .collection('users').doc(userId)
    .collection('workouts').doc(workout.id)
    .set({
      data: workout,              // Original workout object
      hmac: hmacSignature,        // HMAC signature for integrity
      lastModified: FieldValue.serverTimestamp(),
      syncVersion: Date.now()
    });
}
```

### 3. Firestore Document Structure

```json
{
  "data": {
    "id": "workout123",
    "userId": "user456",
    "exercises": [...]
    // ... workout fields
  },
  "hmac": "Xj7kP9mN...", // HMAC-SHA256 signature
  "lastModified": 1735483200000,
  "syncVersion": 1735483200000
}
```

---

## Client-Side Integration (Android/Kotlin)

### 1. Update Firebase Data Source

```kotlin
// FirebaseWorkoutDataSource.kt
class FirebaseWorkoutDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val hmacService: HmacSignatureService
) {
    suspend fun getWorkout(userId: String, workoutId: String): WorkoutDto? {
        val doc = firestore.collection("users/$userId/workouts")
            .document(workoutId)
            .get()
            .await()

        if (!doc.exists()) return null

        val data = doc.data?.get("data") as? Map<String, Any> ?: return null
        val hmac = doc.getString("hmac")
        val dataJson = gson.toJson(data)

        // Verify HMAC signature
        if (!hmacService.verifySignature(userId, dataJson, hmac)) {
            Timber.e("[SEC-001] HMAC verification failed for workout: $workoutId")
            return null // Reject tampered data
        }

        return gson.fromJson(dataJson, WorkoutDto::class.java)
    }
}
```

### 2. Update DAO Methods

**Before (Vulnerable)**:
```kotlin
@Dao
interface WorkoutDao {
    suspend fun upsertFromRemote(workout: WorkoutEntity) {
        val local = getWorkoutByIdForUser(workout.id, workout.userId)
        if (local == null || workout.lastModified > local.lastModified) {
            _insert(workout.copy(isDirty = false, isSynced = true))
        }
    }
}
```

**After (With HMAC Verification)**:
```kotlin
@Dao
abstract class WorkoutDao @Inject constructor(
    private val hmacService: HmacSignatureService,
    private val gson: Gson
) {
    /**
     * Upserts workout from remote source with HMAC signature verification.
     *
     * Security: Verifies HMAC signature before accepting remote data to prevent
     * tampering attacks (SEC-001, SEC-004).
     *
     * @param workout The workout entity from Firestore
     * @param hmacSignature The HMAC signature from Firestore document
     */
    suspend fun upsertFromRemote(workout: WorkoutEntity, hmacSignature: String?) {
        // Serialize workout for HMAC verification
        val workoutJson = gson.toJson(workout)

        // Verify HMAC signature
        if (!hmacService.verifySignature(workout.userId, workoutJson, hmacSignature)) {
            Timber.e("[SEC-001] Rejecting workout due to HMAC verification failure: ${workout.id}")
            return // Do not insert tampered data
        }

        // Standard upsertFromRemote logic
        val local = getWorkoutByIdForUser(workout.id, workout.userId)
        if (local == null || workout.lastModified > local.lastModified) {
            _insert(workout.copy(isDirty = false, isSynced = true))
        }
    }
}
```

### 3. Update Sync Workers

```kotlin
// WorkoutSyncWorker.kt
override suspend fun performSync(userId: String): Result {
    val remoteWorkouts = firebaseDataSource.getWorkouts(userId) // Already HMAC-verified

    remoteWorkouts.forEach { remoteWorkout ->
        // HMAC verification happens in FirebaseDataSource
        // Only valid workouts reach this point
        workoutDao.upsertFromRemote(remoteWorkout)
    }

    return Result.success()
}
```

### 4. Update Real-Time Listeners

```kotlin
// RealtimeSyncService.kt
private suspend fun onRemoteWorkoutUpdate(doc: DocumentSnapshot) {
    val data = doc.data?.get("data") as? Map<String, Any> ?: return
    val hmac = doc.getString("hmac")
    val userId = doc.getString("userId") ?: return

    val dataJson = gson.toJson(data)

    // Verify HMAC before processing
    if (!hmacService.verifySignature(userId, dataJson, hmac)) {
        Timber.e("[SEC-001] Real-time update rejected: Invalid HMAC for ${doc.id}")
        return
    }

    val workout = gson.fromJson(dataJson, WorkoutDto::class.java)
    workoutDao.upsertFromRemote(workout.toEntity())
}
```

---

## Entities Requiring HMAC Verification

**Critical (Implement First)**:
1. ✅ WorkoutEntity - High-value user data
2. ⬜ ExerciseEntity - Part of workout integrity
3. ⬜ AchievementEntity - User accomplishments
4. ⬜ SocialProfileEntity - Public profile data
5. ⬜ WorkoutPostEntity - Social content

**Medium Priority**:
6. ⬜ WorkoutTemplateEntity
7. ⬜ SettingsEntity
8. ⬜ ProgressPhotoEntity

**Low Priority**:
9. ⬜ ChatHistoryEntity (ephemeral)
10. ⬜ NotificationEntity (ephemeral)

---

## Implementation Checklist

### Phase 1: Infrastructure (Completed ✅)
- [x] Create `HmacSignatureService.kt`
- [x] Add Hilt @Inject @Singleton annotations
- [x] Implement signature generation & verification
- [x] Add constant-time comparison

### Phase 2: Server-Side (Backend Team)
- [ ] Implement HMAC generation in Firestore Functions
- [ ] Update all sync endpoints to include `hmac` field
- [ ] Deploy updated Firestore Security Rules
- [ ] Test HMAC generation with sample data

### Phase 3: Client-Side DAO Integration (Android Team)
- [ ] Update FirebaseDataSource classes to verify HMAC
- [ ] Update WorkoutDao.upsertFromRemote() with HMAC param
- [ ] Update ExerciseDao.upsertFromRemote() with HMAC param
- [ ] Update all critical DAOs (see list above)
- [ ] Update sync workers to pass HMAC signatures
- [ ] Update real-time listeners to verify HMAC

### Phase 4: Testing
- [ ] Unit tests for HmacSignatureService
- [ ] Integration tests for HMAC verification in DAOs
- [ ] End-to-end tests with Firestore emulator
- [ ] Security tests with tampered data
- [ ] Performance tests (HMAC overhead < 5ms per entity)

### Phase 5: Monitoring
- [ ] Add Firebase Analytics events for HMAC failures
- [ ] Add Crashlytics logging for verification errors
- [ ] Create dashboard for HMAC failure rates
- [ ] Set up alerts for suspicious patterns (>1% failure rate)

---

## Key Derivation (Production TODO)

**Current Implementation** (Development):
```kotlin
private fun deriveSecretKey(userId: String): String {
    val baseSecret = "liftrix_hmac_secret_v1"
    return "$baseSecret:$userId".hashCode().toString()
}
```

**Production Requirements**:
```kotlin
private fun deriveSecretKey(userId: String): String {
    // 1. Retrieve base secret from Firebase Remote Config (encrypted)
    val baseSecret = remoteConfig.getString("hmac_base_secret")

    // 2. Use PBKDF2 for key derivation
    val spec = PBEKeySpec(
        baseSecret.toCharArray(),
        userId.toByteArray(),
        iterations = 100_000,
        keyLength = 256
    )
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val key = factory.generateSecret(spec).encoded

    // 3. Store derived key in Android Keystore
    return storeInKeystore(userId, key)
}
```

---

## Rollback Plan

If HMAC verification causes sync issues:

1. **Feature Flag**: `OfflineArchitectureFlags.ENABLE_HMAC_VERIFICATION = false`
2. **Graceful Degradation**: Log warnings instead of rejecting data
3. **Gradual Rollout**: Enable for 10% → 50% → 100% of users

```kotlin
suspend fun upsertFromRemote(workout: WorkoutEntity, hmacSignature: String?) {
    if (OfflineArchitectureFlags.ENABLE_HMAC_VERIFICATION) {
        if (!hmacService.verifySignature(workout.userId, workoutJson, hmacSignature)) {
            Timber.e("[SEC-001] HMAC verification failed")
            return // Reject data
        }
    } else {
        // Legacy mode: Accept without verification
        Timber.w("[SEC-001] HMAC verification disabled (feature flag)")
    }

    // Standard insert logic
    val local = getWorkoutByIdForUser(workout.id, workout.userId)
    if (local == null || workout.lastModified > local.lastModified) {
        _insert(workout.copy(isDirty = false, isSynced = true))
    }
}
```

---

## Performance Considerations

- **HMAC Generation**: ~1-2ms per entity
- **HMAC Verification**: ~1-2ms per entity
- **Batch Sync Impact**: +20-40ms for 20 workouts
- **Acceptable**: <5% overhead on sync operations

---

## Security Benefits

1. **Prevents Data Tampering** (SEC-001): Malicious actors cannot modify Firestore data
2. **Prevents Man-in-the-Middle Attacks**: Even if TLS is compromised, data integrity is verified
3. **Prevents Replay Attacks**: Combined with timestamp validation (SEC-006)
4. **Audit Trail**: All HMAC failures are logged for forensic analysis

---

## References

- **Security Spec**: SEC-SPEC-20241229-database-system-security.md
- **Vulnerability**: SEC-001 (Remote data accepted without verification)
- **OWASP**: M4 - Insecure Authentication
- **Priority**: P1 (High) - 8 hours estimated effort
