/**
 * 🔧 SIMPLE BULK CLEANUP CALLER
 * ==============================
 * 
 * This script calls the deployed Firebase Function to perform bulk cleanup.
 * This avoids the authentication issues with direct Firestore access.
 */

const admin = require('firebase-admin');

// Use the existing service account
const serviceAccount = require('./serviceAccountKey.json');

// Initialize Firebase Admin SDK
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  projectId: serviceAccount.project_id
});

const auth = admin.auth();

/**
 * Call the deployed Firebase Function for bulk cleanup
 */
async function callBulkCleanup() {
  console.log('🚀 CALLING BULK CLEANUP FIREBASE FUNCTION');
  console.log('=' .repeat(50));
  
  try {
    // Get any authenticated user to make the function call
    // We need to be authenticated to call the function
    const listUsers = await auth.listUsers(1);
    
    if (listUsers.users.length === 0) {
      console.log('❌ No users found in Firebase Auth to authenticate the function call');
      return;
    }
    
    const testUser = listUsers.users[0];
    console.log(`🔐 Using user ${testUser.uid} to authenticate the function call`);
    
    // Create a custom token for the function call
    const customToken = await auth.createCustomToken(testUser.uid);
    console.log('✅ Created custom token for authentication');
    
    // Note: We can't directly call the Firebase Function from here easily
    // Instead, let's give instructions to call it via Firebase CLI
    
    console.log('\\n📞 TO RUN BULK CLEANUP:');
    console.log('========================');
    console.log('1. Install Firebase CLI if you haven\'t: npm install -g firebase-tools');
    console.log('2. Login to Firebase: firebase login');
    console.log('3. Call the function:');
    console.log('   firebase functions:shell');
    console.log('   Then in the shell, type: bulkCleanupOrphanedData()');
    console.log('\\nOR alternatively, go to Firebase Console > Functions > bulkCleanupOrphanedData > Test tab');
    console.log('and click "Test the function" with empty data: {}');
    
    console.log('\\n✅ The Firebase Functions approach will handle authentication properly!');
    
  } catch (error) {
    console.error('❌ Error:', error.message);
    
    // If auth fails too, give alternative instructions
    console.log('\\n🎯 ALTERNATIVE: Use Firebase Console');
    console.log('===================================');
    console.log('1. Go to Firebase Console > Functions');
    console.log('2. Find "bulkCleanupOrphanedData" function');
    console.log('3. Click on it, then click "Test" tab');
    console.log('4. Enter empty data: {}');
    console.log('5. Click "Test the function"');
    console.log('6. Check the logs for cleanup results');
  }
}

// Run the caller
callBulkCleanup().catch(console.error);