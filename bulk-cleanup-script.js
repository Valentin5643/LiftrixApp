/**
 * 🔧 BULK CLEANUP SCRIPT FOR EXISTING ORPHANED DATA
 * ==================================================
 * 
 * This Node.js script finds and cleans up existing orphaned user data.
 * Run this ONCE to clean up existing orphaned profiles before the automatic
 * cleanup functions take over.
 * 
 * SETUP:
 * 1. Download your Firebase service account key from:
 *    Firebase Console > Project Settings > Service Accounts > Generate new private key
 * 2. Save it as 'service-account-key.json' in the same folder as this script
 * 3. Run: npm install firebase-admin
 * 4. Run: node bulk-cleanup-script.js
 */

const admin = require('firebase-admin');

// ⚠️ IMPORTANT: Replace this path with your service account key file
const serviceAccount = require('./serviceAccountKey.json');

// Initialize Firebase Admin SDK
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  projectId: serviceAccount.project_id
});

const db = admin.firestore();
const auth = admin.auth();

/**
 * Find all orphaned user profiles in Firestore
 */
async function findOrphanedProfiles() {
  console.log('🔍 SEARCHING: Looking for orphaned user profiles...');
  
  const orphanedUsers = [];
  
  try {
    // Get all user documents from Firestore
    const usersSnapshot = await db.collection('users').get();
    console.log(`📊 FOUND: ${usersSnapshot.docs.length} user documents in Firestore`);
    
    let checkedCount = 0;
    
    for (const doc of usersSnapshot.docs) {
      const uid = doc.id;
      checkedCount++;
      
      // Show progress every 10 users
      if (checkedCount % 10 === 0) {
        console.log(`📊 PROGRESS: Checked ${checkedCount}/${usersSnapshot.docs.length} users...`);
      }
      
      try {
        // Check if user exists in Firebase Auth
        await auth.getUser(uid);
        // User exists, not orphaned
        console.log(`✅ ACTIVE: User ${uid} exists in Firebase Auth`);
        
      } catch (error) {
        if (error.code === 'auth/user-not-found') {
          orphanedUsers.push(uid);
          console.log(`🚨 ORPHANED: User ${uid} not found in Firebase Auth - ORPHANED DATA DETECTED`);
        } else {
          console.error(`⚠️ ERROR: Could not check user ${uid}:`, error.message);
        }
      }
    }
    
    console.log(`\n📊 SUMMARY: Found ${orphanedUsers.length} orphaned users out of ${usersSnapshot.docs.length} total users`);
    
  } catch (error) {
    console.error('❌ SEARCH ERROR:', error);
    throw error;
  }
  
  return orphanedUsers;
}

/**
 * Clean up data for a single orphaned user
 */
async function cleanupOrphanedUser(uid) {
  console.log(`🧹 CLEANING: Starting cleanup for orphaned user: ${uid}`);
  
  try {
    let totalOperations = 0;
    
    // We'll use multiple smaller batches instead of one large batch
    // to avoid hitting Firestore limits
    
    // 1. Delete main user document
    console.log(`  - Deleting user profile for ${uid}`);
    await db.collection('users').doc(uid).delete();
    totalOperations++;
    
    // 2. Delete social profile document
    console.log(`  - Deleting social profile for ${uid}`);
    await db.collection('social_profiles').doc(uid).delete();
    totalOperations++;
    
    // 3. Delete subcollections in batches
    const collections = ['workouts', 'templates', 'achievements'];
    
    for (const collectionName of collections) {
      console.log(`  - Deleting ${collectionName} for ${uid}`);
      
      // Get subcollection documents in batches
      let hasMore = true;
      let deletedInCollection = 0;
      
      while (hasMore) {
        const subcollectionSnapshot = await db.collection('users').doc(uid)
          .collection(collectionName).limit(50).get();
        
        if (subcollectionSnapshot.empty) {
          hasMore = false;
          continue;
        }
        
        // Delete this batch
        const batch = db.batch();
        subcollectionSnapshot.docs.forEach(subDoc => {
          batch.delete(subDoc.ref);
        });
        
        await batch.commit();
        deletedInCollection += subcollectionSnapshot.docs.length;
        totalOperations += subcollectionSnapshot.docs.length;
        
        // If we got fewer than 50 docs, we're done with this collection
        if (subcollectionSnapshot.docs.length < 50) {
          hasMore = false;
        }
      }
      
      if (deletedInCollection > 0) {
        console.log(`    ✅ Deleted ${deletedInCollection} ${collectionName} documents`);
      }
    }
    
    // 4. Clean up follow relationships
    console.log(`  - Deleting follow relationships for ${uid}`);
    
    // Following relationships (where user is the follower)
    const followingSnapshot = await db.collection('follow_relationships')
      .where('follower_id', '==', uid).get();
    
    if (!followingSnapshot.empty) {
      const followingBatch = db.batch();
      followingSnapshot.docs.forEach(doc => {
        followingBatch.delete(doc.ref);
      });
      await followingBatch.commit();
      totalOperations += followingSnapshot.docs.length;
      console.log(`    ✅ Deleted ${followingSnapshot.docs.length} following relationships`);
    }
    
    // Follower relationships (where user is being followed)
    const followersSnapshot = await db.collection('follow_relationships')
      .where('followed_id', '==', uid).get();
    
    if (!followersSnapshot.empty) {
      const followersBatch = db.batch();
      followersSnapshot.docs.forEach(doc => {
        followersBatch.delete(doc.ref);
      });
      await followersBatch.commit();
      totalOperations += followersSnapshot.docs.length;
      console.log(`    ✅ Deleted ${followersSnapshot.docs.length} follower relationships`);
    }
    
    // 5. Clean up workout posts
    console.log(`  - Deleting workout posts for ${uid}`);
    const postsSnapshot = await db.collection('workout_posts')
      .where('user_id', '==', uid).get();
    
    if (!postsSnapshot.empty) {
      const postsBatch = db.batch();
      postsSnapshot.docs.forEach(doc => {
        postsBatch.delete(doc.ref);
      });
      await postsBatch.commit();
      totalOperations += postsSnapshot.docs.length;
      console.log(`    ✅ Deleted ${postsSnapshot.docs.length} workout posts`);
    }
    
    console.log(`✅ CLEANUP SUCCESS: Cleaned up ${totalOperations} documents/operations for user ${uid}`);
    
    return { success: true, operations: totalOperations };
    
  } catch (error) {
    console.error(`❌ CLEANUP ERROR: Failed to clean up user ${uid}:`, error);
    return { success: false, error: error.message };
  }
}

