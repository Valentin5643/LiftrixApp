# Audit Logging Integration Guide

**Security Task**: SEC-T005
**Status**: Service Implemented
**Priority**: P1 (High) - Deploy Within 2 Weeks

---

## Overview

This guide explains how to integrate audit logging into DAO upsertFromRemote methods for forensic analysis of conflict resolution events.

## Architecture

```
DAO.upsertFromRemote()
    ↓ Compare local vs remote
AuditLogService.logConflictResolution()
    ↓ Create tamper-evident log
Firestore audit_logs collection (write-only)
```

---

## Example Integration

### Before (No Audit Logging):
```kotlin
@Dao
abstract class WorkoutDao {
    suspend fun upsertFromRemote(workout: WorkoutEntity) {
        val local = getWorkoutByIdForUser(workout.id, workout.userId)
        if (local == null || workout.lastModified > local.lastModified) {
            _insert(workout.copy(isDirty = false, isSynced = true))
        }
    }
}
```

### After (With Audit Logging):
```kotlin
@Dao
abstract class WorkoutDao @Inject constructor(
    private val auditLogService: AuditLogService
) {
    /**
     * Upserts workout from remote source with conflict resolution audit logging.
     *
     * Security: Logs all conflict resolution decisions for forensic analysis (SEC-T005).
     */
    suspend fun upsertFromRemote(workout: WorkoutEntity) {
        val local = getWorkoutByIdForUser(workout.id, workout.userId)

        // Determine conflict resolution strategy
        val resolution = when {
            local == null -> {
                // No conflict - new entity
                _insert(workout.copy(isDirty = false, isSynced = true))
                return // No audit needed for new entities
            }
            workout.lastModified > local.lastModified -> {
                // Remote is newer - remote wins
                _insert(workout.copy(isDirty = false, isSynced = true))
                AuditLogService.RESOLUTION_REMOTE_WIN
            }
            workout.lastModified < local.lastModified -> {
                // Local is newer - keep local
                AuditLogService.RESOLUTION_LOCAL_WIN
            }
            else -> {
                // Same timestamp - keep local (tie-breaker)
                AuditLogService.RESOLUTION_LOCAL_WIN
            }
        }

        // Log conflict resolution decision
        auditLogService.logConflictResolution(
            userId = workout.userId,
            entityType = "Workout",
            entityId = workout.id,
            localData = local,
            remoteData = workout,
            resolution = resolution,
            reason = "Last-write-wins based on timestamp comparison"
        )
    }
}
```

---

## Integration Checklist

### Phase 1: Service Setup (Completed ✅)
- [x] Create `AuditLogService.kt`
- [x] Add tamper-evident Firestore security rules
- [x] Add admin-only read access for audit logs
- [x] Implement privacy-safe snapshot creation

### Phase 2: DAO Integration (Android Team)
- [ ] Inject `AuditLogService` into critical DAOs
- [ ] Update `WorkoutDao.upsertFromRemote()` with audit logging
- [ ] Update `TemplateDao.upsertFromRemote()` with audit logging
- [ ] Update `AchievementDao.upsertFromRemote()` with audit logging
- [ ] Update `SocialProfileDao.upsertFromRemote()` with audit logging
- [ ] Update `WorkoutPostDao.upsertFromRemote()` with audit logging

### Phase 3: Security Event Integration
- [ ] Add `logHmacFailure()` calls to HMAC verification failures
- [ ] Add `logTimestampManipulation()` calls to timestamp validation
- [ ] Add `logUserScopingViolation()` calls to user scoping checks

### Phase 4: Monitoring & Alerting
- [ ] Create Firebase Functions to monitor audit logs
- [ ] Set up alerts for suspicious patterns (>10 conflicts/hour)
- [ ] Create dashboard for audit log visualization
- [ ] Implement automated incident response for critical events

---

## Audit Log Event Types

### 1. CONFLICT_RESOLUTION
**When**: Local and remote data conflict during sync
**Fields**:
- `userId`: The user whose data conflicted
- `entityType`: "Workout", "Template", etc.
- `entityId`: The specific entity ID
- `localSnapshot`: Safe snapshot of local data
- `remoteSnapshot`: Safe snapshot of remote data
- `resolution`: "LOCAL_WIN", "REMOTE_WIN", "MERGE", "REJECTED"
- `reason`: Human-readable explanation

**Example**:
```json
{
  "eventType": "CONFLICT_RESOLUTION",
  "userId": "user123",
  "entityType": "Workout",
  "entityId": "workout456",
  "resolution": "REMOTE_WIN",
  "reason": "Remote timestamp (1735483200000) > local (1735483100000)",
  "localSnapshot": { "id": "workout456", "exercises": "..." },
  "remoteSnapshot": { "id": "workout456", "exercises": "..." },
  "timestamp": "2024-12-29T15:00:00Z"
}
```

### 2. HMAC_VERIFICATION_FAILURE
**When**: HMAC signature verification fails
**Fields**:
- `userId`: User whose data failed verification
- `entityType`: Entity type
- `entityId`: Entity ID
- `expectedSignature`: First 16 chars of expected HMAC
- `actualSignature`: First 16 chars of received HMAC
- `severity`: "HIGH"

