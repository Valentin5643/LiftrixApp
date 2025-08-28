const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
admin.initializeApp();
const db = admin.firestore();

/**
 * 🔥 AUTOMATIC CLEANUP FUNCTION
 * =============================
 * 
 * This function automatically runs when a Firebase Auth user is deleted.
 * It cleans up all associated Firestore data to prevent orphaned profiles.
 */
exports.cleanupDeletedUser = functions.auth.user().onDelete(async (user) => {
  const uid = user.uid;
  console.log(`🧹 CLEANUP: Starting cleanup for deleted user: ${uid}`);
  
  try {
    const batch = db.batch();
    let operationCount = 0;
    
    // 1. Delete main user profile document
    console.log(`🧹 CLEANUP: Deleting user profile for ${uid}`);
    const userDocRef = db.collection('users').doc(uid);
    batch.delete(userDocRef);
    operationCount++;
    
    // 2. Delete social profile document
    console.log(`🧹 CLEANUP: Deleting social profile for ${uid}`);
    const socialDocRef = db.collection('social_profiles').doc(uid);
    batch.delete(socialDocRef);
    operationCount++;
    
    // 3. Delete user's workouts subcollection (limit to 500 per batch)
    console.log(`🧹 CLEANUP: Deleting workouts for ${uid}`);
    const workoutsQuery = await db.collection('users').doc(uid)
      .collection('workouts').limit(500).get();
    
    workoutsQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    if (workoutsQuery.docs.length > 0) {
      console.log(`🧹 CLEANUP: Found ${workoutsQuery.docs.length} workouts to delete for ${uid}`);
    }
    
    // 4. Delete user's templates subcollection
    console.log(`🧹 CLEANUP: Deleting templates for ${uid}`);
    const templatesQuery = await db.collection('users').doc(uid)
      .collection('templates').limit(500).get();
    
    templatesQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    if (templatesQuery.docs.length > 0) {
      console.log(`🧹 CLEANUP: Found ${templatesQuery.docs.length} templates to delete for ${uid}`);
    }
    
    // 5. Delete user's achievements subcollection
    console.log(`🧹 CLEANUP: Deleting achievements for ${uid}`);
    const achievementsQuery = await db.collection('users').doc(uid)
      .collection('achievements').limit(500).get();
    
    achievementsQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    if (achievementsQuery.docs.length > 0) {
      console.log(`🧹 CLEANUP: Found ${achievementsQuery.docs.length} achievements to delete for ${uid}`);
    }
    
    // 6. Clean up follow relationships where user is follower
    console.log(`🧹 CLEANUP: Deleting follow relationships (as follower) for ${uid}`);
    const followingQuery = await db.collection('follow_relationships')
      .where('follower_id', '==', uid).limit(500).get();
    
    followingQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    if (followingQuery.docs.length > 0) {
      console.log(`🧹 CLEANUP: Found ${followingQuery.docs.length} following relationships to delete for ${uid}`);
    }
    
    // 7. Clean up follow relationships where user is followed
    console.log(`🧹 CLEANUP: Deleting follow relationships (as followed) for ${uid}`);
    const followersQuery = await db.collection('follow_relationships')
      .where('followed_id', '==', uid).limit(500).get();
    
    followersQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    if (followersQuery.docs.length > 0) {
      console.log(`🧹 CLEANUP: Found ${followersQuery.docs.length} follower relationships to delete for ${uid}`);
    }
    
    // 8. Clean up workout posts by the user
    console.log(`🧹 CLEANUP: Deleting workout posts for ${uid}`);
    const workoutPostsQuery = await db.collection('workout_posts')
      .where('user_id', '==', uid).limit(500).get();
    
    workoutPostsQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    if (workoutPostsQuery.docs.length > 0) {
      console.log(`🧹 CLEANUP: Found ${workoutPostsQuery.docs.length} workout posts to delete for ${uid}`);
    }
    
    // Execute all deletions in a single batch
    console.log(`🧹 CLEANUP: Executing batch delete of ${operationCount} operations for ${uid}`);
    await batch.commit();
    
    console.log(`✅ CLEANUP SUCCESS: Cleaned up ${operationCount} documents/subcollections for user ${uid}`);
    
    // Log metrics for monitoring
    console.log(`🧹 CLEANUP_METRICS | user_id=${uid} | operations=${operationCount} | status=success | timestamp=${Date.now()}`);
    
  } catch (error) {
    console.error(`❌ CLEANUP ERROR: Failed to clean up data for user ${uid}:`, error);
    console.log(`🧹 CLEANUP_METRICS | user_id=${uid} | status=error | error="${error.message}" | timestamp=${Date.now()}`);
    
    // Don't throw the error - we don't want to prevent user deletion
    // The error is logged for monitoring and manual cleanup if needed
  }
});

/**
 * 🔧 BULK CLEANUP FUNCTION
 * =========================
 * 
 * This function allows you to clean up existing orphaned data.
 * Call this function manually when you need to clean up orphaned profiles.
 */
