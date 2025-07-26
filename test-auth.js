// Firebase Authentication Test Script
// Run with: node test-auth.js

const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  projectId: 'liftrix-390cf',
  databaseURL: 'https://liftrix-390cf-default-rtdb.firebaseio.com'
});

async function testAuthConfiguration() {
  console.log('Testing Firebase Authentication configuration...\n');

  try {
    // Test 1: List existing users (should work if auth is set up)
    const listUsersResult = await admin.auth().listUsers(5);
    console.log('✅ Auth service accessible');
    console.log(`Found ${listUsersResult.users.length} existing users`);

    // Test 2: Create a test user
    const testUserEmail = 'test-user@liftrix.app';
    
    try {
      // Try to delete test user first (cleanup from previous runs)
      try {
        const existingUser = await admin.auth().getUserByEmail(testUserEmail);
        await admin.auth().deleteUser(existingUser.uid);
        console.log('🧹 Cleaned up existing test user');
      } catch (e) {
        // User doesn't exist, that's fine
      }

      // Create new test user
      const userRecord = await admin.auth().createUser({
        email: testUserEmail,
        password: 'testPassword123!',
        displayName: 'Test User',
        emailVerified: false
      });

      console.log('✅ Test user created successfully');
      console.log(`User UID: ${userRecord.uid}`);

      // Test 3: Set custom claims
      const customClaims = {
        premium: false,
        tier: 'free',
        test_user: true
      };

      await admin.auth().setCustomUserClaims(userRecord.uid, customClaims);
      console.log('✅ Custom claims set successfully');

      // Test 4: Verify custom claims
      const userWithClaims = await admin.auth().getUser(userRecord.uid);
      console.log('✅ Custom claims verified:', userWithClaims.customClaims);

      // Test 5: Generate custom token (for testing client auth)
      const customToken = await admin.auth().createCustomToken(userRecord.uid);
      console.log('✅ Custom token generated (length:', customToken.length, 'chars)');

      // Test 6: Generate email verification link
      const verificationLink = await admin.auth().generateEmailVerificationLink(testUserEmail);
      console.log('✅ Email verification link generated');

      // Test 7: Generate password reset link
      const resetLink = await admin.auth().generatePasswordResetLink(testUserEmail);
      console.log('✅ Password reset link generated');

      // Cleanup: Delete test user
      await admin.auth().deleteUser(userRecord.uid);
      console.log('🧹 Test user cleaned up');

      console.log('\n🎉 All authentication tests passed!');
      console.log('\nAuthentication configuration is working correctly.');
      
      console.log('\nNext steps:');
      console.log('1. Verify sign-in methods in Firebase Console');
      console.log('2. Configure email templates');
      console.log('3. Set up authorized domains for production');

    } catch (userError) {
      console.error('❌ Error with user operations:', userError.message);
      throw userError;
    }

  } catch (error) {
    console.error('❌ Authentication test failed:', error.message);
    
    if (error.code === 'auth/insufficient-permission') {
      console.log('\n💡 This might be a permissions issue with your service account.');
      console.log('Make sure your service account has Firebase Authentication Admin role.');
    }
    
    throw error;
  }
}

// Test current auth providers
async function checkAuthProviders() {
  console.log('\n--- Authentication Providers Check ---');
  
  try {
    // Note: Admin SDK doesn't directly list auth providers
    // This would need to be checked manually in Firebase Console
    console.log('📋 Please verify these providers are enabled in Firebase Console:');
    console.log('   → Authentication → Sign-in method');
    console.log('   ✓ Email/Password');
    console.log('   ✓ Google (recommended)');
    console.log('   ✓ Facebook (optional)');
    console.log('   ✓ Apple (optional for iOS)');
    
  } catch (error) {
    console.error('❌ Error checking auth providers:', error.message);
  }
}

// Run all tests
async function runAllTests() {
  try {
    await testAuthConfiguration();
    await checkAuthProviders();
    
    console.log('\n✨ Authentication setup verification complete!');
    process.exit(0);
    
  } catch (error) {
    console.error('\n💥 Authentication setup verification failed!');
    console.error('Error details:', error.message);
    process.exit(1);
  }
}

runAllTests();