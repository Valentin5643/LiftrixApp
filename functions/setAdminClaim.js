/**
 * Set Admin Custom Claim
 *
 * Usage: node setAdminClaim.js <user_email>
 *
 * This script grants admin privileges to a user by setting the 'admin' custom claim.
 * Required for accessing the admin moderation dashboard.
 */

const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

async function setAdminClaim(email) {
  try {
    // Get user by email
    const user = await admin.auth().getUserByEmail(email);

    // Set custom claim
    await admin.auth().setCustomUserClaims(user.uid, { admin: true });

    console.log(`✅ Admin claim set successfully for user: ${email}`);
    console.log(`User ID: ${user.uid}`);
    console.log('\nThe user must sign out and sign back in for the claim to take effect.');

    process.exit(0);
  } catch (error) {
    console.error('❌ Error setting admin claim:', error.message);
    process.exit(1);
  }
}

// Get email from command line argument
const email = process.argv[2];

if (!email) {
  console.error('❌ Error: Email address is required');
  console.log('\nUsage: node setAdminClaim.js <user_email>');
  console.log('Example: node setAdminClaim.js admin@example.com');
  process.exit(1);
}

setAdminClaim(email);