**Example**:
```json
{
  "eventType": "HMAC_VERIFICATION_FAILURE",
  "userId": "user123",
  "entityType": "Workout",
  "entityId": "workout456",
  "expectedSignature": "Xj7kP9mN...",
  "actualSignature": "9kLpQ3xR...",
  "severity": "HIGH",
  "timestamp": "2024-12-29T15:00:00Z"
}
```

### 3. TIMESTAMP_MANIPULATION
**When**: Client timestamp is in the future (>5 minutes)
**Fields**:
- `userId`: User who sent suspicious timestamp
- `entityType`: Entity type
- `entityId`: Entity ID
- `clientTimestamp`: Suspicious timestamp from client
- `serverTimestamp`: Server time for comparison
- `timeDifferenceMs`: Difference in milliseconds
- `severity`: "MEDIUM"

**Example**:
```json
{
  "eventType": "TIMESTAMP_MANIPULATION",
  "userId": "user123",
  "entityType": "Workout",
  "entityId": "workout456",
  "clientTimestamp": 1735570000000,
  "serverTimestamp": 1735483200000,
  "timeDifferenceMs": 86800000,
  "severity": "MEDIUM",
  "timestamp": "2024-12-29T15:00:00Z"
}
```

### 4. USER_SCOPING_VIOLATION
**When**: User attempts to access another user's data
**Fields**:
- `attemptedUserId`: User ID in the query
- `actualUserId`: Authenticated user's ID
- `operation`: "READ", "WRITE", "DELETE"
- `entityType`: Entity type
- `severity`: "CRITICAL"

**Example**:
```json
{
  "eventType": "USER_SCOPING_VIOLATION",
  "attemptedUserId": "user999",
  "actualUserId": "user123",
  "operation": "READ",
  "entityType": "Workout",
  "severity": "CRITICAL",
  "timestamp": "2024-12-29T15:00:00Z"
}
```

---

## Firestore Security Rules

```javascript
// audit_logs collection - write-only for tamper-evident logging
match /audit_logs/{logId} {
  // Only admins can read audit logs
  allow read: if request.auth.token.admin == true;

  // Anyone can write, but only approved event types
  allow create: if isAuthenticated() &&
                   request.resource.data.keys().hasAll(['eventType', 'userId', 'timestamp']) &&
                   request.resource.data.eventType in [
                     'CONFLICT_RESOLUTION',
                     'HMAC_VERIFICATION_FAILURE',
                     'TIMESTAMP_MANIPULATION',
                     'USER_SCOPING_VIOLATION',
                     'DATA_TAMPERING'
                   ];

  // CRITICAL: Prevent updates and deletes (tamper-evident)
  allow update, delete: if false;
}
```

---

## Querying Audit Logs (Admin/Support)

```kotlin
// Query conflict resolution events for a user
val conflicts = auditLogService.queryAuditLogs(
    userId = "user123",
    limit = 50,
    eventType = AuditLogService.EVENT_CONFLICT_RESOLUTION
)

// Query all security events
val securityEvents = auditLogService.queryAuditLogs(
    userId = "user123",
    limit = 100
)
```

---

## Performance Considerations

- **Async Logging**: Audit logging is non-blocking and doesn't delay sync operations
- **Error Handling**: Logging failures are logged to Timber but don't fail sync
- **Batch Writes**: Consider batching audit logs for high-volume scenarios
- **Retention**: Firestore TTL policies can auto-delete logs >90 days old

---

## Privacy & Compliance

1. **Sensitive Data Removal**: The `createSafeSnapshot()` method automatically removes:
   - Passwords, tokens, API keys, secrets
   - Profile images, emails, phone numbers
   - Any field containing sensitive keywords

2. **Data Truncation**: Large strings are truncated to 100 characters

3. **Anonymization**: For GDPR compliance, audit logs can be anonymized by replacing `userId` with a hashed version

---

## Monitoring & Alerting

### Firebase Functions Example:
```typescript
// functions/src/auditMonitoring.ts
export const monitorAuditLogs = functions.firestore
  .document('audit_logs/{logId}')
  .onCreate(async (snapshot, context) => {
    const log = snapshot.data();

    // Alert on critical events
    if (log.eventType === 'USER_SCOPING_VIOLATION') {
      await sendSecurityAlert(log);
    }

    // Alert on suspicious patterns
    const recentConflicts = await getRecentConflicts(log.userId);
    if (recentConflicts > 10) {
      await sendDataIntegrityAlert(log.userId);
    }
  });
```

### Metrics to Monitor:
1. **Conflict Rate**: Conflicts per user per hour (alert if >10)
2. **HMAC Failure Rate**: Should be <0.1% (alert if >1%)
3. **Timestamp Manipulation**: Any occurrence is suspicious (immediate alert)
4. **User Scoping Violations**: Critical security event (immediate alert + lockout)

---

## Security Benefits

1. **Forensic Analysis**: Full audit trail for investigating data corruption or security incidents
2. **Tamper-Evident**: Logs cannot be modified or deleted after creation
3. **Pattern Detection**: Automated monitoring can detect suspicious behavior
4. **Compliance**: Satisfies audit requirements for GDPR, SOC 2, etc.
5. **Incident Response**: Provides data for root cause analysis

---

## References

- **Security Spec**: SEC-SPEC-20241229-database-system-security.md
- **Vulnerability**: SEC-005, SEC-018, SEC-019
- **OWASP**: M10 - Extraneous Functionality
- **Priority**: P1 (High) - 6 hours estimated effort
