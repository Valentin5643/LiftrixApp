#!/usr/bin/env node

/**
 * Simple script to grant admin privileges using Firebase CLI authentication
 * Usage: node setAdminClaimCLI.js <email>
 */

const admin = require('firebase-admin');

// Initialize without service account (uses Application Default Credentials from Firebase CLI)
admin.initializeApp({
  projectId: 'liftrix-390cf'
});

async function setAdminClaim(email) {
  try {
    console.log(`Looking up user: ${email}...`);
    const user = await admin.auth().getUserByEmail(email);

    console.log(`Found user: ${user.uid}`);
    console.log(`Setting admin claim...`);

    await admin.auth().setCustomUserClaims(user.uid, { admin: true });

    console.log(`✅ Admin claim set successfully for user: ${email}`);
    console.log(`User ID: ${user.uid}`);
    console.log('\nThe user needs to sign out and sign back in for the claim to take effect.');

    process.exit(0);
  } catch (error) {
    console.error('❌ Error setting admin claim:', error.message);
    process.exit(1);
  }
}

const email = process.argv[2];

if (!email) {
  console.error('Usage: node setAdminClaimCLI.js <email>');
  process.exit(1);
}

setAdminClaim(email);
