# Orphaned Account Cleanup System - Implementation Guide

## Overview

This system **automatically deletes** orphaned Firestore data when Firebase Auth users are removed. It provides both automated daily cleanup and manual bulk cleanup capabilities.

## What This System Does

### ✅ Automatic Deletion (Server-Side Only)

The system uses **Firebase Cloud Functions with Admin SDK** to:

1. **Detect orphaned profiles**: Firestore documents whose Firebase Auth user no longer exists
2. **Automatically delete** orphaned data across all collections
3. **Run daily** to keep your database clean
4. **Provide manual bulk cleanup** for historical orphans

### ❌ What the Mobile Client Does NOT Do

The mobile client **cannot delete** other users' Firestore documents due to security rules. It can only:

- **Detect** orphaned profiles
- **Log** warnings about them
- **Recommend** server-side cleanup

This is **correct security behavior** - only server-side code should delete cross-user data.

---

## Implementation Details

### 1. Scheduled Daily Cleanup (Automatic)

**Function**: `scheduledOrphanCleanup`
**Schedule**: Daily at 2:00 AM UTC
**Action**: Scans and deletes orphaned profiles automatically

```javascript
exports.scheduledOrphanCleanup = onSchedule(
    {
      schedule: "0 2 * * *", // Daily at 2 AM UTC
      timeZone: "UTC",
    },
    async (event) => {
      // 1. Get all Firestore user profiles
      const usersSnapshot = await db.collection("users").limit(500).get();

      // 2. Check each profile against Firebase Auth
      for (const doc of usersSnapshot.docs) {
        const uid = doc.id;

        try {
          await auth.getUser(uid); // Check if Auth user exists
        } catch (authError) {
          if (authError.code === "auth/user-not-found") {
            // 3. User is orphaned - DELETE their data
            await deleteUserData(uid); // Batch delete all related docs
          }
        }
      }
    });
```

**What Gets Deleted**:
- Main user profile (`users/{uid}`)
- Social profile (`social_profiles/{uid}`)
- Workouts subcollection
- Templates subcollection
- Achievements subcollection
- Follow relationships (follower + following)
- Workout posts
- FCM tokens
- Notification preferences

---

### 2. Manual Bulk Cleanup (On-Demand)

**Function**: `bulkCleanupOrphanedData`
**Trigger**: Callable function (manual invocation)
**Use Case**: Clean historical orphans immediately

#### How to Use from Firebase Console

1. Navigate to Firebase Console → Functions
2. Find `bulkCleanupOrphanedData`
3. Click "Test function"
4. Click "Run test"

**Response Example**:
```json
{
  "success": true,
  "totalUsers": 150,
  "orphanedCleaned": 6,
  "results": [
    { "uid": "abc123", "status": "orphaned", "action": "cleaned", "operations": 12 },
    { "uid": "def456", "status": "active", "action": "skipped" }
  ]
}
```

---

### 3. Deprecated Detection-Only Function

**Function**: `detectOrphanedData`
**Schedule**: Daily at 3:00 AM UTC (1 hour after cleanup)
**Action**: Only logs orphaned profiles WITHOUT deleting

**Purpose**: Verification that `scheduledOrphanCleanup` is working correctly. If this function finds orphans after cleanup runs, it means:
- Cleanup function failed
- More orphans were created between runs
- Pagination limit was exceeded (more than 500 users)

---

## Deployment & Verification

### Deploy Cloud Functions

```bash
# Deploy both cleanup functions
firebase deploy --only functions:scheduledOrphanCleanup,functions:bulkCleanupOrphanedData

# Verify deployment
firebase functions:list
```

### Expected Deployment Output

```
✔ functions[scheduledOrphanCleanup(us-central1)] Successful create operation.
✔ functions[bulkCleanupOrphanedData(us-central1)] Successful update operation.
```

---

## Monitoring & Logs

### View Cloud Function Logs

```bash
# View scheduled cleanup logs
firebase functions:log --only scheduledOrphanCleanup

# View bulk cleanup logs
firebase functions:log --only bulkCleanupOrphanedData
```

### Log Message Patterns

**Successful Cleanup**:
```
🧹 SCHEDULED_CLEANUP: Starting daily orphaned data cleanup
🧹 SCHEDULED_CLEANUP: Checking 150 user profiles
🧹 SCHEDULED_CLEANUP: Found orphaned profile: abc123
✅ SCHEDULED_CLEANUP: Deleted 12 documents for orphaned user abc123
✅ SCHEDULED_CLEANUP COMPLETE: Checked 150 profiles, found 6 orphaned, deleted 6
🧹 CLEANUP_METRICS | checked=150 | orphaned=6 | deleted=6 | timestamp=1234567890
```

**Detection After Cleanup (Verification)**:
```
📊 MONITORING: Starting daily orphaned data detection (deprecated - use scheduledOrphanCleanup)
📊 MONITORING: Found 0 orphaned profiles out of 100 checked
📊 MONITORING_METRICS | orphaned_count=0 | total_checked=100 | timestamp=1234567890
```

**Alert Condition** (if orphans remain after cleanup):
```
🚨 ALERT: HIGH ORPHANED DATA COUNT: 15 profiles detected - scheduledOrphanCleanup should have cleaned these
```

---

## Testing the System

### Test Scenario: Delete a Test User

1. **Create a test user** in Firebase Auth
2. **Manually delete** the user from Firebase Auth console
3. **Trigger cleanup**:
   - Wait for scheduled cleanup (next day at 2 AM UTC)
   - OR run `bulkCleanupOrphanedData` manually from Firebase Console
4. **Verify deletion**: Check Firestore console - the user's documents should be gone

### Expected Behavior

**Before Cleanup**:
- Firestore: User profile exists (`users/{uid}`)
- Firebase Auth: User deleted (auth/user-not-found)