/**
 * Main cleanup execution
 */
async function main() {
  console.log('🚀 STARTING BULK CLEANUP OF ORPHANED DATA');
  console.log('='.repeat(50));
  
  try {
    // Step 1: Find orphaned profiles
    const orphanedUsers = await findOrphanedProfiles();
    
    if (orphanedUsers.length === 0) {
      console.log('🎉 NO ORPHANED DATA: All user profiles are properly linked to Firebase Auth users!');
      return;
    }
    
    console.log('\n' + '='.repeat(50));
    console.log(`🧹 CLEANUP PHASE: Processing ${orphanedUsers.length} orphaned users`);
    console.log('='.repeat(50));
    
    // Step 2: Clean up each orphaned user
    let successCount = 0;
    let errorCount = 0;
    let totalOperations = 0;
    
    for (let i = 0; i < orphanedUsers.length; i++) {
      const uid = orphanedUsers[i];
      console.log(`\n[${i + 1}/${orphanedUsers.length}] Processing orphaned user: ${uid}`);
      
      const result = await cleanupOrphanedUser(uid);
      
      if (result.success) {
        successCount++;
        totalOperations += result.operations;
      } else {
        errorCount++;
        console.error(`❌ FAILED to clean up user ${uid}: ${result.error}`);
      }
      
      // Add a small delay to avoid hitting rate limits
      await new Promise(resolve => setTimeout(resolve, 500));
    }
    
    // Step 3: Final summary
    console.log('\n' + '='.repeat(50));
    console.log('🎉 BULK CLEANUP COMPLETED!');
    console.log('='.repeat(50));
    console.log(`📊 TOTAL ORPHANED USERS: ${orphanedUsers.length}`);
    console.log(`✅ SUCCESSFULLY CLEANED: ${successCount}`);
    console.log(`❌ ERRORS: ${errorCount}`);
    console.log(`📈 TOTAL OPERATIONS: ${totalOperations}`);
    console.log('='.repeat(50));
    
    if (successCount > 0) {
      console.log('✅ Your Firestore database is now clean of orphaned user data!');
      console.log('🔥 The Firebase Functions will handle automatic cleanup going forward.');
    }
    
    if (errorCount > 0) {
      console.log(`⚠️ Some users could not be cleaned up. Check the error messages above.`);
    }
    
  } catch (error) {
    console.error('❌ BULK CLEANUP FAILED:', error);
    process.exit(1);
  }
}

// Show instructions if service account key is missing
try {
  require('./serviceAccountKey.json');
} catch (error) {
  console.error('❌ SETUP ERROR: serviceAccountKey.json not found!');
  console.log('\n📋 SETUP INSTRUCTIONS:');
  console.log('1. Go to Firebase Console > Project Settings > Service Accounts');
  console.log('2. Click "Generate new private key"');
  console.log('3. Save the downloaded file as "serviceAccountKey.json" in this folder');
  console.log('4. Run: npm install firebase-admin');
  console.log('5. Run: node bulk-cleanup-script.js');
  process.exit(1);
}

// Run the cleanup
main().catch(error => {
  console.error('FATAL ERROR:', error);
  process.exit(1);
});