exports.bulkCleanupOrphanedData = functions.https.onCall(async (data, context) => {
  console.log('🧹 BULK_CLEANUP: Starting bulk cleanup of orphaned data');
  
  // Basic authentication check (you can make this more secure)
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'Must be authenticated to run bulk cleanup');
  }
  
  try {
    const results = [];
    let totalCleaned = 0;
    
    // Get all user documents from Firestore
    console.log('🧹 BULK_CLEANUP: Fetching all user documents from Firestore');
    const usersSnapshot = await db.collection('users').get();
    console.log(`🧹 BULK_CLEANUP: Found ${usersSnapshot.docs.length} user documents in Firestore`);
    
    // Check each user document to see if the Firebase Auth user still exists
    for (const doc of usersSnapshot.docs) {
      const uid = doc.id;
      
      try {
        // Check if user exists in Firebase Auth
        await admin.auth().getUser(uid);
        // User exists, not orphaned - skip
        console.log(`🧹 BULK_CLEANUP: User ${uid} exists in Auth - skipping`);
        results.push({ uid, status: 'active', action: 'skipped' });
        
      } catch (authError) {
        if (authError.code === 'auth/user-not-found') {
          // User is orphaned - clean up their data
          console.log(`🧹 BULK_CLEANUP: User ${uid} not found in Auth - cleaning up orphaned data`);
          
          try {
            // Use the same cleanup logic as the automatic function
            const batch = db.batch();
            let operationCount = 0;
            
            // Delete user profile
            batch.delete(db.collection('users').doc(uid));
            operationCount++;
            
            // Delete social profile
            batch.delete(db.collection('social_profiles').doc(uid));
            operationCount++;
            
            // Delete subcollections (limited to prevent timeout)
            const collections = ['workouts', 'templates', 'achievements'];
            
            for (const collectionName of collections) {
              const subcollectionSnapshot = await db.collection('users').doc(uid)
                .collection(collectionName).limit(100).get(); // Reduced limit for bulk operation
              
              subcollectionSnapshot.docs.forEach(subDoc => {
                batch.delete(subDoc.ref);
                operationCount++;
              });
            }
            
            // Delete follow relationships
            const followingSnapshot = await db.collection('follow_relationships')
              .where('follower_id', '==', uid).limit(100).get();
            
            followingSnapshot.docs.forEach(followDoc => {
              batch.delete(followDoc.ref);
              operationCount++;
            });
            
            const followersSnapshot = await db.collection('follow_relationships')
              .where('followed_id', '==', uid).limit(100).get();
            
            followersSnapshot.docs.forEach(followDoc => {
              batch.delete(followDoc.ref);
              operationCount++;
            });
            
            // Delete workout posts
            const postsSnapshot = await db.collection('workout_posts')
              .where('user_id', '==', uid).limit(100).get();
            
            postsSnapshot.docs.forEach(postDoc => {
              batch.delete(postDoc.ref);
              operationCount++;
            });
            
            // Execute batch delete
            await batch.commit();
            
            totalCleaned++;
            console.log(`✅ BULK_CLEANUP: Successfully cleaned up ${operationCount} operations for orphaned user ${uid}`);
            results.push({ uid, status: 'orphaned', action: 'cleaned', operations: operationCount });
            
          } catch (cleanupError) {
            console.error(`❌ BULK_CLEANUP: Error cleaning up user ${uid}:`, cleanupError);
            results.push({ uid, status: 'orphaned', action: 'error', error: cleanupError.message });
          }
          
        } else {
          // Other auth error - skip this user
          console.error(`🧹 BULK_CLEANUP: Auth error for user ${uid}:`, authError);
          results.push({ uid, status: 'auth_error', action: 'skipped', error: authError.message });
        }
      }
    }
    
    console.log(`✅ BULK_CLEANUP COMPLETE: Cleaned up ${totalCleaned} orphaned users out of ${usersSnapshot.docs.length} total users`);
    console.log(`🧹 BULK_CLEANUP_METRICS | total_users=${usersSnapshot.docs.length} | cleaned=${totalCleaned} | timestamp=${Date.now()}`);
    
    return {
      success: true,
      totalUsers: usersSnapshot.docs.length,
      orphanedCleaned: totalCleaned,
      results: results
    };
    
  } catch (error) {
    console.error('❌ BULK_CLEANUP FAILED:', error);
    throw new functions.https.HttpsError('internal', `Bulk cleanup failed: ${error.message}`);
  }
});

/**
 * 📊 MONITORING FUNCTION
 * =======================
 * 
 * Daily scheduled function to check for orphaned data and alert if needed.
 */
exports.detectOrphanedData = functions.pubsub.schedule('0 2 * * *') // Daily at 2 AM UTC
  .timeZone('UTC')
  .onRun(async (context) => {
    console.log('📊 MONITORING: Starting daily orphaned data detection');
    
    try {
      let orphanedCount = 0;
      
      // Get sample of user documents (limit to prevent timeout)
      const usersSnapshot = await db.collection('users').limit(100).get();
      
      for (const doc of usersSnapshot.docs) {
        const uid = doc.id;
        
        try {
          await admin.auth().getUser(uid);
          // User exists, not orphaned
        } catch (authError) {
          if (authError.code === 'auth/user-not-found') {
            orphanedCount++;
          }
        }
      }
      
      console.log(`📊 MONITORING: Found ${orphanedCount} orphaned profiles out of ${usersSnapshot.docs.length} checked`);
      
      // Alert if high number of orphaned profiles
      if (orphanedCount > 5) {
        console.error(`🚨 ALERT: HIGH ORPHANED DATA COUNT: ${orphanedCount} profiles detected - consider running bulk cleanup`);
      }
      
      console.log(`📊 MONITORING_METRICS | orphaned_count=${orphanedCount} | total_checked=${usersSnapshot.docs.length} | timestamp=${Date.now()}`);
      
    } catch (error) {
      console.error('📊 MONITORING ERROR:', error);
    }
  });