**After Cleanup**:
- Firestore: All user documents deleted
- Logs: "Deleted X documents for orphaned user {uid}"

---

## Security Considerations

### Why Mobile Client Can't Delete

**Security Rule** (firestore.rules):
```javascript
match /users/{userId} {
  allow read, write: if request.auth.uid == userId;
  // Other users CANNOT delete this document
}
```

**Mobile Client Limitation**:
- Can only delete `request.auth.uid == userId` (own documents)
- **Cannot** delete other users' documents (orphans)
- This is **correct** and **secure** behavior

**Server-Side Bypass**:
```javascript
// Cloud Functions use Admin SDK (bypasses security rules)
const admin = require("firebase-admin");
admin.firestore().collection("users").doc(anyUserId).delete(); // ✅ Allowed
```

---

## Migration from Detection-Only to Deletion

### Old System (Detection-Only)

```kotlin
// Mobile client logs orphaned profiles
logger.info("🧹 CLEANUP: Found orphaned profile: $uid")
logger.warn("⚠️ SERVER-SIDE CLEANUP REQUIRED - client cannot delete due to security rules")
```

**Result**: `profiles_removed=0` ❌

### New System (Server-Side Deletion)

```javascript
// Cloud Function deletes orphaned profiles
await batch.delete(db.collection("users").doc(uid));
await batch.commit();
logger.info(`✅ Deleted ${operationCount} documents for orphaned user ${uid}`);
```

**Result**: `profiles_removed=6` ✅

---

## Rollback Plan

If cleanup causes issues, you can:

### 1. Disable Scheduled Cleanup

```bash
# Delete the scheduled function
firebase functions:delete scheduledOrphanCleanup
```

### 2. Restore from Firestore Backup

Enable [Firestore automated backups](https://firebase.google.com/docs/firestore/backups):
```bash
gcloud firestore backups schedules create \
    --database='(default)' \
    --recurrence=daily \
    --retention=7d
```

### 3. Audit Logs

All deletions are logged with:
- User ID
- Number of documents deleted
- Timestamp
- Success/failure status

---

## Cost Implications

### Cloud Functions Pricing

- **Scheduled cleanup**: ~500 reads + ~50 deletes per day
- **Daily cost**: ~$0.01 USD
- **Monthly cost**: ~$0.30 USD

### Storage Savings

- **Orphaned profiles**: ~10 KB per user
- **6 orphaned users cleaned daily**: ~60 KB/day = ~1.8 MB/month
- **Storage cost saved**: Negligible but improves database hygiene

---

## Troubleshooting

### Issue: "scheduledOrphanCleanup not running"

**Check**:
```bash
# Verify function is deployed
firebase functions:list | grep scheduledOrphanCleanup

# Check Cloud Scheduler
gcloud scheduler jobs list
```

**Solution**: Redeploy the function
```bash
firebase deploy --only functions:scheduledOrphanCleanup
```

---

### Issue: "Function timeout (540s)"

**Cause**: Too many orphaned profiles (>500)

**Solution**: Run cleanup multiple times
```bash
# Run bulk cleanup from Firebase Console
# Wait for completion
# Run again if needed (processes 500 at a time)
```

---

### Issue: "Orphans detected after cleanup"

**Check Logs**:
```bash
firebase functions:log --only scheduledOrphanCleanup
```

**Possible Causes**:
1. **Pagination limit**: More than 500 users exist (increase limit in code)
2. **Concurrent deletions**: Users deleted between cleanup runs
3. **Batch failures**: Check error logs for specific failures

---

## Summary

### ✅ What This Implementation Provides

1. **Automatic daily cleanup** at 2 AM UTC (`scheduledOrphanCleanup`)
2. **Manual bulk cleanup** on-demand (`bulkCleanupOrphanedData`)
3. **Comprehensive deletion** across all collections
4. **Monitoring & logging** for verification
5. **Security compliance** (server-side Admin SDK only)

### ❌ What It Does NOT Do

1. **Mobile client deletion**: Security rules prevent cross-user deletions (correct)
2. **Real-time cleanup**: Runs daily, not on every auth deletion
3. **Complete subcollection cleanup**: Limited to 100 docs per subcollection to prevent timeout

### 🎯 Recommended Workflow

1. **Deploy Cloud Functions** (done ✅)
2. **Run initial bulk cleanup**: `bulkCleanupOrphanedData` from Firebase Console
3. **Monitor logs** for 1 week to verify scheduled cleanup works
4. **Set up Firestore backups** for safety
5. **Enable alerting** if orphaned count exceeds threshold

---

## Next Steps

### Immediate Actions

1. ✅ **Deploy complete** - Functions are live
2. **Run manual cleanup**: Execute `bulkCleanupOrphanedData` from Firebase Console to clean existing orphans
3. **Verify in 24 hours**: Check logs tomorrow at 2 AM UTC to confirm scheduled cleanup ran

### Long-Term Maintenance

1. **Weekly log review**: Check for cleanup failures or high orphan counts
2. **Monthly audit**: Run `bulkCleanupOrphanedData` to catch any missed orphans
3. **Update limits**: If user base grows >500, increase pagination limits in code

---

## Contact & Support

If you encounter issues:

1. **Check Firebase Console**: Functions → Logs
2. **Review error messages**: Use log patterns above
3. **Run manual cleanup**: `bulkCleanupOrphanedData` as fallback
4. **Report persistent issues**: File GitHub issue with log excerpts

---

**Last Updated**: 2025-12-29
**Implementation Status**: ✅ Deployed and Active
**Cleanup Frequency**: Daily at 2:00 AM UTC
**Current Orphan Count**: Run `bulkCleanupOrphanedData` to get baseline
