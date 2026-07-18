#!/usr/bin/env node

/**
 * Call the deployed setAdminClaim Cloud Function
 * Usage: node callSetAdmin.js <email>
 */

const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');

// Initialize with project ID (will use default credentials)
const app = initializeApp({
  projectId: 'liftrix-390cf'
});

const auth = getAuth(app);

async function setAdminClaim(email) {
  try {
    console.log(`\n🔍 Looking up user: ${email}...`);
    const user = await auth.getUserByEmail(email);

    console.log(`✅ Found user: ${user.uid}`);
    console.log(`📝 Setting admin claim...`);

    // Set the custom claim directly
    await auth.setCustomUserClaims(user.uid, { admin: true });

    console.log(`\n✅ SUCCESS! Admin privileges granted to: ${email}`);
    console.log(`   User ID: ${user.uid}`);
    console.log(`\n⚠️  IMPORTANT: User must sign out and sign back in for changes to take effect.\n`);

    process.exit(0);
  } catch (error) {
    console.error(`\n❌ Error: ${error.message}\n`);

    if (error.code === 'auth/user-not-found') {
      console.error(`User with email ${email} not found.`);
      console.error(`Please make sure the user has signed up first.\n`);
    }

    process.exit(1);
  }
}

const email = process.argv[2];

if (!email) {
  console.error('\n❌ Usage: node callSetAdmin.js <email>\n');
  console.error('Example: node callSetAdmin.js admin@example.com\n');
  process.exit(1);
}

setAdminClaim(email);
